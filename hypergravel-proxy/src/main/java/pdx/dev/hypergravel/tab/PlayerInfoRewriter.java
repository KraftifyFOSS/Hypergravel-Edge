package pdx.dev.hypergravel.tab;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import pdx.dev.hypergravel.proxy.protocol.ProtocolUtils;
import pdx.dev.hypergravel.proxy.protocol.packet.GameProfile;

public final class PlayerInfoRewriter {

    private static final int MAX_ENTRIES = 2048;
    private static final int MAX_STRING = 1 << 20;
    private static final int MAX_BLOB = 1 << 20;
    private static final int MAX_NBT_DEPTH = 32;

    public interface Properties {
        GameProfile.Property[] forPlayer(UUID uuid);
    }

    private PlayerInfoRewriter() {
    }

    public static ByteBuf rewriteAdd(ByteBuf frame, int updateId, Properties replacements,
                                     ByteBufAllocator allocator) {
        int start = frame.readerIndex();
        int end = frame.writerIndex();
        List<ParsedEntry> entries;
        int actions;
        try {
            if (ProtocolUtils.readVarInt(frame) != updateId) {
                return null;
            }
            actions = frame.readUnsignedByte();
            int count = ProtocolUtils.readVarInt(frame);
            if (count < 0 || count > MAX_ENTRIES) {
                return null;
            }
            entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(readEntry(frame, actions, replacements));
            }
            if (frame.readerIndex() != end) {
                return null;
            }
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            return null;
        } finally {
            frame.readerIndex(start);
        }

        ByteBuf out = allocator.ioBuffer(end - start + 64);
        try {
            ProtocolUtils.writeVarInt(out, updateId);
            out.writeByte(actions);
            ProtocolUtils.writeVarInt(out, entries.size());
            for (ParsedEntry entry : entries) {
                entry.writeTo(out, frame);
            }
            return out;
        } catch (Throwable t) {
            out.release();
            return null;
        }
    }

    private static ParsedEntry readEntry(ByteBuf frame, int actions, Properties replacements) {
        ParsedEntry entry = new ParsedEntry(actions, ProtocolUtils.readUuid(frame));
        if ((actions & TabPackets.ACTION_ADD_PLAYER) != 0) {
            entry.name = ProtocolUtils.readString(frame, 256);
            entry.properties = GameProfile.readProperties(frame);
            GameProfile.Property[] fresh = replacements.forPlayer(entry.uuid);
            if (fresh != null && fresh.length > 0) {
                entry.properties = fresh;
            }
        }
        if ((actions & TabPackets.ACTION_INITIALISE_CHAT) != 0) {
            entry.hasSession = frame.readBoolean();
            if (entry.hasSession) {
                entry.sessionId = ProtocolUtils.readUuid(frame);
                entry.sessionExpiry = frame.readLong();
                entry.sessionKey = ProtocolUtils.readByteArray(frame, 512);
                entry.sessionSignature = ProtocolUtils.readByteArray(frame, 4096);
            }
        }
        if ((actions & TabPackets.ACTION_UPDATE_GAME_MODE) != 0) {
            entry.gamemode = ProtocolUtils.readVarInt(frame);
        }
        if ((actions & TabPackets.ACTION_UPDATE_LISTED) != 0) {
            entry.listed = frame.readBoolean();
        }
        if ((actions & TabPackets.ACTION_UPDATE_LATENCY) != 0) {
            entry.latency = ProtocolUtils.readVarInt(frame);
        }
        if ((actions & TabPackets.ACTION_UPDATE_DISPLAY_NAME) != 0) {
            entry.displayed = frame.readBoolean();
            entry.hasDisplay = true;
            if (entry.displayed) {
                entry.displayStart = frame.readerIndex();
                skipNbtRoot(frame);
                entry.displayEnd = frame.readerIndex();
            }
        }
        if ((actions & TabPackets.ACTION_UPDATE_LIST_ORDER) != 0) {
            entry.listOrder = ProtocolUtils.readVarInt(frame);
        }
        if ((actions & TabPackets.ACTION_UPDATE_HAT) != 0) {
            entry.hat = frame.readBoolean();
            entry.hasHat = true;
        }
        return entry;
    }

    private static void skipNbtRoot(ByteBuf frame) {
        if (frame.readUnsignedByte() != 10) {
            throw new IllegalArgumentException("display is not a compound");
        }
        skipNbtCompoundBody(frame, 0);
    }

    private static void skipNbtPayload(ByteBuf frame, int tag, int depth) {
        switch (tag) {
            case 1, 2, 3, 4, 5, 6 -> frame.skipBytes(new int[]{0, 1, 2, 4, 8, 4, 8}[tag]);
            case 7 -> frame.skipBytes(checkedLength(frame, ProtocolUtils.readVarInt(frame)));
            case 8 -> readNbtString(frame);
            case 9 -> {
                int element = frame.readUnsignedByte();
                int length = frame.readInt();
                if (length < 0 || (element == 0 && length != 0)) {
                    throw new IllegalArgumentException("bad nbt list");
                }
                for (int i = 0; i < length; i++) {
                    if (element == 10) {
                        skipNbtCompoundBody(frame, depth + 1);
                    } else {
                        skipNbtPayload(frame, element, depth + 1);
                    }
                }
            }
            case 10 -> skipNbtCompoundBody(frame, depth + 1);
            case 11, 12 -> {
                int length = frame.readInt();
                frame.skipBytes(checkedLength(frame, length * (tag == 11 ? 4 : 8)));
            }
            default -> throw new IllegalArgumentException("bad nbt tag " + tag);
        }
    }

    private static void skipNbtCompoundBody(ByteBuf frame, int depth) {
        if (depth > MAX_NBT_DEPTH) {
            throw new IllegalArgumentException("nbt too deep");
        }
        while (true) {
            int tag = frame.readUnsignedByte();
            if (tag == 0) {
                return;
            }
            readNbtString(frame);
            skipNbtPayload(frame, tag, depth);
        }
    }

    private static int checkedLength(ByteBuf frame, int length) {
        if (length < 0 || length > MAX_BLOB || length > frame.readableBytes()) {
            throw new IllegalArgumentException("bad blob length " + length);
        }
        return length;
    }

    private static String readNbtString(ByteBuf frame) {
        int length = frame.readUnsignedShort();
        if (length > MAX_BLOB || length > frame.readableBytes()) {
            throw new IllegalArgumentException("bad nbt string length " + length);
        }
        String value = frame.toString(frame.readerIndex(), length,
                java.nio.charset.StandardCharsets.UTF_8);
        frame.skipBytes(length);
        return value;
    }

    private static final class ParsedEntry {
        private final int actions;
        private final UUID uuid;
        private String name;
        private GameProfile.Property[] properties;
        private boolean hasSession;
        private UUID sessionId;
        private long sessionExpiry;
        private byte[] sessionKey;
        private byte[] sessionSignature;
        private int gamemode;
        private boolean listed;
        private int latency;
        private boolean hasDisplay;
        private boolean displayed;
        private int displayStart;
        private int displayEnd;
        private int listOrder;
        private boolean hasHat;
        private boolean hat;

        private ParsedEntry(int actions, UUID uuid) {
            this.actions = actions;
            this.uuid = uuid;
        }

        private void writeTo(ByteBuf out, ByteBuf source) {
            ProtocolUtils.writeUuid(out, uuid);
            if ((actions & TabPackets.ACTION_ADD_PLAYER) != 0) {
                ProtocolUtils.writeString(out, name);
                GameProfile.writeProperties(out, properties);
            }
            if ((actions & TabPackets.ACTION_INITIALISE_CHAT) != 0) {
                out.writeBoolean(hasSession);
                if (hasSession) {
                    ProtocolUtils.writeUuid(out, sessionId);
                    out.writeLong(sessionExpiry);
                    ProtocolUtils.writeByteArray(out, sessionKey);
                    ProtocolUtils.writeByteArray(out, sessionSignature);
                }
            }
            if ((actions & TabPackets.ACTION_UPDATE_GAME_MODE) != 0) {
                ProtocolUtils.writeVarInt(out, gamemode);
            }
            if ((actions & TabPackets.ACTION_UPDATE_LISTED) != 0) {
                out.writeBoolean(listed);
            }
            if ((actions & TabPackets.ACTION_UPDATE_LATENCY) != 0) {
                ProtocolUtils.writeVarInt(out, latency);
            }
            if (hasDisplay) {
                out.writeBoolean(displayed);
                if (displayed) {
                    out.writeBytes(source, displayStart, displayEnd - displayStart);
                }
            }
            if ((actions & TabPackets.ACTION_UPDATE_LIST_ORDER) != 0) {
                ProtocolUtils.writeVarInt(out, listOrder);
            }
            if (hasHat) {
                out.writeBoolean(hat);
            }
        }
    }
}
