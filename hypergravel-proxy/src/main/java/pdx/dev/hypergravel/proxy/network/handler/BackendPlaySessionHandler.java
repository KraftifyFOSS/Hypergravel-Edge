package pdx.dev.hypergravel.proxy.network.handler;

import io.netty.buffer.ByteBuf;
import pdx.dev.hypergravel.api.event.PluginMessageEvent;
import pdx.dev.hypergravel.proxy.HyperGravelProxy;
import pdx.dev.hypergravel.proxy.backend.BackendConnection;
import pdx.dev.hypergravel.proxy.network.SessionHandler;
import pdx.dev.hypergravel.proxy.player.ConnectedPlayer;
import pdx.dev.hypergravel.proxy.protocol.Packet;
import pdx.dev.hypergravel.proxy.protocol.PacketType;
import pdx.dev.hypergravel.proxy.protocol.ProtocolState;
import pdx.dev.hypergravel.proxy.protocol.packet.CommandPackets;
import pdx.dev.hypergravel.proxy.protocol.packet.PlayPackets;
import pdx.dev.hypergravel.proxy.protocol.packet.SharedPackets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class BackendPlaySessionHandler implements SessionHandler {

    private static final Logger LOGGER = LogManager.getLogger(BackendPlaySessionHandler.class);

    private final HyperGravelProxy proxy;
    private final BackendConnection backend;
    private final ConnectedPlayer player;

    public BackendPlaySessionHandler(HyperGravelProxy proxy, BackendConnection backend) {
        this.proxy = proxy;
        this.backend = backend;
        this.player = backend.connectedPlayer();
    }

    @Override
    public void forwardRaw(ByteBuf frame) {
        // Frames the proxy does not decode still land here; the backend's
        // player list packets must not reach the client while the network list
        // is drawn, or the backend list and the synthetic list fight each other.
        if (proxy.config().tab().networkList()
                && player.connection().supports(PacketType.PLAYER_INFO_UPDATE)
                && TabPacketFilter.isPlayerListInfo(frame,
                        backend.connection().version(), backend.connection().state())) {
            frame.release();
            return;
        }
        if (player.connection().state() != ProtocolState.PLAY) {
            frame.release();
            return;
        }
        if (!backend.active() || !player.connection().active()) {

            frame.release();
            return;
        }

        player.connection().channel().write(frame,
                player.connection().channel().voidPromise());
    }

    @Override
    public void readComplete() {
        if (player.connection().active()) {
            player.connection().channel().flush();
        }
    }

    @Override
    public void forward(Packet packet) {
        if (backend.active() && player.connection().active()
                && player.connection().state() == ProtocolState.PLAY) {
            player.connection().writeAndFlush(packet);
        }
    }

    @Override
    public boolean handle(pdx.dev.hypergravel.tab.TabPackets.HeaderFooter packet) {

        return proxy.config().tab().networkList()
                && player.connection().supports(PacketType.PLAYER_INFO_UPDATE);
    }

    @Override
    public boolean handle(SharedPackets.KeepAlive packet) {

        backend.connection().writeAndFlush(new SharedPackets.KeepAlive(packet.id()));
        return true;
    }

    @Override
    public boolean handle(SharedPackets.Disconnect packet) {
        if (!backend.active()) {
            return true;
        }

        LOGGER.debug("{} was kicked from {}; falling back", player.username(),
                backend.server().name());
        proxy.connector().handleBackendLoss(player, backend.hypergravelServer(), true);
        return true;
    }

    @Override
    public boolean handle(PlayPackets.StartConfiguration packet) {

        backend.connection().setState(ProtocolState.CONFIGURATION);
        player.connection().setState(ProtocolState.CONFIGURATION);
        return false;
    }

    @Override
    public boolean handle(SharedPackets.PluginMessage packet) {
        String channel = packet.channel();
        if (pdx.dev.hypergravel.proxy.network.ServerBrand.CHANNEL.equals(channel)) {
            if (player.connection().active()) {
                player.connection().writeAndFlush(
                        pdx.dev.hypergravel.proxy.network.ServerBrand.appendTag(packet));
            } else {
                packet.release();
            }
            return true;
        }

        var voice = proxy.voice();
        if (voice != null && pdx.dev.hypergravel.voice.VoiceService.isSecretChannel(channel)) {
            byte[] payload = packet.copyData();
            packet.release();
            voice.onSecret(player, channel, payload);
            return true;
        }

        if (pdx.dev.hypergravel.proxy.network.BungeeCordChannel.isBungeeChannel(channel)) {
            pdx.dev.hypergravel.proxy.network.BungeeCordChannel.handle(
                    proxy, player, backend.hypergravelServer(), channel, packet.data());
            packet.release();
            return true;
        }
        if (!proxy.channels().isRegistered(channel)) {
            return false;
        }
        PluginMessageEvent event = new PluginMessageEvent(backend, player, channel,
                packet.copyData());
        packet.release();
        proxy.eventManager().fireAndForget(event);
        return true;
    }

    @Override
    public boolean handle(CommandPackets.Commands packet) {

        if (player.connection().active()) {
            CommandPackets.Commands rewritten = new CommandPackets.Commands();
            rewritten.setGraph(CommandPackets.Commands.injectServerCommand(packet.graph()));
            player.connection().writeAndFlush(rewritten);
        }
        return true;
    }

    @Override
    public boolean handle(PlayPackets.JoinGame packet) {
        try {
            proxy.connector().onBackendJoinGame(player, backend, packet);
        } finally {
            packet.release();
        }
        return true;
    }

    @Override
    public void writabilityChanged(boolean writable) {

        player.connection().channel().config().setAutoRead(writable);
    }

    @Override
    public void disconnected() {
        if (!backend.active()) {

            return;
        }
        proxy.connector().handleBackendLoss(player, backend.hypergravelServer(), false);
    }

    @Override
    public boolean handleException(Throwable cause) {
        LOGGER.debug("backend {} errored for {}: {}", backend.server().name(),
                player.username(), cause.toString());
        return false;
    }
}
