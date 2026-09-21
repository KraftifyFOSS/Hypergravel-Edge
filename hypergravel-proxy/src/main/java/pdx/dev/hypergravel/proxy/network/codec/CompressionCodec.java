package pdx.dev.hypergravel.proxy.network.codec;

import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.MessageToMessageDecoder;
import pdx.dev.hypergravel.proxy.protocol.ProtocolUtils;

public final class CompressionCodec {

    private CompressionCodec() {
    }

    public static Decoder decoder(int threshold, int maxUncompressedSize) {
        return new Decoder(threshold, maxUncompressedSize);
    }

    public static Encoder encoder(int threshold, int level) {
        return new Encoder(threshold, level);
    }

    public static final class Decoder extends MessageToMessageDecoder<ByteBuf> {

        private final Inflater inflater = new Inflater();
        private final int threshold;
        private final int maxUncompressedSize;

        Decoder(int threshold, int maxUncompressedSize) {
            this.threshold = threshold;
            this.maxUncompressedSize = maxUncompressedSize;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out)
                throws Exception {
            if (!msg.isReadable()) {
                return;
            }
            int declared = ProtocolUtils.readVarInt(msg);

            if (declared == 0) {

                out.add(msg.readRetainedSlice(msg.readableBytes()));
                return;
            }

            if (declared < threshold) {
                throw new CorruptedFrameException("declared uncompressed size " + declared
                        + " is below the compression threshold " + threshold);
            }
            if (declared > maxUncompressedSize) {
                throw new CorruptedFrameException("declared uncompressed size " + declared
                        + " exceeds maximum " + maxUncompressedSize);
            }

            ByteBuf uncompressed = ctx.alloc().directBuffer(declared, declared);
            try {
                inflate(msg, uncompressed, declared);
                out.add(uncompressed);
            } catch (Throwable t) {
                uncompressed.release();
                throw t;
            }
        }

        private void inflate(ByteBuf in, ByteBuf out, int declared) throws DataFormatException {
            try {
                inflater.setInput(in.nioBuffer());
                inflater.inflate(out.nioBuffer(out.writerIndex(), declared));

                if (!inflater.finished()) {

                    throw new CorruptedFrameException(
                            "frame inflates to more than its declared " + declared + " bytes");
                }
                int produced = (int) inflater.getBytesWritten();
                if (produced != declared) {
                    throw new CorruptedFrameException("frame declared " + declared
                            + " uncompressed bytes but produced " + produced);
                }
                out.writerIndex(out.writerIndex() + produced);
            } finally {
                inflater.reset();
            }
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            inflater.end();
        }
    }

    public static final class Encoder extends MessageToByteEncoder<ByteBuf> {

        private final Deflater deflater;
        private final int threshold;

        Encoder(int threshold, int level) {
            super(false);
            this.threshold = threshold;
            this.deflater = new Deflater(level);
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out)
                throws Exception {
            int uncompressedLength = msg.readableBytes();

            if (uncompressedLength < threshold) {

                ProtocolUtils.writeVarInt(out, 0);
                out.writeBytes(msg, msg.readerIndex(), uncompressedLength);
                msg.readerIndex(msg.readerIndex() + uncompressedLength);
                return;
            }

            ProtocolUtils.writeVarInt(out, uncompressedLength);
            deflate(msg, out, uncompressedLength);
        }

        private void deflate(ByteBuf in, ByteBuf out, int length) {
            try {
                deflater.setInput(in.nioBuffer(in.readerIndex(), length));
                deflater.finish();
                while (!deflater.finished()) {
                    out.ensureWritable(Math.max(64, length >> 2));
                    int written = deflater.deflate(
                            out.nioBuffer(out.writerIndex(), out.writableBytes()));
                    if (written == 0 && !deflater.finished()) {
                        out.ensureWritable(out.capacity());
                        continue;
                    }
                    out.writerIndex(out.writerIndex() + written);
                }
                in.readerIndex(in.readerIndex() + length);
            } finally {
                deflater.reset();
            }
        }

        @Override
        protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, ByteBuf msg,
                                         boolean preferDirect) {
            int length = msg.readableBytes();

            int initial = length + 5;
            return preferDirect ? ctx.alloc().ioBuffer(initial) : ctx.alloc().heapBuffer(initial);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            deflater.end();
        }
    }
}
