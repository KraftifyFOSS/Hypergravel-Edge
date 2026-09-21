package media.gitm.hypergravel.proxy.protocol;

public enum PacketDirection {

    SERVERBOUND,

    CLIENTBOUND;

    public PacketDirection opposite() {
        return this == SERVERBOUND ? CLIENTBOUND : SERVERBOUND;
    }
}
