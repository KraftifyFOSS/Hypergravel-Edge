package pdx.dev.hypergravel.proxy.extension;

import pdx.dev.hypergravel.api.extension.Extension;
import pdx.dev.hypergravel.api.extension.HyperGravelExtension;

@Extension(id = "missing-dep", name = "Missing", version = "1.0.0",
        depends = { "ghost-dependency" })
public final class MissingDependencyExtension extends HyperGravelExtension {

    @Override
    public void onEnable() {
        ExtensionSink.record("enable:missing");
    }
}