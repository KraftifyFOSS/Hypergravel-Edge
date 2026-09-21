package pdx.dev.hypergravel.proxy.network;

import java.net.InetSocketAddress;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import pdx.dev.hypergravel.proxy.HyperGravelProxy;
import pdx.dev.hypergravel.proxy.config.HyperGravelConfig;
import pdx.dev.hypergravel.proxy.network.codec.MinecraftDecoder;
import pdx.dev.hypergravel.proxy.network.codec.MinecraftEncoder;
import pdx.dev.hypergravel.proxy.network.codec.VarIntFrameDecoder;
import pdx.dev.hypergravel.proxy.network.codec.VarIntLengthEncoder;
import pdx.dev.hypergravel.proxy.network.handler.HandshakeSessionHandler;
import pdx.dev.hypergravel.proxy.protocol.PacketDirection;
import pdx.dev.hypergravel.proxy.protocol.ProtocolState;
import pdx.dev.hypergravel.proxy.protocol.ProtocolVersion;
import pdx.dev.hypergravel.proxy.security.CidrTrustList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ProxyChannelInitializer extends ChannelInitializer<Channel> {

    private static final Logger LOGGER = LogManager.getLogger(ProxyChannelInitializer.class);

    private final HyperGravelProxy proxy;
    private final CidrTrustList proxyProtocolTrust;

    public ProxyChannelInitializer(HyperGravelProxy proxy, CidrTrustList proxyProtocolTrust) {
        this.proxy = proxy;
        this.proxyProtocolTrust = proxyProtocolTrust;
    }

    @Override
    protected void initChannel(Channel channel) {
        HyperGravelConfig config = proxy.config();

        InetSocketAddress peer = (InetSocketAddress) channel.remoteAddress();
        if (peer != null && !proxy.connectionThrottle().tryAcquire(peer.getAddress())) {

            channel.close();
            return;
        }

        if (config.proxyProtocol().enabled()) {
            boolean trusted = peer != null && proxyProtocolTrust.contains(peer.getAddress());
            if (trusted) {

                channel.pipeline().addLast(Connections.PROXY_PROTOCOL,
                        new ProxyProtocolDetector(config.proxyProtocol().required()));
            } else if (config.proxyProtocol().required()) {
                LOGGER.debug("rejecting {}: PROXY protocol required but source is not trusted",
                        peer);
                channel.close();
                return;
            }

        }

        MinecraftConnection connection = new MinecraftConnection(channel);

        channel.pipeline()
                .addLast(Connections.FRAME_DECODER,
                        new VarIntFrameDecoder(config.network().maxPacketSize()))
                .addLast(Connections.FRAME_ENCODER, VarIntLengthEncoder.INSTANCE)
                .addLast(Connections.MINECRAFT_DECODER,
                        new MinecraftDecoder(PacketDirection.SERVERBOUND,
                                ProtocolState.HANDSHAKE, ProtocolVersion.UNKNOWN))
                .addLast(Connections.MINECRAFT_ENCODER,
                        new MinecraftEncoder(PacketDirection.CLIENTBOUND,
                                ProtocolState.HANDSHAKE, ProtocolVersion.UNKNOWN))
                .addLast(Connections.HANDLER, connection);

        pdx.dev.hypergravel.via.ViaSupport.install(channel);

        connection.setReadTimeout(config.network().handshakeTimeout().toMillis());
        connection.setHandler(new HandshakeSessionHandler(proxy, connection));

        channel.closeFuture().addListener(future -> {
            if (peer != null) {
                proxy.connectionThrottle().release(peer.getAddress());
            }
        });
    }

    private static final class ProxyProtocolDetector
            extends io.netty.handler.codec.ByteToMessageDecoder {

        private final boolean required;

        ProxyProtocolDetector(boolean required) {
            this.required = required;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, io.netty.buffer.ByteBuf in,
                              java.util.List<Object> out) {
            io.netty.handler.codec.ProtocolDetectionResult<
                    io.netty.handler.codec.haproxy.HAProxyProtocolVersion> result =
                    HAProxyMessageDecoder.detectProtocol(in);

            switch (result.state()) {
                case NEEDS_MORE_DATA -> {

                }
                case DETECTED -> {
                    ctx.pipeline().replace(this, Connections.PROXY_PROTOCOL,
                            new HAProxyMessageDecoder());
                    ctx.pipeline().addAfter(Connections.PROXY_PROTOCOL, "proxy-protocol-apply",
                            new ProxyProtocolHandler());
                }
                case INVALID -> {
                    if (required) {
                        LOGGER.debug("closing {}: PROXY protocol is required but the connection "
                                + "did not start with a header", ctx.channel().remoteAddress());
                        ctx.close();
                        return;
                    }

                    ctx.pipeline().remove(this);
                }
            }
        }
    }

    private static final class ProxyProtocolHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof HAProxyMessage message)) {
                ctx.fireChannelRead(msg);
                return;
            }
            try {
                if (message.sourceAddress() != null) {
                    ctx.channel().attr(RealAddress.KEY).set(
                            new InetSocketAddress(message.sourceAddress(), message.sourcePort()));
                }
            } finally {
                message.release();

                ctx.pipeline().remove(this);
                if (ctx.pipeline().get(Connections.PROXY_PROTOCOL) != null) {
                    ctx.pipeline().remove(Connections.PROXY_PROTOCOL);
                }
            }
        }
    }

    public static final class RealAddress {
        public static final io.netty.util.AttributeKey<InetSocketAddress> KEY =
                io.netty.util.AttributeKey.valueOf("hypergravel:real-address");

        private RealAddress() {
        }
    }
}
