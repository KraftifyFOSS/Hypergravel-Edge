package pdx.dev.hypergravel.proxy.backend;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import pdx.dev.hypergravel.api.event.PreServerConnectEvent;
import pdx.dev.hypergravel.api.event.ServerConnectedEvent;
import pdx.dev.hypergravel.api.player.Player;
import pdx.dev.hypergravel.api.server.ForwardingMode;
import pdx.dev.hypergravel.proxy.HyperGravelProxy;
import pdx.dev.hypergravel.proxy.forwarding.LegacyForwarding;
import pdx.dev.hypergravel.proxy.network.Connections;
import pdx.dev.hypergravel.proxy.network.MinecraftConnection;
import pdx.dev.hypergravel.proxy.network.codec.MinecraftDecoder;
import pdx.dev.hypergravel.proxy.network.codec.MinecraftEncoder;
import pdx.dev.hypergravel.proxy.network.codec.VarIntFrameDecoder;
import pdx.dev.hypergravel.proxy.network.codec.VarIntLengthEncoder;
import pdx.dev.hypergravel.proxy.network.handler.BackendLoginHandler;
import pdx.dev.hypergravel.proxy.network.handler.BackendPlaySessionHandler;
import pdx.dev.hypergravel.proxy.network.handler.ClientPlaySessionHandler;
import pdx.dev.hypergravel.proxy.player.ConnectedPlayer;
import pdx.dev.hypergravel.proxy.protocol.PacketDirection;
import pdx.dev.hypergravel.proxy.protocol.ProtocolState;
import pdx.dev.hypergravel.proxy.protocol.packet.HandshakePacket;
import pdx.dev.hypergravel.proxy.protocol.packet.LoginPackets;
import pdx.dev.hypergravel.proxy.protocol.packet.PlayPackets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class BackendConnector {

    private static final Logger LOGGER = LogManager.getLogger(BackendConnector.class);

    private static final long RETURN_TTL_MILLIS = 10 * 60 * 1000;

    private final HyperGravelProxy proxy;

    public BackendConnector(HyperGravelProxy proxy) {
        this.proxy = proxy;
    }

    public void routeInitial(ConnectedPlayer player) {

        if (proxy.queue().admit(player)) {
            return;
        }
        List<HyperGravelServer> candidates = proxy.serverRegistry().routableTryOrder();
        if (candidates.isEmpty()) {
            player.disconnect(MiniMessage.miniMessage().deserialize(
                    "<red>No servers are available right now.\n<gray>Try again in a moment."));
            return;
        }
        tryInOrder(player, candidates, 0, PreServerConnectEvent.Reason.INITIAL);
    }

    private void tryInOrder(ConnectedPlayer player, List<HyperGravelServer> candidates, int index,
                            PreServerConnectEvent.Reason reason) {
        if (index >= candidates.size()) {
            player.disconnect(MiniMessage.miniMessage().deserialize(
                    "<red>Couldn't put you on a server.\n<gray>Try again in a moment."));
            return;
        }
        HyperGravelServer target = candidates.get(index);
        connect(player, target, reason).thenAccept(result -> {
            if (result != Player.ConnectionResult.SUCCESS && player.connection().active()) {
                tryInOrder(player, candidates, index + 1, reason);
            }
        });
    }

    public CompletableFuture<Player.ConnectionResult> connect(
            ConnectedPlayer player, HyperGravelServer target, PreServerConnectEvent.Reason reason) {

        if (!player.connection().active()) {
            return CompletableFuture.completedFuture(Player.ConnectionResult.GONE);
        }
        if (!player.beginConnect()) {
            return CompletableFuture.completedFuture(Player.ConnectionResult.IN_PROGRESS);
        }

        CompletableFuture<Player.ConnectionResult> result = new CompletableFuture<>();
        result.whenComplete((ignored, error) -> player.endConnect());

        PreServerConnectEvent event = new PreServerConnectEvent(player, target, reason);
        proxy.eventManager().fire(event).whenComplete((fired, error) -> {
            if (error != null) {
                LOGGER.error("a PreServerConnectEvent handler threw", error);
                result.complete(Player.ConnectionResult.DENIED);
                return;
            }
            PreServerConnectEvent.Result outcome = fired.result();
            if (!outcome.isAllowed()) {
                outcome.denyReason().ifPresent(player::sendMessage);
                result.complete(Player.ConnectionResult.DENIED);
                return;
            }
            HyperGravelServer finalTarget = (HyperGravelServer) fired.target();
            if (!hasAccess(player, finalTarget)) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<red>You don't have access to that server."));
                result.complete(Player.ConnectionResult.DENIED);
                return;
            }
            dial(player, finalTarget, result);
        });

        return result;
    }

    private boolean hasAccess(ConnectedPlayer player, HyperGravelServer server) {
        String permission = server.info().permission();
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }

    private void dial(ConnectedPlayer player, HyperGravelServer target,
                      CompletableFuture<Player.ConnectionResult> result) {

        var config = proxy.config();

        new Bootstrap()

                .group(player.connection().channel().eventLoop())
                .channel(proxy.transport().channelType())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) config.network().connectTimeout().toMillis())
                .option(ChannelOption.TCP_NODELAY, config.network().tcpNoDelay())
                .option(ChannelOption.SO_RCVBUF, 262144)
                .option(ChannelOption.SO_SNDBUF, 262144)
                .option(ChannelOption.ALLOCATOR, proxy.allocator())
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK,
                        new io.netty.channel.WriteBufferWaterMark(
                                config.network().writeBufferLow(),
                                config.network().writeBufferHigh()))
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        MinecraftConnection connection = new MinecraftConnection(channel);
                        channel.pipeline()
                                .addLast(Connections.FRAME_DECODER,
                                        new VarIntFrameDecoder(config.network().maxPacketSize()))
                                .addLast(Connections.FRAME_ENCODER, VarIntLengthEncoder.INSTANCE)
                                .addLast(Connections.MINECRAFT_DECODER,
                                        new MinecraftDecoder(PacketDirection.CLIENTBOUND,
                                                ProtocolState.HANDSHAKE, player.version()))
                                .addLast(Connections.MINECRAFT_ENCODER,
                                        new MinecraftEncoder(PacketDirection.SERVERBOUND,
                                                ProtocolState.HANDSHAKE, player.version()))
                                .addLast(Connections.HANDLER, connection);
                    }
                })
                .connect(target.address())
                .addListener((io.netty.channel.ChannelFuture future) -> {
                    if (!future.isSuccess()) {
                        LOGGER.debug("cannot reach {} for {}: {}", target.name(),
                                player.username(), future.cause().toString());
                        target.invalidateAddress();
                        result.complete(Player.ConnectionResult.UNREACHABLE);
                        return;
                    }
                    startBackendLogin(player, target, future.channel(), result);
                });
    }

    private void startBackendLogin(ConnectedPlayer player, HyperGravelServer target, Channel channel,
                                   CompletableFuture<Player.ConnectionResult> result) {

        MinecraftConnection connection =
                (MinecraftConnection) channel.pipeline().get(Connections.HANDLER);
        BackendConnection backend = new BackendConnection(player, target, connection);
        connection.setVersion(player.version());
        connection.setHandler(new BackendLoginHandler(proxy, backend, result));

        HandshakePacket handshake = new HandshakePacket();
        handshake.setProtocolVersion(player.protocolVersion());
        handshake.setServerAddress(handshakeAddress(player, target));
        handshake.setPort(target.address().getPort());
        handshake.setIntent(HandshakePacket.INTENT_LOGIN);
        connection.write(handshake);

        connection.setState(ProtocolState.LOGIN);

        connection.writeAndFlush(new LoginPackets.Start(player.username(), player.uuid()));

        result.whenComplete((outcome, error) -> {
            if (outcome != Player.ConnectionResult.SUCCESS) {
                connection.close();
            }
        });
    }

    private String handshakeAddress(ConnectedPlayer player, HyperGravelServer target) {
        ForwardingMode mode = target.info().forwarding();
        String host = player.virtualHost() == null || player.virtualHost().isBlank()
                ? target.address().getHostString()
                : player.virtualHost();
        String clientIp = player.remoteAddress().getAddress().getHostAddress();

        return switch (mode) {

            case VELOCITY_MODERN, NONE -> host;
            case LEGACY -> LegacyForwarding.createAddress(host, clientIp, player.profile());
            case BUNGEEGUARD -> LegacyForwarding.createGuardedAddress(host, clientIp,
                    player.profile(), proxy.config().forwarding().bungeeGuardToken());
        };
    }

    public void completeInitialJoin(BackendConnection backend,
                                    CompletableFuture<Player.ConnectionResult> result) {
        ConnectedPlayer player = backend.connectedPlayer();
        if (!player.connection().active()) {
            backend.close();
            if (!result.isDone()) {
                result.complete(Player.ConnectionResult.GONE);
            }
            return;
        }

        BackendConnection previous = player.backendConnection();
        HyperGravelServer previousServer = previous == null ? null : previous.hypergravelServer();

        backend.markActive();
        player.setBackend(backend);
        backend.connection().setHandler(new BackendPlaySessionHandler(proxy, backend));
        player.connection().setHandler(new ClientPlaySessionHandler(proxy, player));
        player.connection().setState(ProtocolState.PLAY);
        player.clearConfigBridge();
        announceChannels(backend);

        backend.hypergravelServer().addPlayer(player);
        if (previous != null && previous != backend) {
            if (previousServer != null) {
                previousServer.removePlayer(player);
            }
            previous.close();
        }
        if (!result.isDone()) {
            result.complete(Player.ConnectionResult.SUCCESS);
        }
        proxy.eventManager().fireAndForget(
                new ServerConnectedEvent(player, backend.hypergravelServer(), previousServer));
    }

private void announceChannels(BackendConnection backend) {
        String channels = String.join("",
                "bungeecord:main\u0000" + pdx.dev.hypergravel.proxy.queue.QueueService.CHANNEL);
        backend.sendPluginMessage("minecraft:register",
                channels.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public void onBackendReady(BackendConnection backend,
                               CompletableFuture<Player.ConnectionResult> result) {
        ConnectedPlayer player = backend.connectedPlayer();
        if (!player.connection().active()) {
            backend.close();
            result.complete(Player.ConnectionResult.GONE);
            return;
        }

        BackendConnection previous = player.backendConnection();
        HyperGravelServer previousServer = previous == null ? null : previous.hypergravelServer();

        backend.markActive();
        player.setBackend(backend);
        backend.connection().setHandler(new BackendPlaySessionHandler(proxy, backend));
        player.connection().setHandler(new ClientPlaySessionHandler(proxy, player));
        player.connection().setState(ProtocolState.PLAY);
        announceChannels(backend);

        backend.hypergravelServer().addPlayer(player);
        if (previous != null) {
            previousServer.removePlayer(player);
            previous.close();
        }

        player.clearReturnTo();

        result.complete(Player.ConnectionResult.SUCCESS);
        proxy.eventManager().fireAndForget(
                new ServerConnectedEvent(player, backend.hypergravelServer(), previousServer));
    }

    public void onBackendJoinGame(ConnectedPlayer player, BackendConnection backend,
                                  PlayPackets.JoinGame packet) {
        if (backend.active() && player.connection().active()) {
            player.connection().writeAndFlush(packet);
        }
    }

    public void handleBackendLoss(ConnectedPlayer player, HyperGravelServer lost, boolean kicked) {
        if (!player.connection().active()) {
            return;
        }
        lost.removePlayer(player);

        if (!kicked) {
            player.setReturnTo(lost, RETURN_TTL_MILLIS);
        }

        proxy.serverRegistry().holdingFor(lost).ifPresentOrElse(
                holding -> connect(player, holding, PreServerConnectEvent.Reason.FALLBACK)
                        .thenAccept(result -> {
                            if (result != Player.ConnectionResult.SUCCESS) {
                                player.disconnect(MiniMessage.miniMessage().deserialize(
                                        "<red>" + lost.name() + " went away and no fallback "
                                                + "was reachable."));
                            } else {
                                player.sendMessage(MiniMessage.miniMessage().deserialize(
                                        "<yellow>" + lost.name() + " is restarting. "
                                                + "<gray>You'll be moved back automatically."));
                            }
                        }),
                () -> player.disconnect(Component.text(
                        lost.name() + " went away and there is no fallback configured.")));
    }

    public void restoreTo(HyperGravelServer server, List<ConnectedPlayer> waiting, int perSecond) {
        if (waiting.isEmpty()) {
            return;
        }
        LOGGER.info("restoring {} player(s) to {} at {}/s",
                waiting.size(), server.name(), perSecond);
        restoreBatch(server, waiting, 0, Math.max(1, perSecond));
    }

    private void restoreBatch(HyperGravelServer server, List<ConnectedPlayer> waiting, int index,
                              int perSecond) {
        if (index >= waiting.size()) {
            return;
        }
        int end = Math.min(index + perSecond, waiting.size());
        for (int i = index; i < end; i++) {
            ConnectedPlayer player = waiting.get(i);
            if (!player.connection().active()) {
                continue;
            }
            connect(player, server, PreServerConnectEvent.Reason.RESTORE);
        }
        proxy.scheduler().delay(this, java.time.Duration.ofSeconds(1),
                () -> restoreBatch(server, waiting, end, perSecond));
    }
}
