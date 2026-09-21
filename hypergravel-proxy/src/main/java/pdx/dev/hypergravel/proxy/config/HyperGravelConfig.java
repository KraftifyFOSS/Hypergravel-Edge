package pdx.dev.hypergravel.proxy.config;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import pdx.dev.hypergravel.api.server.ForwardingMode;
import pdx.dev.hypergravel.api.server.ServerInfo;

public record HyperGravelConfig(
        Bind bind,
        Network network,
        Status status,
        Forwarding forwarding,
        List<ServerInfo> servers,
        Routing routing,
        Limits limits,
        Queue queue,
        ProxyProtocol proxyProtocol,
        Ops ops,
        Commands commands,
        MultiVersion multiVersion,
        Tab tab,
        Pack pack,
        Voice voice,
        Extensions extensions) {

    public record Bind(String host, int port, boolean onlineMode) {
        public InetSocketAddress address() {
            return new InetSocketAddress(host, port);
        }
    }

    public record Network(
            int workerThreads,
            int compressionThreshold,
            int compressionLevel,
            int maxPacketSize,
            int maxUncompressedSize,
            Duration handshakeTimeout,
            Duration readTimeout,
            Duration connectTimeout,
            Duration keepAliveInterval,
            boolean tcpNoDelay,
            boolean preferIoUring,
            int writeBufferLow,
            int writeBufferHigh) {}

    public record Status(
            String motd,
            int maxPlayers,
            boolean showRealPlayerCount,
            List<String> sample,
            String versionName,
            String faviconPath,
            boolean enforceSecureChat) {}

    public record Forwarding(ForwardingMode defaultMode, String secret, String bungeeGuardToken) {}

    public record Routing(List<String> tryOrder, String fallback, String limbo) {}

    public record Queue(
            boolean enabled,
            String server,
            int slots,
            int releasePerSecond,
            String bypassPermission,
            Duration holdGrace) {}

    public record Limits(
            int maxPlayers,
            int maxConnectionsPerIp,
            int connectionsPerSecondPerIp,
            int loginsPerSecondPerIp,
            int globalConnectionsPerSecond,
            boolean maintenance,
            List<String> maintenanceAllowlist) {}

    public record ProxyProtocol(boolean enabled, boolean required, List<String> trustedCidrs) {}

    public record Commands(boolean enabled, String hub, boolean server, boolean leave,
                           boolean glist, boolean find, boolean ping, boolean send) {}

    public record MultiVersion(boolean enabled, String serverVersion) {}

    public record Tab(boolean enabled, Duration refresh, Map<String, String> playerHeads,
                      String icons) {}

    public record Pack(boolean enabled, String url, String sha1, boolean required, String prompt,
                       boolean build, String file, Duration delay, int backgroundAscent,
                       boolean kickOnDecline, String notice, List<PackVariant> variants) {

        
        public PackVariant variantFor(String server) {
            PackVariant fallback = null;
            for (PackVariant variant : variants) {
                if (variant.servers().contains(server == null
                        ? "" : server.toLowerCase(Locale.ROOT))) {
                    return variant;
                }
                if (variant.fallback() && fallback == null) {
                    fallback = variant;
                }
            }
            return fallback;
        }
    }

    











    public record PackVariant(String name, String url, String file, String extra,
                              List<String> servers, boolean fallback) {}

    public record Voice(boolean enabled, int port, String bindAddress, String host,
                        int advertisePort, boolean allowPings) {

        public int publicPort() {
            return advertisePort > 0 ? advertisePort : port;
        }
    }

    public record Ops(
            boolean metricsEnabled,
            String metricsHost,
            int metricsPort,
            Duration shutdownGrace,
            Duration healthCheckInterval,
            int healthCheckFailuresToDown,
            int restorePerSecond,
            String packEditorUrl) {}

    public record Extensions(boolean enabled, String directory) {}

    public static HyperGravelConfig load(Path file) throws java.io.IOException {
        CommentedConfig raw;
        try (var reader = Files.newBufferedReader(file)) {
            raw = new TomlParser().parse(reader);
        }
        return from(raw);
    }

    static HyperGravelConfig from(Config raw) {
        Reader root = new Reader(raw, "");

        Reader bindSection = root.section("bind");
        Bind bind = new Bind(
                bindSection.string("host", "0.0.0.0"),
                bindSection.integer("port", 25565),
                bindSection.bool("online-mode", true));

        Reader net = root.section("network");
        Network network = new Network(
                net.integer("worker-threads", 0),
                net.integer("compression-threshold", 256),
                net.integer("compression-level", 6),
                net.integer("max-packet-size", 2097152),
                net.integer("max-uncompressed-size", 8388608),
                net.duration("handshake-timeout", Duration.ofSeconds(10)),
                net.duration("read-timeout", Duration.ofSeconds(30)),
                net.duration("connect-timeout", Duration.ofSeconds(5)),
                net.duration("keepalive-interval", Duration.ofSeconds(15)),
                net.bool("tcp-nodelay", true),
                net.bool("prefer-io-uring", false),
                net.integer("write-buffer-low", 32768),
                net.integer("write-buffer-high", 65536));

        Reader st = root.section("status");
        Status status = new Status(
                st.string("motd", "<gradient:#5e4fa2:#f79459>HyperGravel</gradient>"),
                st.integer("max-players", 1000),
                st.bool("show-real-player-count", true),
                st.stringList("sample"),
                st.string("version-name", "HyperGravel"),
                st.string("favicon", "server-icon.png"),
                st.bool("enforce-secure-chat", false));

        Reader fw = root.section("forwarding");
        Forwarding forwarding = new Forwarding(
                ForwardingMode.parse(fw.string("mode", "velocity-modern")),
                fw.string("secret", ""),
                fw.string("bungeeguard-token", ""));

        List<ServerInfo> servers = readServers(root, forwarding.defaultMode());

        Reader rt = root.section("routing");
        Routing routing = new Routing(
                rt.stringList("try"),
                rt.string("fallback", null),
                rt.string("limbo", null));

        Reader lim = root.section("limits");
        Limits limits = new Limits(
                lim.integer("max-players", 0),
                lim.integer("max-connections-per-ip", 8),
                lim.integer("connections-per-second-per-ip", 8),
                lim.integer("logins-per-second-per-ip", 3),
                lim.integer("global-connections-per-second", 2000),
                lim.bool("maintenance", false),
                lim.stringList("maintenance-allowlist"));

        Reader q = root.section("queue");
        Queue queue = new Queue(
                q.bool("enabled", false),
                q.string("server", null),
                q.integer("slots", 0),
                q.integer("release-per-second", 5),
                q.string("bypass-permission", "hypergravel.queue.bypass"),
                q.duration("hold-grace", Duration.ofSeconds(20)));

        Reader pp = root.section("proxy-protocol");
        ProxyProtocol proxyProtocol = new ProxyProtocol(
                pp.bool("enabled", false),
                pp.bool("required", false),
                pp.stringList("trusted"));

        Reader ops = root.section("ops");
        Ops opsConfig = new Ops(
                ops.bool("metrics-enabled", true),
                ops.string("metrics-host", "127.0.0.1"),
                ops.integer("metrics-port", 9100),
                ops.duration("shutdown-grace", Duration.ofSeconds(30)),
                ops.duration("health-check-interval", Duration.ofSeconds(5)),
                ops.integer("health-check-failures-to-down", 3),
                ops.integer("restore-per-second", 20),
                ops.string("pack-editor-url", ""));

        Reader cmd = root.section("commands");
        Commands commands = new Commands(
                cmd.bool("enabled", true),
                cmd.string("hub", "lobby"),
                cmd.bool("server", true),
                cmd.bool("leave", true),
                cmd.bool("glist", true),
                cmd.bool("find", true),
                cmd.bool("ping", true),
                cmd.bool("send", false));

        Reader via = root.section("via");
        MultiVersion multiVersion = new MultiVersion(
                via.bool("enabled", true),
                via.string("server-version", "auto"));

        Reader tabSection = root.section("tab");

        Reader headsSection = tabSection.section("player-heads");
        Map<String, String> playerHeads = new LinkedHashMap<>();
        for (String name : headsSection.keys()) {
            playerHeads.put(name.toLowerCase(Locale.ROOT), headsSection.string(name, ""));
        }
        Tab tab = new Tab(
                tabSection.bool("enabled", true),
                tabSection.duration("refresh", Duration.ofSeconds(1)),
                Map.copyOf(playerHeads),
                tabSection.string("icons", "both"));

        Reader packSection = root.section("pack");
        List<PackVariant> variants = new ArrayList<>();
        Reader variantSection = packSection.section("variants");
        for (String name : variantSection.keys()) {
            Reader entry = variantSection.section(name);
            variants.add(new PackVariant(
                    name.toLowerCase(Locale.ROOT),
                    entry.string("url", ""),
                    entry.string("file", ""),
                    entry.string("extra", ""),
                    entry.stringList("servers").stream()
                            .map(server -> server.toLowerCase(Locale.ROOT))
                            .toList(),
                    entry.bool("default", false)));
        }
        Pack pack = new Pack(
                packSection.bool("enabled", false),
                packSection.string("url", ""),
                packSection.string("sha1", ""),
                packSection.bool("required", false),
                packSection.string("prompt", ""),
                packSection.bool("build", true),
                packSection.string("file", ""),
                packSection.duration("delay", Duration.ofSeconds(4)),
                packSection.integer("background-ascent", 9),
                packSection.bool("kick-on-decline", false),
                packSection.string("notice", ""),
                List.copyOf(variants));

        Reader voiceSection = root.section("voice");
        Voice voice = new Voice(
                voiceSection.bool("enabled", false),
                voiceSection.integer("port", 24530),
                voiceSection.string("bind", ""),
                voiceSection.string("host", ""),
                voiceSection.integer("advertise-port", 0),
                voiceSection.bool("allow-pings", true));

        Reader extensionsSection = root.section("extensions");
        Extensions extensions = new Extensions(
                extensionsSection.bool("enabled", true),
                extensionsSection.string("directory", "extensions"));

        HyperGravelConfig config = new HyperGravelConfig(bind, network, status, forwarding,
                servers, routing, limits, queue, proxyProtocol, opsConfig, commands, multiVersion,
                tab, pack, voice, extensions);
        config.validate();
        return config;
    }

    private static List<ServerInfo> readServers(Reader root, ForwardingMode defaultMode) {
        List<ServerInfo> servers = new ArrayList<>();
        Reader section = root.section("servers");
        for (String name : section.keys()) {
            Reader entry = section.section(name);
            String address = entry.string("address", null);
            if (address == null) {
                throw new ConfigException("servers." + name + " has no address");
            }
            servers.add(new ServerInfo(
                    name.toLowerCase(Locale.ROOT),
                    parseAddress(address, "servers." + name + ".address"),
                    entry.contains("forwarding")
                            ? ForwardingMode.parse(entry.string("forwarding", null))
                            : defaultMode,
                    entry.string("permission", null),
                    entry.bool("limbo", false),
                    entry.bool("restricted", false),
                    entry.string("on-down", null)));
        }
        return List.copyOf(servers);
    }

    static InetSocketAddress parseAddress(String raw, String path) {
        int colon = raw.lastIndexOf(':');
        if (colon < 0) {
            throw new ConfigException(path + " must be host:port, got '" + raw + "'");
        }
        String host = raw.substring(0, colon);
        int port;
        try {
            port = Integer.parseInt(raw.substring(colon + 1));
        } catch (NumberFormatException e) {
            throw new ConfigException(path + " has a non-numeric port: '" + raw + "'");
        }
        if (port < 1 || port > 65535) {
            throw new ConfigException(path + " port out of range: " + port);
        }

        return InetSocketAddress.createUnresolved(host, port);
    }

    public void validate() {
        Map<String, ServerInfo> byName = new LinkedHashMap<>();
        for (ServerInfo server : servers) {
            if (byName.put(server.name(), server) != null) {
                throw new ConfigException("duplicate server '" + server.name() + "'");
            }
        }

        if (servers.isEmpty()) {
            throw new ConfigException("no servers configured; the proxy would have nowhere to route");
        }

        for (String name : routing.tryOrder()) {
            if (!byName.containsKey(name)) {
                throw new ConfigException("routing.try references unknown server '" + name + "'");
            }
        }
        if (routing.tryOrder().isEmpty()) {
            throw new ConfigException("routing.try is empty; nobody could log in");
        }
        requireKnown(byName, routing.fallback(), "routing.fallback");
        requireKnown(byName, routing.limbo(), "routing.limbo");

        for (ServerInfo server : servers) {
            requireKnown(byName, server.onDown(), "servers." + server.name() + ".on-down");
        }

        if (queue.enabled()) {

            if (queue.server() == null || queue.server().isBlank()) {
                throw new ConfigException("queue.enabled is true but queue.server is unset");
            }
            requireKnown(byName, queue.server(), "queue.server");
            if (queue.slots() <= 0) {
                throw new ConfigException("queue.slots must be greater than zero when the queue "
                        + "is enabled; nobody would ever be let in");
            }
            if (routing.tryOrder().contains(queue.server())) {
                throw new ConfigException("queue.server '" + queue.server() + "' is also in "
                        + "routing.try; the holding server must not be a login destination");
            }
        }

        if (forwarding.defaultMode() == ForwardingMode.VELOCITY_MODERN
                && forwarding.secret().isBlank()) {
            throw new ConfigException("forwarding.mode is velocity-modern but forwarding.secret "
                    + "is empty; backends would reject every player");
        }
        if (forwarding.defaultMode() == ForwardingMode.BUNGEEGUARD
                && forwarding.bungeeGuardToken().isBlank()) {
            throw new ConfigException("forwarding.mode is bungeeguard but "
                    + "forwarding.bungeeguard-token is empty");
        }

        if (proxyProtocol.enabled() && proxyProtocol.trustedCidrs().isEmpty()) {

            throw new ConfigException("proxy-protocol.enabled is true but proxy-protocol.trusted "
                    + "is empty; that would let any client spoof its source IP");
        }

        if (voice.enabled() && (voice.port() < 1 || voice.port() > 65535)) {
            throw new ConfigException("voice.port out of range: " + voice.port());
        }
        if (voice.enabled() && (voice.advertisePort() < 0 || voice.advertisePort() > 65535)) {
            throw new ConfigException("voice.advertise-port out of range: " + voice.advertisePort());
        }
        if (voice.enabled() && voice.port() == bind.port()) {

            throw new ConfigException("voice.port must differ from bind.port");
        }

        if (network.compressionThreshold() < -1) {
            throw new ConfigException("network.compression-threshold must be -1 (off) or >= 0");
        }
        if (network.compressionLevel() < 0 || network.compressionLevel() > 9) {
            throw new ConfigException("network.compression-level must be 0-9");
        }
        if (network.writeBufferHigh() <= network.writeBufferLow()) {
            throw new ConfigException("network.write-buffer-high must exceed write-buffer-low");
        }
        if (network.maxUncompressedSize() < network.maxPacketSize()) {
            throw new ConfigException(
                    "network.max-uncompressed-size must be >= network.max-packet-size");
        }
    }

    private static void requireKnown(Map<String, ServerInfo> byName, String name, String path) {
        if (name != null && !name.isBlank() && !byName.containsKey(name)) {
            throw new ConfigException(path + " references unknown server '" + name + "'");
        }
    }

    public boolean requiresRestartVersus(HyperGravelConfig other) {
        return !bind.equals(other.bind)
                || network.workerThreads() != other.network.workerThreads()
                || network.preferIoUring() != other.network.preferIoUring()
                || ops.metricsPort() != other.ops.metricsPort()
                || !ops.metricsHost().equals(other.ops.metricsHost())

                || voice.enabled() != other.voice.enabled()
                || voice.port() != other.voice.port()
                || !voice.bindAddress().equals(other.voice.bindAddress())

                || extensions.enabled() != other.extensions.enabled()
                || !extensions.directory().equals(other.extensions.directory());
    }

    public static final class ConfigException extends RuntimeException {
        public ConfigException(String message) {
            super(message);
        }
    }
}
