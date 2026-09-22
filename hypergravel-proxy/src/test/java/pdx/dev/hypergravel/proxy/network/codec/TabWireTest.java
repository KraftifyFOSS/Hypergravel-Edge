package pdx.dev.hypergravel.proxy.network.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import pdx.dev.hypergravel.proxy.protocol.PacketDirection;
import pdx.dev.hypergravel.proxy.protocol.ProtocolState;
import pdx.dev.hypergravel.proxy.protocol.ProtocolUtils;
import pdx.dev.hypergravel.proxy.protocol.ProtocolVersion;
import pdx.dev.hypergravel.proxy.protocol.packet.GameProfile;
import pdx.dev.hypergravel.proxy.protocol.packet.HandshakePacket;
import pdx.dev.hypergravel.tab.TabPackets;
import org.junit.jupiter.api.Test;

class TabWireTest {

    private static final ByteBufAllocator ALLOC = UnpooledByteBufAllocator.DEFAULT;

    @Test
    void playerInfoUpdateUsesServerIds() {
        UUID uuid = UUID.randomUUID();
        TabPackets.Entry entry = new TabPackets.Entry(uuid, "Test", GameProfile.NO_PROPERTIES,
                true, 50, null);
        int actions = TabPackets.ACTION_ADD_PLAYER | TabPackets.ACTION_UPDATE_LISTED;
        ByteBuf buf = TabWire.encodeRaw(new TabPackets.PlayerInfoUpdate(actions, List.of(entry)),
                ProtocolState.PLAY, PacketDirection.CLIENTBOUND,
                ProtocolVersion.MINECRAFT_26_2, ALLOC);
        assertNotNull(buf);
        try {
            assertEquals(70, ProtocolUtils.readVarInt(buf));
            assertEquals(actions, buf.readByte());
            assertEquals(1, ProtocolUtils.readVarInt(buf));
            assertEquals(uuid, ProtocolUtils.readUuid(buf));
            assertEquals("Test", ProtocolUtils.readString(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void playerInfoRemoveAndHeaderFooterUseServerIds() {
        ByteBuf remove = TabWire.encodeRaw(
                new TabPackets.PlayerInfoRemove(List.of(UUID.randomUUID())),
                ProtocolState.PLAY, PacketDirection.CLIENTBOUND,
                ProtocolVersion.MINECRAFT_26_2, ALLOC);
        assertNotNull(remove);
        try {
            assertEquals(69, ProtocolUtils.readVarInt(remove));
        } finally {
            remove.release();
        }

        ByteBuf header = TabWire.encodeRaw(
                new TabPackets.HeaderFooter(net.kyori.adventure.text.Component.empty(),
                        net.kyori.adventure.text.Component.empty()),
                ProtocolState.PLAY, PacketDirection.CLIENTBOUND,
                ProtocolVersion.MINECRAFT_26_2, ALLOC);
        assertNotNull(header);
        try {
            assertEquals(122, ProtocolUtils.readVarInt(header));
            assertTrue(header.isReadable());
        } finally {
            header.release();
        }
    }

    @Test
    void olderWireVersionsHaveNoTabIds() {
        ByteBuf buf = TabWire.encodeRaw(
                new TabPackets.PlayerInfoUpdate(TabPackets.ACTION_UPDATE_LISTED, List.of()),
                ProtocolState.PLAY, PacketDirection.CLIENTBOUND,
                ProtocolVersion.MINECRAFT_1_21_11, ALLOC);
        assertNull(buf);
    }

    @Test
    void unmappedPacketsEncodeToNull() {
        assertNull(TabWire.encodeRaw(new HandshakePacket(), ProtocolState.PLAY,
                PacketDirection.CLIENTBOUND, ProtocolVersion.MINECRAFT_26_2, ALLOC));
    }
}
