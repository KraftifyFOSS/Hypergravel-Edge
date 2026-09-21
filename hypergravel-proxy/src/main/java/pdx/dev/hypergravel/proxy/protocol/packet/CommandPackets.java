package pdx.dev.hypergravel.proxy.protocol.packet;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import pdx.dev.hypergravel.proxy.protocol.Packet;
import pdx.dev.hypergravel.proxy.protocol.PacketHandler;
import pdx.dev.hypergravel.proxy.protocol.ProtocolUtils;
import pdx.dev.hypergravel.proxy.protocol.ProtocolVersion;

public final class CommandPackets {

    private CommandPackets() {
    }

    public static final class Commands implements Packet {

        private byte[] graph = new byte[0];

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            this.graph = new byte[buf.readableBytes()];
            buf.readBytes(this.graph);
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            buf.writeBytes(graph);
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        @Override
        public int expectedSize() {
            return graph.length + 64;
        }

        public byte[] graph() {
            return graph;
        }

        public void setGraph(byte[] graph) {
            this.graph = graph;
        }

        private static final String[] LITERALS =
                {"server", "leave", "hub", "lobby", "glist", "find", "ping", "send", "rspack", "epack"};

        public static byte[] injectServerCommand(byte[] graph) {
            ByteBuf in = Unpooled.wrappedBuffer(graph);
            try {
                int nodeCount = ProtocolUtils.readVarInt(in);
                int flags = in.readUnsignedByte();
                if ((flags & 0x03) != 0) {
                    return graph;
                }
                int childCount = ProtocolUtils.readVarInt(in);
                int[] children = new int[childCount];
                for (int i = 0; i < childCount; i++) {
                    children[i] = ProtocolUtils.readVarInt(in);
                }
                if ((flags & 0x08) != 0) {
                    return graph;
                }
                int rootEnd = in.readerIndex();
                if ((graph[graph.length - 1] & 0xFF) != 0) {
                    return graph;
                }

                int base = nodeCount;
                int argNode = base + LITERALS.length;
                ByteBuf out = Unpooled.buffer(graph.length + 128);
                ProtocolUtils.writeVarInt(out, nodeCount + LITERALS.length + 1);

                out.writeByte(flags);
                ProtocolUtils.writeVarInt(out, childCount + LITERALS.length);
                for (int child : children) {
                    ProtocolUtils.writeVarInt(out, child);
                }
                for (int i = 0; i < LITERALS.length; i++) {
                    ProtocolUtils.writeVarInt(out, base + i);
                }

                out.writeBytes(graph, rootEnd, (graph.length - 1) - rootEnd);

                for (String literal : LITERALS) {
                    if (literal.equals("server")) {
                        out.writeByte(0x05);
                        ProtocolUtils.writeVarInt(out, 1);
                        ProtocolUtils.writeVarInt(out, argNode);
                        ProtocolUtils.writeString(out, literal);
                    } else {
                        out.writeByte(0x05);
                        ProtocolUtils.writeVarInt(out, 0);
                        ProtocolUtils.writeString(out, literal);
                    }
                }

                out.writeByte(0x16);
                ProtocolUtils.writeVarInt(out, 0);
                ProtocolUtils.writeString(out, "name");
                ProtocolUtils.writeVarInt(out, 5);
                ProtocolUtils.writeVarInt(out, 0);
                ProtocolUtils.writeString(out, "minecraft:ask_server");
                ProtocolUtils.writeVarInt(out, 0);
                byte[] result = new byte[out.readableBytes()];
                out.readBytes(result);
                out.release();
                return result;
            } catch (RuntimeException e) {
                return graph;
            }
        }
    }

    public static final class SuggestionRequest implements Packet {

        private int transactionId;
        private String text = "";

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            this.transactionId = ProtocolUtils.readVarInt(buf);
            this.text = ProtocolUtils.readString(buf, 32500);
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            ProtocolUtils.writeVarInt(buf, transactionId);
            ProtocolUtils.writeString(buf, text);
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return handler.handle(this);
        }

        public int transactionId() {
            return transactionId;
        }

        public String text() {
            return text;
        }
    }

    public static final class Suggestions implements Packet {

        private final int transactionId;
        private final int start;
        private final int length;
        private final List<String> matches;

        public Suggestions() {
            this(0, 0, 0, List.of());
        }

        public Suggestions(int transactionId, int start, int length, List<String> matches) {
            this.transactionId = transactionId;
            this.start = start;
            this.length = length;
            this.matches = matches;
        }

        @Override
        public void decode(ByteBuf buf, ProtocolVersion version) {
            throw new UnsupportedOperationException("clientbound only");
        }

        @Override
        public void encode(ByteBuf buf, ProtocolVersion version) {
            ProtocolUtils.writeVarInt(buf, transactionId);
            ProtocolUtils.writeVarInt(buf, start);
            ProtocolUtils.writeVarInt(buf, length);
            ProtocolUtils.writeVarInt(buf, matches.size());
            for (String match : matches) {
                ProtocolUtils.writeString(buf, match);
                buf.writeBoolean(false);
            }
        }

        @Override
        public boolean handle(PacketHandler handler) {
            return false;
        }

        @Override
        public int expectedSize() {
            return 16 + matches.size() * 24;
        }
    }
}
