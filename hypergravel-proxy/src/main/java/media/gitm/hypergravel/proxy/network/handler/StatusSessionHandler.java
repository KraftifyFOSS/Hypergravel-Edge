package media.gitm.hypergravel.proxy.network.handler;

import media.gitm.hypergravel.api.event.ProxyPingEvent;
import media.gitm.hypergravel.api.server.ServerPing;
import media.gitm.hypergravel.proxy.HyperGravelProxy;
import media.gitm.hypergravel.proxy.network.MinecraftConnection;
import media.gitm.hypergravel.proxy.network.SessionHandler;
import media.gitm.hypergravel.proxy.protocol.packet.StatusPackets;
import media.gitm.hypergravel.proxy.status.StatusCache;

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
