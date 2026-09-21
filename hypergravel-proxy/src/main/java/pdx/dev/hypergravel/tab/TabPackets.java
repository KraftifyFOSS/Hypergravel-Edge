package pdx.dev.hypergravel.tab;

import java.util.List;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import media.gitm.hypergravel.proxy.protocol.ComponentNbt;
import media.gitm.hypergravel.proxy.protocol.Packet;
import media.gitm.hypergravel.proxy.protocol.PacketHandler;
import media.gitm.hypergravel.proxy.protocol.ProtocolUtils;
import media.gitm.hypergravel.proxy.protocol.ProtocolVersion;
import media.gitm.hypergravel.proxy.protocol.packet.GameProfile;
import net.kyori.adventure.text.Component;

public final class TabPackets {

    public static final int ACTION_ADD_PLAYER = 1;
    public static final int ACTION_INITIALISE_CHAT = 1 << 1;
    public static final int ACTION_UPDATE_GAME_MODE = 1 << 2;
    public static final int ACTION_UPDATE_LISTED = 1 << 3;
    public static final int ACTION_UPDATE_LATENCY = 1 << 4;
    public static final int ACTION_UPDATE_DISPLAY_NAME = 1 << 5;
    public static final int ACTION_UPDATE_LIST_ORDER = 1 << 6;
    public static final int ACTION_UPDATE_HAT = 1 << 7;

    private TabPackets() {
    }

    public record Entry(UUID uuid, String name, GameProfile.Property[] properties,
                        boolean listed, int latency, Component displayName) {

        public static Entry listedFlag(UUID uuid, boolean listed) {
            return new Entry(uuid, null, GameProfile.NO_PROPERTIES, listed, 0, null);
        }
    }

    public static final class PlayerInfoUpdate implements Packet {

        private int actions;
        private List<Entry> entries = List.of();

        public PlayerInfoUpdate() {
        }

        public PlayerInfoUpdate(int actions, List<Entry> entries) {
            this.actions = actions;
            this.entries = entries;
        }

        public List<Entry> entries() {
            return entries;
        }

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            throw new UnsupportedOperationException("player_info_update is written, never read");
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {

            buf.writeByte(actions);
            ProtocolUtils.writeVarInt(buf, entries.size());

            for (Entry entry : entries) {
                ProtocolUtils.writeUuid(buf, entry.uuid());

                if ((actions & ACTION_ADD_PLAYER) != 0) {
                    ProtocolUtils.writeString(buf, entry.name() == null ? "" : entry.name());
                    GameProfile.Property[] properties = entry.properties();
                    ProtocolUtils.writeVarInt(buf, properties.length);
                    for (GameProfile.Property property : properties) {
                        ProtocolUtils.writeString(buf, property.name());
                        ProtocolUtils.writeString(buf, property.value());
                        buf.writeBoolean(property.signed());
                        if (property.signed()) {
                            ProtocolUtils.writeString(buf, property.signature());
                        }
                    }
                }
                if ((actions & ACTION_INITIALISE_CHAT) != 0) {
                    buf.writeBoolean(false);
                }
                if ((actions & ACTION_UPDATE_GAME_MODE) != 0) {

                    ProtocolUtils.writeVarInt(buf, 0);
                }
                if ((actions & ACTION_UPDATE_LISTED) != 0) {
                    buf.writeBoolean(entry.listed());
                }
                if ((actions & ACTION_UPDATE_LATENCY) != 0) {
                    ProtocolUtils.writeVarInt(buf, entry.latency());
                }
                if ((actions & ACTION_UPDATE_DISPLAY_NAME) != 0) {
                    Component displayName = entry.displayName();
                    buf.writeBoolean(displayName != null);
                    if (displayName != null) {
                        ComponentNbt.write(buf, displayName);
                    }
                }
                if ((actions & ACTION_UPDATE_LIST_ORDER) != 0) {
                    ProtocolUtils.writeVarInt(buf, 0);
                }
                if ((actions & ACTION_UPDATE_HAT) != 0) {
                    buf.writeBoolean(true);
                }
            }
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return false;
        }

        @Override
        public int expectedSize() {

            return 64 + entries.size() * 512;
        }
    }

    public static final class PlayerInfoRemove implements Packet {

        private List<UUID> uuids = List.of();

        public PlayerInfoRemove() {
        }

        public PlayerInfoRemove(List<UUID> uuids) {
            this.uuids = uuids;
        }

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            throw new UnsupportedOperationException("player_info_remove is written, never read");
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            ProtocolUtils.writeVarInt(buf, uuids.size());
            for (UUID uuid : uuids) {
                ProtocolUtils.writeUuid(buf, uuid);
            }
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return false;
        }

        @Override
        public int expectedSize() {
            return 8 + uuids.size() * 16;
        }
    }

    public static final class HeaderFooter implements Packet {

        private Component header = Component.empty();
        private Component footer = Component.empty();

        public HeaderFooter() {
        }

        public HeaderFooter(Component header, Component footer) {
            this.header = header;
            this.footer = footer;
        }

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {

            buf.skipBytes(buf.readableBytes());
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            ComponentNbt.write(buf, header);
            ComponentNbt.write(buf, footer);
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return 512;
        }
    }
}
