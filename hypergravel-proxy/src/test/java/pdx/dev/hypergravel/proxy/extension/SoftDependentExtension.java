package pdx.dev.hypergravel.proxy.extension;

import pdx.dev.hypergravel.api.extension.Extension;
import pdx.dev.hypergravel.api.extension.HyperGravelExtension;

@Extension(id = "aaa-soft", name = "Soft Dependent", version = "1.0.0",
        softDepends = { "zzz-present" })
public final class SoftDependentExtension extends HyperGravelExtension {

    @Override
    public void onEnable() {
        ExtensionSink.record("enable:soft");
    }

    @Override
    public void onDisable() {
        ExtensionSink.record("disable:soft");
    }
}