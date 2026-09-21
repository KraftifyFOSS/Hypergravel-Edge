package pdx.dev.hypergravel.proxy.extension;

import pdx.dev.hypergravel.api.extension.Extension;
import pdx.dev.hypergravel.api.extension.HyperGravelExtension;

@Extension(id = "zzz-present", name = "Soft Target", version = "1.0.0")
public final class SoftTargetExtension extends HyperGravelExtension {

    @Override
    public void onEnable() {
        ExtensionSink.record("enable:zzz");
    }

    @Override
    public void onDisable() {
        ExtensionSink.record("disable:zzz");
    }
}