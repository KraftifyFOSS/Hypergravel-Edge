package pdx.dev.hypergravel.proxy.network.handler;

import io.netty.buffer.ByteBuf;
import pdx.dev.hypergravel.api.event.PluginMessageEvent;
import pdx.dev.hypergravel.proxy.HyperGravelProxy;
import pdx.dev.hypergravel.proxy.backend.BackendConnection;
import pdx.dev.hypergravel.proxy.network.SessionHandler;
import pdx.dev.hypergravel.proxy.player.ConnectedPlayer;
import pdx.dev.hypergravel.proxy.protocol.Packet;
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
        // The backend's player list frames pass through, except the profile
        // properties on add-player rows, which are replaced with the Mojang
        // properties the proxy verified at login. Backends have been seen to
        // emit mismatched value/signature pairs, which strict clients reject
        // with blank tab heads while lenient ones render them anyway.
        if (player.connection().state() != ProtocolState.PLAY) {
            frame.release();
            return;
        }
        ByteBuf rewritten = rewritePlayerInfo(frame);
        ByteBuf outgoing = rewritten != null ? rewritten : frame;
        if (rewritten != null) {
            frame.release();
        }
        if (!backend.active() || !player.connection().active()) {

            outgoing.release();
            return;
        }

        player.connection().channel().write(outgoing,
                player.connection().channel().voidPromise());
    }

    private ByteBuf rewritePlayerInfo(ByteBuf frame) {
        if (backend.connection().state() != ProtocolState.PLAY) {
            return null;
        }
        int updateId = pdx.dev.hypergravel.proxy.protocol.StateRegistry
                .of(ProtocolState.PLAY)
                .direction(pdx.dev.hypergravel.proxy.protocol.PacketDirection.CLIENTBOUND)
                .forVersion(pdx.dev.hypergravel.via.ViaSupport.serverProtocolVersion())
                .idOf(pdx.dev.hypergravel.proxy.protocol.PacketType.PLAYER_INFO_UPDATE);
        if (updateId < 0) {
            return null;
        }
        return pdx.dev.hypergravel.tab.PlayerInfoRewriter.rewriteAdd(frame, updateId,
                uuid -> proxy.playerRegistry().byUuid(uuid)
                        .map(connected -> connected.profile().properties())
                        .filter(properties -> properties.length > 0)
                        .orElse(null),
                player.connection().channel().alloc());
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
                && pdx.dev.hypergravel.tab.TabService.reaches(player);
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
