package pdx.dev.hypergravel.api;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import pdx.dev.hypergravel.api.command.CommandManager;
import pdx.dev.hypergravel.api.event.EventManager;
import pdx.dev.hypergravel.api.permission.PermissionProvider;
import pdx.dev.hypergravel.api.player.Player;
import pdx.dev.hypergravel.api.scheduler.Scheduler;
import pdx.dev.hypergravel.api.server.RegisteredServer;
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

    RegisteredServer registerServer(pdx.dev.hypergravel.api.server.ServerInfo info);

    boolean unregisterServer(String name);

    Audience audience();

    default void broadcast(Component message) {
        audience().sendMessage(message);
    }

    void registerPluginChannel(String channel);

    void unregisterPluginChannel(String channel);

    boolean isPluginChannelRegistered(String channel);

    void shutdown(Component reason);
}
