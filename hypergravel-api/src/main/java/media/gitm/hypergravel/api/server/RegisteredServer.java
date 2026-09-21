package media.gitm.hypergravel.api.server;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import media.gitm.hypergravel.api.player.Player;
import net.kyori.adventure.audience.Audience;

public interface RegisteredServer extends Audience {

    ServerInfo info();

    default String name() {
        return info().name();
    }

    Collection<Player> players();

    int playerCount();

    ServerHealth health();

    long latencyMillis();

    boolean sendPluginMessage(String channel, byte[] data);

    CompletableFuture<ServerPing> ping();
}
