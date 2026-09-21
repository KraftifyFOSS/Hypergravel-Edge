package media.gitm.hypergravel.api.permission;

import media.gitm.hypergravel.api.command.CommandSource;

@FunctionalInterface
public interface PermissionProvider {

    PermissionProvider DEFAULT = (source, permission) -> Tristate.UNDEFINED;

    Tristate lookup(CommandSource source, String permission);
}
