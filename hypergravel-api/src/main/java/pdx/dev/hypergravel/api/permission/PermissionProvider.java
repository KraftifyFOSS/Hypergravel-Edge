package pdx.dev.hypergravel.api.permission;

import pdx.dev.hypergravel.api.command.CommandSource;

@FunctionalInterface
public interface PermissionProvider {

    PermissionProvider DEFAULT = (source, permission) -> Tristate.UNDEFINED;

    Tristate lookup(CommandSource source, String permission);
}
