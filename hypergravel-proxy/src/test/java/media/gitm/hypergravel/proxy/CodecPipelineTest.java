package media.gitm.hypergravel.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import media.gitm.hypergravel.proxy.network.codec.MinecraftDecoder;
import media.gitm.hypergravel.proxy.network.codec.MinecraftEncoder;
import media.gitm.hypergravel.proxy.network.codec.VarIntFrameDecoder;
import media.gitm.hypergravel.proxy.network.codec.VarIntLengthEncoder;
import media.gitm.hypergravel.proxy.protocol.PacketDirection;
import media.gitm.hypergravel.proxy.protocol.ProtocolState;
import media.gitm.hypergravel.proxy.protocol.ProtocolUtils;
import media.gitm.hypergravel.proxy.protocol.ProtocolVersion;
import media.gitm.hypergravel.proxy.protocol.packet.HandshakePacket;
import media.gitm.hypergravel.proxy.protocol.packet.StatusPackets;
import org.junit.jupiter.api.Test;

class CodecPipelineTest {

    private static EmbeddedChannel serverbound() {
        return new EmbeddedChannel(
                new VarIntFrameDecoder(2097152),
                new MinecraftDecoder(PacketDirection.SERVERBOUND, ProtocolState.HANDSHAKE,
                        ProtocolVersion.UNKNOWN));
    }

    private static ByteBuf framed(int packetId, byte[] body) {
        ByteBuf payload = Unpooled.buffer();
        ProtocolUtils.writeVarInt(payload, packetId);
        payload.writeBytes(body);

        ByteBuf framedBuf = Unpooled.buffer();
        ProtocolUtils.writeVarInt(framedBuf, payload.readableBytes());
        framedBuf.writeBytes(payload);
        payload.release();
        return framedBuf;
    }

    @Test
    void decodesAHandshake() {
        EmbeddedChannel channel = serverbound();

        ByteBuf body = Unpooled.buffer();
        ProtocolUtils.writeVarInt(body, 767);
        ProtocolUtils.writeString(body, "mc.hypergravel.net");
        body.writeShort(25565);
        ProtocolUtils.writeVarInt(body, 1);
        byte[] bodyBytes = new byte[body.readableBytes()];
        body.readBytes(bodyBytes);
        body.release();

        assertTrue(channel.writeInbound(framed(0x00, bodyBytes)));

        HandshakePacket packet = channel.readInbound();
        assertNotNull(packet, "handshake did not decode");
        assertEquals(767, packet.protocolVersion());
        assertEquals("mc.hypergravel.net", packet.cleanAddress());
        assertEquals(25565, packet.port());
        assertEquals(HandshakePacket.INTENT_STATUS, packet.intent());

        channel.finishAndReleaseAll();
    }

    @Test
    void handshakeAndStatusRequestInOneReadBothDecode() {

        EmbeddedChannel channel = serverbound();
        MinecraftDecoder decoder = channel.pipeline().get(MinecraftDecoder.class);

        ByteBuf body = Unpooled.buffer();
        ProtocolUtils.writeVarInt(body, 767);
        ProtocolUtils.writeString(body, "mc.hypergravel.net");
        body.writeShort(25565);
        ProtocolUtils.writeVarInt(body, 1);
        byte[] bodyBytes = new byte[body.readableBytes()];
        body.readBytes(bodyBytes);
        body.release();

        ByteBuf combined = Unpooled.buffer();
        combined.writeBytes(framed(0x00, bodyBytes));
        combined.writeBytes(framed(0x00, new byte[0]));

        channel.writeInbound(combined);

        Object first = channel.readInbound();
        assertInstanceOf(HandshakePacket.class, first);

        decoder.setVersion(ProtocolVersion.MINECRAFT_1_21);
        decoder.setState(ProtocolState.STATUS);

        Object second = channel.readInbound();
        assertNotNull(second, "status request did not decode");

        channel.finishAndReleaseAll();
    }

    @Test
    void encodesAStatusResponseWithALengthPrefix() {
        EmbeddedChannel channel = new EmbeddedChannel(
                VarIntLengthEncoder.INSTANCE,
                new MinecraftEncoder(PacketDirection.CLIENTBOUND, ProtocolState.STATUS,
                        ProtocolVersion.MINECRAFT_1_21));

        String json = "{\"version\":{\"name\":\"HyperGravel\",\"protocol\":767}}";
        assertTrue(channel.writeOutbound(new StatusPackets.Response(json)));

        ByteBuf encoded = channel.readOutbound();
        assertNotNull(encoded, "status response produced no bytes");

        int frameLength = ProtocolUtils.readVarInt(encoded);
        assertEquals(frameLength, encoded.readableBytes(), "length prefix disagrees with body");
        assertEquals(0x00, ProtocolUtils.readVarInt(encoded), "wrong packet id");
        assertEquals(json, ProtocolUtils.readString(encoded, 262144));

        encoded.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void roundTripsAPingPayload() {
        EmbeddedChannel out = new EmbeddedChannel(
                VarIntLengthEncoder.INSTANCE,
                new MinecraftEncoder(PacketDirection.CLIENTBOUND, ProtocolState.STATUS,
                        ProtocolVersion.MINECRAFT_1_21));
        out.writeOutbound(new StatusPackets.Ping(0x0123456789ABCDEFL));
        ByteBuf wire = out.readOutbound();

        EmbeddedChannel in = new EmbeddedChannel(
                new VarIntFrameDecoder(2097152),
                new MinecraftDecoder(PacketDirection.SERVERBOUND, ProtocolState.STATUS,
                        ProtocolVersion.MINECRAFT_1_21));
        in.writeInbound(wire);

        StatusPackets.Ping decoded = in.readInbound();
        assertNotNull(decoded, "ping did not decode");
        assertEquals(0x0123456789ABCDEFL, decoded.payload());

        out.finishAndReleaseAll();
        in.finishAndReleaseAll();
    }

    @Test
    void unmappedPacketsPassThroughAsRawBuffers() {

        EmbeddedChannel channel = new EmbeddedChannel(
                new VarIntFrameDecoder(2097152),
                new MinecraftDecoder(PacketDirection.CLIENTBOUND, ProtocolState.PLAY,
                        ProtocolVersion.MINECRAFT_1_21));

        byte[] payload = { 1, 2, 3, 4, 5 };
        channel.writeInbound(framed(0x7E, payload));

        Object result = channel.readInbound();
        assertInstanceOf(ByteBuf.class, result, "an uninspected packet should stay a raw buffer");

        ByteBuf raw = (ByteBuf) result;
        assertEquals(0x7E, ProtocolUtils.readVarInt(raw));
        assertEquals(payload.length, raw.readableBytes());

        raw.release();
        channel.finishAndReleaseAll();
    }
}
