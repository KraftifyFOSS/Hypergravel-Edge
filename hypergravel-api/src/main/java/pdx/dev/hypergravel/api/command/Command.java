package pdx.dev.hypergravel.api.command;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface Command {

    void execute(CommandSource source, String[] args);

    default CompletableFuture<List<String>> suggest(CommandSource source, String[] args) {
        return CompletableFuture.completedFuture(List.of());
    }

    default boolean hasPermission(CommandSource source, String[] args) {
        return true;
    }
}
