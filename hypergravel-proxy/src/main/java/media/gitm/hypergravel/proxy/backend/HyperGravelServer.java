package media.gitm.hypergravel.proxy.backend;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import media.gitm.hypergravel.api.player.Player;
import media.gitm.hypergravel.api.server.RegisteredServer;
import media.gitm.hypergravel.api.server.ServerHealth;
import media.gitm.hypergravel.api.server.ServerInfo;
import media.gitm.hypergravel.api.server.ServerPing;
import media.gitm.hypergravel.proxy.player.ConnectedPlayer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;

public final class HyperGravelServer implements RegisteredServer, ForwardingAudience {

    private final ServerInfo info;
    private final Set<ConnectedPlayer> players = ConcurrentHashMap.newKeySet();
    private final AtomicReference<ServerHealth> health =
            new AtomicReference<>(ServerHealth.UNKNOWN);
    private final AtomicLong latencyMillis = new AtomicLong(-1);
    private final AtomicLong lastSeenUp = new AtomicLong();

    private int consecutiveFailures;

    private int consecutiveSuccesses;

    private volatile InetSocketAddress resolvedAddress;

    public HyperGravelServer(ServerInfo info) {
        this.info = info;
    }

    @Override
    public ServerInfo info() {
        return info;
    }

    @Override
    public Collection<Player> players() {
        return Collections.unmodifiableCollection(players);
    }

    public Set<ConnectedPlayer> connectedPlayers() {
        return players;
    }

    @Override
    public int playerCount() {
        return players.size();
    }

    public void addPlayer(ConnectedPlayer player) {
        players.add(player);
    }

    public void removePlayer(ConnectedPlayer player) {
        players.remove(player);
    }

    @Override
    public ServerHealth health() {
        return health.get();
    }

    public ServerHealth setHealth(ServerHealth newHealth) {
        ServerHealth previous = health.getAndSet(newHealth);
        if (newHealth == ServerHealth.UP) {
            lastSeenUp.set(System.currentTimeMillis());
        }
        return previous;
    }

    @Override
    public long latencyMillis() {
        return latencyMillis.get();
    }

    public void recordLatency(long millis) {
        latencyMillis.set(millis);
    }

    public long lastSeenUpMillis() {
        return lastSeenUp.get();
    }

    public int recordFailure() {
        consecutiveSuccesses = 0;
        return ++consecutiveFailures;
    }

    public int recordSuccess() {
        consecutiveFailures = 0;
        return ++consecutiveSuccesses;
    }

    public InetSocketAddress address() {
        InetSocketAddress cached = resolvedAddress;
        if (cached != null && !cached.isUnresolved()) {
            return cached;
        }
        InetSocketAddress configured = (InetSocketAddress) info.address();
        InetSocketAddress resolved = configured.isUnresolved()
                ? new InetSocketAddress(configured.getHostString(), configured.getPort())
                : configured;
        this.resolvedAddress = resolved;
        return resolved;
    }

    public void invalidateAddress() {
        this.resolvedAddress = null;
    }

    @Override
    public boolean sendPluginMessage(String channel, byte[] data) {
        for (ConnectedPlayer player : players) {
            if (player.sendPluginMessageToBackend(channel, data)) {

                return true;
            }
        }
        return false;
    }

    @Override
    public CompletableFuture<ServerPing> ping() {
        return ServerPinger.ping(this);
    }

    @Override
    public Iterable<? extends Audience> audiences() {
        return players;
    }

    @Override
    public String toString() {
        return "HyperGravelServer{" + info.name() + " " + info.address() + " " + health.get() + '}';
    }
}
