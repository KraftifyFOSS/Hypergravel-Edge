package media.gitm.hypergravel.proxy.network.handler;

import java.util.concurrent.CompletableFuture;

import io.netty.buffer.ByteBuf;
import media.gitm.hypergravel.api.player.Player;
import media.gitm.hypergravel.proxy.HyperGravelProxy;
import media.gitm.hypergravel.proxy.backend.BackendConnection;
import media.gitm.hypergravel.proxy.network.SessionHandler;
import media.gitm.hypergravel.proxy.player.ConnectedPlayer;
import media.gitm.hypergravel.proxy.protocol.Packet;
import media.gitm.hypergravel.proxy.protocol.ProtocolState;
import media.gitm.hypergravel.proxy.protocol.packet.ConfigPackets;

public final class ClientConfigSessionHandler implements SessionHandler {

    private final HyperGravelProxy proxy;
    private final ConnectedPlayer player;

    public ClientConfigSessionHandler(HyperGravelProxy proxy, ConnectedPlayer player) {
        this.proxy = proxy;
        this.player = player;
    }

    @Override
    public void forwardRaw(ByteBuf frame) {
        media.gitm.hypergravel.proxy.network.ClientSettings.remember(player, frame);
        BackendConnection backend = player.connectingBackend();
        if (backend != null && backend.connection().active()) {
            backend.connection().channel().writeAndFlush(
                    frame, backend.connection().channel().voidPromise());
        } else {

            player.bufferClientConfig(frame);
        }
    }

    @Override
    public void forward(Packet packet) {
        BackendConnection backend = player.connectingBackend();
        if (backend != null && backend.connection().active()) {
            backend.connection().writeAndFlush(packet);
        }
    }

    @Override
    public boolean handle(ConfigPackets.AckFinishConfiguration packet) {
        BackendConnection backend = player.connectingBackend();
        CompletableFuture<Player.ConnectionResult> result = player.connectingResult();
        if (backend == null || result == null || !backend.connection().active()) {
            player.connection().close();
            return true;
        }

        backend.connection().writeAndFlush(new ConfigPackets.AckFinishConfiguration());
        backend.connection().setState(ProtocolState.PLAY);
        proxy.connector().completeInitialJoin(backend, result);
        return true;
    }

    @Override
    public void disconnected() {
        proxy.disconnect(player);
    }
}
