package media.gitm.hypergravel.proxy.network.codec;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import media.gitm.hypergravel.proxy.protocol.ProtocolUtils;

public final class VarIntFrameDecoder extends ByteToMessageDecoder {

    private final int maxFrameLength;

    public VarIntFrameDecoder(int maxFrameLength) {
        this.maxFrameLength = maxFrameLength;

        setCumulator(COMPOSITE_CUMULATOR);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (!ctx.channel().isActive()) {
            in.clear();
            return;
        }

        int readerIndex = in.readerIndex();
        int length = ProtocolUtils.readVarIntSafely(in);

        if (length == Integer.MIN_VALUE) {

            if (in.readerIndex() - readerIndex >= ProtocolUtils.MAX_VARINT_BYTES) {
                throw new CorruptedFrameException("frame length varint is malformed");
            }
            in.readerIndex(readerIndex);
            return;
        }

        if (length < 0) {
            throw new CorruptedFrameException("negative frame length: " + length);
        }
        if (length > maxFrameLength) {
            throw new CorruptedFrameException(
                    "frame length " + length + " exceeds maximum " + maxFrameLength);
        }
        if (length == 0) {

            throw new CorruptedFrameException("zero-length frame");
        }

        if (in.readableBytes() < length) {
            in.readerIndex(readerIndex);
            return;
        }

        out.add(in.readRetainedSlice(length));
    }
}
