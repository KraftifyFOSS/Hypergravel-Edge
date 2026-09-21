package pdx.dev.hypergravel.api.event;

import java.util.Objects;
import java.util.Optional;

import pdx.dev.hypergravel.api.player.Player;
import net.kyori.adventure.text.Component;

public final class LoginEvent {

    private final Player player;
    private Component denyReason;

    public LoginEvent(Player player) {
        this.player = Objects.requireNonNull(player);
    }

    public Player player() {
        return player;
    }

    public boolean allowed() {
        return denyReason == null;
    }

    public Optional<Component> denyReason() {
        return Optional.ofNullable(denyReason);
    }

    public void deny(Component reason) {
        this.denyReason = Objects.requireNonNull(reason);
    }

    public void allow() {
        this.denyReason = null;
    }
}
