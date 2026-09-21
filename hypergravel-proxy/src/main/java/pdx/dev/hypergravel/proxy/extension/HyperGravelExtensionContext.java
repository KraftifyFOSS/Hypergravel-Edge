package pdx.dev.hypergravel.proxy.extension;

import java.nio.file.Path;

import pdx.dev.hypergravel.api.ProxyServer;
import pdx.dev.hypergravel.api.config.ConfigSection;
import pdx.dev.hypergravel.api.extension.ExtensionContext;
import pdx.dev.hypergravel.api.extension.ExtensionDescription;
import org.apache.logging.log4j.Logger;

final class HyperGravelExtensionContext implements ExtensionContext {

    private final LoadedExtension extension;
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final Path configDirectory;
    private final ConfigSection config;

    HyperGravelExtensionContext(LoadedExtension extension, ProxyServer proxy, Logger logger,
                                Path dataDirectory, Path configDirectory, ConfigSection config) {
        this.extension = extension;
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.configDirectory = configDirectory;
        this.config = config;
    }

    @Override
    public ExtensionDescription description() {
        return extension.description();
    }

    @Override
    public ProxyServer proxy() {
        return proxy;
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public Path dataDirectory() {
        return dataDirectory;
    }

    @Override
    public Path configDirectory() {
        return configDirectory;
    }

    @Override
    public ConfigSection config() {
        return config;
    }

    @Override
    public ClassLoader classLoader() {
        return extension.classLoader();
    }
}