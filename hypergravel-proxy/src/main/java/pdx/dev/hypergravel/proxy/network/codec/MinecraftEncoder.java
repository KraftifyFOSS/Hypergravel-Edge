package pdx.dev.hypergravel.proxy.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import pdx.dev.hypergravel.proxy.protocol.Packet;
import pdx.dev.hypergravel.proxy.protocol.PacketDirection;
import pdx.dev.hypergravel.proxy.protocol.PacketType;
import pdx.dev.hypergravel.proxy.protocol.ProtocolState;
import pdx.dev.hypergravel.proxy.protocol.ProtocolUtils;
import pdx.dev.hypergravel.proxy.protocol.ProtocolVersion;
import pdx.dev.hypergravel.proxy.protocol.StateRegistry;

public final class MinecraftEncoder extends ChannelOutboundHandlerAdapter {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger(MinecraftEncoder.class);

    private final PacketDirection direction;
    private volatile ProtocolVersion version;
    private volatile ProtocolState state;
    private volatile StateRegistry.VersionTable table;

    public MinecraftEncoder(PacketDirection direction, ProtocolState state, ProtocolVersion version) {
        this.direction = direction;
        this.version = version;
        setState(state);
    }

    public void setState(ProtocolState state) {
        this.state = state;
        this.table = StateRegistry.of(state).direction(direction).forVersion(version);
    }

    public void setVersion(ProtocolVersion version) {
        this.version = version;
        setState(state);
    }

    public ProtocolState state() {
        return state;
    }

    public boolean supports(PacketType type) {
        return table.has(type);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (!(msg instanceof Packet packet)) {

            ctx.write(msg, promise);
            return;
        }

        PacketType type = PacketRegistryLookup.typeOf(packet, state);
        int id = type == null ? -1 : table.idOf(type);
        if (id < 0) {
            
            
            
            
            
            
            LOGGER.debug("dropped {} - no id in {}/{} at {}",
                    packet.getClass().getSimpleName(), state, direction, version);
            io.netty.util.ReferenceCountUtil.release(packet);
            promise.setSuccess();
            return;
        }

        ByteBuf buf = ctx.alloc().ioBuffer(packet.expectedSize() + ProtocolUtils.varIntBytes(id));
        try {
            ProtocolUtils.writeVarInt(buf, id);
            packet.encode(buf, version);
        } catch (Throwable t) {
            buf.release();
            promise.setFailure(t);
            return;
        }
        ctx.write(buf, promise);
    }
}
