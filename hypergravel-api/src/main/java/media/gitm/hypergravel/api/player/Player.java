package media.gitm.hypergravel.api.player;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import media.gitm.hypergravel.api.command.CommandSource;
import media.gitm.hypergravel.api.server.RegisteredServer;
import media.gitm.hypergravel.api.server.ServerConnection;
import net.kyori.adventure.text.Component;

public interface Player extends CommandSource {

    UUID uuid();

    String username();

    InetSocketAddress remoteAddress();

    int protocolVersion();

    boolean bedrock();

    Optional<ServerConnection> currentServer();

    CompletableFuture<ConnectionResult> connect(RegisteredServer server);

    CompletableFuture<ConnectionResult> transfer(String host, int port);

    void disconnect(Component reason);

    long pingMillis();

    boolean sendPluginMessage(String channel, byte[] data);

    enum ConnectionResult {
        SUCCESS,

        DENIED,

        UNREACHABLE,

        REJECTED,

        IN_PROGRESS,

        GONE
    }
}
