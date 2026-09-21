package pdx.dev.hypergravel.proxy.extension;

import pdx.dev.hypergravel.api.extension.Extension;
import pdx.dev.hypergravel.api.extension.HyperGravelExtension;

@Extension(id = "b", name = "B", version = "1.0.0", depends = { "a" })
public final class SampleExtensionB extends HyperGravelExtension {

    @Override
    public void onEnable() {
        ExtensionSink.record("enable:b");
    }

    @Override
    public void onDisable() {
        ExtensionSink.record("disable:b");
    }
}