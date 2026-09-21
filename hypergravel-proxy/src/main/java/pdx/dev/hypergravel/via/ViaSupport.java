package pdx.dev.hypergravel.via;

import java.io.File;
import java.nio.file.Path;
import java.util.SortedSet;

import com.viaversion.viaversion.ViaManagerImpl;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.platform.ViaPlatformLoader;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.commands.ViaCommandHandler;
import com.viaversion.viaversion.platform.ViaChannelInitializer;
import com.viaversion.viaversion.platform.ViaDecodeHandler;
import com.viaversion.viaversion.platform.ViaEncodeHandler;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import pdx.dev.hypergravel.proxy.network.Connections;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ViaSupport {

    private static final Logger LOGGER = LogManager.getLogger(ViaSupport.class);

    public static final AttributeKey<UserConnection> USER =
            AttributeKey.valueOf("hypergravel:via-user");

    private static volatile boolean enabled;
    private static volatile ProtocolVersion serverVersion;
    private static volatile ViaManagerImpl manager;

    private ViaSupport() {
    }

    public static void enable(Path dataFolder, int serverProtocol) {
        if (enabled) {
            return;
        }

        try {
            ProtocolVersion target = ProtocolVersion.getProtocol(serverProtocol);
            if (!target.isKnown()) {
                LOGGER.error("Via does not know protocol {}, so multi-version support is off. "
                        + "Set [via] server-version to a version this Via build supports.",
                        serverProtocol);
                return;
            }

            File folder = dataFolder.toFile();
            if (!folder.isDirectory() && !folder.mkdirs()) {
                LOGGER.error("cannot create {}, so multi-version support is off", folder);
                return;
            }

            HyperGravelViaPlatform platform = new HyperGravelViaPlatform(folder);
            HyperGravelViaBackwards backwards = new HyperGravelViaBackwards(folder);

            manager = (ViaManagerImpl) ViaManagerImpl.initAndLoad(
                    platform,
                    new HyperGravelViaInjector(target),
                    new ViaCommandHandler(false),
                    ViaPlatformLoader.NOOP,
                    backwards::start);

            serverVersion = target;
            enabled = true;
            LOGGER.info("multi-version enabled: {} clients accepted, backends speak {}",
                    supportedRange(), target.getName());
        } catch (Throwable t) {

            enabled = false;
            LOGGER.error("Via failed to load — multi-version support is off", t);
        }
    }

    public static void shutdown() {
        if (!enabled) {
            return;
        }
        enabled = false;
        try {
            manager.destroy();
        } catch (Throwable t) {
            LOGGER.debug("Via shutdown complained", t);
        }
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void install(Channel channel) {
        if (!enabled) {
            return;
        }
        UserConnection user = ViaChannelInitializer.createUserConnection(channel, false);
        channel.attr(USER).set(user);

        channel.pipeline()
                .addBefore(Connections.MINECRAFT_DECODER, ViaDecodeHandler.NAME,
                        new ViaDecodeHandler(user))
                .addBefore(Connections.MINECRAFT_ENCODER, ViaEncodeHandler.NAME,
                        new ViaEncodeHandler(user));
    }

    public static void onLoginSuccess(Channel channel) {
        if (!enabled) {
            return;
        }
        UserConnection user = channel.attr(USER).get();
        if (user != null && user.getProtocolInfo().getUuid() != null) {
            Via.getManager().getConnectionManager().onLoginSuccess(user);
        }
    }

    public static String translatedVersionName(Channel channel) {
        if (!enabled) {
            return null;
        }
        UserConnection user = channel.attr(USER).get();
        if (user == null) {
            return null;
        }
        ProtocolVersion client = user.getProtocolInfo().protocolVersion();
        if (client == null || serverVersion == null || client.equalTo(serverVersion)) {
            return null;
        }
        return client.getName();
    }

    public static String oldestSupportedName() {
        if (!enabled) {
            return null;
        }
        SortedSet<ProtocolVersion> supported = supportedVersions();
        return supported.isEmpty() ? null : supported.first().getName();
    }

    private static String supportedRange() {
        SortedSet<ProtocolVersion> supported = supportedVersions();
        if (supported.isEmpty()) {
            return "none";
        }
        return supported.first().getName() + " - " + supported.last().getName();
    }

    @SuppressWarnings("unchecked")
    private static SortedSet<ProtocolVersion> supportedVersions() {
        return (SortedSet<ProtocolVersion>) Via.getAPI().getSupportedProtocolVersions();
    }

    public static int resolveServerProtocol(String configured, int fallback) {
        if (configured == null || configured.isBlank() || configured.equalsIgnoreCase("auto")) {
            return fallback;
        }
        String value = configured.trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {

        }
        try {
            ProtocolVersion named = ProtocolVersion.getClosest(value);
            if (named != null) {
                return named.getVersion();
            }
        } catch (Throwable t) {
            LOGGER.error("Via could not be consulted for [via] server-version", t);
            return fallback;
        }
        LOGGER.warn("[via] server-version '{}' is not a version Via knows; using {}",
                configured, fallback);
        return fallback;
    }
}
