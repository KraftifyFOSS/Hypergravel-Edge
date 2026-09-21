package pdx.dev.hypergravel.proxy.network;

import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.ReferenceCountUtil;
import pdx.dev.hypergravel.proxy.network.codec.CipherCodec;
import pdx.dev.hypergravel.proxy.network.codec.CompressionCodec;
import pdx.dev.hypergravel.proxy.network.codec.MinecraftDecoder;
import pdx.dev.hypergravel.proxy.network.codec.MinecraftEncoder;
import pdx.dev.hypergravel.proxy.protocol.Packet;
import pdx.dev.hypergravel.proxy.protocol.PacketHandler;
import pdx.dev.hypergravel.proxy.protocol.ProtocolState;
import pdx.dev.hypergravel.proxy.protocol.ProtocolVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MinecraftConnection extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LogManager.getLogger(MinecraftConnection.class);

    private final Channel channel;
    private volatile ProtocolState state = ProtocolState.HANDSHAKE;
    private volatile ProtocolVersion version = ProtocolVersion.UNKNOWN;
    private SessionHandler handler;
    private boolean closed;
    private boolean compressionEnabled;

    public MinecraftConnection(Channel channel) {
        this.channel = channel;
    }

    public Channel channel() {
        return channel;
    }

    public ProtocolState state() {
        return state;
    }

    public ProtocolVersion version() {
        return version;
    }

    public boolean active() {
        return !closed && channel.isActive();
    }

    public InetSocketAddress remoteAddress() {
        InetSocketAddress real =
                channel.attr(ProxyChannelInitializer.RealAddress.KEY).get();
        return real != null ? real : (InetSocketAddress) channel.remoteAddress();
    }

    public boolean writable() {
        return channel.isWritable();
    }

    










    public void setState(ProtocolState newState) {
        this.state = newState;
        if (channel.eventLoop().inEventLoop()) {
            applyState(newState);
        } else {
            channel.eventLoop().execute(() -> applyState(newState));
        }
    }

    private void applyState(ProtocolState newState) {
        MinecraftDecoder decoder = decoder();
        MinecraftEncoder encoder = encoder();
        if (decoder != null) {
            decoder.setState(newState);
        }
        if (encoder != null) {
            encoder.setState(newState);
        }
    }

    public void setVersion(ProtocolVersion newVersion) {
        this.version = newVersion;
        decoder().setVersion(newVersion);
        encoder().setVersion(newVersion);
    }

    public boolean supports(pdx.dev.hypergravel.proxy.protocol.PacketType type) {
        MinecraftEncoder encoder = encoder();
        return encoder != null && encoder.supports(type);
    }

    public SessionHandler handler() {
        return handler;
    }

    public void setHandler(SessionHandler newHandler) {
        if (handler != null) {
            handler.deactivated();
        }
        this.handler = newHandler;
        if (newHandler != null) {
            newHandler.activated();
        }
    }

    private MinecraftDecoder decoder() {
        return channel.pipeline().get(MinecraftDecoder.class);
    }

    private MinecraftEncoder encoder() {
        return channel.pipeline().get(MinecraftEncoder.class);
    }

    public void enableEncryption(byte[] sharedSecret) throws GeneralSecurityException {
        SecretKey key = CipherCodec.key(sharedSecret);
        channel.pipeline()
                .addBefore(Connections.FRAME_DECODER, Connections.CIPHER_DECODER,
                        CipherCodec.decoder(key))
                .addBefore(Connections.FRAME_ENCODER, Connections.CIPHER_ENCODER,
                        CipherCodec.encoder(key));
    }

    public void setCompressionThreshold(int threshold, int level, int maxUncompressedSize) {
        if (threshold < 0) {
            if (compressionEnabled) {
                channel.pipeline().remove(Connections.COMPRESSION_DECODER);
                channel.pipeline().remove(Connections.COMPRESSION_ENCODER);
                compressionEnabled = false;
            }
            return;
        }
        if (compressionEnabled) {
            return;
        }
        channel.pipeline()
                .addAfter(Connections.FRAME_DECODER, Connections.COMPRESSION_DECODER,
                        CompressionCodec.decoder(threshold, maxUncompressedSize))
                .addAfter(Connections.FRAME_ENCODER, Connections.COMPRESSION_ENCODER,
                        CompressionCodec.encoder(threshold, level));
        compressionEnabled = true;
    }

    public boolean compressionEnabled() {
        return compressionEnabled;
    }

    public void setReadTimeout(long millis) {
        if (channel.pipeline().get(Connections.READ_TIMEOUT) != null) {
            channel.pipeline().remove(Connections.READ_TIMEOUT);
        }
        channel.pipeline().addFirst(Connections.READ_TIMEOUT,
                new ReadTimeoutHandler(millis, TimeUnit.MILLISECONDS));
    }

    public void write(Object message) {
        if (active()) {
            channel.write(message, channel.voidPromise());
        } else {
            ReferenceCountUtil.release(message);
        }
    }

    public void writeAndFlush(Object message) {
        if (active()) {
            channel.writeAndFlush(message, channel.voidPromise());
        } else {
            ReferenceCountUtil.release(message);
        }
    }

    public void flush() {
        if (active()) {
            channel.flush();
        }
    }

    public void closeWith(Packet packet) {
        if (!active()) {
            return;
        }
        closed = true;
        channel.writeAndFlush(packet).addListener(ChannelFutureListener.CLOSE);
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        channel.close();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (handler == null) {
            ReferenceCountUtil.release(msg);
            return;
        }
        try {
            if (msg instanceof Packet packet) {
                boolean consumed = packet.handle(handlerAsPacketHandler());
                if (!consumed) {
                    handler.forward(packet);
                }
            } else if (msg instanceof ByteBuf raw) {

                handler.forwardRaw(raw);
                return;
            }
        } catch (Exception e) {
            exceptionCaught(ctx, e);
        } finally {
            if (!(msg instanceof ByteBuf)) {
                ReferenceCountUtil.release(msg);
            }
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {

        if (handler != null) {
            handler.readComplete();
        }
    }

    private PacketHandler handlerAsPacketHandler() {
        return handler;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        closed = true;
        if (handler != null) {
            handler.disconnected();
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        if (handler != null) {
            handler.writabilityChanged(channel.isWritable());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!active()) {
            return;
        }
        if (handler != null && handler.handleException(cause)) {
            return;
        }

        LOGGER.debug("closing {} after {}", channel.remoteAddress(), cause.toString());
        close();
    }
}
