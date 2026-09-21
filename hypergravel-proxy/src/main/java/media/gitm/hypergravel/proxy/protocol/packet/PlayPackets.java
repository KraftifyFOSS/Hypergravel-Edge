package media.gitm.hypergravel.proxy.protocol.packet;

import io.netty.buffer.ByteBuf;
import media.gitm.hypergravel.proxy.protocol.ComponentNbt;
import media.gitm.hypergravel.proxy.protocol.Packet;
import media.gitm.hypergravel.proxy.protocol.PacketHandler;
import media.gitm.hypergravel.proxy.protocol.ProtocolUtils;
import media.gitm.hypergravel.proxy.protocol.ProtocolVersion;
import net.kyori.adventure.text.Component;

public final class PlayPackets {

    private PlayPackets() {
    }

    public static final class JoinGame implements Packet {

        private int entityId;
        private ByteBuf remainder;

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            this.entityId = buf.readInt();

            this.remainder = buf.readRetainedSlice(buf.readableBytes());
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            buf.writeInt(entityId);
            buf.writeBytes(remainder, remainder.readerIndex(), remainder.readableBytes());
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return (remainder == null ? 0 : remainder.readableBytes()) + 8;
        }

        public int entityId() {
            return entityId;
        }

        public ByteBuf remainder() {
            return remainder;
        }

        public void release() {
            if (remainder != null && remainder.refCnt() > 0) {
                remainder.release();
                remainder = null;
            }
        }
    }

    public static final class StartConfiguration implements Packet {

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

    public static final class Transfer implements Packet {

        private String host = "";
        private int port;

        public Transfer() {
        }

        public Transfer(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            this.host = ProtocolUtils.readString(buf, 255);
            this.port = ProtocolUtils.readVarInt(buf);
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            ProtocolUtils.writeString(buf, host);
            ProtocolUtils.writeVarInt(buf, port);
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return host.length() + 10;
        }

        public String host() {
            return host;
        }

        public int port() {
            return port;
        }
    }

    public static final class ChatCommand implements Packet {

        public static final int MAX_COMMAND_LENGTH = 256;

        private String command = "";

        private byte[] signature = new byte[0];

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            this.command = ProtocolUtils.readString(buf, MAX_COMMAND_LENGTH);
            this.signature = new byte[buf.readableBytes()];
            buf.readBytes(this.signature);
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            ProtocolUtils.writeString(buf, command);
            buf.writeBytes(signature);
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return command.length() + 5 + signature.length;
        }

        public String command() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }
    }

    












    public static final class ChatCommandSigned implements Packet {

        private String command = "";

        private byte[] rest = new byte[0];

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            this.command = ProtocolUtils.readString(buf, ChatCommand.MAX_COMMAND_LENGTH);
            this.rest = new byte[buf.readableBytes()];
            buf.readBytes(this.rest);
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            ProtocolUtils.writeString(buf, command);
            buf.writeBytes(rest);
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return command.length() + 5 + rest.length;
        }

        public String command() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }
    }

    public static final class ChatMessage implements Packet {

        public static final int MAX_MESSAGE_LENGTH = 256;

        private String message = "";

        private byte[] signature = new byte[0];

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            this.message = ProtocolUtils.readString(buf, MAX_MESSAGE_LENGTH);
            this.signature = new byte[buf.readableBytes()];
            buf.readBytes(this.signature);
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            ProtocolUtils.writeString(buf, message);
            buf.writeBytes(signature);
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return message.length() + 5 + signature.length;
        }

        public String message() {
            return message;
        }
    }

    public static final class SystemChat implements Packet {

        private Component message;
        private byte[] raw;
        private boolean overlay;

        public SystemChat() {
        }

        public SystemChat(Component message) {
            this.message = message;
        }

        public SystemChat(Component message, boolean overlay) {
            this.message = message;
            this.overlay = overlay;
        }

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            this.raw = new byte[buf.readableBytes()];
            buf.readBytes(this.raw);
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            if (message != null) {
                ComponentNbt.write(buf, message);
                buf.writeBoolean(overlay);
            } else if (raw != null) {
                buf.writeBytes(raw);
            }
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return false;
        }

        @Override
        public int expectedSize() {
            return raw != null ? raw.length + 4 : 64;
        }
    }
}
