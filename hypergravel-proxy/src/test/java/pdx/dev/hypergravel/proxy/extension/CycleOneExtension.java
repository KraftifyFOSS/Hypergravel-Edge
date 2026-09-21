package pdx.dev.hypergravel.proxy.extension;

import pdx.dev.hypergravel.api.extension.Extension;
import pdx.dev.hypergravel.api.extension.HyperGravelExtension;

@Extension(id = "cycle-one", name = "Cycle One", version = "1.0.0",
        depends = { "cycle-two" })
public final class CycleOneExtension extends HyperGravelExtension {

    @Override
    public void onEnable() {
        ExtensionSink.record("enable:cycle1");
    }
}