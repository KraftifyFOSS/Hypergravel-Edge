package pdx.dev.hypergravel.proxy.network.handler;

import io.netty.buffer.ByteBuf;
import pdx.dev.hypergravel.proxy.protocol.PacketDirection;
import pdx.dev.hypergravel.proxy.protocol.PacketType;
import pdx.dev.hypergravel.proxy.protocol.ProtocolState;
import pdx.dev.hypergravel.proxy.protocol.ProtocolUtils;
import pdx.dev.hypergravel.proxy.protocol.ProtocolVersion;
import pdx.dev.hypergravel.proxy.protocol.StateRegistry;

final class TabPacketFilter {

    private TabPacketFilter() {
    }

    static boolean isPlayerListInfo(ByteBuf frame, ProtocolVersion version, ProtocolState state) {
        if (version == ProtocolVersion.UNKNOWN) {
            return false;
        }
        int readerIndex = frame.readerIndex();
        final int packetId;
        try {
            packetId = ProtocolUtils.readVarInt(frame);
        } finally {
            frame.readerIndex(readerIndex);
        }
        StateRegistry.VersionTable table = StateRegistry.of(state)
                .direction(PacketDirection.CLIENTBOUND)
                .forVersion(version);
        int updateId = table.idOf(PacketType.PLAYER_INFO_UPDATE);
        int removeId = table.idOf(PacketType.PLAYER_INFO_REMOVE);
        if (updateId < 0 && removeId < 0) {
            return false;
        }
        return packetId == updateId || packetId == removeId;
    }
}