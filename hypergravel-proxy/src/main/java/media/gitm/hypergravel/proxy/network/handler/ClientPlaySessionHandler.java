package media.gitm.hypergravel.proxy.network.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import io.netty.buffer.ByteBuf;
import media.gitm.hypergravel.api.event.CommandExecuteEvent;
import media.gitm.hypergravel.api.event.PluginMessageEvent;
import media.gitm.hypergravel.api.permission.Tristate;
import media.gitm.hypergravel.api.player.Player;
import media.gitm.hypergravel.api.server.ServerInfo;
import media.gitm.hypergravel.proxy.HyperGravelProxy;
import media.gitm.hypergravel.proxy.backend.BackendConnection;
import media.gitm.hypergravel.proxy.backend.HyperGravelServer;
import media.gitm.hypergravel.proxy.config.HyperGravelConfig;
import media.gitm.hypergravel.proxy.network.MinecraftConnection;
import media.gitm.hypergravel.proxy.network.SessionHandler;
import media.gitm.hypergravel.proxy.player.ConnectedPlayer;
import media.gitm.hypergravel.proxy.protocol.Packet;
import media.gitm.hypergravel.proxy.protocol.ProtocolState;
import media.gitm.hypergravel.proxy.protocol.packet.ConfigPackets;
import media.gitm.hypergravel.proxy.protocol.packet.PlayPackets;
import media.gitm.hypergravel.proxy.protocol.packet.SharedPackets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class ClientPlaySessionHandler implements SessionHandler {

    private final HyperGravelProxy proxy;
    private final ConnectedPlayer player;

    public ClientPlaySessionHandler(HyperGravelProxy proxy, ConnectedPlayer player) {
        this.proxy = proxy;
        this.player = player;
    }

    @Override
    public void forwardRaw(ByteBuf frame) {

        if (player.connection().state() == media.gitm.hypergravel.proxy.protocol.ProtocolState.CONFIGURATION) {
            media.gitm.hypergravel.proxy.network.ClientSettings.remember(player, frame);
            BackendConnection switching = player.pendingSwitchBackend();
            if (switching != null && switching.connection().active()) {
                switching.connection().channel().writeAndFlush(
                        frame, switching.connection().channel().voidPromise());
                return;
            }
        }
        BackendConnection backend = player.backendConnection();
        if (backend == null || !backend.connection().active()) {

            frame.release();
            return;
        }

        backend.connection().channel().write(frame,
                backend.connection().channel().voidPromise());
    }

    @Override
    public void readComplete() {
        BackendConnection backend = player.backendConnection();
        if (backend != null && backend.connection().active()) {
            backend.connection().channel().flush();
        }
    }

    @Override
    public void forward(Packet packet) {
        BackendConnection backend = player.backendConnection();
        if (backend != null && backend.connection().active()) {
            backend.connection().writeAndFlush(packet);
        }
    }

    @Override
    public boolean handle(pdx.dev.hypergravel.pack.ResourcePackPackets.Response packet) {

        var pack = proxy.resourcePack();
        return pack != null && pack.handleResponse(player, packet);
    }

    @Override
    public boolean handle(SharedPackets.KeepAlive packet) {

        player.acknowledgeKeepAlive(packet.id());
        return true;
    }

    




    @Override
    public boolean handle(PlayPackets.ChatMessage packet) {
        try {
            proxy.networkChat().relay(player, packet.message());
        } catch (RuntimeException e) {
            
            org.apache.logging.log4j.LogManager.getLogger(ClientPlaySessionHandler.class)
                    .warn("chat relay: {}", e.toString());
        }
        return false;
    }

    @Override
    public boolean handle(PlayPackets.ChatCommand packet) {
        return dispatch(packet.command());
    }

    @Override
    public boolean handle(PlayPackets.ChatCommandSigned packet) {
        
        
        
        return dispatch(packet.command());
    }

    private boolean dispatch(String line) {
        HyperGravelConfig.Commands cfg = proxy.config().commands();
        if (!cfg.enabled()) {
            return interceptCommand(line);
        }
        int sp = line.indexOf(' ');
        String name = (sp < 0 ? line : line.substring(0, sp)).toLowerCase(Locale.ROOT);
        String args = sp < 0 ? "" : line.substring(sp + 1).trim();
        switch (name) {
            case "server" -> { if (cfg.server()) { cmdServer(args); return true; } }
            case "leave", "hub", "lobby" -> { if (cfg.leave()) { cmdHub(cfg.hub()); return true; } }
            case "glist" -> { if (cfg.glist()) { cmdGlist(); return true; } }
            case "find" -> { if (cfg.find()) { cmdFind(args); return true; } }
            case "ping" -> { if (cfg.ping()) { cmdPing(); return true; } }
            case "send" -> { if (cfg.send()) { cmdSend(args); return true; } }
            case "rspack", "resourcepack", "pack" -> { cmdPack(); return true; }
            case "epack", "editpack" -> { cmdEditPack(); return true; }
            default -> { }
        }
        return interceptCommand(line);
    }

    private void cmdServer(String args) {
        if (args.isEmpty()) {
            msg("<gray>Servers: <white>" + serverNames() + "<gray>. You are on <white>"
                    + player.currentHyperGravelServer().map(HyperGravelServer::name).orElse("?"));
            return;
        }
        String target = args.split("\\s+")[0].toLowerCase(Locale.ROOT);
        Optional<HyperGravelServer> server = proxy.serverRegistry().get(target);
        if (server.isEmpty()) {
            msg("<red>Unknown server '" + target + "'. <gray>Try: <white>" + serverNames());
        } else if (onServer(server.get())) {
            msg("<yellow>You are already connected to " + server.get().name() + ".");
        } else if (!reachableByCommand(server.get())) {
            
            msg("<red>No");
        } else {
            msg("<gray>Sending you to <white>" + server.get().name() + "<gray>…");
            player.connect(server.get());
        }
    }

    private void cmdHub(String hub) {
        Optional<HyperGravelServer> server = proxy.serverRegistry().get(hub.toLowerCase(Locale.ROOT));
        if (server.isEmpty()) {
            msg("<red>The hub (" + hub + ") is not configured.");
        } else if (onServer(server.get())) {
            msg("<yellow>You are already in the hub.");
        } else {
            msg("<gray>Sending you to the hub…");
            player.connect(server.get());
        }
    }

    private void cmdGlist() {
        int total = 0;
        StringBuilder sb = new StringBuilder("<gray>Network players:");
        for (HyperGravelServer server : proxy.serverRegistry().all()) {
            List<String> names = server.connectedPlayers().stream()
                    .map(ConnectedPlayer::username).sorted().collect(Collectors.toList());
            total += names.size();
            sb.append("\n <white>").append(server.name()).append(" <gray>(").append(names.size())
                    .append("): <yellow>").append(names.isEmpty() ? "-" : String.join(", ", names));
        }
        sb.append("\n<gray>Total online: <white>").append(total);
        msg(sb.toString());
    }

    private void cmdFind(String args) {
        if (args.isEmpty()) {
            msg("<red>Usage: /find <player>");
            return;
        }
        String who = args.split("\\s+")[0];
        for (HyperGravelServer server : proxy.serverRegistry().all()) {
            for (ConnectedPlayer other : server.connectedPlayers()) {
                if (other.username().equalsIgnoreCase(who)) {
                    msg("<white>" + other.username() + " <gray>is on <white>" + server.name());
                    return;
                }
            }
        }
        msg("<red>" + who + " is not online.");
    }

    






    private void cmdPack() {
        var pack = proxy.resourcePack();
        if (pack == null || !pack.configured()) {
            msg("<gray>There is no resource pack on this network.");
            return;
        }
        msg("<gray>Sending the prompt again - answer it in the world, not on the loading screen.");
        pack.offerNow(player);
    }

    






    private void cmdEditPack() {
        var editor = proxy.packEditor();
        if (editor == null) {
            msg("<gray>This proxy does not build the pack, so there is nothing to edit.");
            return;
        }
        if (!editor.mayEdit(player.username())) {
            msg("<gray>You are not on the pack editor list.");
            return;
        }
        var session = editor.mint(player.uuid(), player.username());
        if (session == null) {
            msg("<gray>Could not open a session.");
            return;
        }
        String base = proxy.config().ops().packEditorUrl();
        String url = base.isBlank()
                ? "https://example.com/epack/" + session.token()
                : base + session.token();
        msg("<white>Pack editor open. <gray>The link is yours, it dies in two hours.");
        player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<click:open_url:'" + url + "'><hover:show_text:'<gray>Opens the live editor'>"
                + "<gradient:#86d8ff:#9aa4ff><bold>Click here to edit the pack</bold></gradient></hover></click>"));
        msg("<dark_gray>" + url);
    }

    private void cmdPing() {
        long ping = player.pingMillis();
        msg(ping < 0 ? "<gray>Ping: <white>measuring…"
                : "<gray>Your ping: <white>" + ping + "ms");
    }

    private void cmdSend(String args) {
        String[] parts = args.split("\\s+");
        if (parts.length < 2) {
            msg("<red>Usage: /send <player|all> <server>");
            return;
        }
        Optional<HyperGravelServer> dest = proxy.serverRegistry().get(parts[1].toLowerCase(Locale.ROOT));
        if (dest.isEmpty()) {
            msg("<red>Unknown server '" + parts[1] + "'.");
            return;
        }
        if (parts[0].equalsIgnoreCase("all")) {
            int moved = 0;
            for (HyperGravelServer server : proxy.serverRegistry().all()) {
                for (ConnectedPlayer other : new ArrayList<>(server.connectedPlayers())) {
                    if (!other.currentHyperGravelServer()
                            .map(s -> s.name().equals(dest.get().name())).orElse(false)) {
                        other.connect(dest.get());
                        moved++;
                    }
                }
            }
            msg("<gray>Sent <white>" + moved + " <gray>player(s) to <white>" + dest.get().name());
            return;
        }
        for (HyperGravelServer server : proxy.serverRegistry().all()) {
            for (ConnectedPlayer other : server.connectedPlayers()) {
                if (other.username().equalsIgnoreCase(parts[0])) {
                    other.connect(dest.get());
                    msg("<gray>Sent <white>" + other.username()
                            + " <gray>to <white>" + dest.get().name());
                    return;
                }
            }
        }
        msg("<red>Player '" + parts[0] + "' is not online.");
    }

    private boolean onServer(HyperGravelServer server) {
        return player.currentHyperGravelServer().map(s -> s.name().equals(server.name())).orElse(false);
    }

    private void msg(String miniMessage) {
        player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<gray>[</gray><gradient:#5e4fa2:#f79459>HyperGravel</gradient><gray>]</gray> " + miniMessage));
    }

    private String serverNames() {
        return proxy.serverRegistry().all().stream()
                .filter(this::reachableByCommand)
                .map(HyperGravelServer::name).collect(Collectors.joining(", "));
    }

    






    private boolean reachableByCommand(HyperGravelServer server) {
        ServerInfo info = server.info();
        if (player.permissionValue("hypergravel.server.bypass") == Tristate.TRUE) {
            return true;
        }
        return !info.restricted();
    }

    @Override
    public boolean handle(media.gitm.hypergravel.proxy.protocol.packet.CommandPackets.SuggestionRequest packet) {

        String text = packet.text();
        String prefix = "/server ";
        if (!text.startsWith(prefix)) {
            return false;
        }
        String arg = text.substring(prefix.length());
        if (arg.contains(" ")) {
            return false;
        }
        String lower = arg.toLowerCase(Locale.ROOT);
        List<String> matches = proxy.serverRegistry().all().stream()
                .filter(this::reachableByCommand)
                .map(HyperGravelServer::name)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted()
                .collect(Collectors.toList());
        player.connection().writeAndFlush(
                new media.gitm.hypergravel.proxy.protocol.packet.CommandPackets.Suggestions(
                        packet.transactionId(), prefix.length(), arg.length(), matches));
        return true;
    }

    @Override
    public boolean handle(ConfigPackets.AckFinishConfiguration packet) {

        BackendConnection target = player.pendingSwitchBackend();
        CompletableFuture<Player.ConnectionResult> result = player.pendingSwitchResult();
        if (target == null || result == null) {
            return true;
        }
        player.connection().setState(ProtocolState.CONFIGURATION);
        player.connection().setHandler(new ClientConfigSessionHandler(proxy, player));
        player.beginBackendConfig(target, result);
        player.clearPendingSwitch();
        if (target.connection().active()) {
            target.connection().channel().config().setAutoRead(true);
        }
        return true;
    }

    @Override
    public boolean handle(SharedPackets.PluginMessage packet) {
        String channel = packet.channel();

        if (media.gitm.hypergravel.proxy.network.BungeeCordChannel.isBungeeChannel(channel)) {
            packet.release();
            return true;
        }

        var voice = proxy.voice();
        if (voice != null && pdx.dev.hypergravel.voice.VoiceService.isRequestChannel(channel)) {
            voice.onRequest(player, packet.copyData());
            return false;
        }
        if (!proxy.channels().isRegistered(channel)) {
            return false;
        }
        PluginMessageEvent event = new PluginMessageEvent(
                player, player.backendConnection(), channel, packet.copyData());
        packet.release();
        proxy.eventManager().fireAndForget(event);
        return true;
    }

    private boolean interceptCommand(String commandLine) {
        CommandExecuteEvent event = new CommandExecuteEvent(player, commandLine);
        proxy.eventManager().fire(event).whenComplete((result, error) -> {
            if (error != null || !player.connection().active()) {
                return;
            }
            player.connection().channel().eventLoop().execute(() -> applyCommandResult(result));
        });

        return true;
    }

    private void applyCommandResult(CommandExecuteEvent event) {
        CommandExecuteEvent.Result result = event.result();

        if (!result.isAllowed()) {
            result.denyReason().ifPresent(player::sendMessage);
            return;
        }

        String line = result.rewritten().orElse(event.commandLine());

        if (result.forwardsToBackend()) {
            sendCommandToBackend(line);
            return;
        }

        proxy.commandManager().execute(player, line).whenComplete((handled, error) -> {
            if (error != null) {
                player.sendMessage(Component.text("That command failed. It has been logged."));
                return;
            }
            if (!handled) {
                sendCommandToBackend(line);
            }
        });
    }

    private void sendCommandToBackend(String commandLine) {
        BackendConnection backend = player.backendConnection();
        if (backend == null || !backend.connection().active()) {
            return;
        }

        PlayPackets.ChatCommand rewritten = new PlayPackets.ChatCommand();
        rewritten.setCommand(commandLine);
        backend.connection().writeAndFlush(rewritten);
    }

    @Override
    public void writabilityChanged(boolean writable) {

        BackendConnection backend = player.backendConnection();
        if (backend != null && backend.connection().active()) {
            backend.connection().channel().config().setAutoRead(writable);
        }
    }

    @Override
    public void disconnected() {
        proxy.disconnect(player);
    }

    MinecraftConnection connection() {
        return player.connection();
    }
}
