package pdx.dev.hypergravel.api.server;

import pdx.dev.hypergravel.api.player.Player;

public interface ServerConnection {

    RegisteredServer server();

    Player player();

    boolean sendPluginMessage(String channel, byte[] data);

    boolean active();
}
