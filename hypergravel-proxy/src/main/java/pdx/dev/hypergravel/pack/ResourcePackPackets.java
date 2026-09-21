package pdx.dev.hypergravel.pack;

import java.util.UUID;

import io.netty.buffer.ByteBuf;
import pdx.dev.hypergravel.proxy.protocol.ComponentNbt;
import pdx.dev.hypergravel.proxy.protocol.Packet;
import pdx.dev.hypergravel.proxy.protocol.PacketHandler;
import pdx.dev.hypergravel.proxy.protocol.ProtocolUtils;
import pdx.dev.hypergravel.proxy.protocol.ProtocolVersion;
import net.kyori.adventure.text.Component;

public final class ResourcePackPackets {

    private ResourcePackPackets() {
    }

    public static final class Push implements Packet {

        private UUID id;
        private String url = "";
        private String hash = "";
        private boolean required;
        private Component prompt;

        public Push() {
        }

        public Push(UUID id, String url, String hash, boolean required, Component prompt) {
            this.id = id;
            this.url = url;
            this.hash = hash;
            this.required = required;
            this.prompt = prompt;
        }

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            throw new UnsupportedOperationException("resource_pack_push is written, never read");
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            ProtocolUtils.writeUuid(buf, id);
            ProtocolUtils.writeString(buf, url);

            ProtocolUtils.writeString(buf, hash);
            buf.writeBoolean(required);
            buf.writeBoolean(prompt != null);
            if (prompt != null) {
                ComponentNbt.write(buf, prompt);
            }
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return false;
        }

        @Override
        public int expectedSize() {
            return 128 + url.length() + hash.length();
        }
    }

    










    public static final class Pop implements Packet {

        private UUID id;

        public Pop() {
        }

        public Pop(UUID id) {
            this.id = id;
        }

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            throw new UnsupportedOperationException("resource_pack_pop is written, never read");
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            buf.writeBoolean(id != null);
            if (id != null) {
                ProtocolUtils.writeUuid(buf, id);
            }
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return false;
        }

        @Override
        public int expectedSize() {
            return 24;
        }
    }

    public static final class Response implements Packet {

        private UUID id;
        private int result;

        public UUID id() {
            return id;
        }

        public int result() {
            return result;
        }

        public String describe() {
            return switch (result) {
                case 0 -> "loaded";
                case 1 -> "declined";
                case 2 -> "download failed";
                case 3 -> "accepted";
                case 4 -> "downloaded";
                case 5 -> "invalid url";
                case 6 -> "failed to reload";
                case 7 -> "discarded";
                default -> "result " + result;
            };
        }

        public boolean settled() {
            return result != 3 && result != 4;
        }

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            id = ProtocolUtils.readUuid(buf);
            result = ProtocolUtils.readVarInt(buf);
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            ProtocolUtils.writeUuid(buf, id);
            ProtocolUtils.writeVarInt(buf, result);
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return 24;
        }
    }
}
