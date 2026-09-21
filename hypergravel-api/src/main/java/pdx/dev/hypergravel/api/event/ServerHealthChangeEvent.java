package pdx.dev.hypergravel.api.event;

import java.util.Objects;

import pdx.dev.hypergravel.api.server.RegisteredServer;
import pdx.dev.hypergravel.api.server.ServerHealth;

public record ServerHealthChangeEvent(
        RegisteredServer server,
        ServerHealth previous,
        ServerHealth current) {

    public ServerHealthChangeEvent {
        Objects.requireNonNull(server);
        Objects.requireNonNull(previous);
        Objects.requireNonNull(current);
    }

    public boolean losingPlayers() {
        return previous.routable() && !current.routable();
    }

    public boolean regainingPlayers() {
        return !previous.routable() && current.routable();
    }
}
