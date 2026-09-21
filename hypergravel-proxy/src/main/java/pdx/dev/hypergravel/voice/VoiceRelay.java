package pdx.dev.hypergravel.voice;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class VoiceRelay {

    private static final Logger LOGGER = LogManager.getLogger(VoiceRelay.class);

    private static final byte MAGIC = (byte) 0xFF;

    private static final int MAX_DATAGRAM = 4096;

    private static final UUID PING_V1 = UUID.fromString("58bc9ae9-c7a8-45e4-a11c-efbb67199425");
    private static final int PING_PAYLOAD = 24;

    private static final long IDLE_TIMEOUT_MILLIS = 300_000L;
    private static final long SWEEP_INTERVAL_MILLIS = 30_000L;

    interface Endpoints {

        UUID resolvePlayer(UUID fromDatagram);

        SocketAddress backendAddressFor(UUID player);
    }

    private final Endpoints endpoints;
    private final boolean allowPings;
    private final Map<UUID, Bridge> bridges = new ConcurrentHashMap<>();

    private volatile DatagramSocket socket;
    private volatile boolean running;
    private Thread receiver;
    private Thread sweeper;

    VoiceRelay(Endpoints endpoints, boolean allowPings) {
        this.endpoints = endpoints;
        this.allowPings = allowPings;
    }

    void start(String bindHost, int port) throws IOException {
        InetAddress address = bindHost == null || bindHost.isBlank() || "*".equals(bindHost.trim())
                ? null
                : InetAddress.getByName(bindHost.trim());
        this.socket = address == null ? new DatagramSocket(port) : new DatagramSocket(port, address);
        this.running = true;

        receiver = new Thread(this::receiveLoop, "hypergravel-voice");
        receiver.setDaemon(true);
        receiver.start();

        sweeper = new Thread(this::sweepLoop, "hypergravel-voice-sweep");
        sweeper.setDaemon(true);
        sweeper.start();
    }

    void stop() {
        running = false;
        DatagramSocket current = socket;
        if (current != null) {
            current.close();
        }
        for (Bridge bridge : bridges.values()) {
            bridge.close();
        }
        bridges.clear();
        if (receiver != null) {
            receiver.interrupt();
        }
        if (sweeper != null) {
            sweeper.interrupt();
        }
    }

    void close(UUID player) {
        Bridge bridge = bridges.remove(player);
        if (bridge != null) {
            bridge.close();
        }
    }

    int bridgeCount() {
        return bridges.size();
    }

    int boundPort() {
        DatagramSocket current = socket;
        return current == null ? -1 : current.getLocalPort();
    }

    private void receiveLoop() {
        byte[] buffer = new byte[MAX_DATAGRAM];
        while (running && !socket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                handle(packet);
            } catch (Exception e) {
                if (running && !socket.isClosed()) {
                    LOGGER.debug("voice: dropped an inbound datagram", e);
                }
            }
        }
    }

    private void handle(DatagramPacket packet) throws IOException {
        ByteBuffer view = ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength());
        if (view.remaining() < 17 || view.get() != MAGIC) {
            return;
        }
        UUID id = new UUID(view.getLong(), view.getLong());

        if (PING_V1.equals(id)) {
            if (allowPings) {
                replyToPing(packet.getSocketAddress(), view);
            }
            return;
        }

        UUID player = endpoints.resolvePlayer(id);
        Bridge bridge = bridgeFor(player, packet.getSocketAddress());
        if (bridge != null) {
            bridge.forward(packet);
        }
    }

    private void replyToPing(SocketAddress from, ByteBuffer view) {
        if (view.remaining() < PING_PAYLOAD) {
            return;
        }
        byte[] payload = new byte[PING_PAYLOAD];
        view.get(payload);
        try {
            socket.send(new DatagramPacket(payload, payload.length, from));
        } catch (IOException e) {
            LOGGER.debug("voice: could not answer a ping from {}", from, e);
        }
    }

    private Bridge bridgeFor(UUID player, SocketAddress clientAddress) {
        Bridge existing = bridges.get(player);
        if (existing != null) {
            existing.seen(clientAddress);
            return existing;
        }
        SocketAddress backend = endpoints.backendAddressFor(player);
        if (backend == null) {

            return null;
        }
        return bridges.computeIfAbsent(player, uuid -> {
            try {
                Bridge bridge = new Bridge(uuid, clientAddress, backend);
                bridge.start();
                LOGGER.debug("voice: bridged {} to {}", uuid, backend);
                return bridge;
            } catch (IOException e) {
                LOGGER.warn("voice: could not open a bridge for {}: {}", uuid, e.toString());
                return null;
            }
        });
    }

    private void sweepLoop() {
        while (running) {
            try {
                Thread.sleep(SWEEP_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                return;
            }
            long cutoff = System.currentTimeMillis() - IDLE_TIMEOUT_MILLIS;
            for (Bridge bridge : bridges.values()) {
                if (bridge.lastSeenMillis < cutoff) {
                    LOGGER.debug("voice: bridge for {} went idle", bridge.player);
                    close(bridge.player);
                }
            }
        }
    }

    private final class Bridge {

        private final UUID player;
        private final SocketAddress backend;
        private final DatagramSocket upstream;
        private final Thread pump;

        private volatile SocketAddress client;
        private volatile long lastSeenMillis = System.currentTimeMillis();

        Bridge(UUID player, SocketAddress client, SocketAddress backend) throws IOException {
            this.player = player;
            this.client = client;
            this.backend = backend;
            this.upstream = new DatagramSocket();
            this.pump = new Thread(this::pumpLoop, "hypergravel-voice-" + player);
            this.pump.setDaemon(true);
        }

        void start() {
            pump.start();
        }

        void seen(SocketAddress from) {
            lastSeenMillis = System.currentTimeMillis();
            if (!from.equals(client)) {
                client = from;
            }
        }

        void forward(DatagramPacket packet) throws IOException {
            if (upstream.isClosed()) {
                return;
            }
            upstream.send(new DatagramPacket(packet.getData(), packet.getOffset(),
                    packet.getLength(), backend));
        }

        private void pumpLoop() {
            byte[] buffer = new byte[MAX_DATAGRAM];
            while (!upstream.isClosed()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    upstream.receive(packet);
                    lastSeenMillis = System.currentTimeMillis();
                    packet.setSocketAddress(client);
                    socket.send(packet);
                } catch (Exception e) {
                    if (!upstream.isClosed() && running) {
                        LOGGER.debug("voice: bridge for {} dropped a reply", player, e);
                    }
                }
            }
        }

        void close() {
            upstream.close();
            pump.interrupt();
        }
    }
}
