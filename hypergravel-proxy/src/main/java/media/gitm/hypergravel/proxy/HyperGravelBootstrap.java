package media.gitm.hypergravel.proxy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import media.gitm.hypergravel.api.event.ProxyInitializeEvent;
import media.gitm.hypergravel.api.event.ProxyShutdownEvent;
import media.gitm.hypergravel.api.server.RegisteredServer;
import media.gitm.hypergravel.proxy.backend.HealthMonitor;
import media.gitm.hypergravel.proxy.status.StatusCache;
import media.gitm.hypergravel.proxy.backend.ServerPinger;
import media.gitm.hypergravel.proxy.config.HyperGravelConfig;
import media.gitm.hypergravel.proxy.network.ProxyChannelInitializer;
import media.gitm.hypergravel.proxy.ops.OpsServer;
import media.gitm.hypergravel.proxy.player.ConnectedPlayer;
import media.gitm.hypergravel.proxy.security.CidrTrustList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class HyperGravelBootstrap {

    private static final Logger LOGGER = LogManager.getLogger(HyperGravelBootstrap.class);

    private static final CountDownLatch SHUTDOWN = new CountDownLatch(1);
    private static volatile Component shutdownReason;

    public static void main(String[] args) throws Exception {
        Path configDirectory = Path.of(args.length > 0 ? args[0] : "config");
        Path configFile = configDirectory.resolve("hypergravel.toml");

        if (!Files.isReadable(configFile)) {
            System.err.println("HyperGravel: no config at " + configFile.toAbsolutePath());
            System.err.println("Copy the example from config/hypergravel.toml and edit it.");
            System.exit(1);
            return;
        }

        HyperGravelConfig config;
        try {
            config = HyperGravelConfig.load(configFile);
        } catch (HyperGravelConfig.ConfigException e) {

            System.err.println("HyperGravel: " + e.getMessage());
            System.exit(1);
            return;
        }

        LOGGER.info("HyperGravel {} starting", HyperGravelProxy.VERSION);

        HyperGravelProxy proxy = new HyperGravelProxy(config, configDirectory);
        proxy.registerBuiltinCommands();

        if (config.multiVersion().enabled()) {
            pdx.dev.hypergravel.via.ViaSupport.enable(
                    configDirectory.resolve("via"),
                    pdx.dev.hypergravel.via.ViaSupport.resolveServerProtocol(
                            config.multiVersion().serverVersion(),
                            media.gitm.hypergravel.proxy.protocol.ProtocolVersion
                                    .MAXIMUM_NATIVE.protocol()));
        } else {
            LOGGER.info("multi-version disabled; only {} clients can join",
                    media.gitm.hypergravel.proxy.protocol.ProtocolVersion
                            .MAXIMUM_NATIVE.versionName());
        }

        int workerThreads = config.network().workerThreads() > 0
                ? config.network().workerThreads()
                : Runtime.getRuntime().availableProcessors();

        EventLoopGroup bossGroup = proxy.transport().createGroup(1, "hypergravel-boss");
        EventLoopGroup workerGroup = proxy.transport().createGroup(workerThreads, "hypergravel-worker");
        proxy.setGroups(bossGroup, workerGroup);
        ServerPinger.configure(workerGroup, proxy.transport().channelType());

        CidrTrustList trust = config.proxyProtocol().enabled()
                ? CidrTrustList.parse(config.proxyProtocol().trustedCidrs())
                : CidrTrustList.DENY_ALL;

        Channel listener;
        try {
            listener = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(proxy.transport().serverChannelType())
                    .childHandler(new ProxyChannelInitializer(proxy, trust))
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.TCP_NODELAY, config.network().tcpNoDelay())
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.SO_RCVBUF, 262144)
                    .childOption(ChannelOption.SO_SNDBUF, 262144)
                    .childOption(ChannelOption.ALLOCATOR, proxy.allocator())
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                            new WriteBufferWaterMark(config.network().writeBufferLow(),
                                    config.network().writeBufferHigh()))
                    .bind(config.bind().address())
                    .sync()
                    .channel();
        } catch (Exception e) {
            LOGGER.error("cannot bind {}: {}", config.bind().address(), e.toString());
            System.exit(1);
            return;
        }

        LOGGER.info("listening on {} ({} transport, {} worker threads)",
                config.bind().address(), proxy.transport().name(), workerThreads);
        LOGGER.info("online-mode={}, forwarding={}, {} backend(s)",
                config.bind().onlineMode(), config.forwarding().defaultMode(),
                config.servers().size());
        if (config.proxyProtocol().enabled()) {
            LOGGER.info("PROXY protocol enabled, {} trusted CIDR(s), required={}",
                    config.proxyProtocol().trustedCidrs().size(), config.proxyProtocol().required());
        }

        new HealthMonitor(proxy).start();
        proxy.queue().start();

        var faces = new pdx.dev.hypergravel.tab.PlayerFaces(configDirectory.resolve("tab-faces"));
        if (config.tab().enabled()) {
            faces.resolve(config.tab().playerHeads().entrySet().stream()
                    .filter(entry -> "face".equalsIgnoreCase(entry.getValue().trim()))
                    .map(java.util.Map.Entry::getKey)
                    .toList());
        }

        pdx.dev.hypergravel.pack.PackBuilder.setBackgroundAscent(config.pack().backgroundAscent());

        if (config.pack().enabled() && !config.pack().url().isBlank()) {
            var packService = new pdx.dev.hypergravel.pack.ResourcePackService(
                    proxy, config.pack().url(), config.pack().sha1(),
                    config.pack().required(), config.pack().prompt(), config.pack().delay(),
                    config.pack().kickOnDecline(), config.pack().notice());

            
            
            
            for (var variant : config.pack().variants()) {
                if (variant.url().isBlank() || variant.file().isBlank()) {
                    LOGGER.warn("pack: variant {} has no url or no file - skipped", variant.name());
                    continue;
                }
                packService.addVariant(new pdx.dev.hypergravel.pack.ResourcePackService.Variant(
                        variant.name(),
                        variant.url(),
                        Path.of(variant.file()),
                        variant.extra().isBlank() ? null : Path.of(variant.extra()),
                        java.util.Set.copyOf(variant.servers()),
                        variant.fallback()));
            }

            if (config.pack().build() && !config.pack().file().isBlank()) {
                Path packFile = Path.of(config.pack().file());
                
                
                packService.buildInto(packFile, faces.glyphs());
                proxy.setPackEditor(new pdx.dev.hypergravel.pack.PackEditor(packFile));
            }
            packService.logSummary();
            proxy.setResourcePack(packService);

            proxy.eventManager().register(proxy,
                    media.gitm.hypergravel.api.event.ServerConnectedEvent.class,
                    media.gitm.hypergravel.api.event.PostOrder.LAST,
                    event -> {
                        
                        
                        
                        
                        
                        
                        
                        if (event.player() instanceof ConnectedPlayer connected) {
                            packService.applyFor(connected, event.server().name());
                        }
                    });

            
            
            proxy.eventManager().register(proxy,
                    media.gitm.hypergravel.api.event.DisconnectEvent.class,
                    media.gitm.hypergravel.api.event.PostOrder.LAST,
                    event -> packService.forget(event.player().uuid()));
            if (config.pack().variants().isEmpty()) {
                LOGGER.info("pack: offering {} until it is on every client", config.pack().url());
            } else {
                LOGGER.info("pack: {} variant(s), swapped as players move between backends",
                        config.pack().variants().size());
            }
        }

        pdx.dev.hypergravel.tab.TabService tab = null;
        if (config.tab().enabled()) {
            tab = new pdx.dev.hypergravel.tab.TabService(
                    proxy, configDirectory.resolve("tab-heads.json"), config.tab().refresh(),
                    config.tab().playerHeads(), config.tab().icons(), faces);
            tab.start();

            pdx.dev.hypergravel.tab.TabService started = tab;
            proxy.eventManager().register(proxy,
                    media.gitm.hypergravel.api.event.ServerConnectedEvent.class,
                    media.gitm.hypergravel.api.event.PostOrder.LAST,
                    event -> {
                        if (event.player() instanceof ConnectedPlayer connected) {
                            started.onServerConnected(connected);
                        }
                    });
            proxy.eventManager().register(proxy,
                    media.gitm.hypergravel.api.event.DisconnectEvent.class,
                    media.gitm.hypergravel.api.event.PostOrder.LAST,
                    event -> {
                        if (event.player() instanceof ConnectedPlayer connected) {
                            started.onDisconnect(connected);
                        }
                    });
        }

        if (config.voice().enabled()) {
            var voice = new pdx.dev.hypergravel.voice.VoiceService(
                    proxy, config.voice().port(), config.voice().allowPings());
            try {
                voice.start(config.voice().bindAddress());
                proxy.setVoice(voice);
                LOGGER.info("voice: relaying on UDP {}, clients told {}:{}",
                        config.voice().port(),
                        config.voice().host().isBlank()
                                ? "the address they reached" : config.voice().host(),
                        config.voice().publicPort());

                proxy.eventManager().register(proxy,
                        media.gitm.hypergravel.api.event.ServerConnectedEvent.class,
                        media.gitm.hypergravel.api.event.PostOrder.LAST,
                        event -> {
                            if (!event.firstConnect()
                                    && event.player() instanceof ConnectedPlayer connected) {
                                voice.onServerChange(connected);
                            }
                        });
                proxy.eventManager().register(proxy,
                        media.gitm.hypergravel.api.event.DisconnectEvent.class,
                        media.gitm.hypergravel.api.event.PostOrder.LAST,
                        event -> {
                            if (event.player() instanceof ConnectedPlayer connected) {
                                voice.onDisconnect(connected);
                            }
                        });
            } catch (java.io.IOException e) {

                LOGGER.error("voice: cannot bind UDP {}: {} — voice chat stays per-backend",
                        config.voice().port(), e.toString());
            }
        }

        proxy.statusCache().setActivitySource(() -> {
            String lobby = proxy.config().routing().fallback();
            String holding = proxy.config().queue().server();
            int playing = 0;
            for (RegisteredServer server : proxy.servers()) {
                String name = server.info().name();
                if (name.equals(lobby) || name.equals(holding) || server.info().limbo()) {
                    continue;
                }
                playing += server.players().size();
            }
            return new StatusCache.Activity(proxy.playerCount(), playing);
        });

        java.time.Duration keepAlive = config.network().keepAliveInterval();
        proxy.scheduler().repeat(proxy, keepAlive, keepAlive, () -> {
            for (media.gitm.hypergravel.proxy.player.ConnectedPlayer player
                    : proxy.playerRegistry().all()) {
                var conn = player.connection();
                if (conn.active()
                        && conn.state() == media.gitm.hypergravel.proxy.protocol.ProtocolState.PLAY) {
                    player.sendKeepAlive(proxy.nextKeepAliveId());
                }
            }
        });
        
        proxy.scheduler().repeat(proxy, java.time.Duration.ofSeconds(60),
                java.time.Duration.ofSeconds(60), () -> {
                    proxy.proxyBans().purgeExpired();
                    proxy.proxyMutes().purgeExpired();
                });

        OpsServer opsServer = null;
        if (config.ops().metricsEnabled()) {
            opsServer = new OpsServer(proxy, workerGroup);
            opsServer.start(config.ops().metricsHost(), config.ops().metricsPort());
        }

        proxy.eventManager().fireAndForget(new ProxyInitializeEvent(proxy));

        OpsServer finalOpsServer = opsServer;
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> shutdown(proxy, listener, finalOpsServer), "hypergravel-shutdown"));

        SHUTDOWN.await();
        shutdown(proxy, listener, opsServer);
    }

    public static void requestShutdown(HyperGravelProxy proxy, Component reason) {
        shutdownReason = reason;
        SHUTDOWN.countDown();
    }

    private static volatile boolean shutdownStarted;

    private static synchronized void shutdown(HyperGravelProxy proxy, Channel listener,
                                              OpsServer opsServer) {
        if (shutdownStarted) {
            return;
        }
        shutdownStarted = true;
        proxy.markShuttingDown();

        Duration grace = proxy.config().ops().shutdownGrace();
        LOGGER.info("shutting down (grace {}s)", grace.toSeconds());

        listener.close();

        proxy.eventManager().fireAndForget(new ProxyShutdownEvent(proxy));

        Component reason = shutdownReason != null
                ? shutdownReason
                : MiniMessage.miniMessage().deserialize(
                        "<yellow>The proxy is restarting.\n<gray>Reconnect in a moment.");

        proxy.serverRegistry().limbo().ifPresentOrElse(limbo -> {
            List<ConnectedPlayer> online = List.copyOf(proxy.playerRegistry().all());
            LOGGER.info("draining {} player(s) to {}", online.size(), limbo.name());
            for (ConnectedPlayer player : online) {
                player.connect(limbo);
            }
            awaitDrain(proxy, grace);
            for (ConnectedPlayer player : List.copyOf(proxy.playerRegistry().all())) {
                player.disconnect(reason);
            }
        }, () -> {
            LOGGER.warn("no limbo configured; disconnecting players directly");
            for (ConnectedPlayer player : List.copyOf(proxy.playerRegistry().all())) {
                player.disconnect(reason);
            }
        });

        if (opsServer != null) {
            opsServer.stop();
        }

        if (proxy.voice() != null) {
            proxy.voice().stop();
        }
        pdx.dev.hypergravel.via.ViaSupport.shutdown();
        proxy.shutdownInternals(grace);
        LOGGER.info("HyperGravel stopped");
    }

    private static void awaitDrain(HyperGravelProxy proxy, Duration grace) {
        long deadline = System.nanoTime() + grace.toNanos();
        while (System.nanoTime() < deadline) {
            boolean allSettled = proxy.playerRegistry().all().stream()
                    .allMatch(player -> player.currentHyperGravelServer()
                            .map(server -> server.info().limbo())
                            .orElse(true));
            if (allSettled) {
                return;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        LOGGER.warn("drain did not complete within the grace period");
    }
}
