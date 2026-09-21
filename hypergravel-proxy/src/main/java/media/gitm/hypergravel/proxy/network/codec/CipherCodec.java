package media.gitm.hypergravel.proxy.network.codec;

import java.security.GeneralSecurityException;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.MessageToMessageDecoder;

public final class CipherCodec {

    private CipherCodec() {
    }

    public static SecretKey key(byte[] sharedSecret) {
        return new SecretKeySpec(sharedSecret, "AES");
    }

    private static Cipher cipher(int mode, SecretKey key) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
        cipher.init(mode, key, new IvParameterSpec(key.getEncoded()));
        return cipher;
    }

    public static Decoder decoder(SecretKey key) throws GeneralSecurityException {
        return new Decoder(cipher(Cipher.DECRYPT_MODE, key));
    }

    public static Encoder encoder(SecretKey key) throws GeneralSecurityException {
        return new Encoder(cipher(Cipher.ENCRYPT_MODE, key));
    }

    public static final class Decoder extends MessageToMessageDecoder<ByteBuf> {

        private final Cipher cipher;

        Decoder(Cipher cipher) {
            this.cipher = cipher;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out)
                throws Exception {
            processInPlace(cipher, msg);
            out.add(msg.retain());
        }
    }

    public static final class Encoder extends MessageToByteEncoder<ByteBuf> {

        private final Cipher cipher;

        Encoder(Cipher cipher) {
            super(false);
            this.cipher = cipher;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out)
                throws Exception {
            int length = msg.readableBytes();
            out.ensureWritable(length);
            processInto(cipher, msg, out, length);
        }

        @Override
        protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, ByteBuf msg,
                                         boolean preferDirect) {
            int length = msg.readableBytes();
            return preferDirect
                    ? ctx.alloc().ioBuffer(length, length)
                    : ctx.alloc().heapBuffer(length, length);
        }
    }

    private static void processInPlace(Cipher cipher, ByteBuf buf) throws GeneralSecurityException {
        int length = buf.readableBytes();
        if (length == 0) {
            return;
        }
        java.nio.ByteBuffer nio = buf.nioBuffer(buf.readerIndex(), length);

        cipher.update(nio.duplicate(), nio);
    }

    private static void processInto(Cipher cipher, ByteBuf src, ByteBuf dst, int length)
            throws GeneralSecurityException {
        if (length == 0) {
            return;
        }
        java.nio.ByteBuffer in = src.nioBuffer(src.readerIndex(), length);
        java.nio.ByteBuffer outNio = dst.nioBuffer(dst.writerIndex(), length);
        int written = cipher.update(in, outNio);
        dst.writerIndex(dst.writerIndex() + written);
        src.readerIndex(src.readerIndex() + length);
    }
}
