package pdx.dev.hypergravel.proxy.protocol.packet;

import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CorruptedFrameException;
import pdx.dev.hypergravel.proxy.protocol.Packet;
import pdx.dev.hypergravel.proxy.protocol.PacketHandler;
import pdx.dev.hypergravel.proxy.protocol.ProtocolUtils;
import pdx.dev.hypergravel.proxy.protocol.ProtocolVersion;

public final class LoginPackets {

    private LoginPackets() {
    }

    public static final class Start implements Packet {

        private String username = "";
        private UUID uuid;

        public Start() {
        }

        public Start(String username, UUID uuid) {
            this.username = username;
            this.uuid = uuid;
        }

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            this.username = ProtocolUtils.readString(buf, 16);
            if (username.isEmpty()) {
                throw new CorruptedFrameException("empty username");
            }
            if (buf.isReadable(16)) {
                this.uuid = ProtocolUtils.readUuid(buf);
            }
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            ProtocolUtils.writeString(buf, username);
            ProtocolUtils.writeUuid(buf, uuid == null ? new UUID(0, 0) : uuid);
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return 34;
        }

        public String username() {
            return username;
        }

        public UUID claimedUuid() {
            return uuid;
        }
    }

    public static final class EncryptionResponse implements Packet {

        private byte[] sharedSecret = new byte[0];
        private byte[] verifyToken = new byte[0];

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            this.sharedSecret = ProtocolUtils.readByteArray(buf, 256);
            this.verifyToken = ProtocolUtils.readByteArray(buf, 256);
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            ProtocolUtils.writeByteArray(buf, sharedSecret);
            ProtocolUtils.writeByteArray(buf, verifyToken);
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return sharedSecret.length + verifyToken.length + 8;
        }

        public byte[] sharedSecret() {
            return sharedSecret;
        }

        public byte[] verifyToken() {
            return verifyToken;
        }
    }

    public static final class Acknowledged implements Packet {

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return 1;
        }
    }

    public static final class EncryptionRequest implements Packet {

        private String serverId = "";
        private byte[] publicKey = new byte[0];
        private byte[] verifyToken = new byte[0];

        private boolean shouldAuthenticate = true;

        public EncryptionRequest() {
        }

        public EncryptionRequest(String serverId, byte[] publicKey, byte[] verifyToken) {
            this.serverId = serverId;
            this.publicKey = publicKey;
            this.verifyToken = verifyToken;
        }

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            this.serverId = ProtocolUtils.readString(buf, 20);
            this.publicKey = ProtocolUtils.readByteArray(buf, 512);
            this.verifyToken = ProtocolUtils.readByteArray(buf, 256);
            if (version.atLeast(ProtocolVersion.MINECRAFT_1_20_5) && buf.isReadable()) {
                this.shouldAuthenticate = buf.readBoolean();
            }
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            ProtocolUtils.writeString(buf, serverId);
            ProtocolUtils.writeByteArray(buf, publicKey);
            ProtocolUtils.writeByteArray(buf, verifyToken);
            if (version.atLeast(ProtocolVersion.MINECRAFT_1_20_5)) {
                buf.writeBoolean(shouldAuthenticate);
            }
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return publicKey.length + verifyToken.length + 32;
        }

        public String serverId() {
            return serverId;
        }

        public byte[] publicKey() {
            return publicKey;
        }

        public byte[] verifyToken() {
            return verifyToken;
        }
    }

    public static final class Success implements Packet {

        private UUID uuid;
        private String username = "";
        private GameProfile.Property[] properties = GameProfile.NO_PROPERTIES;

        private UUID trailingId;

        public Success() {
        }

        public Success(UUID uuid, String username, GameProfile.Property[] properties) {
            this.uuid = uuid;
            this.username = username;
            this.properties = properties;
        }

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            this.uuid = ProtocolUtils.readUuid(buf);
            this.username = ProtocolUtils.readString(buf, 16);
            this.properties = GameProfile.readProperties(buf);

            if (version.atLeast(ProtocolVersion.MINECRAFT_26_2) && buf.readableBytes() >= 16) {
                this.trailingId = ProtocolUtils.readUuid(buf);
            }
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            ProtocolUtils.writeUuid(buf, uuid);
            ProtocolUtils.writeString(buf, username);
            GameProfile.writeProperties(buf, properties);

            if (version.atLeast(ProtocolVersion.MINECRAFT_1_20_5)
                    && version.atMost(ProtocolVersion.MINECRAFT_1_21)) {
                buf.writeBoolean(true);
            }

            if (version.atLeast(ProtocolVersion.MINECRAFT_26_2)) {
                ProtocolUtils.writeUuid(buf, trailingId != null ? trailingId : uuid);
            }
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return 64 + properties.length * 256;
        }

        public UUID uuid() {
            return uuid;
        }

        public String username() {
            return username;
        }

        public GameProfile.Property[] properties() {
            return properties;
        }
    }

    public static final class SetCompression implements Packet {

        private int threshold;

        public SetCompression() {
        }

        public SetCompression(int threshold) {
            this.threshold = threshold;
        }

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            this.threshold = ProtocolUtils.readVarInt(buf);
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            ProtocolUtils.writeVarInt(buf, threshold);
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return 6;
        }

        public int threshold() {
            return threshold;
        }
    }

    public static final class PluginRequest implements Packet {

        private int messageId;
        private String channel = "";
        private byte[] data = new byte[0];

        public PluginRequest() {
        }

        public PluginRequest(int messageId, String channel, byte[] data) {
            this.messageId = messageId;
            this.channel = channel;
            this.data = data;
        }

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            this.messageId = ProtocolUtils.readVarInt(buf);
            this.channel = ProtocolUtils.readString(buf, 256);
            this.data = ProtocolUtils.readRemaining(buf, 1048576);
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            ProtocolUtils.writeVarInt(buf, messageId);
            ProtocolUtils.writeString(buf, channel);
            buf.writeBytes(data);
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return data.length + channel.length() + 8;
        }

        public int messageId() {
            return messageId;
        }

        public String channel() {
            return channel;
        }

        public byte[] data() {
            return data;
        }
    }

    public static final class PluginResponse implements Packet {

        private int messageId;
        private boolean success;
        private byte[] data = new byte[0];

        public PluginResponse() {
        }

        public PluginResponse(int messageId, boolean success, byte[] data) {
            this.messageId = messageId;
            this.success = success;
            this.data = data;
        }

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            this.messageId = ProtocolUtils.readVarInt(buf);
            this.success = buf.readBoolean();
            this.data = ProtocolUtils.readRemaining(buf, 1048576);
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            ProtocolUtils.writeVarInt(buf, messageId);
            buf.writeBoolean(success);
            buf.writeBytes(data);
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return data.length + 8;
        }

        public int messageId() {
            return messageId;
        }

        public boolean success() {
            return success;
        }

        public byte[] data() {
            return data;
        }
    }
}
