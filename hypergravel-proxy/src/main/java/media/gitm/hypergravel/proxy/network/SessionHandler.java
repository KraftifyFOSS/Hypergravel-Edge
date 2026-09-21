package media.gitm.hypergravel.proxy.network;

import io.netty.buffer.ByteBuf;
import media.gitm.hypergravel.proxy.protocol.Packet;
import media.gitm.hypergravel.proxy.protocol.PacketHandler;

public interface SessionHandler extends PacketHandler {

    default void activated() {
    }

    default void deactivated() {
    }

    default void forward(Packet packet) {
    }

    default void forwardRaw(ByteBuf frame) {
        frame.release();
    }

    default void readComplete() {
    }

    default void disconnected() {
    }

    default void writabilityChanged(boolean writable) {
    }

    default boolean handleException(Throwable cause) {
        return false;
    }
}
