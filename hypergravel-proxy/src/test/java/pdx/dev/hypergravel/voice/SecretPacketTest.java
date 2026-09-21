package pdx.dev.hypergravel.voice;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecretPacketTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final byte[] SECRET = {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
    };

    private static byte[] payload(int serverPort, String voiceHost) {
        byte[] host = voiceHost.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(64 + host.length);
        buf.put(SECRET);
        buf.putInt(serverPort);
        buf.putLong(PLAYER.getMostSignificantBits());
        buf.putLong(PLAYER.getLeastSignificantBits());
        buf.put((byte) 1);
        buf.putInt(1024);
        buf.putDouble(48.0);
        buf.putInt(1000);
        buf.put((byte) 1);
        buf.put((byte) host.length);
        buf.put(host);
        buf.put((byte) 0);
        byte[] out = new byte[buf.position()];
        buf.flip();
        buf.get(out);
        return out;
    }

    @Test
    void readsThePortAndPlayerTheBackendSent() throws Exception {
        SecretPacket packet = SecretPacket.read(payload(24521, ""), 20);

        assertEquals(24521, packet.serverPort());
        assertEquals(PLAYER, packet.playerUuid());
    }

    @Test
    void rewritesOnlyTheAddressFields() throws Exception {
        SecretPacket packet = SecretPacket.read(payload(24521, ""), 20);
        packet.redirectTo("voice.hypergravel.net", 24530);

        assertArrayEquals(payload(24530, "voice.hypergravel.net"), packet.write());
    }

    @Test
    void survivesAHostThatWasAlreadySet() throws Exception {
        SecretPacket packet = SecretPacket.read(payload(24521, "backend.internal:24521"), 20);
        packet.redirectTo("", 24530);

        assertArrayEquals(payload(24530, ""), packet.write());
    }

    @Test
    void refusesVersionsItCannotReadRatherThanGuessing() {
        assertThrows(IncompatibleVoiceException.class, () -> SecretPacket.read(payload(1, ""), 9));
        assertThrows(IncompatibleVoiceException.class, () -> SecretPacket.read(payload(1, ""), 19));
        assertThrows(IncompatibleVoiceException.class, () -> SecretPacket.read(payload(1, ""), 21));
    }

    @Test
    void refusesATruncatedPacketRatherThanHalfReadingIt() {
        byte[] full = payload(24521, "");
        byte[] short1 = new byte[full.length - 3];
        System.arraycopy(full, 0, short1, 0, short1.length);

        assertThrows(IncompatibleVoiceException.class, () -> SecretPacket.read(short1, 20));
    }
}
