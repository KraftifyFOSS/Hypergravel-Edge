package media.gitm.hypergravel.proxy.queue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import media.gitm.hypergravel.api.event.PreServerConnectEvent;
import media.gitm.hypergravel.api.player.Player;
import media.gitm.hypergravel.proxy.HyperGravelProxy;
import media.gitm.hypergravel.proxy.backend.HyperGravelServer;
import media.gitm.hypergravel.proxy.config.HyperGravelConfig;
import media.gitm.hypergravel.proxy.player.ConnectedPlayer;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class QueueService {

    private static final Logger LOGGER = LogManager.getLogger(QueueService.class);

    public static final String CHANNEL = "hypergravel:queue";

    private static final Duration TICK = Duration.ofSeconds(1);

    private static final long POSITION_INTERVAL_MILLIS = 2000;

    private static final long RATE_WINDOW_MILLIS = 120_000;

    private final HyperGravelProxy proxy;

    private final Deque<Waiting> waiting = new ArrayDeque<>();

    private final Set<UUID> queued = new HashSet<>();

    private final Set<UUID> releasing = new HashSet<>();

    private final AtomicLong tickets = new AtomicLong();
    private final Deque<Long> recentReleases = new ArrayDeque<>();

    private long lastPositionPush;
    private volatile int lastReportedSize;

    private record Waiting(ConnectedPlayer player, long ticket, long since) {}

    public QueueService(HyperGravelProxy proxy) {
        this.proxy = proxy;
    }

    private HyperGravelConfig.Queue settings() {
        return proxy.config().queue();
    }

    public boolean enabled() {
        return settings().enabled();
    }

    public int size() {
        synchronized (waiting) {
            return waiting.size();
        }
    }

    public int occupancy() {
        String holding = settings().server();
        int count = 0;
        synchronized (waiting) {
            for (ConnectedPlayer player : proxy.playerRegistry().all()) {
                UUID id = player.uuid();
                if (queued.contains(id)) {
                    continue;
                }
                String on = player.currentHyperGravelServer()
                        .map(HyperGravelServer::name).orElse(null);
                if (on == null) {
                    count++;
                } else if (!on.equals(holding)) {
                    count++;
                } else if (releasing.contains(id)) {
                    count++;
                }
            }
        }
        return count;
    }

    public int slots() {
        return settings().slots();
    }

    public void start() {
        if (!enabled()) {
            return;
        }
        LOGGER.info("queue: holding on '{}', {} slots, releasing up to {}/s",
                settings().server(), settings().slots(), settings().releasePerSecond());
        proxy.scheduler().repeat(this, TICK, TICK, this::pump);
    }

    public boolean admit(ConnectedPlayer player) {
        if (!enabled()) {
            return false;
        }
        if (player.hasPermission(settings().bypassPermission())) {
            return false;
        }

        if (occupancy() < settings().slots() && size() == 0) {
            return false;
        }

        HyperGravelServer holding = proxy.serverRegistry().get(settings().server()).orElse(null);
        if (holding == null || !holding.health().routable()) {

            LOGGER.warn("queue: holding server '{}' is unavailable; admitting {} directly",
                    settings().server(), player.username());
            return false;
        }

        long ticket = tickets.incrementAndGet();
        synchronized (waiting) {
            if (!queued.add(player.uuid())) {
                return true;
            }
            waiting.addLast(new Waiting(player, ticket, System.currentTimeMillis()));
        }

        proxy.connector().connect(player, holding, PreServerConnectEvent.Reason.INITIAL)
                .thenAccept(result -> {
                    if (result != Player.ConnectionResult.SUCCESS) {
                        LOGGER.warn("queue: could not park {} ({})", player.username(), result);
                        forget(player.uuid());
                        player.disconnect(MiniMessage.miniMessage().deserialize(
                                "<red>The server is full.\n<gray>Try again in a moment."));
                    }
                });
        return true;
    }

    public void forget(UUID uuid) {
        synchronized (waiting) {
            if (queued.remove(uuid)) {
                waiting.removeIf(entry -> entry.player().uuid().equals(uuid));
            }
            releasing.remove(uuid);
        }
    }

    private void pump() {
        try {
            prune();
            release();
            pushPositions();
        } catch (Exception e) {
            LOGGER.error("queue: pump failed", e);
        }
    }

    private void prune() {
        synchronized (waiting) {
            Iterator<Waiting> it = waiting.iterator();
            while (it.hasNext()) {
                Waiting entry = it.next();
                if (!entry.player().connection().active()) {
                    it.remove();
                    queued.remove(entry.player().uuid());
                }
            }
        }
    }

    private void release() {
        int free = settings().slots() - occupancy();
        if (free <= 0) {
            return;
        }
        int allowed = Math.min(free, Math.max(1, settings().releasePerSecond()));

        List<Waiting> batch = new ArrayList<>(allowed);
        synchronized (waiting) {
            while (batch.size() < allowed && !waiting.isEmpty()) {
                Waiting next = waiting.pollFirst();
                queued.remove(next.player().uuid());
                if (next.player().connection().active()) {
                    releasing.add(next.player().uuid());
                    batch.add(next);
                }
            }
        }
        for (Waiting entry : batch) {
            letIn(entry);
        }
    }

    private void letIn(Waiting entry) {
        ConnectedPlayer player = entry.player();
        List<HyperGravelServer> candidates = proxy.serverRegistry().routableTryOrder();
        if (candidates.isEmpty()) {
            requeueAtFront(entry);
            return;
        }
        long waited = (System.currentTimeMillis() - entry.since()) / 1000;
        player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<green>You're in!<gray> Sending you through" + (waited > 5
                        ? " after " + describe(waited) + "." : ".")));

        proxy.connector().connect(player, candidates.get(0), PreServerConnectEvent.Reason.INITIAL)
                .thenAccept(result -> {
                    synchronized (waiting) {
                        releasing.remove(player.uuid());
                    }
                    if (result == Player.ConnectionResult.SUCCESS) {
                        recordRelease();
                        LOGGER.info("queue: let {} in after {}s ({} still waiting)",
                                player.username(), waited, size());
                    } else if (player.connection().active()) {

                        LOGGER.warn("queue: {} could not be let in ({}), returning to front",
                                player.username(), result);
                        requeueAtFront(entry);
                    }
                });
    }

    private void requeueAtFront(Waiting entry) {
        synchronized (waiting) {
            releasing.remove(entry.player().uuid());
            if (queued.add(entry.player().uuid())) {
                waiting.addFirst(entry);
            }
        }
    }

    private void recordRelease() {
        long now = System.currentTimeMillis();
        synchronized (recentReleases) {
            recentReleases.addLast(now);
            while (!recentReleases.isEmpty() && now - recentReleases.peekFirst() > RATE_WINDOW_MILLIS) {
                recentReleases.removeFirst();
            }
        }
    }

    private double drainPerMinute() {
        long now = System.currentTimeMillis();
        synchronized (recentReleases) {
            while (!recentReleases.isEmpty() && now - recentReleases.peekFirst() > RATE_WINDOW_MILLIS) {
                recentReleases.removeFirst();
            }
            if (recentReleases.isEmpty()) {
                return 0;
            }
            long span = Math.max(15_000, now - recentReleases.peekFirst());
            return recentReleases.size() * 60_000.0 / span;
        }
    }

    private void pushPositions() {
        long now = System.currentTimeMillis();
        if (now - lastPositionPush < POSITION_INTERVAL_MILLIS) {
            return;
        }
        lastPositionPush = now;

        List<Waiting> snapshot;
        synchronized (waiting) {
            snapshot = new ArrayList<>(waiting);
        }
        lastReportedSize = snapshot.size();
        if (snapshot.isEmpty()) {
            return;
        }

        int total = snapshot.size();
        double perMinute = drainPerMinute();
        int position = 0;
        for (Waiting entry : snapshot) {
            position++;
            int eta = perMinute > 0.01 ? (int) Math.ceil(position / perMinute * 60) : -1;
            byte[] message = encodePosition(position, total, eta,
                    (int) ((now - entry.since()) / 1000));
            if (message != null) {
                entry.player().sendPluginMessageToBackend(CHANNEL, message);
            }
        }
    }

    private static byte[] encodePosition(int position, int total, int etaSeconds, int waitedSeconds) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(32);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("QUEUE_POSITION");
            out.writeInt(position);
            out.writeInt(total);
            out.writeInt(etaSeconds);
            out.writeInt(waitedSeconds);
        } catch (IOException e) {
            return null;
        }
        return bytes.toByteArray();
    }

    private static String describe(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        return minutes + "m " + (seconds % 60) + "s";
    }

    public String summary() {
        return String.format("%d waiting, %d/%d slots used, draining %.1f/min",
                lastReportedSize, occupancy(), settings().slots(), drainPerMinute());
    }
}
