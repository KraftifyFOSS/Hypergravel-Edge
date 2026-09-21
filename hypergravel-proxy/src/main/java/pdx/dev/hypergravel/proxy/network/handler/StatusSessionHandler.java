package pdx.dev.hypergravel.proxy.network.handler;

import pdx.dev.hypergravel.api.event.ProxyPingEvent;
import pdx.dev.hypergravel.api.server.ServerPing;
import pdx.dev.hypergravel.proxy.HyperGravelProxy;
import pdx.dev.hypergravel.proxy.network.MinecraftConnection;
import pdx.dev.hypergravel.proxy.network.SessionHandler;
import pdx.dev.hypergravel.proxy.protocol.packet.StatusPackets;
import pdx.dev.hypergravel.proxy.status.StatusCache;

public final class StatusSessionHandler implements SessionHandler {

    private final HyperGravelProxy proxy;
    private final MinecraftConnection connection;
    private final String virtualHost;
    private final int protocolVersion;

    private boolean requestSeen;

    public StatusSessionHandler(HyperGravelProxy proxy, MinecraftConnection connection,
                                String virtualHost, int protocolVersion) {
        this.proxy = proxy;
        this.connection = connection;
        this.virtualHost = virtualHost;
        this.protocolVersion = protocolVersion;
    }

    @Override
    public boolean handle(StatusPackets.Request packet) {
        if (requestSeen) {
            connection.close();
            return true;
        }
        requestSeen = true;

        StatusCache cache = proxy.statusCache();
        int playerCount = proxy.playerCount();

        if (!proxy.eventManager().hasListeners(ProxyPingEvent.class)) {
            connection.writeAndFlush(new StatusPackets.Response(cache.json(playerCount)));
            return true;
        }

        ProxyPingEvent event = new ProxyPingEvent(connection.remoteAddress(), protocolVersion,
                virtualHost, cache.toServerPing(playerCount));
        proxy.eventManager().fire(event).thenAccept(result -> {
            if (!connection.active()) {
                return;
            }

            connection.channel().eventLoop().execute(() ->
                    connection.writeAndFlush(
                            new StatusPackets.Response(StatusCache.serialize(result.ping()))));
        });
        return true;
    }

    @Override
    public boolean handle(StatusPackets.Ping packet) {

        connection.closeWith(new StatusPackets.Ping(packet.payload()));
        return true;
    }
}
