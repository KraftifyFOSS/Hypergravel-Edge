package pdx.dev.hypergravel.tab;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import media.gitm.hypergravel.api.server.RegisteredServer;
import media.gitm.hypergravel.proxy.HyperGravelProxy;
import media.gitm.hypergravel.proxy.backend.HyperGravelServer;
import media.gitm.hypergravel.proxy.player.ConnectedPlayer;
import media.gitm.hypergravel.proxy.protocol.PacketType;
import media.gitm.hypergravel.proxy.protocol.ProtocolState;
import media.gitm.hypergravel.proxy.protocol.packet.GameProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class TabService {

    private static final Logger LOGGER = LogManager.getLogger(TabService.class);

    private static final net.kyori.adventure.key.Key UI_FONT =
            net.kyori.adventure.key.Key.key("hypergravel", "ui");

    private static final TextColor ACCENT = TextColor.fromHexString("#ff8a3d");
    private static final TextColor SOFT = TextColor.fromHexString("#a78bfa");
    private static final TextColor MUTED = TextColor.fromHexString("#8a8fa0");
    private static final TextColor FAINT = TextColor.fromHexString("#4a4f5a");
    private static final TextColor GOOD = TextColor.fromHexString("#4ade80");

    private static final int MAX_ROWS = 80;

    private static final int MAX_LOBBY_NAMES = 24;

    private final HyperGravelProxy proxy;
    private final HeadTextures heads;
    private final Duration refresh;

    private final Map<String, HeadIcon> playerHeads = new HashMap<>();

    private final PlayerFaces faces;
    private final IconMode iconMode;

    public enum IconMode {
        HEAD, GLYPH, BOTH,

        NONE;

        static IconMode parse(String value) {
            if (value == null) {
                return BOTH;
            }
            return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "head", "heads" -> HEAD;
                case "glyph", "glyphs", "font", "text" -> GLYPH;
                case "none", "off", "minimal", "plain" -> NONE;
                default -> BOTH;
            };
        }
    }

    private Component decorate(HeadIcon icon, Component text) {
        if (iconMode == IconMode.NONE) {
            return text;
        }
        if (icon == null || icon == HeadIcon.SPACER || iconMode == IconMode.HEAD) {
            return text;
        }
        return Component.text(icon.glyph() + " ", NamedTextColor.WHITE).font(UI_FONT).append(text);
    }

    private GameProfile.Property[] propertiesFor(HeadIcon icon) {

        if (iconMode == IconMode.NONE) {
            return heads.propertiesFor(HeadIcon.SPACER);
        }
        if (icon == null) {
            return GameProfile.NO_PROPERTIES;
        }
        return heads.propertiesFor(iconMode == IconMode.GLYPH ? HeadIcon.SPACER : icon);
    }

    private final Map<UUID, Map<UUID, Row>> sentRows = new ConcurrentHashMap<>();

    private final Map<UUID, Integer> needsHide = new ConcurrentHashMap<>();

    private static final int HIDE_REPEATS = 6;

    








    private static final int SWEEP_EVERY = 1;

    private int tickCount;

    public TabService(HyperGravelProxy proxy, Path cacheFile, Duration refresh,
                      Map<String, String> configuredHeads, String iconMode, PlayerFaces faces) {
        this.proxy = proxy;
        this.heads = new HeadTextures(cacheFile);
        this.refresh = refresh;
        this.iconMode = IconMode.parse(iconMode);
        this.faces = faces;

        for (Map.Entry<String, String> entry : configuredHeads.entrySet()) {
            if ("face".equalsIgnoreCase(entry.getValue().trim())) {

                continue;
            }
            HeadIcon icon = HeadIcon.byName(entry.getValue());
            if (icon == null) {
                LOGGER.warn("tab: '{}' is not a head icon, so {} keeps their own skin. "
                        + "Known icons: {}", entry.getValue(), entry.getKey(), knownIcons());
                continue;
            }
            playerHeads.put(entry.getKey().toLowerCase(java.util.Locale.ROOT), icon);
        }
        if (!playerHeads.isEmpty()) {
            LOGGER.info("tab: {} player head override(s) configured", playerHeads.size());
        }
        LOGGER.info("tab: icons drawn as {}", this.iconMode.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static String knownIcons() {
        StringBuilder names = new StringBuilder();
        for (HeadIcon icon : HeadIcon.values()) {
            if (!names.isEmpty()) {
                names.append(", ");
            }
            names.append(icon.name().toLowerCase(java.util.Locale.ROOT));
        }
        return names.toString();
    }

    public void start() {
        heads.warmUp();
        proxy.scheduler().repeat(this, refresh, refresh, this::tick);
        LOGGER.info("tab: network list on, refreshing every {}ms", refresh.toMillis());
    }

    public void stop() {
        heads.stop();
    }

    public void onServerConnected(ConnectedPlayer player) {
        needsHide.put(player.uuid(), HIDE_REPEATS);
        
        
        
        
        
        sentRows.remove(player.uuid());

        player.currentHyperGravelServer().ifPresent(server -> {
            for (media.gitm.hypergravel.api.player.Player other : server.players()) {
                needsHide.put(other.uuid(), HIDE_REPEATS);
            }
        });
    }

    public void onDisconnect(ConnectedPlayer player) {
        sentRows.remove(player.uuid());
        needsHide.remove(player.uuid());
    }

    private void tick() {
        try {
            tickCount++;

            List<Row> rows = buildRows();
            List<UUID> online = new ArrayList<>();
            for (ConnectedPlayer player : proxy.playerRegistry().all()) {
                online.add(player.uuid());
            }

            for (ConnectedPlayer player : proxy.playerRegistry().all()) {
                if (!canReceive(player)) {
                    continue;
                }
                try {
                    push(player, rows, online);
                } catch (Exception e) {
                    LOGGER.debug("tab: could not update {}", player.username(), e);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("tab: refresh failed", e);
        }
    }

    private boolean canReceive(ConnectedPlayer player) {
        var connection = player.connection();
        
        
        return connection.active()
                && connection.state() == ProtocolState.PLAY
                && player.pendingSwitchBackend() == null
                && connection.supports(PacketType.PLAYER_INFO_UPDATE);
    }

    private void push(ConnectedPlayer player, List<Row> rows, List<UUID> online) {

        Integer remaining = needsHide.get(player.uuid());
        boolean sweep = tickCount % SWEEP_EVERY == 0;
        if (remaining != null) {
            if (remaining <= 1) {
                needsHide.remove(player.uuid());
            } else {
                needsHide.put(player.uuid(), remaining - 1);
            }
        }
        if (remaining != null || sweep) {
            List<TabPackets.Entry> hidden = new ArrayList<>(online.size());
            for (UUID uuid : online) {
                hidden.add(TabPackets.Entry.listedFlag(uuid, false));
            }
            player.connection().write(new TabPackets.PlayerInfoUpdate(
                    TabPackets.ACTION_UPDATE_LISTED, hidden));
        }

        Map<UUID, Row> previous = sentRows.computeIfAbsent(player.uuid(), key -> new HashMap<>());
        Map<UUID, Row> current = new LinkedHashMap<>();
        for (Row row : rows) {
            current.put(row.uuid(), row);
        }

        List<TabPackets.Entry> added = new ArrayList<>();
        List<TabPackets.Entry> changed = new ArrayList<>();
        for (Row row : rows) {
            Row before = previous.get(row.uuid());
            if (before == null || !before.sameIdentity(row)) {
                added.add(row.toEntry());
            } else if (!before.sameFace(row)) {
                changed.add(row.toEntry());
            }
        }

        List<UUID> removed = new ArrayList<>();
        for (UUID uuid : previous.keySet()) {
            if (!current.containsKey(uuid)) {
                removed.add(uuid);
            }
        }

        if (!removed.isEmpty()) {
            player.connection().write(new TabPackets.PlayerInfoRemove(removed));
        }
        if (!added.isEmpty()) {
            player.connection().write(new TabPackets.PlayerInfoUpdate(
                    TabPackets.ACTION_ADD_PLAYER
                            | TabPackets.ACTION_UPDATE_GAME_MODE
                            | TabPackets.ACTION_UPDATE_LISTED
                            | TabPackets.ACTION_UPDATE_LATENCY
                            | TabPackets.ACTION_UPDATE_DISPLAY_NAME,
                    added));
        }
        if (!changed.isEmpty()) {
            player.connection().write(new TabPackets.PlayerInfoUpdate(
                    TabPackets.ACTION_UPDATE_LATENCY | TabPackets.ACTION_UPDATE_DISPLAY_NAME,
                    changed));
        }

        previous.clear();
        previous.putAll(current);

        player.connection().write(new TabPackets.HeaderFooter(header(player), footer(player)));
        player.connection().flush();
    }

    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();

        String lobbyName = proxy.config().routing().fallback();
        List<ConnectedPlayer> lobby = playersOnServer(lobbyName);
        lobby.sort(Comparator.comparing(ConnectedPlayer::username, String.CASE_INSENSITIVE_ORDER));

        rows.add(section("1", HeadIcon.MARK, "LOBBY", lobby.size() + " waiting"));
        int shown = 0;
        for (ConnectedPlayer player : lobby) {
            if (shown++ == MAX_LOBBY_NAMES) {
                rows.add(new Row("2~~", HeadIcon.MARK,
                        decorate(HeadIcon.MARK,
                                Component.text("+ " + (lobby.size() - MAX_LOBBY_NAMES) + " more",
                                        MUTED)),
                        0, propertiesFor(HeadIcon.MARK)));
                break;
            }
            rows.add(playerRow(sortKey("2", player.username()), player,
                    playerHeads.get(player.username().toLowerCase(java.util.Locale.ROOT))));
        }

        String queueName = proxy.config().queue().server();
        char group = 'a';
        for (RegisteredServer server : proxy.servers()) {
            String name = server.info().name();
            if (name.equals(lobbyName) || name.equals(queueName) || server.info().limbo()) {
                continue;
            }
            List<ConnectedPlayer> on = playersOnServer(name);
            if (on.isEmpty()) {
                continue;
            }
            on.sort(Comparator.comparing(ConnectedPlayer::username, String.CASE_INSENSITIVE_ORDER));

            rows.add(Row.spacer("5" + group + "0"));
            rows.add(section("5" + group + "1", HeadIcon.MARK,
                    name.toUpperCase(java.util.Locale.ROOT), on.size() + " playing"));
            for (ConnectedPlayer player : on) {
                rows.add(playerRow(sortKey("5" + group + "2", player.username()), player,
                        playerHeads.get(player.username().toLowerCase(java.util.Locale.ROOT))));
            }
            group++;
        }

        List<String> open = openBoxes();
        if (!open.isEmpty()) {
            rows.add(Row.spacer("6"));
            rows.add(section("7", HeadIcon.OPEN, "OPEN", String.join(", ", open)));
        }

        for (Row row : rows) {
            row.attachHead(propertiesFor(row.icon()));
        }

        return rows.size() <= MAX_ROWS ? rows : rows.subList(0, MAX_ROWS);
    }

    private static String sortKey(String prefix, String username) {
        int room = MAX_PROFILE_NAME - prefix.length();
        return prefix + (username.length() <= room ? username : username.substring(0, room));
    }

    private static final int MAX_PROFILE_NAME = 16;

    private Row section(String sortKey, HeadIcon icon, String title, String detail) {
        Component text = Component.text(title, NamedTextColor.WHITE)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.text("  " + detail, MUTED)
                        .decoration(TextDecoration.BOLD, false));
        return new Row(sortKey, icon, decorate(icon, text), 0, propertiesFor(icon));
    }

    private Row playerRow(String sortKey, ConnectedPlayer player, HeadIcon badge) {
        Component name = Component.text(player.username(), NamedTextColor.WHITE);
        int ping = (int) Math.max(0, player.pingMillis());

        if (iconMode == IconMode.NONE) {
            return new Row(sortKey, null, name, ping, propertiesFor(null));
        }

        char face = faces == null ? 0 : faces.glyphFor(player.username());
        var pack = proxy.resourcePack();
        boolean canDrawFace = face != 0 && iconMode != IconMode.HEAD
                && (pack == null || pack.hasPack(player.uuid()));
        if (canDrawFace) {
            return new Row(sortKey, null,
                    Component.text(face + " ", NamedTextColor.WHITE).font(UI_FONT).append(name),
                    ping, player.profile().properties());
        }
        if (badge != null) {
            return new Row(sortKey, badge, decorate(badge, name), ping, propertiesFor(badge));
        }

        return new Row(sortKey, null, Component.text("  ").append(name), ping,
                player.profile().properties());
    }

    private List<String> openBoxes() {
        List<String> open = new ArrayList<>();
        String lobbyName = proxy.config().routing().fallback();
        String queueName = proxy.config().queue().server();
        char group = 'a';
        for (RegisteredServer server : proxy.servers()) {
            String name = server.info().name();
            if (name.equals(lobbyName) || name.equals(queueName) || server.info().limbo()) {
                continue;
            }
            if (playersOn(name) == 0) {
                open.add(name);
            }
        }
        return open;
    }

    private List<ConnectedPlayer> playersOnServer(String name) {
        List<ConnectedPlayer> found = new ArrayList<>();
        for (ConnectedPlayer player : proxy.playerRegistry().all()) {
            if (player.currentHyperGravelServer()
                    .map(server -> server.name().equals(name)).orElse(false)) {
                found.add(player);
            }
        }
        return found;
    }

    private int playersOn(String name) {
        int count = 0;
        for (ConnectedPlayer player : proxy.playerRegistry().all()) {
            if (player.currentHyperGravelServer()
                    .map(server -> server.name().equals(name)).orElse(false)) {
                count++;
            }
        }
        return count;
    }

    private Component header(ConnectedPlayer player) {
        long ping = player.pingMillis();

        return Component.text("\n  ")
                .append(Component.text("HyperGravel", NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, true))

                .append(Component.text("   " + player.currentHyperGravelServer()
                        .map(HyperGravelServer::name).orElse("connecting"), FAINT))
                .append(Component.text("\n  "))
                .append(Component.text(proxy.playerCount(), ACCENT))
                .append(Component.text(" online", MUTED))

                .append(Component.text("   -   ", FAINT))
                .append(Component.text(ping < 0 ? "--" : Long.toString(ping), pingColour(ping)))
                .append(Component.text("ms", MUTED))
                .append(Component.text("\n"));
    }

    private Component footer(ConnectedPlayer player) {
        String where = player.currentHyperGravelServer()
                .map(HyperGravelServer::name).orElse("connecting");
        return Component.text("\n  ")
                .append(Component.text("you are on ", FAINT))
                .append(Component.text(where, SOFT))
                .append(Component.text("   -   ", FAINT))
                .append(Component.text("hypergravel.network", MUTED))
                .append(Component.text("\n"));
    }

    private static TextColor pingColour(long ping) {
        if (ping < 0) {
            return MUTED;
        }
        if (ping < 60) {
            return GOOD;
        }
        return ping < 150 ? ACCENT : NamedTextColor.RED;
    }

    private static final class Row {

        private final String sortKey;
        private final UUID uuid;
        private final HeadIcon icon;
        private final Component display;
        private final int latency;
        private GameProfile.Property[] properties;

        private Row(String sortKey, HeadIcon icon, Component display, int latency,
                    GameProfile.Property[] properties) {
            this.sortKey = sortKey;
            this.uuid = UUID.nameUUIDFromBytes(("hypergravel:tab:" + sortKey)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            this.icon = icon;
            this.display = display;
            this.latency = latency;
            this.properties = properties;
        }

        static Row spacer(String sortKey) {
            return new Row(sortKey, HeadIcon.SPACER, Component.text(" "), 0,
                    GameProfile.NO_PROPERTIES);
        }

        HeadIcon icon() {
            return icon;
        }

        void attachHead(GameProfile.Property[] resolved) {
            if (icon != null && properties.length == 0) {
                properties = resolved;
            }
        }

        UUID uuid() {
            return uuid;
        }

        boolean sameIdentity(Row other) {
            return sortKey.equals(other.sortKey) && properties.length == other.properties.length;
        }

        boolean sameFace(Row other) {
            return latency == other.latency && display.equals(other.display);
        }

        TabPackets.Entry toEntry() {

            return new TabPackets.Entry(uuid, sortKey, properties, true,
                    Math.max(latency, 0), display);
        }
    }
}
