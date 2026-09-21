package pdx.dev.hypergravel.proxy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import pdx.dev.hypergravel.api.ProxyServer;
import pdx.dev.hypergravel.api.command.CommandManager;
import pdx.dev.hypergravel.api.event.DisconnectEvent;
import pdx.dev.hypergravel.api.event.EventManager;
import pdx.dev.hypergravel.api.event.ServerHealthChangeEvent;
import pdx.dev.hypergravel.api.permission.PermissionProvider;
import pdx.dev.hypergravel.api.player.Player;
import pdx.dev.hypergravel.api.scheduler.Scheduler;
import pdx.dev.hypergravel.api.server.RegisteredServer;
import pdx.dev.hypergravel.api.server.ServerHealth;
import pdx.dev.hypergravel.api.server.ServerInfo;
import pdx.dev.hypergravel.proxy.auth.EncryptionManager;
import pdx.dev.hypergravel.proxy.auth.MojangAuthenticator;
import pdx.dev.hypergravel.proxy.backend.BackendConnector;
import pdx.dev.hypergravel.proxy.backend.HyperGravelServer;
import pdx.dev.hypergravel.proxy.backend.ServerRegistry;
import pdx.dev.hypergravel.proxy.command.HyperGravelCommandManager;
import pdx.dev.hypergravel.proxy.command.ServerCommand;
import pdx.dev.hypergravel.proxy.config.HyperGravelConfig;
import pdx.dev.hypergravel.proxy.event.HyperGravelEventManager;
import pdx.dev.hypergravel.proxy.network.Transport;
import pdx.dev.hypergravel.proxy.player.ConnectedPlayer;
import pdx.dev.hypergravel.proxy.player.PlayerRegistry;
import pdx.dev.hypergravel.proxy.queue.QueueService;
import pdx.dev.hypergravel.proxy.scheduler.HyperGravelScheduler;
import pdx.dev.hypergravel.proxy.security.ConnectionThrottle;
import pdx.dev.hypergravel.proxy.status.StatusCache;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class HyperGravelProxy implements ProxyServer {

    private static final Logger LOGGER = LogManager.getLogger(HyperGravelProxy.class);

    public static final String VERSION = "0.1.0";

    private final AtomicReference<HyperGravelConfig> config = new AtomicReference<>();
    private final Path configDirectory;

    private final HyperGravelEventManager eventManager = new HyperGravelEventManager();
    private final HyperGravelCommandManager commandManager = new HyperGravelCommandManager();
    private final HyperGravelScheduler scheduler = new HyperGravelScheduler();
    private final ServerRegistry servers = new ServerRegistry();
    private final PlayerRegistry players = new PlayerRegistry();
    private final BackendConnector connector = new BackendConnector(this);
    private final QueueService queue = new QueueService(this);
    private volatile pdx.dev.hypergravel.pack.ResourcePackService resourcePack;
    private volatile pdx.dev.hypergravel.voice.VoiceService voice;
    private final pdx.dev.hypergravel.proxy.ban.ProxyBans proxyBans;
    private final pdx.dev.hypergravel.proxy.ban.ProxyMutes proxyMutes;
    private final EncryptionManager encryption = new EncryptionManager();
    private final ChannelRegistry channels = new ChannelRegistry();
    private final Messenger messenger = new Messenger();
    private volatile pdx.dev.hypergravel.proxy.extension.ExtensionManager extensionManager;

    private final AtomicReference<PermissionProvider> permissions =
            new AtomicReference<>(PermissionProvider.DEFAULT);

    private final MojangAuthenticator authenticator;
    private final ConnectionThrottle connectionThrottle;
    private final ConnectionThrottle loginThrottle;
    private final StatusCache statusCache;
    private final Transport transport;
    private final PooledByteBufAllocator allocator;

    private final AtomicLong keepAliveCounter = new AtomicLong();
    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public HyperGravelProxy(HyperGravelConfig initialConfig, Path configDirectory) {
        this.config.set(initialConfig);
        this.configDirectory = configDirectory;
        this.transport = Transport.best(initialConfig.network().preferIoUring());
        this.authenticator = new MojangAuthenticator(
                java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        this.connectionThrottle = new ConnectionThrottle(
                initialConfig.limits().connectionsPerSecondPerIp(),
                initialConfig.limits().maxConnectionsPerIp());
        this.loginThrottle = new ConnectionThrottle(
                initialConfig.limits().loginsPerSecondPerIp(), 0);
        this.statusCache = new StatusCache(initialConfig, configDirectory);
        this.proxyBans = new pdx.dev.hypergravel.proxy.ban.ProxyBans(configDirectory);
        this.proxyMutes = new pdx.dev.hypergravel.proxy.ban.ProxyMutes(configDirectory);

        int workers = initialConfig.network().workerThreads() > 0
                ? initialConfig.network().workerThreads()
                : Runtime.getRuntime().availableProcessors();

        this.allocator = new PooledByteBufAllocator(
                true, 0, workers, 8192, 9, 0, 0, true);

        servers.apply(initialConfig);
    }

    public HyperGravelConfig config() {
        return config.get();
    }

    public Path configDirectory() {
        return configDirectory;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public EventManager eventManager() {
        return eventManager;
    }

    @Override
    public CommandManager commandManager() {
        return commandManager;
    }

    public HyperGravelCommandManager commands() {
        return commandManager;
    }

    @Override
    public Scheduler scheduler() {
        return scheduler;
    }

    public HyperGravelScheduler hypergravelScheduler() {
        return scheduler;
    }

    @Override
    public PermissionProvider permissions() {
        return permissions.get();
    }

    public void setPermissionProvider(PermissionProvider provider) {
        permissions.set(provider == null ? PermissionProvider.DEFAULT : provider);
    }

    public ServerRegistry serverRegistry() {
        return servers;
    }

    public PlayerRegistry playerRegistry() {
        return players;
    }

    private volatile pdx.dev.hypergravel.pack.PackEditor packEditor;

    public pdx.dev.hypergravel.pack.PackEditor packEditor() {
        return packEditor;
    }

    public void setPackEditor(pdx.dev.hypergravel.pack.PackEditor editor) {
        this.packEditor = editor;
    }

    public pdx.dev.hypergravel.pack.ResourcePackService resourcePack() {
        return resourcePack;
    }

    public void setResourcePack(pdx.dev.hypergravel.pack.ResourcePackService service) {
        this.resourcePack = service;
    }

    public pdx.dev.hypergravel.voice.VoiceService voice() {
        return voice;
    }

    public void setVoice(pdx.dev.hypergravel.voice.VoiceService service) {
        this.voice = service;
    }

    public QueueService queue() {
        return queue;
    }

    public BackendConnector connector() {
        return connector;
    }

    public EncryptionManager encryption() {
        return encryption;
    }

    public MojangAuthenticator authenticator() {
        return authenticator;
    }

    public ConnectionThrottle connectionThrottle() {
        return connectionThrottle;
    }

    public ConnectionThrottle loginThrottle() {
        return loginThrottle;
    }

    public StatusCache statusCache() {
        return statusCache;
    }

    public pdx.dev.hypergravel.proxy.ban.ProxyBans proxyBans() {
        return proxyBans;
    }

    private volatile pdx.dev.hypergravel.chat.NetworkChat networkChat;

    
    public pdx.dev.hypergravel.chat.NetworkChat networkChat() {
        pdx.dev.hypergravel.chat.NetworkChat have = networkChat;
        if (have == null) {
            synchronized (this) {
                have = networkChat;
                if (have == null) {
                    have = new pdx.dev.hypergravel.chat.NetworkChat(this);
                    networkChat = have;
                }
            }
        }
        return have;
    }

    public pdx.dev.hypergravel.proxy.ban.ProxyMutes proxyMutes() {
        return proxyMutes;
    }

    public Transport transport() {
        return transport;
    }

    public ByteBufAllocator allocator() {
        return allocator;
    }

    public PooledByteBufAllocator pooledAllocator() {
        return allocator;
    }

    public ChannelRegistry channels() {
        return channels;
    }

    public void setExtensionManager(pdx.dev.hypergravel.proxy.extension.ExtensionManager manager) {
        this.extensionManager = manager;
    }

    public pdx.dev.hypergravel.proxy.extension.ExtensionManager extensionManager() {
        return extensionManager;
    }

    public Messenger messenger() {
        return messenger;
    }

    public long nextKeepAliveId() {
        return keepAliveCounter.incrementAndGet();
    }

    public boolean shuttingDown() {
        return shuttingDown.get();
    }

    void setGroups(EventLoopGroup boss, EventLoopGroup worker) {
        this.bossGroup = boss;
        this.workerGroup = worker;
    }

    public EventLoopGroup workerGroup() {
        return workerGroup;
    }

    @Override
    public Optional<Player> player(UUID uuid) {
        return players.byUuid(uuid).map(Player.class::cast);
    }

    @Override
    public Optional<Player> player(String username) {
        return players.byName(username).map(Player.class::cast);
    }

    @Override
    public Collection<Player> players() {
        return java.util.Collections.unmodifiableCollection(players.all());
    }

    @Override
    public int playerCount() {
        return players.count();
    }

    @Override
    public Optional<RegisteredServer> server(String name) {
        return servers.get(name).map(RegisteredServer.class::cast);
    }

    @Override
    public Collection<RegisteredServer> servers() {
        return List.copyOf(servers.all());
    }

    @Override
    public RegisteredServer registerServer(ServerInfo info) {
        return servers.register(info);
    }

    @Override
    public boolean unregisterServer(String name) {
        return servers.unregister(name);
    }

    @Override
    public Audience audience() {
        return Audience.audience(players.all());
    }

    @Override
    public void shutdown(Component reason) {
        HyperGravelBootstrap.requestShutdown(this, reason);
    }

    @Override
    public void registerPluginChannel(String channel) {
        channels.register(channel);
    }

    @Override
    public void unregisterPluginChannel(String channel) {
        channels.unregister(channel);
    }

    @Override
    public boolean isPluginChannelRegistered(String channel) {
        return channels.isRegistered(channel);
    }

    public void registerBuiltinCommands() {
        commandManager.register("server", new ServerCommand(this), "s");
    }

    public void applyReload(HyperGravelConfig updated) {
        HyperGravelConfig previous = config.getAndSet(updated);
        servers.apply(updated);
        statusCache.reload(updated, configDirectory);

        if (updated.requiresRestartVersus(previous)) {
            LOGGER.warn("config reloaded, but some changed settings only take effect on restart "
                    + "(bind address, worker threads, transport, metrics listener)");
        }
        LOGGER.info("config reloaded: {} server(s), {} online",
                updated.servers().size(), playerCount());
    }

    public void disconnect(ConnectedPlayer player) {
        players.unregister(player);
        player.currentHyperGravelServer().ifPresent(server -> server.removePlayer(player));
        var backend = player.backendConnection();
        if (backend != null) {
            backend.close();
        }
        connectionThrottle.release(player.remoteAddress().getAddress());
        eventManager.fireAndForget(
                new DisconnectEvent(player, shuttingDown.get()
                        ? DisconnectEvent.Stage.SHUTDOWN
                        : DisconnectEvent.Stage.CONNECTED));
    }

    public void onHealthChange(HyperGravelServer server, ServerHealth previous, ServerHealth current) {
        if (previous == current) {
            return;
        }
        eventManager.fireAndForget(new ServerHealthChangeEvent(server, previous, current));

        if (previous.routable() && !current.routable()) {
            LOGGER.warn("{} is {} — moving {} player(s) out",
                    server.name(), current, server.playerCount());
            for (ConnectedPlayer player : List.copyOf(server.connectedPlayers())) {
                connector.handleBackendLoss(player, server, false);
            }
        } else if (!previous.routable() && current.routable() && previous != ServerHealth.UNKNOWN) {
            List<ConnectedPlayer> waiting = players.all().stream()
                    .filter(player -> player.pendingReturn()
                            .map(target -> target == server)
                            .orElse(false))
                    .toList();
            LOGGER.info("{} is back up ({} player(s) waiting to return)",
                    server.name(), waiting.size());
            connector.restoreTo(server, waiting, config().ops().restorePerSecond());
        }
    }

    public void markShuttingDown() {
        shuttingDown.set(true);
    }

    public void shutdownInternals(Duration grace) {
        scheduler.shutdown(grace);
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    public static final class ChannelRegistry {

        private final Set<String> channels = ConcurrentHashMap.newKeySet();

        public ChannelRegistry() {

            channels.add("bungeecord:main");
            channels.add("BungeeCord");
        }

        public void register(String channel) {
            channels.add(channel);
        }

        public void unregister(String channel) {
            channels.remove(channel);
        }

        public boolean isRegistered(String channel) {
            return channels.contains(channel);
        }
    }

    public static final class Messenger {

        private static final Logger MESSENGER_LOGGER = LogManager.getLogger(Messenger.class);

        public void sendActionBar(ConnectedPlayer player, Component message) {
            var connection = player.connection();
            if (connection.active()
                    && connection.state() == pdx.dev.hypergravel.proxy.protocol.ProtocolState.PLAY) {
                connection.writeAndFlush(
                        new pdx.dev.hypergravel.proxy.protocol.packet.PlayPackets.SystemChat(
                                message, true));
            }
        }

        public void sendSystemMessage(ConnectedPlayer player, Component message) {
            var connection = player.connection();
            if (connection.active()
                    && connection.state() == pdx.dev.hypergravel.proxy.protocol.ProtocolState.PLAY) {
                connection.writeAndFlush(
                        new pdx.dev.hypergravel.proxy.protocol.packet.PlayPackets.SystemChat(message));
            } else {
                MESSENGER_LOGGER.debug("dropped message to {} (not in PLAY): {}",
                        player.username(),
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText().serialize(message));
            }
        }
    }
}
