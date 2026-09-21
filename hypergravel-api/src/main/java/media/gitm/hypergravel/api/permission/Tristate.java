package media.gitm.hypergravel.api.permission;

public enum Tristate {
    TRUE,
    FALSE,
    UNDEFINED;

    public boolean asBoolean() {
        return this == TRUE;
    }

    public boolean orElse(boolean fallback) {
        return this == UNDEFINED ? fallback : this == TRUE;
    }

    public static Tristate of(boolean value) {
        return value ? TRUE : FALSE;
    }
}
