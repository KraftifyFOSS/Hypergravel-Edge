package pdx.dev.hypergravel.proxy.protocol.packet;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import pdx.dev.hypergravel.proxy.protocol.ProtocolUtils;

public record GameProfile(UUID uuid, String name, Property[] properties) {

    public static final Property[] NO_PROPERTIES = new Property[0];

    private static final int MAX_PROPERTIES = 16;

    public record Property(String name, String value, String signature) {

        public boolean signed() {
            return signature != null;
        }
    }

    public static GameProfile offline(String username) {
        return new GameProfile(offlineUuid(username), username, NO_PROPERTIES);
    }

    public static UUID offlineUuid(String username) {
        byte[] source = ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8);
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("MD5").digest(source);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("MD5 is required by the JLS", e);
        }
        digest[6] = (byte) ((digest[6] & 0x0F) | 0x30);
        digest[8] = (byte) ((digest[8] & 0x3F) | 0x80);
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (digest[i] & 0xFF);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (digest[i] & 0xFF);
        }
        return new UUID(msb, lsb);
    }

    public GameProfile withProperty(Property extra) {
        Property[] merged = Arrays.copyOf(properties, properties.length + 1);
        merged[properties.length] = extra;
        return new GameProfile(uuid, name, merged);
    }

    public GameProfile withUuid(UUID newUuid) {
        return new GameProfile(newUuid, name, properties);
    }

    public static Property[] readProperties(ByteBuf buf) {
        int count = ProtocolUtils.readVarInt(buf);
        if (count < 0 || count > MAX_PROPERTIES) {
            throw new io.netty.handler.codec.CorruptedFrameException(
                    "profile property count out of range: " + count);
        }
        if (count == 0) {
            return NO_PROPERTIES;
        }
        Property[] properties = new Property[count];
        for (int i = 0; i < count; i++) {
            String name = ProtocolUtils.readString(buf, 64);
            String value = ProtocolUtils.readString(buf, 32767);
            String signature = buf.readBoolean() ? ProtocolUtils.readString(buf, 32767) : null;
            properties[i] = new Property(name, value, signature);
        }
        return properties;
    }

    public static void writeProperties(ByteBuf buf, Property[] properties) {
        ProtocolUtils.writeVarInt(buf, properties.length);
        for (Property property : properties) {
            ProtocolUtils.writeString(buf, property.name());
            ProtocolUtils.writeString(buf, property.value());
            if (property.signature() != null) {
                buf.writeBoolean(true);
                ProtocolUtils.writeString(buf, property.signature());
            } else {
                buf.writeBoolean(false);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GameProfile other
                && uuid.equals(other.uuid)
                && name.equals(other.name)
                && Arrays.equals(properties, other.properties);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * uuid.hashCode() + name.hashCode()) + Arrays.hashCode(properties);
    }

    @Override
    public String toString() {
        return "GameProfile{" + name + "/" + uuid + ", " + properties.length + " properties}";
    }
}
