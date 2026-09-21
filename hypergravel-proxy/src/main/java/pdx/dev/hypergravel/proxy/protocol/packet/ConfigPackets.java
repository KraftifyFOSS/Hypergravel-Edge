package pdx.dev.hypergravel.proxy.protocol.packet;

import io.netty.buffer.ByteBuf;
import pdx.dev.hypergravel.proxy.protocol.Packet;
import pdx.dev.hypergravel.proxy.protocol.PacketHandler;
import pdx.dev.hypergravel.proxy.protocol.ProtocolVersion;

public final class ConfigPackets {

    private ConfigPackets() {
    }

    public static final class FinishConfiguration implements Packet {

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

    public static final class AckFinishConfiguration implements Packet {

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
}
