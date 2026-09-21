package pdx.dev.hypergravel.api.command;

import pdx.dev.hypergravel.api.permission.Tristate;
import net.kyori.adventure.audience.Audience;

public interface CommandSource extends Audience {

    Tristate permissionValue(String permission);

    default boolean hasPermission(String permission) {
        return permissionValue(permission).asBoolean();
    }

    String name();
}
