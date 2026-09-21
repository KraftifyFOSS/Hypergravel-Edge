package pdx.dev.hypergravel.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import pdx.dev.hypergravel.proxy.protocol.PacketDirection;
import pdx.dev.hypergravel.proxy.protocol.PacketType;
import pdx.dev.hypergravel.proxy.protocol.ProtocolState;
import pdx.dev.hypergravel.proxy.protocol.ProtocolVersion;
import pdx.dev.hypergravel.proxy.protocol.StateRegistry;
import org.junit.jupiter.api.Test;

class StateRegistryTest {

    @Test
    void everyStateLoadsWithoutDuplicateIds() {

        for (ProtocolState state : ProtocolState.values()) {
            for (PacketDirection direction : PacketDirection.values()) {
                for (ProtocolVersion version : ProtocolVersion.values()) {
                    assertNotNull(StateRegistry.of(state).direction(direction).forVersion(version),
                            state + "/" + direction + " at " + version);
                }
            }
        }
    }

    @Test
    void handshakeIsIdZero() {
        StateRegistry.VersionTable table = StateRegistry.of(ProtocolState.HANDSHAKE)
                .direction(PacketDirection.SERVERBOUND)
                .forVersion(ProtocolVersion.MINECRAFT_1_21);
        assertEquals(PacketType.HANDSHAKE, table.lookup(0));
        assertEquals(0, table.idOf(PacketType.HANDSHAKE));
    }

    @Test
    void loginIdsAreStableAcrossTheSupportedRange() {
        for (ProtocolVersion version : new ProtocolVersion[] {
                ProtocolVersion.MINECRAFT_1_20_2, ProtocolVersion.MINECRAFT_1_21,
                ProtocolVersion.MINECRAFT_1_21_9 }) {
            StateRegistry.VersionTable clientbound = StateRegistry.of(ProtocolState.LOGIN)
                    .direction(PacketDirection.CLIENTBOUND).forVersion(version);
            assertEquals(1, clientbound.idOf(PacketType.LOGIN_ENCRYPTION_REQUEST), version.toString());
            assertEquals(2, clientbound.idOf(PacketType.LOGIN_SUCCESS), version.toString());
            assertEquals(3, clientbound.idOf(PacketType.LOGIN_SET_COMPRESSION), version.toString());

            StateRegistry.VersionTable serverbound = StateRegistry.of(ProtocolState.LOGIN)
                    .direction(PacketDirection.SERVERBOUND).forVersion(version);
            assertEquals(0, serverbound.idOf(PacketType.LOGIN_START), version.toString());
            assertEquals(3, serverbound.idOf(PacketType.LOGIN_ACKNOWLEDGED), version.toString());
        }
    }

    @Test
    void configurationIdsShiftAt1_20_5() {

        StateRegistry.DirectionRegistry clientbound =
                StateRegistry.of(ProtocolState.CONFIGURATION).direction(PacketDirection.CLIENTBOUND);

        assertEquals(0, clientbound.forVersion(ProtocolVersion.MINECRAFT_1_20_2)
                .idOf(PacketType.PLUGIN_MESSAGE));
        assertEquals(1, clientbound.forVersion(ProtocolVersion.MINECRAFT_1_20_5)
                .idOf(PacketType.PLUGIN_MESSAGE));
        assertEquals(1, clientbound.forVersion(ProtocolVersion.MINECRAFT_1_21_9)
                .idOf(PacketType.PLUGIN_MESSAGE));
    }

    @Test
    void unmappedIdsResolveToNullSoTheyPassThrough() {
        StateRegistry.VersionTable play = StateRegistry.of(ProtocolState.PLAY)
                .direction(PacketDirection.CLIENTBOUND)
                .forVersion(ProtocolVersion.MINECRAFT_1_21);

        assertNull(play.lookup(0));
        assertNull(play.lookup(9999));
        assertNull(play.lookup(-1));
    }

    @Test
    void transferOnlyExistsFrom1_20_5() {
        StateRegistry.DirectionRegistry play =
                StateRegistry.of(ProtocolState.PLAY).direction(PacketDirection.CLIENTBOUND);

        assertTrue(play.forVersion(ProtocolVersion.MINECRAFT_1_20_2).idOf(PacketType.TRANSFER) < 0);
        assertTrue(play.forVersion(ProtocolVersion.MINECRAFT_1_21).has(PacketType.TRANSFER));
    }
}
