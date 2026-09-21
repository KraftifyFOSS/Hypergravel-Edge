package pdx.dev.hypergravel.voice;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class SecretPacket {

    static final int OLDEST_COMPATIBILITY = 10;

    static final int BROKEN_COMPATIBILITY = 19;

    static final int NEWEST_COMPATIBILITY = 20;

    private byte[] secret;
    private int serverPort;
    private UUID playerUuid;
    private byte codec;
    private int mtuSize;
    private double voiceChatDistance;
    private int keepAlive;
    private boolean groupsEnabled;
    private String voiceHost;
    private boolean allowRecording;

    private SecretPacket() {
    }

    static SecretPacket read(byte[] payload, int compatibilityVersion)
            throws IncompatibleVoiceException {
        if (compatibilityVersion < OLDEST_COMPATIBILITY) {
            throw new IncompatibleVoiceException(
                    "outdated voice chat compatibility version " + compatibilityVersion);
        }
        if (compatibilityVersion == BROKEN_COMPATIBILITY) {
            throw new IncompatibleVoiceException(
                    "unsupported voice chat compatibility version " + compatibilityVersion);
        }
        if (compatibilityVersion > NEWEST_COMPATIBILITY) {
            throw new IncompatibleVoiceException(
                    "newer voice chat compatibility version " + compatibilityVersion);
        }

        ByteBuffer buf = ByteBuffer.wrap(payload);
        SecretPacket packet = new SecretPacket();
        try {
            packet.secret = new byte[16];
            buf.get(packet.secret);
            packet.serverPort = buf.getInt();
            packet.playerUuid = new UUID(buf.getLong(), buf.getLong());
            packet.codec = buf.get();
            packet.mtuSize = buf.getInt();
            packet.voiceChatDistance = buf.getDouble();
            packet.keepAlive = buf.getInt();
            packet.groupsEnabled = buf.get() != 0;
            packet.voiceHost = readUtf(buf);
            packet.allowRecording = buf.get() != 0;
        } catch (BufferUnderflowException | IllegalArgumentException e) {
            throw new IncompatibleVoiceException("malformed secret packet: " + e);
        }
        return packet;
    }

    void redirectTo(String host, int port) {
        this.serverPort = port;
        this.voiceHost = host == null ? "" : host;
    }

    byte[] write() {
        byte[] hostBytes = voiceHost.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(
                secret.length + 4 + 16 + 1 + 4 + 8 + 4 + 1 + 5 + hostBytes.length + 1);
        buf.put(secret);
        buf.putInt(serverPort);
        buf.putLong(playerUuid.getMostSignificantBits());
        buf.putLong(playerUuid.getLeastSignificantBits());
        buf.put(codec);
        buf.putInt(mtuSize);
        buf.putDouble(voiceChatDistance);
        buf.putInt(keepAlive);
        buf.put(groupsEnabled ? (byte) 1 : (byte) 0);
        writeVarInt(buf, hostBytes.length);
        buf.put(hostBytes);
        buf.put(allowRecording ? (byte) 1 : (byte) 0);
        byte[] out = new byte[buf.position()];
        buf.flip();
        buf.get(out);
        return out;
    }

    int serverPort() {
        return serverPort;
    }

    UUID playerUuid() {
        return playerUuid;
    }

    private static String readUtf(ByteBuffer buf) {
        int length = readVarInt(buf);
        if (length < 0 || length > buf.remaining()) {
            throw new IllegalArgumentException("voice host length " + length);
        }
        byte[] bytes = new byte[length];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readVarInt(ByteBuffer buf) {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            byte current = buf.get();
            value |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) {
                return value;
            }
        }
        throw new IllegalArgumentException("varint too long");
    }

    private static void writeVarInt(ByteBuffer buf, int value) {
        while ((value & 0xFFFFFF80) != 0) {
            buf.put((byte) (value & 0x7F | 0x80));
            value >>>= 7;
        }
        buf.put((byte) value);
    }
}
