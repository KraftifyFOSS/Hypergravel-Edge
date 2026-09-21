package media.gitm.hypergravel.api.server;

import java.net.SocketAddress;
import java.util.Objects;








public record ServerInfo(
        String name,
        SocketAddress address,
        ForwardingMode forwarding,
        String permission,
        boolean limbo,
        boolean restricted,
        String onDown) {

    public ServerInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(forwarding, "forwarding");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("server name must not be empty");
        }
    }

    public static ServerInfo of(String name, SocketAddress address, ForwardingMode forwarding) {
        return new ServerInfo(name, address, forwarding, null, false, false, null);
    }
}
