package pdx.dev.hypergravel.proxy.protocol.packet;

import io.netty.buffer.ByteBuf;
import pdx.dev.hypergravel.proxy.protocol.Packet;
import pdx.dev.hypergravel.proxy.protocol.PacketHandler;
import pdx.dev.hypergravel.proxy.protocol.ProtocolUtils;
import pdx.dev.hypergravel.proxy.protocol.ProtocolVersion;

public final class StatusPackets {

    private StatusPackets() {
    }

    public static final class Request implements Packet {

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

    public static final class Response implements Packet {

        private String json = "";

        public Response() {
        }

        public Response(String json) {
            this.json = json;
        }

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {

            this.json = ProtocolUtils.readString(buf, 262144);
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            ProtocolUtils.writeString(buf, json);
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return json.length() + 5;
        }

        public String json() {
            return json;
        }
    }

    public static final class Ping implements Packet {

        private long payload;

        public Ping() {
        }

        public Ping(long payload) {
            this.payload = payload;
        }

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            this.payload = buf.readLong();
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            buf.writeLong(payload);
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return 9;
        }

        public long payload() {
            return payload;
        }
    }
}
