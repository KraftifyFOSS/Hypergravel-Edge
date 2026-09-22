package pdx.dev.hypergravel.proxy.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import pdx.dev.hypergravel.proxy.protocol.Packet;
import pdx.dev.hypergravel.proxy.protocol.PacketDirection;
import pdx.dev.hypergravel.proxy.protocol.PacketType;
import pdx.dev.hypergravel.proxy.protocol.ProtocolState;
import pdx.dev.hypergravel.proxy.protocol.ProtocolUtils;
import pdx.dev.hypergravel.proxy.protocol.ProtocolVersion;
import pdx.dev.hypergravel.proxy.protocol.StateRegistry;

public final class TabWire {

    private TabWire() {
    }

    public static ByteBuf encodeRaw(Packet packet, ProtocolState state, PacketDirection direction,
                                    ProtocolVersion wireVersion, ByteBufAllocator allocator) {
        PacketType type = PacketRegistryLookup.typeOf(packet, state);
        int id = type == null ? -1 : StateRegistry.of(state).direction(direction)
                .forVersion(wireVersion).idOf(type);
        if (id < 0) {
            return null;
        }
        ByteBuf buf = allocator.ioBuffer(packet.expectedSize() + ProtocolUtils.varIntBytes(id));
        try {
            ProtocolUtils.writeVarInt(buf, id);
            packet.encode(buf, wireVersion);
            return buf;
        } catch (Throwable t) {
            buf.release();
            return null;
        }
    }
}
