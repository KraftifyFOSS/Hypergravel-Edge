package pdx.dev.hypergravel.voice;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.UUID;

import media.gitm.hypergravel.proxy.HyperGravelProxy;
import media.gitm.hypergravel.proxy.player.ConnectedPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class VoiceService implements VoiceRelay.Endpoints {

    private static final Logger LOGGER = LogManager.getLogger(VoiceService.class);

    private static final String REQUEST_CHANNEL = "voicechat:request_secret";
    private static final String REQUEST_CHANNEL_LEGACY = "vc:request_secret";
    private static final String SECRET_CHANNEL = "voicechat:secret";
    private static final String SECRET_CHANNEL_LEGACY = "vc:secret";

    private final HyperGravelProxy proxy;
    private final VoiceSniffer sniffer = new VoiceSniffer();
    private final VoiceRelay relay;
    private final int bindPort;

    public VoiceService(HyperGravelProxy proxy, int bindPort, boolean allowPings) {
        this.proxy = proxy;
        this.bindPort = bindPort;
        this.relay = new VoiceRelay(this, allowPings);
    }

    public void start(String bindHost) throws IOException {
        relay.start(bindHost, bindPort);
    }

    public void stop() {
        relay.stop();
    }

    public int port() {
        return bindPort;
    }

    public int bridgeCount() {
        return relay.bridgeCount();
    }

    public static boolean isRequestChannel(String channel) {
        return REQUEST_CHANNEL.equals(channel) || REQUEST_CHANNEL_LEGACY.equals(channel);
    }

    public static boolean isSecretChannel(String channel) {
        return SECRET_CHANNEL.equals(channel) || SECRET_CHANNEL_LEGACY.equals(channel);
    }

    public void onRequest(ConnectedPlayer player, byte[] payload) {
        if (payload.length < 4) {
            return;
        }
        sniffer.rememberRequest(player.uuid(), ByteBuffer.wrap(payload).getInt());
    }

    public boolean onSecret(ConnectedPlayer player, String channel, byte[] payload) {
        UUID uuid = player.uuid();
        try {
            SecretPacket packet = SecretPacket.read(payload, sniffer.compatibilityFor(uuid));
            sniffer.rememberSecret(uuid, packet);

            relay.close(uuid);

            var voice = proxy.config().voice();
            packet.redirectTo(voice.host(), voice.publicPort());
            player.sendPluginMessage(channel, packet.write());
        } catch (IncompatibleVoiceException e) {
            LOGGER.info("voice: {} has an incompatible voice chat ({})",
                    player.username(), e.getMessage());
        }
        return true;
    }

    public void onServerChange(ConnectedPlayer player) {
        forget(player.uuid());
    }

    public void onDisconnect(ConnectedPlayer player) {
        forget(player.uuid());
    }

    private void forget(UUID player) {
        relay.close(player);
        sniffer.forget(player);
    }

    @Override
    public UUID resolvePlayer(UUID fromDatagram) {
        return sniffer.resolve(fromDatagram);
    }

    @Override
    public SocketAddress backendAddressFor(UUID player) {
        if (!sniffer.ready(player)) {
            return null;
        }
        ConnectedPlayer connected = proxy.playerRegistry().byUuid(player).orElse(null);
        if (connected == null) {
            return null;
        }
        var server = connected.currentHyperGravelServer().orElse(null);
        if (server == null) {
            return null;
        }
        Integer voicePort = sniffer.backendPort(player);
        if (voicePort == null) {
            return null;
        }

        return new InetSocketAddress(server.address().getHostString(), voicePort);
    }
}
