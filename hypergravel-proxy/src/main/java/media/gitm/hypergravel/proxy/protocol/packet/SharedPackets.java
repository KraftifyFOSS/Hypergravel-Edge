package media.gitm.hypergravel.proxy.protocol.packet;

import io.netty.buffer.ByteBuf;
import media.gitm.hypergravel.proxy.protocol.ComponentNbt;
import media.gitm.hypergravel.proxy.protocol.Packet;
import media.gitm.hypergravel.proxy.protocol.PacketHandler;
import media.gitm.hypergravel.proxy.protocol.ProtocolUtils;
import media.gitm.hypergravel.proxy.protocol.ProtocolVersion;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

public final class SharedPackets {

    private SharedPackets() {
    }

    public static final class KeepAlive implements Packet {

        private long id;

        public KeepAlive() {
        }

        public KeepAlive(long id) {
            this.id = id;
        }

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            this.id = buf.readLong();
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            buf.writeLong(id);
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return 9;
        }

        public long id() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }
    }

    public static final class PluginMessage implements Packet {

        private String channel = "";
        private ByteBuf data;

        public PluginMessage() {
        }

        public PluginMessage(String channel, ByteBuf data) {
            this.channel = channel;
            this.data = data;
        }

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            this.channel = ProtocolUtils.readString(buf, 256);

            this.data = buf.readRetainedSlice(buf.readableBytes());
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            ProtocolUtils.writeString(buf, channel);
            buf.writeBytes(data, data.readerIndex(), data.readableBytes());
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return (data == null ? 0 : data.readableBytes()) + channel.length() + 8;
        }

        public String channel() {
            return channel;
        }

        public ByteBuf data() {
            return data;
        }

        public byte[] copyData() {
            if (data == null) {
                return new byte[0];
            }
            byte[] copy = new byte[data.readableBytes()];
            data.getBytes(data.readerIndex(), copy);
            return copy;
        }

        public void release() {
            if (data != null && data.refCnt() > 0) {
                data.release();
                data = null;
            }
        }
    }

    public static final class Disconnect implements Packet {

        private String reasonJson = "";
        private boolean nbtEncoded;

        public Disconnect() {
        }

        public Disconnect(String reasonJson, boolean nbtEncoded) {
            this.reasonJson = reasonJson;
            this.nbtEncoded = nbtEncoded;
        }

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            if (nbtEncoded) {

                this.reasonJson = "";
                buf.skipBytes(buf.readableBytes());
            } else {
                this.reasonJson = ProtocolUtils.readString(buf, 262144);
            }
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            if (nbtEncoded) {

                ComponentNbt.write(buf, GsonComponentSerializer.gson().deserialize(reasonJson));
            } else {
                ProtocolUtils.writeString(buf, reasonJson);
            }
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return reasonJson.length() + 5;
        }

        public String reasonJson() {
            return reasonJson;
        }

        public void setNbtEncoded(boolean nbtEncoded) {
            this.nbtEncoded = nbtEncoded;
        }
    }
}
