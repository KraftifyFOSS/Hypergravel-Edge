package pdx.dev.hypergravel.api.server;

public enum ForwardingMode {

    VELOCITY_MODERN,

    BUNGEEGUARD,

    LEGACY,

    NONE;

    public static ForwardingMode parse(String raw) {
        if (raw == null) {
            return VELOCITY_MODERN;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_')) {
            case "modern", "velocity", "velocity_modern" -> VELOCITY_MODERN;
            case "bungeeguard", "guard" -> BUNGEEGUARD;
            case "legacy", "bungeecord", "bungee" -> LEGACY;
            case "none", "off" -> NONE;
            default -> throw new IllegalArgumentException("unknown forwarding mode: " + raw);
        };
    }
}
