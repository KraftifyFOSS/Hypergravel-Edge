package pdx.dev.hypergravel.proxy.network.handler;

import pdx.dev.hypergravel.proxy.HyperGravelProxy;
import pdx.dev.hypergravel.proxy.network.MinecraftConnection;
import pdx.dev.hypergravel.proxy.network.SessionHandler;
import pdx.dev.hypergravel.proxy.protocol.ProtocolState;
import pdx.dev.hypergravel.proxy.protocol.ProtocolVersion;
import pdx.dev.hypergravel.proxy.protocol.packet.HandshakePacket;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class HandshakeSessionHandler implements SessionHandler {

    private final HyperGravelProxy proxy;
    private final MinecraftConnection connection;

    public HandshakeSessionHandler(HyperGravelProxy proxy, MinecraftConnection connection) {
        this.proxy = proxy;
        this.connection = connection;
    }

    @Override
    public boolean handle(HandshakePacket packet) {
        ProtocolVersion version = ProtocolVersion.fromId(packet.protocolVersion());
        connection.setVersion(version);

        switch (packet.intent()) {
            case HandshakePacket.INTENT_STATUS -> {

                connection.setState(ProtocolState.STATUS);
                connection.setHandler(new StatusSessionHandler(
                        proxy, connection, packet.cleanAddress(), packet.protocolVersion()));
            }
            case HandshakePacket.INTENT_LOGIN, HandshakePacket.INTENT_TRANSFER -> {
                if (!version.nativelySupported()) {
                    rejectVersion(packet.protocolVersion());
                    return true;
                }
                connection.setState(ProtocolState.LOGIN);
                connection.setHandler(new LoginSessionHandler(proxy, connection, packet));
            }
            default -> {

                connection.close();
            }
        }
        return true;
    }

    private void rejectVersion(int protocol) {
        String message;
        if (protocol < ProtocolVersion.MINIMUM_NATIVE.protocol()) {

            String floor = pdx.dev.hypergravel.via.ViaSupport.oldestSupportedName();
            if (floor == null) {
                floor = ProtocolVersion.MINIMUM_NATIVE.versionName();
            }
            message = "<red>You're on <white>" + ProtocolVersion.describe(protocol)
                    + "<red>.\n<gray>This server needs <white>" + floor + "<gray> or newer.";
        } else {
            message = "<red>You're on <white>" + ProtocolVersion.describe(protocol)
                    + "<red>, which we don't support yet.\n<gray>Try <white>"
                    + ProtocolVersion.MAXIMUM_NATIVE.versionName() + "<gray>.";
        }
        Component component = MiniMessage.miniMessage().deserialize(message);
        LoginSessionHandler.disconnectDuringLogin(connection, component);
    }

    @Override
    public void disconnected() {

    }
}
