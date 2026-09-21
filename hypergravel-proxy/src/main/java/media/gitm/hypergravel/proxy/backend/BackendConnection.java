package media.gitm.hypergravel.proxy.backend;

import io.netty.buffer.Unpooled;
import media.gitm.hypergravel.api.player.Player;
import media.gitm.hypergravel.api.server.RegisteredServer;
import media.gitm.hypergravel.api.server.ServerConnection;
import media.gitm.hypergravel.proxy.network.MinecraftConnection;
import media.gitm.hypergravel.proxy.player.ConnectedPlayer;
import media.gitm.hypergravel.proxy.protocol.packet.SharedPackets;

public final class BackendConnection implements ServerConnection {

    private final ConnectedPlayer player;
    private final HyperGravelServer server;
    private final MinecraftConnection connection;

    private volatile boolean active;

    public BackendConnection(ConnectedPlayer player, HyperGravelServer server,
                             MinecraftConnection connection) {
        this.player = player;
        this.server = server;
        this.connection = connection;
    }

    @Override
    public RegisteredServer server() {
        return server;
    }

    public HyperGravelServer hypergravelServer() {
        return server;
    }

    @Override
    public Player player() {
        return player;
    }

    public ConnectedPlayer connectedPlayer() {
        return player;
    }

    public MinecraftConnection connection() {
        return connection;
    }

    @Override
    public boolean active() {
        return active && connection.active();
    }

    public void markActive() {
        this.active = true;
    }

    @Override
    public boolean sendPluginMessage(String channel, byte[] data) {
        if (!connection.active()) {
            return false;
        }
        connection.writeAndFlush(new SharedPackets.PluginMessage(channel,
                Unpooled.wrappedBuffer(data)));
        return true;
    }

    public void close() {
        connection.close();
    }

    @Override
    public String toString() {
        return "BackendConnection{" + player.username() + " -> " + server.name()
                + (active ? " (active)" : " (pending)") + '}';
    }
}
