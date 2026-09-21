package media.gitm.hypergravel.api.event;

import java.util.Objects;
import java.util.Optional;

import media.gitm.hypergravel.api.player.Player;
import media.gitm.hypergravel.api.server.RegisteredServer;
import net.kyori.adventure.text.Component;

public final class PreServerConnectEvent {

    private final Player player;
    private final RegisteredServer original;
    private final Reason reason;
    private Result result;

    public PreServerConnectEvent(Player player, RegisteredServer original, Reason reason) {
        this.player = Objects.requireNonNull(player);
        this.original = Objects.requireNonNull(original);
        this.reason = Objects.requireNonNull(reason);
        this.result = Result.allowed();
    }

    public Player player() {
        return player;
    }

    public RegisteredServer originalTarget() {
        return original;
    }

    public RegisteredServer target() {
        return result.target != null ? result.target : original;
    }

    public Reason reason() {
        return reason;
    }

    public Result result() {
        return result;
    }

    public void setResult(Result result) {
        this.result = Objects.requireNonNull(result);
    }

    public enum Reason {

        INITIAL,

        COMMAND,

        PLUGIN_MESSAGE,

        FALLBACK,

        RESTORE,

        BACKEND_INITIATED
    }

    public static final class Result {
        private static final Result ALLOWED = new Result(true, null, null);

        private final boolean allowed;
        private final RegisteredServer target;
        private final Component denyReason;

        private Result(boolean allowed, RegisteredServer target, Component denyReason) {
            this.allowed = allowed;
            this.target = target;
            this.denyReason = denyReason;
        }

        public static Result allowed() {
            return ALLOWED;
        }

        public static Result redirect(RegisteredServer target) {
            return new Result(true, Objects.requireNonNull(target), null);
        }

        public static Result denied(Component reason) {
            return new Result(false, null, reason);
        }

        public static Result denied() {
            return new Result(false, null, null);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public Optional<Component> denyReason() {
            return Optional.ofNullable(denyReason);
        }
    }
}
