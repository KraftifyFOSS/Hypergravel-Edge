package pdx.dev.hypergravel.proxy.extension;

import pdx.dev.hypergravel.api.extension.Extension;

/** Has the annotation but does not extend HyperGravelExtension. */
@Extension(id = "not-a-base", name = "Not a base", version = "1.0.0")
public final class NotAnExtensionClass {

    public NotAnExtensionClass() {
    }
}