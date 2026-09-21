package media.gitm.hypergravel.api.server;

import media.gitm.hypergravel.api.player.Player;

public interface ServerConnection {

    RegisteredServer server();

    Player player();

    boolean sendPluginMessage(String channel, byte[] data);

    boolean active();
}
