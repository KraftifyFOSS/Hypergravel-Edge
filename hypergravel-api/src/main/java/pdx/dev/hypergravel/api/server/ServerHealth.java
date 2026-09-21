package pdx.dev.hypergravel.api.server;

public enum ServerHealth {

    UP,

    DRAINING,

    DOWN,

    UNKNOWN;

    public boolean routable() {
        return this == UP;
    }
}
