package pdx.dev.hypergravel.proxy.network.handler;

import java.util.concurrent.CompletableFuture;

import io.netty.buffer.ByteBuf;
import pdx.dev.hypergravel.api.player.Player;
import pdx.dev.hypergravel.proxy.HyperGravelProxy;
import pdx.dev.hypergravel.proxy.backend.BackendConnection;
import pdx.dev.hypergravel.proxy.network.SessionHandler;
import pdx.dev.hypergravel.proxy.player.ConnectedPlayer;
import pdx.dev.hypergravel.proxy.protocol.Packet;
import pdx.dev.hypergravel.proxy.protocol.ProtocolState;
import pdx.dev.hypergravel.proxy.protocol.packet.ConfigPackets;

public final class ClientConfigSessionHandler implements SessionHandler {

    private final HyperGravelProxy proxy;
    private final ConnectedPlayer player;

    public ClientConfigSessionHandler(HyperGravelProxy proxy, ConnectedPlayer player) {
        this.proxy = proxy;
        this.player = player;
    }

    @Override
    public void forwardRaw(ByteBuf frame) {
        pdx.dev.hypergravel.proxy.network.ClientSettings.remember(player, frame);
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
