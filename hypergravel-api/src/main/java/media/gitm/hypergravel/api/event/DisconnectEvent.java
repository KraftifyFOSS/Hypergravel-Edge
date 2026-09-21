package media.gitm.hypergravel.api.event;

import java.util.Objects;

import media.gitm.hypergravel.api.player.Player;

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
