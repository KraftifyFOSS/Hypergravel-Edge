package com.example.hypergravel;

import java.time.Duration;

import pdx.dev.hypergravel.api.command.CommandSource;
import pdx.dev.hypergravel.api.event.LoginEvent;
import pdx.dev.hypergravel.api.extension.Extension;
import pdx.dev.hypergravel.api.extension.HyperGravelExtension;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * A minimal extension that shows everything a plugin can reach in one file:
 * the proxy, commands, events, the scheduler, and the extension's own config.
 */
@Extension(
        id = "sample-hello",
        name = "Sample Greeter",
        version = "1.0.0",
        description = "Greets players when they log in and adds /hello",
        authors = { "you" })
public final class HelloExtension extends HyperGravelExtension {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        logger().info("Hello enabled on HyperGravel {}", proxy().version());

        commandManager().register("hello", this::executeHello);

        eventManager().register(this, LoginEvent.class, event -> {
            String greeting = config().getString("greeting", "<gold>Welcome, <player>!</gold>");
            event.player().sendMessage(MM.deserialize(greeting
                    .replace("<player>", event.player().username())));
        });

        scheduler().repeat(this, Duration.ofMinutes(1), Duration.ofMinutes(1),
                () -> logger().info("heartbeat: {} player(s) online", proxy().playerCount()));

        logger().info("data directory: {}", dataDirectory());
        logger().info("hello.toml in the extensions directory is read as: {}",
                config().getString("greeting", "<gold>Welcome, <player>!</gold>"));
    }

    @Override
    public void onDisable() {
        logger().info("Hello disabled");
    }

    private void executeHello(CommandSource source, String[] args) {
        String target = args.length > 0 ? args[0] : source.name();
        source.sendMessage(MM.deserialize("<green>Hello, <white>" + target
                + "</white>! <gray>running on HyperGravel</gray>"));
    }
}