package pdx.dev.hypergravel.api.event;

import java.util.Objects;

import pdx.dev.hypergravel.api.player.Player;

public record DisconnectEvent(Player player, Stage stage) {

    public DisconnectEvent {
        Objects.requireNonNull(player);
        Objects.requireNonNull(stage);
    }

    public enum Stage {

        LOGIN,

        CONNECTED,

        SHUTDOWN
    }
}
