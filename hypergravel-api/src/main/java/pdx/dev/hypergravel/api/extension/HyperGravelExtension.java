package pdx.dev.hypergravel.api.extension;

import java.nio.file.Path;

import pdx.dev.hypergravel.api.ProxyServer;
import pdx.dev.hypergravel.api.command.CommandManager;
import pdx.dev.hypergravel.api.config.ConfigSection;
import pdx.dev.hypergravel.api.event.EventManager;
import pdx.dev.hypergravel.api.scheduler.Scheduler;
import org.apache.logging.log4j.Logger;

/**
 * Base class for a server-side proxy plugin ("extension").
 *
 * <p>Mark the concrete subclass with {@link Extension @Extension} to carry its
 * metadata. The proxy scans every jar in the configured extension directory at
 * startup, resolves {@link Extension#depends() dependencies}, instantiates the
 * annotated class, then calls {@link #onEnable()} in a topologically sorted
 * order. {@link #onDisable()} is called in reverse order at shutdown.
 */
public abstract class HyperGravelExtension {

    private volatile ExtensionContext context;

    public abstract void onEnable();

    public void onDisable() {
    }

    public final void setContext(ExtensionContext context) {
        this.context = context;
    }

    public final ExtensionContext context() {
        ExtensionContext have = context;
        if (have == null) {
            throw new IllegalStateException("context is not set; onEnable has not run yet");
        }
        return have;
    }

    public final ProxyServer proxy() {
        return context().proxy();
    }

    public final Logger logger() {
        return context().logger();
    }

    public final Path dataDirectory() {
        return context().dataDirectory();
    }

    public final Path configDirectory() {
        return context().configDirectory();
    }

    public final ConfigSection config() {
        return context().config();
    }

    public final ClassLoader classLoader() {
        return context().classLoader();
    }

    public final Scheduler scheduler() {
        return proxy().scheduler();
    }

    public final EventManager eventManager() {
        return proxy().eventManager();
    }

    public final CommandManager commandManager() {
        return proxy().commandManager();
    }
}