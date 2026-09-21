package pdx.dev.hypergravel.api.extension;

import java.nio.file.Path;

import pdx.dev.hypergravel.api.ProxyServer;
import pdx.dev.hypergravel.api.config.ConfigSection;
import org.apache.logging.log4j.Logger;

public interface ExtensionContext {

    ExtensionDescription description();

    ProxyServer proxy();

    Logger logger();

    Path dataDirectory();

    Path configDirectory();

    ConfigSection config();

    ClassLoader classLoader();
}