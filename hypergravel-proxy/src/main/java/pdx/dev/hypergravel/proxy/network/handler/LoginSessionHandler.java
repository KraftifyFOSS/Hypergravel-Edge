package pdx.dev.hypergravel.proxy.network.handler;

import java.security.MessageDigest;
import java.util.Arrays;

import pdx.dev.hypergravel.api.event.DisconnectEvent;
import pdx.dev.hypergravel.api.event.LoginEvent;
import pdx.dev.hypergravel.proxy.HyperGravelProxy;
import pdx.dev.hypergravel.proxy.auth.EncryptionManager;
import pdx.dev.hypergravel.proxy.auth.MojangAuthenticator;
import pdx.dev.hypergravel.proxy.config.HyperGravelConfig;
import pdx.dev.hypergravel.proxy.network.MinecraftConnection;
import pdx.dev.hypergravel.proxy.network.SessionHandler;
import pdx.dev.hypergravel.proxy.player.ConnectedPlayer;
import pdx.dev.hypergravel.proxy.protocol.ProtocolState;
import pdx.dev.hypergravel.proxy.protocol.packet.GameProfile;
import pdx.dev.hypergravel.proxy.protocol.packet.HandshakePacket;
import pdx.dev.hypergravel.proxy.protocol.packet.LoginPackets;
import pdx.dev.hypergravel.proxy.protocol.packet.SharedPackets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class LoginSessionHandler implements SessionHandler {

    private static final Logger LOGGER = LogManager.getLogger(LoginSessionHandler.class);

    private static final String SERVER_ID = "";

    private final HyperGravelProxy proxy;
    private final MinecraftConnection connection;
    private final HandshakePacket handshake;

    private String username;
    private byte[] verifyToken;
    private ConnectedPlayer player;
    private boolean loginStarted;

    public LoginSessionHandler(HyperGravelProxy proxy, MinecraftConnection connection,
                               HandshakePacket handshake) {
        this.proxy = proxy;
        this.connection = connection;
        this.handshake = handshake;
    }

    @Override
    public boolean handle(LoginPackets.Start packet) {
        if (loginStarted) {
            connection.close();
            return true;
        }
        loginStarted = true;
        this.username = packet.username();

        if (!isValidUsername(username)) {
            disconnect(Component.text("Invalid username."));
            return true;
        }

        if (!proxy.loginThrottle().tryAcquire(connection.remoteAddress().getAddress())) {

            connection.close();
            return true;
        }

        HyperGravelConfig config = proxy.config();
        if (config.limits().maintenance() && !isAllowlisted(username, config)) {
            disconnect(MiniMessage.miniMessage().deserialize(
                    "<red>The network is in maintenance.\n<gray>Check back shortly."));
            return true;
        }

        if (config.bind().onlineMode()) {
            this.verifyToken = EncryptionManager.newVerifyToken();
            connection.writeAndFlush(new LoginPackets.EncryptionRequest(
                    SERVER_ID, proxy.encryption().publicKey(), verifyToken));
        } else {
            completeLogin(GameProfile.offline(username));
        }
        return true;
    }

    @Override
    public boolean handle(LoginPackets.EncryptionResponse packet) {
        if (verifyToken == null) {
            connection.close();
            return true;
        }

        byte[] sharedSecret;
        try {
            byte[] decryptedToken = proxy.encryption().decrypt(packet.verifyToken());

            if (!MessageDigest.isEqual(decryptedToken, verifyToken)) {
                LOGGER.debug("verify token mismatch for {}", username);
                connection.close();
                return true;
            }
            sharedSecret = proxy.encryption().decrypt(packet.sharedSecret());
        } catch (Exception e) {
            LOGGER.debug("encryption response from {} failed to decrypt: {}", username, e.toString());
            connection.close();
            return true;
        }

        try {
            connection.enableEncryption(sharedSecret);
        } catch (Exception e) {
            LOGGER.warn("cannot enable encryption for {}: {}", username, e.toString());
            connection.close();
            return true;
        }

        String serverIdHash = proxy.encryption().serverIdHash(SERVER_ID, sharedSecret);

        proxy.authenticator()
                .hasJoined(username, serverIdHash, connection.remoteAddress().getAddress())
                .whenComplete((result, error) -> connection.channel().eventLoop().execute(() -> {
                    if (!connection.active()) {
                        return;
                    }
                    if (error != null) {
                        LOGGER.warn("session lookup for {} failed: {}", username, error.toString());
                        disconnect(MiniMessage.miniMessage().deserialize(
                                "<red>Couldn't reach the login servers.\n<gray>Try again shortly."));
                        return;
                    }
                    onAuthResult(result);
                }));
        return true;
    }

    private void onAuthResult(MojangAuthenticator.Result result) {
        switch (result.status()) {
            case AUTHENTICATED -> completeLogin(result.profile());
            case UNAUTHENTICATED -> disconnect(MiniMessage.miniMessage().deserialize(
                    "<red>Couldn't verify your session.\n<gray>Restart your launcher and try again."));
            case UNAVAILABLE -> {
                LOGGER.warn("session server unavailable while authenticating {}: {}",
                        username, result.detail());
                disconnect(MiniMessage.miniMessage().deserialize(
                        "<red>The login servers are unreachable.\n<gray>Try again shortly."));
            }
        }
    }

    private void completeLogin(GameProfile profile) {
        HyperGravelConfig config = proxy.config();

        int threshold = config.network().compressionThreshold();
        if (threshold >= 0) {
            connection.writeAndFlush(new LoginPackets.SetCompression(threshold));
            connection.setCompressionThreshold(threshold,
                    config.network().compressionLevel(),
                    config.network().maxUncompressedSize());
        }

        ConnectedPlayer connected = new ConnectedPlayer(proxy, connection, profile,
                connection.version(), connection.remoteAddress(),
                handshake.cleanAddress(), false);

        if (!proxy.playerRegistry().tryRegister(connected)) {
            disconnect(MiniMessage.miniMessage().deserialize(
                    "<red>You're already connected to this network."));
            return;
        }
        this.player = connected;
        connection.channel().attr(
                pdx.dev.hypergravel.proxy.network.Connections.PLAYER).set(connected);

        
        if (proxy.proxyBans().isBanned(profile.uuid())) {
            disconnect(proxy.proxyBans().denyReason(profile.uuid()));
            return;
        }

        proxy.eventManager().fire(new LoginEvent(connected))
                .whenComplete((event, error) -> connection.channel().eventLoop().execute(() -> {
                    if (!connection.active()) {
                        return;
                    }
                    if (error != null) {
                        LOGGER.error("a LoginEvent handler threw for {}", username, error);
                    }
                    if (error == null && !event.allowed()) {
                        disconnect(event.denyReason().orElse(Component.text("Login denied.")));
                        return;
                    }
                    sendSuccess(profile);
                }));
    }

    private void sendSuccess(GameProfile profile) {
        connection.writeAndFlush(new LoginPackets.Success(
                profile.uuid(), profile.name(), profile.properties()));

        pdx.dev.hypergravel.via.ViaSupport.onLoginSuccess(connection.channel());
        String translated =
                pdx.dev.hypergravel.via.ViaSupport.translatedVersionName(connection.channel());
        if (translated != null) {
            LOGGER.info("{} joined on {} (translated)", profile.name(), translated);
        }
    }

    @Override
    public boolean handle(LoginPackets.Acknowledged packet) {
        if (player == null) {
            connection.close();
            return true;
        }
        connection.setState(ProtocolState.CONFIGURATION);

        connection.setHandler(new ClientConfigSessionHandler(proxy, player));
        proxy.connector().routeInitial(player);
        return true;
    }

    @Override
    public void disconnected() {
        if (player != null) {
            proxy.playerRegistry().unregister(player);
            proxy.eventManager().fireAndForget(
                    new DisconnectEvent(player, DisconnectEvent.Stage.LOGIN));
        }
        proxy.loginThrottle().release(connection.remoteAddress().getAddress());
    }

    private void disconnect(Component reason) {
        disconnectDuringLogin(connection, reason);
    }

    static void disconnectDuringLogin(MinecraftConnection connection, Component reason) {
        connection.closeWith(new SharedPackets.Disconnect(
                GsonComponentSerializer.gson().serialize(reason), false));
    }

    private static boolean isAllowlisted(String username, HyperGravelConfig config) {
        for (String entry : config.limits().maintenanceAllowlist()) {
            if (entry.equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    static boolean isValidUsername(String username) {
        if (username.length() < 3 || username.length() > 16) {
            return false;
        }
        for (int i = 0; i < username.length(); i++) {
            char c = username.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    static boolean tokensEqual(byte[] a, byte[] b) {
        return Arrays.equals(a, b);
    }
}
