package pdx.dev.hypergravel.proxy.extension;

import pdx.dev.hypergravel.api.extension.Extension;
import pdx.dev.hypergravel.api.extension.HyperGravelExtension;

@Extension(id = "c", name = "C", version = "1.0.0", depends = { "a", "b" })
public final class SampleExtensionC extends HyperGravelExtension {

    @Override
    public void onEnable() {
        ExtensionSink.record("enable:c");
    }

    @Override
    public void onDisable() {
        ExtensionSink.record("disable:c");
    }
}