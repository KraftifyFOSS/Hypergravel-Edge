package pdx.dev.hypergravel.api.event;

import java.util.Objects;
import java.util.Optional;

import pdx.dev.hypergravel.api.command.CommandSource;
import net.kyori.adventure.text.Component;

public final class CommandExecuteEvent {

    private final CommandSource source;
    private final String commandLine;
    private Result result = Result.allowed();

    public CommandExecuteEvent(CommandSource source, String commandLine) {
        this.source = Objects.requireNonNull(source);
        this.commandLine = Objects.requireNonNull(commandLine);
    }

    public CommandSource source() {
        return source;
    }

    public String commandLine() {
        return commandLine;
    }

    public String rootCommand() {
        int space = commandLine.indexOf(' ');
        String root = space < 0 ? commandLine : commandLine.substring(0, space);
        return root.toLowerCase(java.util.Locale.ROOT);
    }

    public Result result() {
        return result;
    }

    public void setResult(Result result) {
        this.result = Objects.requireNonNull(result);
    }

    public static final class Result {
        private static final Result ALLOWED = new Result(true, false, null, null);
        private static final Result FORWARD = new Result(true, true, null, null);

        private final boolean allowed;
        private final boolean forwardToBackend;
        private final String rewritten;
        private final Component denyReason;

        private Result(boolean allowed, boolean forwardToBackend, String rewritten, Component denyReason) {
            this.allowed = allowed;
            this.forwardToBackend = forwardToBackend;
            this.rewritten = rewritten;
            this.denyReason = denyReason;
        }

        public static Result allowed() {
            return ALLOWED;
        }

        public static Result forwardToBackend() {
            return FORWARD;
        }

        public static Result rewrite(String newCommandLine) {
            return new Result(true, false, Objects.requireNonNull(newCommandLine), null);
        }

        public static Result denied(Component reason) {
            return new Result(false, false, null, reason);
        }

        public static Result denied() {
            return new Result(false, false, null, null);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public boolean forwardsToBackend() {
            return forwardToBackend;
        }

        public Optional<String> rewritten() {
            return Optional.ofNullable(rewritten);
        }

        public Optional<Component> denyReason() {
            return Optional.ofNullable(denyReason);
        }
    }
}
