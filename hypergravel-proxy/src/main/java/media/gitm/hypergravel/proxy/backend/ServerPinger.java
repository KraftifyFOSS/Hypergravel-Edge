package media.gitm.hypergravel.proxy.backend;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import media.gitm.hypergravel.api.server.ServerPing;
import media.gitm.hypergravel.proxy.network.Connections;
import media.gitm.hypergravel.proxy.network.codec.MinecraftDecoder;
import media.gitm.hypergravel.proxy.network.codec.MinecraftEncoder;
import media.gitm.hypergravel.proxy.network.codec.VarIntFrameDecoder;
import media.gitm.hypergravel.proxy.network.codec.VarIntLengthEncoder;
import media.gitm.hypergravel.proxy.protocol.PacketDirection;
import media.gitm.hypergravel.proxy.protocol.ProtocolState;
import media.gitm.hypergravel.proxy.protocol.ProtocolVersion;
import media.gitm.hypergravel.proxy.protocol.packet.HandshakePacket;
import media.gitm.hypergravel.proxy.protocol.packet.StatusPackets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

public final class ServerPinger {

    private static final int TIMEOUT_MILLIS = 3000;

    private static volatile EventLoopGroup group;
    private static volatile Class<? extends Channel> channelType;

    private ServerPinger() {
    }

    public static void configure(EventLoopGroup eventLoopGroup,
                                 Class<? extends Channel> transport) {
        group = eventLoopGroup;
        channelType = transport;
    }

    public static CompletableFuture<ServerPing> ping(HyperGravelServer server) {
        EventLoopGroup loops = group;
        Class<? extends Channel> transport = channelType;
        if (loops == null || transport == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("ServerPinger has not been configured"));
        }

        CompletableFuture<ServerPing> future = new CompletableFuture<>();
        long startNanos = System.nanoTime();

        new Bootstrap()
                .group(loops)
                .channel(transport)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, TIMEOUT_MILLIS)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        channel.pipeline()
                                .addLast(Connections.FRAME_DECODER, new VarIntFrameDecoder(1 << 21))
                                .addLast(Connections.FRAME_ENCODER, VarIntLengthEncoder.INSTANCE)
                                .addLast(Connections.MINECRAFT_DECODER, new MinecraftDecoder(
                                        PacketDirection.CLIENTBOUND, ProtocolState.HANDSHAKE,
                                        ProtocolVersion.MAXIMUM_NATIVE))
                                .addLast(Connections.MINECRAFT_ENCODER, new MinecraftEncoder(
                                        PacketDirection.SERVERBOUND, ProtocolState.HANDSHAKE,
                                        ProtocolVersion.MAXIMUM_NATIVE))
                                .addLast(Connections.HANDLER, new PingHandler(future, startNanos));
                    }
                })
                .connect(server.address())
                .addListener(f -> {
                    if (!f.isSuccess()) {
                        future.completeExceptionally(f.cause());
                    }
                });

        loops.schedule(() -> future.completeExceptionally(
                        new java.util.concurrent.TimeoutException("ping timed out")),
                TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        return future;
    }

    private static final class PingHandler extends ChannelInboundHandlerAdapter {

        private final CompletableFuture<ServerPing> future;
        private final long startNanos;

        PingHandler(CompletableFuture<ServerPing> future, long startNanos) {
            this.future = future;
            this.startNanos = startNanos;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            HandshakePacket handshake = new HandshakePacket();
            handshake.setProtocolVersion(ProtocolVersion.MAXIMUM_NATIVE.protocol());
            handshake.setServerAddress("hypergravel-health-check");
            handshake.setPort(((java.net.InetSocketAddress) ctx.channel().remoteAddress()).getPort());
            handshake.setIntent(HandshakePacket.INTENT_STATUS);
            ctx.write(handshake);

            ctx.pipeline().get(MinecraftEncoder.class).setState(ProtocolState.STATUS);
            ctx.pipeline().get(MinecraftDecoder.class).setState(ProtocolState.STATUS);

            ctx.writeAndFlush(new StatusPackets.Request());
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            try {
                if (msg instanceof StatusPackets.Response response) {
                    future.complete(parse(response.json(),
                            (System.nanoTime() - startNanos) / 1_000_000L));
                    ctx.close();
                }
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
                ctx.close();
            } finally {
                io.netty.util.ReferenceCountUtil.release(msg);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            future.completeExceptionally(new java.io.IOException("backend closed during ping"));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            future.completeExceptionally(cause);
            ctx.close();
        }

        private static ServerPing parse(String json, long latencyMillis) {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            int protocol = 0;
            String versionName = "";
            if (root.has("version")) {
                JsonObject version = root.getAsJsonObject("version");
                protocol = version.has("protocol") ? version.get("protocol").getAsInt() : 0;
                versionName = version.has("name") ? version.get("name").getAsString() : "";
            }

            int online = 0;
            int max = 0;
            if (root.has("players")) {
                JsonObject players = root.getAsJsonObject("players");
                online = players.has("online") ? players.get("online").getAsInt() : 0;
                max = players.has("max") ? players.get("max").getAsInt() : 0;
            }

            Component description = root.has("description")
                    ? GsonComponentSerializer.gson().deserializeFromTree(root.get("description"))
                    : Component.empty();

            return new ServerPing(
                    new ServerPing.Version(protocol, versionName),
                    new ServerPing.Players(online, max, List.of()),
                    description,
                    root.has("favicon") ? root.get("favicon").getAsString() : null,
                    root.has("enforcesSecureChat") && root.get("enforcesSecureChat").getAsBoolean());
        }
    }
}
