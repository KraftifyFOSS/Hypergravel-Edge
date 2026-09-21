package pdx.dev.hypergravel.proxy.extension;

import pdx.dev.hypergravel.api.extension.Extension;
import pdx.dev.hypergravel.api.extension.HyperGravelExtension;

@Extension(id = "failing", name = "Failing", version = "1.0.0")
public final class FailingExtension extends HyperGravelExtension {

    @Override
    public void onEnable() {
        ExtensionSink.record("enable:failing");
        throw new IllegalStateException("boom");
    }

    @Override
    public void onDisable() {
        ExtensionSink.record("disable:failing");
    }
}