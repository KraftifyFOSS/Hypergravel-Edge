package pdx.dev.hypergravel.tab;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import net.kyori.adventure.text.Component;
import pdx.dev.hypergravel.proxy.protocol.ComponentNbt;
import pdx.dev.hypergravel.proxy.protocol.ProtocolUtils;
import pdx.dev.hypergravel.proxy.protocol.packet.GameProfile;
import org.junit.jupiter.api.Test;

class PlayerInfoRewriterTest {

    private static final int UPDATE_ID = 70;

    private static final UUID KNOWN = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    private static final GameProfile.Property[] BACKEND_PROPS = {
            new GameProfile.Property("textures", "backend-value", "backend-sig")};
    private static final GameProfile.Property[] FRESH_PROPS = {
            new GameProfile.Property("textures", "mojang-value", "mojang-sig")};

    private final Map<UUID, GameProfile.Property[]> known = Map.of(KNOWN, FRESH_PROPS);

    @Test
    void swapsKnownPropertiesAndKeepsEverythingElse() {
        ByteBuf frame = fullAddFrame();
        try {
            ByteBuf rewritten = PlayerInfoRewriter.rewriteAdd(frame, UPDATE_ID,
                    known::get, UnpooledByteBufAllocator.DEFAULT);
            assertNotNull(rewritten);
            try {
                assertEquals(UPDATE_ID, ProtocolUtils.readVarInt(rewritten));
                assertEquals(0xFF, rewritten.readUnsignedByte());
                assertEquals(2, ProtocolUtils.readVarInt(rewritten));
                GameProfile.Property[] first = readEntryProperties(rewritten);
                assertArrayEquals(FRESH_PROPS, first);
                GameProfile.Property[] second = readEntryProperties(rewritten);
                assertArrayEquals(BACKEND_PROPS, second);
            } finally {
                rewritten.release();
            }
        } finally {
            frame.release();
        }
    }

    @Test
    void rewriteIsIdempotent() {
        ByteBuf frame = fullAddFrame();
        ByteBuf once;
        try {
            once = PlayerInfoRewriter.rewriteAdd(frame, UPDATE_ID,
                    known::get, UnpooledByteBufAllocator.DEFAULT);
            assertNotNull(once);
        } finally {
            frame.release();
        }
        try {
            ByteBuf twice = PlayerInfoRewriter.rewriteAdd(once, UPDATE_ID,
                    known::get, UnpooledByteBufAllocator.DEFAULT);
            assertNotNull(twice);
            try {
                assertEquals(once.readableBytes(), twice.readableBytes());
                byte[] a = new byte[once.readableBytes()];
                byte[] b = new byte[twice.readableBytes()];
                once.getBytes(once.readerIndex(), a);
                twice.getBytes(twice.readerIndex(), b);
                assertArrayEquals(a, b);
            } finally {
                twice.release();
            }
        } finally {
            once.release();
        }
    }

    @Test
    void latencyFramesPassThroughUntouched() {
        ByteBuf frame = Unpooled.buffer();
        ProtocolUtils.writeVarInt(frame, UPDATE_ID);
        frame.writeByte(TabPackets.ACTION_UPDATE_LATENCY);
        ProtocolUtils.writeVarInt(frame, 1);
        ProtocolUtils.writeUuid(frame, STRANGER);
        ProtocolUtils.writeVarInt(frame, 42);
        try {
            ByteBuf rewritten = PlayerInfoRewriter.rewriteAdd(frame, UPDATE_ID,
                    known::get, UnpooledByteBufAllocator.DEFAULT);
            assertNotNull(rewritten);
            try {
                assertEquals(frame.readableBytes(), rewritten.readableBytes());
            } finally {
                rewritten.release();
            }
        } finally {
            frame.release();
        }
    }

    @Test
    void wrongIdTruncatedAndTrailingBytesYieldNull() {
        ByteBuf other = Unpooled.buffer();
        ProtocolUtils.writeVarInt(other, 5);
        other.writeByte(0);
        try {
            assertNull(PlayerInfoRewriter.rewriteAdd(other, UPDATE_ID,
                    known::get, UnpooledByteBufAllocator.DEFAULT));
            assertEquals(0, other.readerIndex());
        } finally {
            other.release();
        }

        ByteBuf cut = fullAddFrame();
        ByteBuf truncated = cut.copy(0, cut.readableBytes() - 3);
        cut.release();
        try {
            assertNull(PlayerInfoRewriter.rewriteAdd(truncated, UPDATE_ID,
                    known::get, UnpooledByteBufAllocator.DEFAULT));
        } finally {
            truncated.release();
        }

        ByteBuf padded = fullAddFrame();
        padded.writeByte(0x7F);
        try {
            assertNull(PlayerInfoRewriter.rewriteAdd(padded, UPDATE_ID,
                    known::get, UnpooledByteBufAllocator.DEFAULT));
        } finally {
            padded.release();
        }
    }

    private static ByteBuf fullAddFrame() {
        ByteBuf frame = Unpooled.buffer();
        ProtocolUtils.writeVarInt(frame, UPDATE_ID);
        frame.writeByte(0xFF);
        ProtocolUtils.writeVarInt(frame, 2);
        writeFullEntry(frame, KNOWN);
        writeFullEntry(frame, STRANGER);
        return frame;
    }

    private static void writeFullEntry(ByteBuf frame, UUID uuid) {
        ProtocolUtils.writeUuid(frame, uuid);
        ProtocolUtils.writeString(frame, "SomeName");
        GameProfile.writeProperties(frame, BACKEND_PROPS);
        frame.writeBoolean(true);
        ProtocolUtils.writeUuid(frame, UUID.randomUUID());
        frame.writeLong(123456789L);
        ProtocolUtils.writeByteArray(frame, new byte[]{1, 2, 3});
        ProtocolUtils.writeByteArray(frame, new byte[]{4, 5});
        ProtocolUtils.writeVarInt(frame, 1);
        frame.writeBoolean(true);
        ProtocolUtils.writeVarInt(frame, 88);
        frame.writeBoolean(true);
        ByteBuf nbt = Unpooled.buffer();
        try {
            ComponentNbt.write(nbt, Component.text("hello"));
            frame.writeBytes(nbt);
        } finally {
            nbt.release();
        }
        ProtocolUtils.writeVarInt(frame, 3);
        frame.writeBoolean(true);
    }

    private static GameProfile.Property[] readEntryProperties(ByteBuf buf) {
        ProtocolUtils.readUuid(buf);
        ProtocolUtils.readString(buf);
        GameProfile.Property[] properties = GameProfile.readProperties(buf);
        if (buf.readBoolean()) {
            ProtocolUtils.readUuid(buf);
            buf.readLong();
            ProtocolUtils.readByteArray(buf, 1 << 20);
            ProtocolUtils.readByteArray(buf, 1 << 20);
        }
        ProtocolUtils.readVarInt(buf);
        buf.readBoolean();
        ProtocolUtils.readVarInt(buf);
        if (buf.readBoolean()) {
            skipTestNbt(buf);
        }
        ProtocolUtils.readVarInt(buf);
        buf.readBoolean();
        return properties;
    }

    private static void skipTestNbt(ByteBuf buf) {
        if (buf.readUnsignedByte() != 10) {
            throw new IllegalArgumentException("expected compound");
        }
        int tag;
        while ((tag = buf.readUnsignedByte()) != 0) {
            readTestNbtString(buf);
            skipTestPayload(buf, tag);
        }
    }

    private static void readTestNbtString(ByteBuf buf) {
        int length = buf.readUnsignedShort();
        if (length > buf.readableBytes()) {
            throw new IllegalArgumentException("bad nbt string");
        }
        buf.skipBytes(length);
    }

    private static void skipTestPayload(ByteBuf buf, int tag) {
        switch (tag) {
            case 1 -> buf.skipBytes(1);
            case 8 -> readTestNbtString(buf);
            case 10 -> {
                int nested;
                while ((nested = buf.readUnsignedByte()) != 0) {
                    readTestNbtString(buf);
                    skipTestPayload(buf, nested);
                }
            }
            default -> throw new IllegalArgumentException("unexpected tag " + tag);
        }
    }
}
