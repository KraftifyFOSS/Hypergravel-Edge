package pdx.dev.hypergravel.proxy.extension;

import pdx.dev.hypergravel.api.extension.Extension;
import pdx.dev.hypergravel.api.extension.HyperGravelExtension;

@Extension(id = "dup-id", name = "Duplicate One", version = "1.0.0")
public final class DuplicateExtensionOne extends HyperGravelExtension {

    @Override
    public void onEnable() {
        ExtensionSink.record("enable:dup1");
    }
}