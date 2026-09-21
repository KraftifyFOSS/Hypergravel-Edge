package media.gitm.hypergravel.api;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import media.gitm.hypergravel.api.command.CommandManager;
import media.gitm.hypergravel.api.event.EventManager;
import media.gitm.hypergravel.api.permission.PermissionProvider;
import media.gitm.hypergravel.api.player.Player;
import media.gitm.hypergravel.api.scheduler.Scheduler;
import media.gitm.hypergravel.api.server.RegisteredServer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

public interface ProxyServer {

    String version();

    EventManager eventManager();

    CommandManager commandManager();

    Scheduler scheduler();

    PermissionProvider permissions();

    void setPermissionProvider(PermissionProvider provider);

    Optional<Player> player(UUID uuid);

    Optional<Player> player(String username);

    Collection<Player> players();

    int playerCount();

    Optional<RegisteredServer> server(String name);

    Collection<RegisteredServer> servers();

    RegisteredServer registerServer(media.gitm.hypergravel.api.server.ServerInfo info);

    boolean unregisterServer(String name);

    Audience audience();

    default void broadcast(Component message) {
        audience().sendMessage(message);
    }

    void shutdown(Component reason);
}
