package pdx.dev.hypergravel.proxy.command;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import pdx.dev.hypergravel.api.command.Command;
import pdx.dev.hypergravel.api.command.CommandSource;
import pdx.dev.hypergravel.api.player.Player;
import pdx.dev.hypergravel.api.server.ServerHealth;
import pdx.dev.hypergravel.proxy.HyperGravelProxy;
import pdx.dev.hypergravel.proxy.backend.HyperGravelServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ServerCommand implements Command {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final HyperGravelProxy proxy;

    public ServerCommand(HyperGravelProxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public void execute(CommandSource source, String[] args) {
        if (!(source instanceof Player player)) {
            source.sendMessage(Component.text("Only players can switch servers."));
            return;
        }

        if (args.length == 0) {
            listServers(player);
            return;
        }

        String name = args[0].toLowerCase(Locale.ROOT);
        proxy.serverRegistry().get(name).ifPresentOrElse(
                target -> switchTo(player, target),
                () -> player.sendMessage(MM.deserialize(
                        "<red>There's no server called <white><name></white>.",
                        Placeholder.unparsed("name", args[0]))));
    }

    private void switchTo(Player player, HyperGravelServer target) {
        boolean alreadyThere = player.currentServer()
                .map(current -> current.server().name().equals(target.name()))
                .orElse(false);
        if (alreadyThere) {
            player.sendMessage(MM.deserialize("<yellow>You're already on <white><name></white>.",
                    Placeholder.unparsed("name", target.name())));
            return;
        }

        if (!mayPass(player, target)) {
            player.sendMessage(MM.deserialize("<red>No"));
            return;
        }

        if (!target.health().routable()) {
            player.sendMessage(MM.deserialize(
                    "<red><name></red> <gray>is <name_state>.",
                    Placeholder.unparsed("name", target.name()),
                    Placeholder.unparsed("name_state",
                            target.health() == ServerHealth.DRAINING ? "restarting" : "offline")));
            return;
        }

        player.connect(target).thenAccept(result -> {
            switch (result) {
                case SUCCESS -> {  }
                case DENIED -> {  }
                case IN_PROGRESS -> player.sendMessage(
                        MM.deserialize("<yellow>You're already switching servers."));
                case GONE -> {  }
                default -> player.sendMessage(MM.deserialize(
                        "<red>Couldn't reach <white><name></white>. <gray>You're still on your "
                                + "current server.",
                        Placeholder.unparsed("name", target.name())));
            }
        });
    }

    private void listServers(Player player) {
        String current = player.currentServer()
                .map(connection -> connection.server().name())
                .orElse("");

        Component list = Component.empty();
        boolean first = true;
        for (HyperGravelServer server : proxy.serverRegistry().all()) {
            if (server.info().limbo() || !visible(player, server) || !mayPass(player, server)) {
                continue;
            }
            if (!first) {
                list = list.append(Component.text(", "));
            }
            first = false;
            String colour = server.name().equals(current) ? "<green>"
                    : server.health().routable() ? "<white>" : "<dark_gray>";
            list = list.append(MM.deserialize(colour + "<name> <gray>(<count>)",
                    Placeholder.unparsed("name", server.name()),
                    Placeholder.unparsed("count", String.valueOf(server.playerCount()))));
        }

        player.sendMessage(MM.deserialize("<gray>Servers: ").append(list));
    }

    private boolean visible(Player player, HyperGravelServer server) {
        String permission = server.info().permission();
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }

    




    private boolean mayPass(Player player, HyperGravelServer target) {
        var info = target.info();
        if (player.hasPermission("hypergravel.server.bypass")) return true;
        return !info.restricted();
    }

    @Override
    public CompletableFuture<List<String>> suggest(CommandSource source, String[] args) {
        if (args.length > 1) {
            return CompletableFuture.completedFuture(List.of());
        }
        String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        return CompletableFuture.completedFuture(proxy.serverRegistry().all().stream()
                .filter(server -> !server.info().limbo())
                .filter(server -> !(source instanceof Player player) || mayPass(player, server))
                .filter(server -> !(source instanceof Player player) || visible(player, server))
                .map(HyperGravelServer::name)
                .filter(name -> name.startsWith(prefix))
                .sorted()
                .toList());
    }
}
