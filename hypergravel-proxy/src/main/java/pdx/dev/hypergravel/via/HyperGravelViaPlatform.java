package pdx.dev.hypergravel.via;

import java.io.File;
import java.util.logging.Logger;

import com.viaversion.viaversion.platform.UserConnectionViaVersionPlatform;
import pdx.dev.hypergravel.proxy.HyperGravelProxy;

final class HyperGravelViaPlatform extends UserConnectionViaVersionPlatform {

    HyperGravelViaPlatform(File dataFolder) {
        super(dataFolder);
    }

    @Override
    public Logger createLogger(String name) {
        return ViaLogging.create(name);
    }

    @Override
    public String getPlatformName() {
        return "HyperGravel";
    }

    @Override
    public String getPlatformVersion() {
        return HyperGravelProxy.VERSION;
    }
}
