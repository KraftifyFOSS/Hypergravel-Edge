package pdx.dev.hypergravel.api.event;

import java.net.InetSocketAddress;
import java.util.Objects;

import pdx.dev.hypergravel.api.server.ServerPing;

public final class ProxyPingEvent {

    private final InetSocketAddress remoteAddress;
    private final int protocolVersion;
    private final String virtualHost;
    private ServerPing ping;

    public ProxyPingEvent(InetSocketAddress remoteAddress, int protocolVersion,
                          String virtualHost, ServerPing ping) {
        this.remoteAddress = remoteAddress;
        this.protocolVersion = protocolVersion;
        this.virtualHost = virtualHost;
        this.ping = Objects.requireNonNull(ping);
    }

    public InetSocketAddress remoteAddress() {
        return remoteAddress;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public String virtualHost() {
        return virtualHost;
    }

    public ServerPing ping() {
        return ping;
    }

    public void setPing(ServerPing ping) {
        this.ping = Objects.requireNonNull(ping);
    }
}
