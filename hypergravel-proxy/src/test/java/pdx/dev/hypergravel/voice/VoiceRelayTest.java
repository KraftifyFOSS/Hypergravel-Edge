package pdx.dev.hypergravel.voice;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VoiceRelayTest {

    private static final UUID PLAYER = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");
    private static final int TIMEOUT_MILLIS = 2000;

    private VoiceRelay relay;
    private DatagramSocket backend;
    private DatagramSocket client;

    @AfterEach
    void tearDown() {
        if (relay != null) {
            relay.stop();
        }
        if (backend != null) {
            backend.close();
        }
        if (client != null) {
            client.close();
        }
    }

    private static byte[] datagram(UUID id, String body) {
        byte[] payload = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(17 + payload.length);
        buf.put((byte) 0xFF);
        buf.putLong(id.getMostSignificantBits());
        buf.putLong(id.getLeastSignificantBits());
        buf.put(payload);
        return buf.array();
    }

    private void startRelay(VoiceRelay.Endpoints endpoints) throws Exception {
        relay = new VoiceRelay(endpoints, true);
        relay.start("127.0.0.1", 0);
    }

    private DatagramPacket receive(DatagramSocket socket) throws Exception {
        socket.setSoTimeout(TIMEOUT_MILLIS);
        DatagramPacket packet = new DatagramPacket(new byte[4096], 4096);
        socket.receive(packet);
        return packet;
    }

    private static byte[] contents(DatagramPacket packet) {
        byte[] out = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), packet.getOffset(), out, 0, out.length);
        return out;
    }

    @Test
    void carriesAudioToTheBackendAndTheReplyBack() throws Exception {
        backend = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        SocketAddress backendAddress =
                new InetSocketAddress(InetAddress.getLoopbackAddress(), backend.getLocalPort());
        startRelay(new VoiceRelay.Endpoints() {
            @Override
            public UUID resolvePlayer(UUID fromDatagram) {
                return fromDatagram;
            }

            @Override
            public SocketAddress backendAddressFor(UUID player) {
                return PLAYER.equals(player) ? backendAddress : null;
            }
        });

        client = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        byte[] outbound = datagram(PLAYER, "hello");
        client.send(new DatagramPacket(outbound, outbound.length,
                InetAddress.getLoopbackAddress(), relay.boundPort()));

        DatagramPacket atBackend = receive(backend);
        assertArrayEquals(outbound, contents(atBackend));
        assertEquals(1, relay.bridgeCount());

        byte[] reply = datagram(PLAYER, "hi back");
        backend.send(new DatagramPacket(reply, reply.length, atBackend.getSocketAddress()));

        DatagramPacket atClient = receive(client);
        assertArrayEquals(reply, contents(atClient));
        assertEquals(relay.boundPort(), ((InetSocketAddress) atClient.getSocketAddress()).getPort());
    }

    @Test
    void bridgesNobodyWeHaveNotSeenASecretFor() throws Exception {
        startRelay(new VoiceRelay.Endpoints() {
            @Override
            public UUID resolvePlayer(UUID fromDatagram) {
                return fromDatagram;
            }

            @Override
            public SocketAddress backendAddressFor(UUID player) {
                return null;
            }
        });

        client = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        byte[] outbound = datagram(UUID.randomUUID(), "who is this");
        client.send(new DatagramPacket(outbound, outbound.length,
                InetAddress.getLoopbackAddress(), relay.boundPort()));

        client.setSoTimeout(200);
        assertThrows(java.net.SocketTimeoutException.class,
                () -> client.receive(new DatagramPacket(new byte[64], 64)));
        assertEquals(0, relay.bridgeCount());
    }

    @Test
    void answersAPingWithoutABackend() throws Exception {
        startRelay(new VoiceRelay.Endpoints() {
            @Override
            public UUID resolvePlayer(UUID fromDatagram) {
                throw new AssertionError("a ping must never be looked up as a player");
            }

            @Override
            public SocketAddress backendAddressFor(UUID player) {
                throw new AssertionError("a ping must never open a bridge");
            }
        });

        UUID pingId = UUID.fromString("58bc9ae9-c7a8-45e4-a11c-efbb67199425");
        ByteBuffer payload = ByteBuffer.allocate(24);
        payload.putLong(1L);
        payload.putLong(2L);
        payload.putLong(1234567890L);

        ByteBuffer ping = ByteBuffer.allocate(17 + 24);
        ping.put((byte) 0xFF);
        ping.putLong(pingId.getMostSignificantBits());
        ping.putLong(pingId.getLeastSignificantBits());
        ping.put(payload.array());

        client = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        client.send(new DatagramPacket(ping.array(), ping.array().length,
                InetAddress.getLoopbackAddress(), relay.boundPort()));

        assertArrayEquals(payload.array(), contents(receive(client)));
    }
}
