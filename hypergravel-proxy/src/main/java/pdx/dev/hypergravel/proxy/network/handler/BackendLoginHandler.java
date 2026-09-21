package pdx.dev.hypergravel.proxy.network.handler;

import java.util.concurrent.CompletableFuture;

import pdx.dev.hypergravel.api.player.Player;
import pdx.dev.hypergravel.api.server.ForwardingMode;
import pdx.dev.hypergravel.proxy.HyperGravelProxy;
import pdx.dev.hypergravel.proxy.forwarding.VelocityForwarding;
import pdx.dev.hypergravel.proxy.network.SessionHandler;
import pdx.dev.hypergravel.proxy.protocol.ProtocolState;
import pdx.dev.hypergravel.proxy.protocol.packet.ConfigPackets;
import pdx.dev.hypergravel.proxy.protocol.packet.LoginPackets;
import pdx.dev.hypergravel.proxy.protocol.packet.SharedPackets;
import pdx.dev.hypergravel.proxy.backend.BackendConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class BackendLoginHandler implements SessionHandler {

    private static final Logger LOGGER = LogManager.getLogger(BackendLoginHandler.class);

    private final HyperGravelProxy proxy;
    private final BackendConnection backend;
    private final CompletableFuture<Player.ConnectionResult> result;

    public BackendLoginHandler(HyperGravelProxy proxy, BackendConnection backend,
                               CompletableFuture<Player.ConnectionResult> result) {
        this.proxy = proxy;
        this.backend = backend;
        this.result = result;
    }

    @Override
    public boolean handle(LoginPackets.PluginRequest packet) {
        if (!VelocityForwarding.CHANNEL.equals(packet.channel())) {

            backend.connection().writeAndFlush(
                    new LoginPackets.PluginResponse(packet.messageId(), false, new byte[0]));
            return true;
        }

        ForwardingMode mode = backend.hypergravelServer().info().forwarding();
        if (mode != ForwardingMode.VELOCITY_MODERN) {
            LOGGER.warn("{} requested modern forwarding but is configured for {}",
                    backend.server().name(), mode);
            backend.connection().writeAndFlush(
                    new LoginPackets.PluginResponse(packet.messageId(), false, new byte[0]));
            return true;
        }

        byte[] req = packet.data();
        LOGGER.debug("velocity player_info from {}: reqDataLen={} reqData={} profileProps={}",
                backend.server().name(), req.length,
                io.netty.buffer.ByteBufUtil.hexDump(io.netty.buffer.Unpooled.wrappedBuffer(req)),
                backend.connectedPlayer().profile().properties().length);

        byte[] payload = VelocityForwarding.createPayload(
                proxy.config().forwarding().secret(),
                backend.connectedPlayer().remoteAddress().getAddress().getHostAddress(),
                backend.connectedPlayer().profile(),
                backend.connectedPlayer().version());

        backend.connection().writeAndFlush(
                new LoginPackets.PluginResponse(packet.messageId(), true, payload));
        return true;
    }

    @Override
    public boolean handle(LoginPackets.SetCompression packet) {
        backend.connection().setCompressionThreshold(packet.threshold(),
                proxy.config().network().compressionLevel(),
                proxy.config().network().maxUncompressedSize());
        return true;
    }

    @Override
    public boolean handle(LoginPackets.Success packet) {

        backend.connection().writeAndFlush(new LoginPackets.Acknowledged());
        backend.connection().setState(ProtocolState.CONFIGURATION);

        if (backend.connectedPlayer().backendConnection() == null) {
            backend.connection().setHandler(
                    new BackendConfigSessionHandler(proxy, backend, result));
            backend.connectedPlayer().beginBackendConfig(backend, result);
        } else {

            backend.connection().setHandler(
                    new BackendConfigSessionHandler(proxy, backend, result));
            backend.connection().channel().config().setAutoRead(false);
            backend.connectedPlayer().beginSwitch(backend, result);

            pdx.dev.hypergravel.proxy.network.ClientSettings.replay(
                    backend.connectedPlayer(), backend);
        }
        return true;
    }

    @Override
    public boolean handle(ConfigPackets.FinishConfiguration packet) {

        backend.connection().writeAndFlush(new ConfigPackets.AckFinishConfiguration());
        backend.connection().setState(ProtocolState.PLAY);
        proxy.connector().onBackendReady(backend, result);
        return true;
    }

    @Override
    public boolean handle(SharedPackets.Disconnect packet) {

        LOGGER.warn("{} refused our login for {}: {}", backend.server().name(),
                backend.connectedPlayer().username(),
                packet.reasonJson().isEmpty() ? "(no reason given)" : packet.reasonJson());
        result.complete(Player.ConnectionResult.REJECTED);
        backend.close();
        return true;
    }

    @Override
    public void forwardRaw(io.netty.buffer.ByteBuf frame) {

        if (backend.active() && backend.connectedPlayer().connection().active()) {
            backend.connectedPlayer().connection().channel()
                    .writeAndFlush(frame, backend.connectedPlayer()
                            .connection().channel().voidPromise());
        } else {
            frame.release();
        }
    }

    @Override
    public void disconnected() {
        if (!result.isDone()) {
            result.complete(Player.ConnectionResult.UNREACHABLE);
        }
    }

    @Override
    public boolean handleException(Throwable cause) {
        if (!result.isDone()) {
            result.complete(Player.ConnectionResult.UNREACHABLE);
        }
        return false;
    }
}
