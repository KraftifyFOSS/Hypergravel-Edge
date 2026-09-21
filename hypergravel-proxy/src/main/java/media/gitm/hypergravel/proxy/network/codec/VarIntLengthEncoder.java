package media.gitm.hypergravel.proxy.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import media.gitm.hypergravel.proxy.protocol.ProtocolUtils;

@ChannelHandler.Sharable
public final class VarIntLengthEncoder extends MessageToByteEncoder<ByteBuf> {

    public static final VarIntLengthEncoder INSTANCE = new VarIntLengthEncoder();

    private VarIntLengthEncoder() {
        super(false);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
        int bodyLength = msg.readableBytes();
        out.ensureWritable(ProtocolUtils.varIntBytes(bodyLength) + bodyLength);
        ProtocolUtils.writeVarInt(out, bodyLength);
        out.writeBytes(msg, msg.readerIndex(), bodyLength);
    }

    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, ByteBuf msg, boolean preferDirect) {
        int bodyLength = msg.readableBytes();
        int total = ProtocolUtils.varIntBytes(bodyLength) + bodyLength;
        return preferDirect
                ? ctx.alloc().ioBuffer(total, total)
                : ctx.alloc().heapBuffer(total, total);
    }
}
