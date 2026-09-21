package pdx.dev.hypergravel.via;

import java.io.File;
import java.util.logging.Logger;

import com.viaversion.viabackwards.api.ViaBackwardsPlatform;

final class HyperGravelViaBackwards implements ViaBackwardsPlatform {

    private final Logger logger;
    private final File dataFolder;

    HyperGravelViaBackwards(File dataFolder) {
        this.dataFolder = dataFolder;
        this.logger = ViaLogging.create("ViaBackwards");
    }

    void start() {
        init(new File(dataFolder, "viabackwards.yml"));
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public File getDataFolder() {
        return dataFolder;
    }

    @Override
    public void disable() {

        logger.severe("ViaBackwards disabled itself — clients older than the backend "
                + "version will not be able to play.");
    }
}
