package media.gitm.hypergravel.proxy.protocol;

import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import media.gitm.hypergravel.proxy.protocol.packet.CommandPackets;
import media.gitm.hypergravel.proxy.protocol.packet.ConfigPackets;
import media.gitm.hypergravel.proxy.protocol.packet.HandshakePacket;
import media.gitm.hypergravel.proxy.protocol.packet.LoginPackets;
import media.gitm.hypergravel.proxy.protocol.packet.PlayPackets;
import media.gitm.hypergravel.proxy.protocol.packet.SharedPackets;
import media.gitm.hypergravel.proxy.protocol.packet.StatusPackets;

public enum PacketType {

    HANDSHAKE("handshake", HandshakePacket::new),

    STATUS_REQUEST("status_request", StatusPackets.Request::new),
    STATUS_RESPONSE("status_response", StatusPackets.Response::new),
    STATUS_PING("status_ping", StatusPackets.Ping::new),

    LOGIN_START("login_start", LoginPackets.Start::new),
    LOGIN_ENCRYPTION_REQUEST("login_encryption_request", LoginPackets.EncryptionRequest::new),
    LOGIN_ENCRYPTION_RESPONSE("login_encryption_response", LoginPackets.EncryptionResponse::new),
    LOGIN_SUCCESS("login_success", LoginPackets.Success::new),
    LOGIN_SET_COMPRESSION("login_set_compression", LoginPackets.SetCompression::new),
    LOGIN_PLUGIN_REQUEST("login_plugin_request", LoginPackets.PluginRequest::new),
    LOGIN_PLUGIN_RESPONSE("login_plugin_response", LoginPackets.PluginResponse::new),
    LOGIN_ACKNOWLEDGED("login_acknowledged", LoginPackets.Acknowledged::new),
    LOGIN_DISCONNECT("login_disconnect", SharedPackets.Disconnect::new),

    KEEP_ALIVE("keep_alive", SharedPackets.KeepAlive::new),
    PLUGIN_MESSAGE("plugin_message", SharedPackets.PluginMessage::new),
    DISCONNECT("disconnect", SharedPackets.Disconnect::new),

    FINISH_CONFIGURATION("finish_configuration", ConfigPackets.FinishConfiguration::new),
    ACK_FINISH_CONFIGURATION("ack_finish_configuration", ConfigPackets.AckFinishConfiguration::new),

    JOIN_GAME("join_game", PlayPackets.JoinGame::new),
    START_CONFIGURATION("start_configuration", PlayPackets.StartConfiguration::new),
    TRANSFER("transfer", PlayPackets.Transfer::new),
    CHAT_COMMAND("chat_command", PlayPackets.ChatCommand::new),
    CHAT_COMMAND_SIGNED("chat_command_signed", PlayPackets.ChatCommandSigned::new),
    CHAT_MESSAGE("chat_message", PlayPackets.ChatMessage::new),
    COMMANDS("commands", CommandPackets.Commands::new),
    COMMAND_SUGGESTION("command_suggestion", CommandPackets.SuggestionRequest::new),
    COMMAND_SUGGESTIONS("command_suggestions", CommandPackets.Suggestions::new),
    SYSTEM_CHAT("system_chat", PlayPackets.SystemChat::new),

    PLAYER_INFO_UPDATE("player_info_update", pdx.dev.hypergravel.tab.TabPackets.PlayerInfoUpdate::new),
    PLAYER_INFO_REMOVE("player_info_remove", pdx.dev.hypergravel.tab.TabPackets.PlayerInfoRemove::new),
    TAB_LIST("tab_list", pdx.dev.hypergravel.tab.TabPackets.HeaderFooter::new),
    RESOURCE_PACK_POP("resource_pack_pop",
            pdx.dev.hypergravel.pack.ResourcePackPackets.Pop::new),
    RESOURCE_PACK_PUSH("resource_pack_push",
            pdx.dev.hypergravel.pack.ResourcePackPackets.Push::new),
    RESOURCE_PACK_RESPONSE("resource_pack_response",
            pdx.dev.hypergravel.pack.ResourcePackPackets.Response::new);

    private static final Map<String, PacketType> BY_KEY = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(PacketType::key, t -> t));

    private final String key;
    private final Supplier<Packet> factory;

    PacketType(String key, Supplier<Packet> factory) {
        this.key = key;
        this.factory = factory;
    }

    public String key() {
        return key;
    }

    public Packet create() {
        return factory.get();
    }

    public static PacketType byKey(String key) {
        return BY_KEY.get(key);
    }
}
