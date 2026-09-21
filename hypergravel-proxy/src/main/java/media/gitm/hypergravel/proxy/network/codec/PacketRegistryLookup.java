package media.gitm.hypergravel.proxy.network.codec;

import media.gitm.hypergravel.proxy.protocol.Packet;
import media.gitm.hypergravel.proxy.protocol.PacketType;
import media.gitm.hypergravel.proxy.protocol.ProtocolState;
import media.gitm.hypergravel.proxy.protocol.packet.CommandPackets;
import media.gitm.hypergravel.proxy.protocol.packet.ConfigPackets;
import media.gitm.hypergravel.proxy.protocol.packet.HandshakePacket;
import media.gitm.hypergravel.proxy.protocol.packet.LoginPackets;
import media.gitm.hypergravel.proxy.protocol.packet.PlayPackets;
import media.gitm.hypergravel.proxy.protocol.packet.SharedPackets;
import media.gitm.hypergravel.proxy.protocol.packet.StatusPackets;

final class PacketRegistryLookup {

    private PacketRegistryLookup() {
    }

    static PacketType typeOf(Packet packet, ProtocolState state) {
        return switch (packet) {
            case HandshakePacket ignored -> PacketType.HANDSHAKE;

            case StatusPackets.Request ignored -> PacketType.STATUS_REQUEST;
            case StatusPackets.Response ignored -> PacketType.STATUS_RESPONSE;
            case StatusPackets.Ping ignored -> PacketType.STATUS_PING;

            case LoginPackets.Start ignored -> PacketType.LOGIN_START;
            case LoginPackets.EncryptionRequest ignored -> PacketType.LOGIN_ENCRYPTION_REQUEST;
            case LoginPackets.EncryptionResponse ignored -> PacketType.LOGIN_ENCRYPTION_RESPONSE;
            case LoginPackets.Success ignored -> PacketType.LOGIN_SUCCESS;
            case LoginPackets.SetCompression ignored -> PacketType.LOGIN_SET_COMPRESSION;
            case LoginPackets.PluginRequest ignored -> PacketType.LOGIN_PLUGIN_REQUEST;
            case LoginPackets.PluginResponse ignored -> PacketType.LOGIN_PLUGIN_RESPONSE;
            case LoginPackets.Acknowledged ignored -> PacketType.LOGIN_ACKNOWLEDGED;

            case SharedPackets.KeepAlive ignored -> PacketType.KEEP_ALIVE;
            case SharedPackets.PluginMessage ignored -> PacketType.PLUGIN_MESSAGE;
            case SharedPackets.Disconnect ignored ->
                    state == ProtocolState.LOGIN ? PacketType.LOGIN_DISCONNECT : PacketType.DISCONNECT;

            case ConfigPackets.FinishConfiguration ignored -> PacketType.FINISH_CONFIGURATION;
            case ConfigPackets.AckFinishConfiguration ignored -> PacketType.ACK_FINISH_CONFIGURATION;

            case PlayPackets.JoinGame ignored -> PacketType.JOIN_GAME;
            case PlayPackets.StartConfiguration ignored -> PacketType.START_CONFIGURATION;
            case PlayPackets.Transfer ignored -> PacketType.TRANSFER;
            case PlayPackets.ChatCommand ignored -> PacketType.CHAT_COMMAND;
            case PlayPackets.ChatMessage ignored -> PacketType.CHAT_MESSAGE;

            case CommandPackets.Commands ignored -> PacketType.COMMANDS;
            case CommandPackets.SuggestionRequest ignored -> PacketType.COMMAND_SUGGESTION;
            case CommandPackets.Suggestions ignored -> PacketType.COMMAND_SUGGESTIONS;

            case PlayPackets.SystemChat ignored -> PacketType.SYSTEM_CHAT;

            case pdx.dev.hypergravel.tab.TabPackets.PlayerInfoUpdate ignored ->
                    PacketType.PLAYER_INFO_UPDATE;
            case pdx.dev.hypergravel.tab.TabPackets.PlayerInfoRemove ignored ->
                    PacketType.PLAYER_INFO_REMOVE;
            case pdx.dev.hypergravel.tab.TabPackets.HeaderFooter ignored -> PacketType.TAB_LIST;
            case pdx.dev.hypergravel.pack.ResourcePackPackets.Pop ignored ->
                    PacketType.RESOURCE_PACK_POP;
            case pdx.dev.hypergravel.pack.ResourcePackPackets.Push ignored ->
                    PacketType.RESOURCE_PACK_PUSH;
            case pdx.dev.hypergravel.pack.ResourcePackPackets.Response ignored ->
                    PacketType.RESOURCE_PACK_RESPONSE;

            default -> null;
        };
    }
}
