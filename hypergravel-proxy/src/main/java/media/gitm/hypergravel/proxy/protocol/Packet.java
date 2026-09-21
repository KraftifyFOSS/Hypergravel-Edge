package media.gitm.hypergravel.proxy.protocol;

import io.netty.buffer.ByteBuf;

public interface Packet {

    void decode(ByteBuf buf, ProtocolVersion version);

    void encode(ByteBuf buf, ProtocolVersion version);

    boolean handle(PacketHandler handler);

    default int expectedSize() {
        return 64;
    }
}
