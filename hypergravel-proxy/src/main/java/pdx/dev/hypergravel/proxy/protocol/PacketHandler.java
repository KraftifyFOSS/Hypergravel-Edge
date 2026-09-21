package pdx.dev.hypergravel.proxy.protocol;

import io.netty.buffer.ByteBuf;
import pdx.dev.hypergravel.proxy.protocol.packet.ConfigPackets;
import pdx.dev.hypergravel.proxy.protocol.packet.HandshakePacket;
import pdx.dev.hypergravel.proxy.protocol.packet.LoginPackets;
import pdx.dev.hypergravel.proxy.protocol.packet.PlayPackets;
import pdx.dev.hypergravel.proxy.protocol.packet.SharedPackets;
import pdx.dev.hypergravel.proxy.protocol.packet.StatusPackets;

public interface PacketHandler {

    default boolean handleGeneric(ByteBuf raw) {
        return false;
    }

    default boolean handle(HandshakePacket packet) {
        return false;
    }

    default boolean handle(StatusPackets.Request packet) {
        return false;
    }

    default boolean handle(StatusPackets.Response packet) {
        return false;
    }

    default boolean handle(StatusPackets.Ping packet) {
        return false;
    }

    default boolean handle(LoginPackets.Start packet) {
        return false;
    }

    default boolean handle(LoginPackets.EncryptionRequest packet) {
        return false;
    }

    default boolean handle(LoginPackets.EncryptionResponse packet) {
        return false;
    }

    default boolean handle(LoginPackets.Success packet) {
        return false;
    }

    default boolean handle(LoginPackets.SetCompression packet) {
        return false;
    }

    default boolean handle(LoginPackets.PluginRequest packet) {
        return false;
    }

    default boolean handle(LoginPackets.PluginResponse packet) {
        return false;
    }

    default boolean handle(LoginPackets.Acknowledged packet) {
        return false;
    }

    default boolean handle(SharedPackets.KeepAlive packet) {
        return false;
    }

    default boolean handle(SharedPackets.PluginMessage packet) {
        return false;
    }

    default boolean handle(SharedPackets.Disconnect packet) {
        return false;
    }

    default boolean handle(pdx.dev.hypergravel.proxy.protocol.packet.CommandPackets.Commands packet) {
        return false;
    }

    default boolean handle(
            pdx.dev.hypergravel.proxy.protocol.packet.CommandPackets.SuggestionRequest packet) {
        return false;
    }

    default boolean handle(ConfigPackets.FinishConfiguration packet) {
        return false;
    }

    default boolean handle(ConfigPackets.AckFinishConfiguration packet) {
        return false;
    }

    default boolean handle(PlayPackets.JoinGame packet) {
        return false;
    }

    default boolean handle(PlayPackets.StartConfiguration packet) {
        return false;
    }

    default boolean handle(PlayPackets.Transfer packet) {
        return false;
    }

    default boolean handle(PlayPackets.ChatCommand packet) {
        return false;
    }
    default boolean handle(PlayPackets.ChatCommandSigned packet) {
        return false;
    }

    default boolean handle(pdx.dev.hypergravel.tab.TabPackets.HeaderFooter packet) {
        return false;
    }

    default boolean handle(PlayPackets.ChatMessage packet) {
        return false;
    }

    default boolean handle(pdx.dev.hypergravel.pack.ResourcePackPackets.Response packet) {
        return false;
    }
}
