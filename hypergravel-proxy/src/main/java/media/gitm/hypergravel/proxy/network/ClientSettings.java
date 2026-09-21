package media.gitm.hypergravel.proxy.network;

import io.netty.buffer.ByteBuf;
import media.gitm.hypergravel.proxy.backend.BackendConnection;
import media.gitm.hypergravel.proxy.player.ConnectedPlayer;
import media.gitm.hypergravel.proxy.protocol.ProtocolUtils;

public final class ClientSettings {

    private static final int CONFIG_CLIENT_INFORMATION = 0x00;

    private ClientSettings() {
    }

    public static void remember(ConnectedPlayer player, ByteBuf frame) {
        ByteBuf peek = frame.duplicate();
        if (!peek.isReadable()) {
            return;
        }
        int packetId;
        try {
            packetId = ProtocolUtils.readVarInt(peek);
        } catch (RuntimeException e) {
            return;
        }
        if (packetId != CONFIG_CLIENT_INFORMATION) {
            return;
        }
        byte[] copy = new byte[frame.readableBytes()];
        frame.duplicate().readBytes(copy);
        player.rememberClientInformation(copy);
    }

    public static void replay(ConnectedPlayer player, BackendConnection backend) {
        byte[] frame = player.clientInformation();
        if (frame == null || backend == null || !backend.connection().active()) {
            return;
        }
        ByteBuf buffer = backend.connection().channel().alloc().buffer(frame.length);
        buffer.writeBytes(frame);
        backend.connection().channel().writeAndFlush(
                buffer, backend.connection().channel().voidPromise());
    }
}
