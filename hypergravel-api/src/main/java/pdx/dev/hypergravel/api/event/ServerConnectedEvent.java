package pdx.dev.hypergravel.api.event;

import java.util.Objects;
import java.util.Optional;

import pdx.dev.hypergravel.api.player.Player;
import pdx.dev.hypergravel.api.server.RegisteredServer;

public record ServerConnectedEvent(
        Player player,
        RegisteredServer server,
        RegisteredServer previousServer) {

    public ServerConnectedEvent {
        Objects.requireNonNull(player);
        Objects.requireNonNull(server);
    }

    public Optional<RegisteredServer> previous() {
        return Optional.ofNullable(previousServer);
    }

    public boolean firstConnect() {
        return previousServer == null;
    }
}
