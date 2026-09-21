package pdx.dev.hypergravel.proxy.extension;

import java.nio.file.Path;

import pdx.dev.hypergravel.api.extension.ExtensionDescription;
import pdx.dev.hypergravel.api.extension.HyperGravelExtension;

public final class LoadedExtension {

    public enum State {
        LOADED,
        ENABLED,
        DISABLED,
        BROKEN
    }

    private final Path jar;
    private final ExtensionDescription description;
    private final ClassLoader classLoader;
    private final HyperGravelExtension plugin;

    private volatile State state;
    private volatile Throwable failure;

    LoadedExtension(Path jar, ExtensionDescription description, ClassLoader classLoader,
                    HyperGravelExtension plugin) {
        this.jar = jar;
        this.description = description;
        this.classLoader = classLoader;
        this.plugin = plugin;
        this.state = State.LOADED;
    }

    public Path jar() {
        return jar;
    }

    public ExtensionDescription description() {
        return description;
    }

    public ClassLoader classLoader() {
        return classLoader;
    }

    public HyperGravelExtension plugin() {
        return plugin;
    }

    public State state() {
        return state;
    }

    public void state(State state) {
        this.state = state;
    }

    public Throwable failure() {
        return failure;
    }

    public void failure(Throwable failure) {
        this.failure = failure;
    }
}