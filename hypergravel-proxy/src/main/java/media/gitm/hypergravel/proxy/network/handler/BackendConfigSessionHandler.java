package media.gitm.hypergravel.proxy.network.handler;

import java.util.concurrent.CompletableFuture;

import io.netty.buffer.ByteBuf;
import media.gitm.hypergravel.api.player.Player;
import media.gitm.hypergravel.proxy.HyperGravelProxy;
import media.gitm.hypergravel.proxy.backend.BackendConnection;
import media.gitm.hypergravel.proxy.network.SessionHandler;
import media.gitm.hypergravel.proxy.player.ConnectedPlayer;
import media.gitm.hypergravel.proxy.protocol.Packet;
import media.gitm.hypergravel.proxy.protocol.packet.ConfigPackets;
import media.gitm.hypergravel.proxy.protocol.packet.SharedPackets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class BackendConfigSessionHandler implements SessionHandler {

    private static final Logger LOGGER = LogManager.getLogger(BackendConfigSessionHandler.class);

    private final HyperGravelProxy proxy;
    private final BackendConnection backend;
    private final ConnectedPlayer player;
    private final CompletableFuture<Player.ConnectionResult> result;

    public BackendConfigSessionHandler(HyperGravelProxy proxy, BackendConnection backend,
                                       CompletableFuture<Player.ConnectionResult> result) {
        this.proxy = proxy;
        this.backend = backend;
        this.player = backend.connectedPlayer();
        this.result = result;
    }

    @Override
    public void forwardRaw(ByteBuf frame) {
        if (player.connection().active()) {
            player.connection().channel().writeAndFlush(
                    frame, player.connection().channel().voidPromise());
        } else {
            frame.release();
        }
    }

    @Override
    public void forward(Packet packet) {
        if (player.connection().active()) {
            player.connection().writeAndFlush(packet);
        }
    }

    @Override
    public boolean handle(SharedPackets.PluginMessage packet) {

        if (media.gitm.hypergravel.proxy.network.ServerBrand.CHANNEL.equals(packet.channel())) {
            if (player.connection().active()) {
                player.connection().writeAndFlush(
                        media.gitm.hypergravel.proxy.network.ServerBrand.appendTag(packet));
            } else {
                packet.release();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean handle(ConfigPackets.FinishConfiguration packet) {

        if (player.connection().active()) {
            player.connection().writeAndFlush(new ConfigPackets.FinishConfiguration());
        }
        return true;
    }

    @Override
    public boolean handle(SharedPackets.Disconnect packet) {
        LOGGER.warn("{} refused {} during configuration: {}", backend.server().name(),
                player.username(),
                packet.reasonJson().isEmpty() ? "(no reason given)" : packet.reasonJson());
        if (!result.isDone()) {
            result.complete(Player.ConnectionResult.REJECTED);
        }
        backend.close();
        return true;
    }

    @Override
    public void writabilityChanged(boolean writable) {

        if (player.connection().active()) {
            player.connection().channel().config().setAutoRead(writable);
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
        LOGGER.debug("backend {} config errored for {}: {}", backend.server().name(),
                player.username(), cause.toString());
        if (!result.isDone()) {
            result.complete(Player.ConnectionResult.UNREACHABLE);
        }
        return false;
    }
}
