package media.gitm.hypergravel.proxy.network.codec;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import media.gitm.hypergravel.proxy.protocol.Packet;
import media.gitm.hypergravel.proxy.protocol.PacketDirection;
import media.gitm.hypergravel.proxy.protocol.PacketType;
import media.gitm.hypergravel.proxy.protocol.ProtocolState;
import media.gitm.hypergravel.proxy.protocol.ProtocolUtils;
import media.gitm.hypergravel.proxy.protocol.ProtocolVersion;
import media.gitm.hypergravel.proxy.protocol.StateRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MinecraftDecoder extends MessageToMessageDecoder<ByteBuf> {

    private static final Logger LOGGER = LogManager.getLogger(MinecraftDecoder.class);

    private final PacketDirection direction;
    private ProtocolVersion version;
    private ProtocolState state;
    private StateRegistry.VersionTable table;

    public MinecraftDecoder(PacketDirection direction, ProtocolState state, ProtocolVersion version) {
        this.direction = direction;
        this.version = version;
        setState(state);
    }

    public void setState(ProtocolState state) {
        this.state = state;
        this.table = StateRegistry.of(state).direction(direction).forVersion(version);
    }

    public void setVersion(ProtocolVersion version) {
        this.version = version;
        setState(state);
    }

    public ProtocolState state() {
        return state;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
        if (!msg.isReadable()) {
            return;
        }

        int startIndex = msg.readerIndex();
        int packetId = ProtocolUtils.readVarInt(msg);
        PacketType type = table.lookup(packetId);

        if (type == null) {

            msg.readerIndex(startIndex);
            out.add(msg.retain());
            return;
        }

        Packet packet = type.create();

        if (packet instanceof media.gitm.hypergravel.proxy.protocol.packet.SharedPackets.Disconnect d
                && state != media.gitm.hypergravel.proxy.protocol.ProtocolState.LOGIN) {
            d.setNbtEncoded(true);
        }
        try {
            packet.decode(msg, version);
        } catch (Exception e) {

            LOGGER.debug("failed to decode {} (id 0x{}) in {}/{} at {}; forwarding raw",
                    type, Integer.toHexString(packetId), state, direction, version, e);
            msg.readerIndex(startIndex);
            out.add(msg.retain());
            return;
        }

        if (msg.isReadable() && !toleratesTrailingBytes(type)) {
            LOGGER.debug("{} left {} unread bytes in {}/{} at {}; forwarding raw",
                    type, msg.readableBytes(), state, direction, version);
            release(packet);
            msg.readerIndex(startIndex);
            out.add(msg.retain());
            return;
        }

        out.add(packet);
    }

    private static boolean toleratesTrailingBytes(PacketType type) {
        return switch (type) {
            case TAB_LIST, CHAT_COMMAND, CHAT_MESSAGE, JOIN_GAME, PLUGIN_MESSAGE, DISCONNECT,
                 LOGIN_DISCONNECT, LOGIN_PLUGIN_REQUEST, LOGIN_PLUGIN_RESPONSE,
                 LOGIN_START -> true;
            default -> false;
        };
    }

    private static void release(Packet packet) {
        if (packet instanceof media.gitm.hypergravel.proxy.protocol.packet.SharedPackets.PluginMessage pm) {
            pm.release();
        } else if (packet instanceof media.gitm.hypergravel.proxy.protocol.packet.PlayPackets.JoinGame join) {
            join.release();
        }
    }
}
