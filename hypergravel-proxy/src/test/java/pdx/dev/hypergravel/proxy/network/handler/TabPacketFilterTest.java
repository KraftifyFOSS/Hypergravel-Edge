package pdx.dev.hypergravel.proxy.network.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import pdx.dev.hypergravel.proxy.protocol.PacketDirection;
import pdx.dev.hypergravel.proxy.protocol.PacketType;
import pdx.dev.hypergravel.proxy.protocol.ProtocolState;
import pdx.dev.hypergravel.proxy.protocol.ProtocolUtils;
import pdx.dev.hypergravel.proxy.protocol.ProtocolVersion;
import pdx.dev.hypergravel.proxy.protocol.StateRegistry;
import org.junit.jupiter.api.Test;

class TabPacketFilterTest {

    @Test
    void undefinedSchemeNeverDropsAnything() {
        assertFalse(TabPacketFilter.isPlayerListInfo(
                frameFor(PacketType.PLAYER_INFO_UPDATE),
                ProtocolVersion.MINECRAFT_1_21, ProtocolState.PLAY));
        assertFalse(TabPacketFilter.isPlayerListInfo(
                frameFor(PacketType.PLAYER_INFO_REMOVE),
                ProtocolVersion.MINECRAFT_1_21, ProtocolState.PLAY));
    }

    @Test
    void unrelatedPacketsPassThrough() {
        assertFalse(TabPacketFilter.isPlayerListInfo(
                frameFor(PacketType.TAB_LIST),
                ProtocolVersion.MINECRAFT_1_21, ProtocolState.PLAY));
    }

    @Test
    void unknownProtocolIsNeverFiltered() {
        assertFalse(TabPacketFilter.isPlayerListInfo(
                frameFor(PacketType.PLAYER_INFO_UPDATE),
                ProtocolVersion.UNKNOWN, ProtocolState.PLAY));
    }

    @Test
    void outOfStatePacketsPassThrough() {
        assertFalse(TabPacketFilter.isPlayerListInfo(
                frameFor(PacketType.PLAYER_INFO_UPDATE),
                ProtocolVersion.MINECRAFT_1_21_11, ProtocolState.LOGIN));
    }

    private static ByteBuf frameFor(PacketType type) {
        StateRegistry.VersionTable table = StateRegistry.of(ProtocolState.PLAY)
                .direction(PacketDirection.CLIENTBOUND)
                .forVersion(ProtocolVersion.MINECRAFT_1_21);
        ByteBuf frame = Unpooled.buffer();
        ProtocolUtils.writeVarInt(frame, table.idOf(type));
        return frame;
    }
}