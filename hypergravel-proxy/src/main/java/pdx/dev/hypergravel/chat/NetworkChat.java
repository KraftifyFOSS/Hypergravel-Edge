package pdx.dev.hypergravel.chat;

import pdx.dev.hypergravel.proxy.HyperGravelProxy;
import pdx.dev.hypergravel.proxy.player.ConnectedPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;



















public final class NetworkChat {

    private static final Logger LOGGER = LogManager.getLogger(NetworkChat.class);
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final HyperGravelProxy proxy;

    public NetworkChat(HyperGravelProxy proxy) {
        this.proxy = proxy;
    }

    
    public void relay(ConnectedPlayer from, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        
        
        if (proxy.proxyMutes().isMuted(from.uuid())) {
            return;
        }
        String where = from.currentHyperGravelServer()
                .map(server -> server.info().name())
                .orElse("");

        Component line = Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text(where.isEmpty() ? "net" : where, NamedTextColor.AQUA))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(from.username(), NamedTextColor.GRAY))
                .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                
                
                .append(Component.text(message, NamedTextColor.WHITE));

        for (ConnectedPlayer player : others(where)) {
            proxy.messenger().sendSystemMessage(player, line);
        }
    }

    





    public int announce(String miniMessage) {
        Component line = MM.deserialize(miniMessage);
        int reached = 0;
        for (ConnectedPlayer player : all()) {
            proxy.messenger().sendSystemMessage(player, line);
            reached++;
        }
        LOGGER.info("announce: reached {} player(s)", reached);
        return reached;
    }

    private java.util.List<ConnectedPlayer> all() {
        return proxy.players().stream()
                .filter(ConnectedPlayer.class::isInstance)
                .map(ConnectedPlayer.class::cast)
                .toList();
    }

    private java.util.List<ConnectedPlayer> others(String where) {
        return all().stream()
                .filter(player -> !player.currentHyperGravelServer()
                        .map(server -> server.info().name())
                        .orElse("")
                        .equals(where))
                .toList();
    }
}
