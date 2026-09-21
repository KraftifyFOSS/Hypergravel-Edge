package pdx.dev.hypergravel.proxy.protocol;

public enum ProtocolState {

    HANDSHAKE,
    STATUS,
    LOGIN,

    CONFIGURATION,
    PLAY;

    public boolean passthrough() {
        return this == PLAY || this == CONFIGURATION;
    }
}
