package pdx.dev.hypergravel.api.command;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface CommandManager {

    void register(String name, Command command, String... aliases);

    boolean unregister(String name);

    Optional<Command> lookup(String name);

    Set<String> registered();

    CompletableFuture<Boolean> execute(CommandSource source, String commandLine);

    CompletableFuture<List<String>> suggest(CommandSource source, String commandLine);
}
