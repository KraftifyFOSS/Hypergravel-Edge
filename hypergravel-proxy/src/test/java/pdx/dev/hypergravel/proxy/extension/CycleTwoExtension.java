package pdx.dev.hypergravel.proxy.extension;

import pdx.dev.hypergravel.api.extension.Extension;
import pdx.dev.hypergravel.api.extension.HyperGravelExtension;

@Extension(id = "cycle-two", name = "Cycle Two", version = "1.0.0",
        depends = { "cycle-one" })
public final class CycleTwoExtension extends HyperGravelExtension {

    @Override
    public void onEnable() {
        ExtensionSink.record("enable:cycle2");
    }
}