package pdx.dev.hypergravel.proxy.extension;

import pdx.dev.hypergravel.api.extension.Extension;
import pdx.dev.hypergravel.api.extension.HyperGravelExtension;

@Extension(id = "a", name = "A", version = "1.0.0")
public final class SampleExtensionA extends HyperGravelExtension {

    @Override
    public void onEnable() {
        ExtensionSink.record("enable:a");
    }

    @Override
    public void onDisable() {
        ExtensionSink.record("disable:a");
    }
}