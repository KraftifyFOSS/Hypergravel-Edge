package pdx.dev.hypergravel.proxy.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import pdx.dev.hypergravel.api.command.Command;
import pdx.dev.hypergravel.api.command.CommandManager;
import pdx.dev.hypergravel.api.command.CommandSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class HyperGravelCommandManager implements CommandManager {

    private static final Logger LOGGER = LogManager.getLogger(HyperGravelCommandManager.class);

    private final Map<String, Command> commands = new ConcurrentHashMap<>();

    @Override
    public void register(String name, Command command, String... aliases) {
        commands.put(name.toLowerCase(Locale.ROOT), command);
        for (String alias : aliases) {
            commands.put(alias.toLowerCase(Locale.ROOT), command);
        }
    }

    @Override
    public boolean unregister(String name) {
        return commands.remove(name.toLowerCase(Locale.ROOT)) != null;
    }

    @Override
    public Optional<Command> lookup(String name) {
        return Optional.ofNullable(commands.get(name.toLowerCase(Locale.ROOT)));
    }

    @Override
    public Set<String> registered() {
        return Set.copyOf(commands.keySet());
    }

    @Override
    public CompletableFuture<Boolean> execute(CommandSource source, String commandLine) {
        String[] parts = split(commandLine);
        if (parts.length == 0) {
            return CompletableFuture.completedFuture(false);
        }
        Command command = commands.get(parts[0].toLowerCase(Locale.ROOT));
        if (command == null) {

            return CompletableFuture.completedFuture(false);
        }

        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);

        if (!command.hasPermission(source, args)) {

            return CompletableFuture.completedFuture(false);
        }

        try {
            command.execute(source, args);
        } catch (Throwable t) {
            LOGGER.error("command '{}' from {} threw", commandLine, source.name(), t);
            return CompletableFuture.failedFuture(t);
        }
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<List<String>> suggest(CommandSource source, String commandLine) {
        String[] parts = split(commandLine);
        if (parts.length <= 1 && !commandLine.endsWith(" ")) {
            String prefix = parts.length == 0 ? "" : parts[0].toLowerCase(Locale.ROOT);
            return CompletableFuture.completedFuture(commands.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(prefix))
                    .filter(entry -> entry.getValue().hasPermission(source, new String[0]))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList());
        }
        Command command = commands.get(parts[0].toLowerCase(Locale.ROOT));
        if (command == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        if (!command.hasPermission(source, args)) {
            return CompletableFuture.completedFuture(List.of());
        }
        return command.suggest(source, args);
    }

    private static String[] split(String commandLine) {
        String trimmed = commandLine.strip();
        if (trimmed.isEmpty()) {
            return new String[0];
        }
        return trimmed.split("\\s+");
    }
}
