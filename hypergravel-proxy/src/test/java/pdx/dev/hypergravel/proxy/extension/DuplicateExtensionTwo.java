package pdx.dev.hypergravel.proxy.extension;

import pdx.dev.hypergravel.api.extension.Extension;
import pdx.dev.hypergravel.api.extension.HyperGravelExtension;

@Extension(id = "dup-id", name = "Duplicate Two", version = "1.0.0")
public final class DuplicateExtensionTwo extends HyperGravelExtension {

    @Override
    public void onEnable() {
        ExtensionSink.record("enable:dup2");
    }
}