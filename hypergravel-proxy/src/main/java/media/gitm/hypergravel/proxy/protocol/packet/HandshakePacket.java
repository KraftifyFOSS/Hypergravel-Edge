package media.gitm.hypergravel.proxy.protocol.packet;

import io.netty.buffer.ByteBuf;
import media.gitm.hypergravel.proxy.protocol.Packet;
import media.gitm.hypergravel.proxy.protocol.PacketHandler;
import media.gitm.hypergravel.proxy.protocol.ProtocolUtils;
import media.gitm.hypergravel.proxy.protocol.ProtocolVersion;

public final class HandshakePacket implements Packet {

    public static final int MAX_ADDRESS_LENGTH = 4096;

    public static final int INTENT_STATUS = 1;
    public static final int INTENT_LOGIN = 2;

    public static final int INTENT_TRANSFER = 3;

    private int protocolVersion;
    private String serverAddress = "";
    private int port;
    private int intent;

    @Override
    public void decode(ByteBuf buf, ProtocolVersion version) {
        this.protocolVersion = ProtocolUtils.readVarInt(buf);
        this.serverAddress = ProtocolUtils.readString(buf, MAX_ADDRESS_LENGTH);
        this.port = buf.readUnsignedShort();
        this.intent = ProtocolUtils.readVarInt(buf);
    }

    @Override
    public void encode(ByteBuf buf, ProtocolVersion version) {
        ProtocolUtils.writeVarInt(buf, protocolVersion);
        ProtocolUtils.writeString(buf, serverAddress);
        buf.writeShort(port);
        ProtocolUtils.writeVarInt(buf, intent);
    }

    @Override
    public boolean handle(PacketHandler handler) {
        return handler.handle(this);
    }

    @Override
    public int expectedSize() {
        return 8 + serverAddress.length();
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String serverAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public String cleanAddress() {
        int nul = serverAddress.indexOf('\0');
        return nul < 0 ? serverAddress : serverAddress.substring(0, nul);
    }

    public boolean modded() {
        return serverAddress.contains("\0FML");
    }

    public int port() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int intent() {
        return intent;
    }

    public void setIntent(int intent) {
        this.intent = intent;
    }

    public boolean isLogin() {
        return intent == INTENT_LOGIN || intent == INTENT_TRANSFER;
    }

    @Override
    public String toString() {
        return "Handshake{protocol=" + protocolVersion + ", host=" + cleanAddress()
                + ":" + port + ", intent=" + intent + '}';
    }
}
