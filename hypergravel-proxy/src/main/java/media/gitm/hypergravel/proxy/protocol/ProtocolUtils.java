package media.gitm.hypergravel.proxy.protocol;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.util.ByteProcessor;

public final class ProtocolUtils {

    public static final int MAX_VARINT_BYTES = 5;

    public static final int DEFAULT_MAX_STRING_LENGTH = 32767;

    private ProtocolUtils() {
    }

    public static int readVarInt(ByteBuf buf) {
        int result = readVarIntSafely(buf);
        if (result == Integer.MIN_VALUE) {
            throw new CorruptedFrameException("malformed varint");
        }
        return result;
    }

    public static int readVarIntSafely(ByteBuf buf) {
        int value = 0;
        for (int shift = 0; shift < MAX_VARINT_BYTES * 7; shift += 7) {
            if (!buf.isReadable()) {
                return Integer.MIN_VALUE;
            }
            byte read = buf.readByte();
            value |= (read & 0x7F) << shift;
            if ((read & 0x80) == 0) {
                return value;
            }
        }
        return Integer.MIN_VALUE;
    }

    public static void writeVarInt(ByteBuf buf, int value) {

        if ((value & (0xFFFFFFFF << 7)) == 0) {
            buf.writeByte(value);
        } else if ((value & (0xFFFFFFFF << 14)) == 0) {
            buf.writeShort((value & 0x7F | 0x80) << 8 | (value >>> 7));
        } else {
            writeVarIntFull(buf, value);
        }
    }

    private static void writeVarIntFull(ByteBuf buf, int value) {
        while (true) {
            if ((value & ~0x7F) == 0) {
                buf.writeByte(value);
                return;
            }
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    public static int varIntBytes(int value) {
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            return 1;
        } else if ((value & (0xFFFFFFFF << 14)) == 0) {
            return 2;
        } else if ((value & (0xFFFFFFFF << 21)) == 0) {
            return 3;
        } else if ((value & (0xFFFFFFFF << 28)) == 0) {
            return 4;
        }
        return 5;
    }

    public static long readVarLong(ByteBuf buf) {
        long value = 0;
        for (int shift = 0; shift < 70; shift += 7) {
            byte read = buf.readByte();
            value |= (long) (read & 0x7F) << shift;
            if ((read & 0x80) == 0) {
                return value;
            }
        }
        throw new CorruptedFrameException("malformed varlong");
    }

    public static void writeVarLong(ByteBuf buf, long value) {
        while (true) {
            if ((value & ~0x7FL) == 0) {
                buf.writeByte((int) value);
                return;
            }
            buf.writeByte((int) (value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    public static String readString(ByteBuf buf) {
        return readString(buf, DEFAULT_MAX_STRING_LENGTH);
    }

    public static String readString(ByteBuf buf, int cap) {
        int length = readVarInt(buf);
        if (length < 0) {
            throw new CorruptedFrameException("string length is negative: " + length);
        }
        if (length > cap * 3) {
            throw new CorruptedFrameException(
                    "string byte length " + length + " exceeds maximum " + (cap * 3));
        }
        if (!buf.isReadable(length)) {
            throw new CorruptedFrameException(
                    "string of " + length + " bytes but only " + buf.readableBytes() + " readable");
        }
        String value = buf.toString(buf.readerIndex(), length, StandardCharsets.UTF_8);
        buf.skipBytes(length);
        if (value.length() > cap) {
            throw new CorruptedFrameException(
                    "string of " + value.length() + " chars exceeds maximum " + cap);
        }
        return value;
    }

    public static void writeString(ByteBuf buf, CharSequence value) {

        int byteLength = ByteBufUtil.utf8Bytes(value);
        writeVarInt(buf, byteLength);
        buf.writeCharSequence(value, StandardCharsets.UTF_8);
    }

    public static byte[] readByteArray(ByteBuf buf, int cap) {
        int length = readVarInt(buf);
        checkArrayLength(buf, length, cap);
        byte[] array = new byte[length];
        buf.readBytes(array);
        return array;
    }

    public static void writeByteArray(ByteBuf buf, byte[] array) {
        writeVarInt(buf, array.length);
        buf.writeBytes(array);
    }

    public static byte[] readRemaining(ByteBuf buf, int cap) {
        int length = buf.readableBytes();
        if (length > cap) {
            throw new CorruptedFrameException(
                    "payload of " + length + " bytes exceeds maximum " + cap);
        }
        byte[] array = new byte[length];
        buf.readBytes(array);
        return array;
    }

    private static void checkArrayLength(ByteBuf buf, int length, int cap) {
        if (length < 0) {
            throw new CorruptedFrameException("array length is negative: " + length);
        }
        if (length > cap) {
            throw new CorruptedFrameException(
                    "array length " + length + " exceeds maximum " + cap);
        }
        if (!buf.isReadable(length)) {
            throw new CorruptedFrameException(
                    "array of " + length + " bytes but only " + buf.readableBytes() + " readable");
        }
    }

    public static UUID readUuid(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    public static void writeUuid(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    public static UUID readUuidIntArray(ByteBuf buf) {
        long msbHigh = (long) buf.readInt() << 32;
        long msbLow = buf.readInt() & 0xFFFFFFFFL;
        long lsbHigh = (long) buf.readInt() << 32;
        long lsbLow = buf.readInt() & 0xFFFFFFFFL;
        return new UUID(msbHigh | msbLow, lsbHigh | lsbLow);
    }

    public static void writeVarIntArray(ByteBuf buf, int[] values) {
        writeVarInt(buf, values.length);
        for (int value : values) {
            writeVarInt(buf, value);
        }
    }

    public static int indexOf(ByteBuf buf, byte target) {
        return buf.forEachByte(new ByteProcessor.IndexOfProcessor(target));
    }
}
