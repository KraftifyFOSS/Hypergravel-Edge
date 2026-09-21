package media.gitm.hypergravel.proxy.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import media.gitm.hypergravel.api.server.ServerPing;
import media.gitm.hypergravel.proxy.config.HyperGravelConfig;
import media.gitm.hypergravel.proxy.protocol.ProtocolVersion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class StatusCache {

    private static final Logger LOGGER = LogManager.getLogger(StatusCache.class);

    private static final long REBUILD_DEBOUNCE_MILLIS = 1000;

    private final AtomicReference<String> cachedJson = new AtomicReference<>("{}");
    private volatile HyperGravelConfig config;
    private volatile String faviconBase64;
    private volatile String motdSource = "";
    private volatile int lastRenderedCount = -1;
    private volatile String lastSignature = "";
    private volatile long lastRenderedAt;

    public record Activity(int online, int playing) {}

    private volatile java.util.function.Supplier<Activity> activity = () -> new Activity(0, 0);

    public void setActivitySource(java.util.function.Supplier<Activity> source) {
        this.activity = source;
    }

    public StatusCache(HyperGravelConfig config, Path configDirectory) {
        reload(config, configDirectory);
    }

    public void reload(HyperGravelConfig config, Path configDirectory) {
        this.config = config;
        this.motdSource = config.status().motd();
        this.faviconBase64 = loadFavicon(configDirectory.resolve(config.status().faviconPath()));
        this.lastRenderedCount = -1;
        this.lastSignature = "";
        rebuild(0);
    }

    public String json(int playerCount) {

        Activity now = activity.get();
        String signature = playerCount + "/" + now.playing();
        if (!signature.equals(lastSignature)
                && System.currentTimeMillis() - lastRenderedAt >= REBUILD_DEBOUNCE_MILLIS) {
            lastSignature = signature;
            rebuild(playerCount);
        }
        return cachedJson.get();
    }

    private String fill(String template, int playerCount) {
        Activity now = activity.get();
        return template
                .replace("%in-game%", String.valueOf(now.playing()))
                .replace("%online%", String.valueOf(playerCount));
    }

    private void rebuild(int playerCount) {
        HyperGravelConfig current = config;
        JsonObject root = new JsonObject();

        JsonObject version = new JsonObject();
        version.addProperty("name", current.status().versionName());

        version.addProperty("protocol", ProtocolVersion.MAXIMUM_NATIVE.protocol());
        root.add("version", version);

        JsonObject players = new JsonObject();
        players.addProperty("online",
                current.status().showRealPlayerCount() ? playerCount : 0);
        players.addProperty("max", current.status().maxPlayers());

        JsonArray sample = new JsonArray();
        for (String line : current.status().sample()) {
            JsonObject entry = new JsonObject();

            entry.addProperty("name", net.kyori.adventure.text.serializer.legacy
                    .LegacyComponentSerializer.legacySection()
                    .serialize(MiniMessage.miniMessage().deserialize(line)));
            entry.addProperty("id", UUID.nameUUIDFromBytes(
                    line.getBytes(StandardCharsets.UTF_8)).toString());
            sample.add(entry);
        }
        if (!sample.isEmpty()) {
            players.add("sample", sample);
        }
        root.add("players", players);

        Component rendered = MiniMessage.miniMessage()
                .deserialize(fill(motdSource, playerCount));
        root.add("description", GsonComponentSerializer.gson().serializeToTree(rendered));

        String favicon = faviconBase64;
        if (favicon != null) {
            root.addProperty("favicon", favicon);
        }
        root.addProperty("enforcesSecureChat", current.status().enforceSecureChat());

        cachedJson.set(root.toString());
        lastRenderedCount = playerCount;
        lastRenderedAt = System.currentTimeMillis();
    }

    public ServerPing toServerPing(int playerCount) {
        HyperGravelConfig current = config;
        List<ServerPing.SamplePlayer> sample = current.status().sample().stream()
                .map(line -> new ServerPing.SamplePlayer(line,
                        UUID.nameUUIDFromBytes(line.getBytes(StandardCharsets.UTF_8))))
                .toList();
        return new ServerPing(
                new ServerPing.Version(ProtocolVersion.MAXIMUM_NATIVE.protocol(),
                        current.status().versionName()),
                new ServerPing.Players(
                        current.status().showRealPlayerCount() ? playerCount : 0,
                        current.status().maxPlayers(), sample),
                MiniMessage.miniMessage().deserialize(fill(motdSource, playerCount)),
                faviconBase64,
                current.status().enforceSecureChat());
    }

    public static String serialize(ServerPing ping) {
        JsonObject root = new JsonObject();

        JsonObject version = new JsonObject();
        version.addProperty("name", ping.version().name());
        version.addProperty("protocol", ping.version().protocol());
        root.add("version", version);

        JsonObject players = new JsonObject();
        players.addProperty("online", ping.players().online());
        players.addProperty("max", ping.players().max());
        JsonArray sample = new JsonArray();
        for (ServerPing.SamplePlayer entry : ping.players().sample()) {
            JsonObject object = new JsonObject();
            object.addProperty("name", entry.name());
            object.addProperty("id", entry.id().toString());
            sample.add(object);
        }
        if (!sample.isEmpty()) {
            players.add("sample", sample);
        }
        root.add("players", players);

        root.add("description", GsonComponentSerializer.gson()
                .serializeToTree(ping.description()));
        if (ping.faviconBase64() != null) {
            root.addProperty("favicon", ping.faviconBase64());
        }
        root.addProperty("enforcesSecureChat", ping.enforcesSecureChat());
        return root.toString();
    }

    private static String loadFavicon(Path path) {
        if (!Files.isReadable(path)) {
            LOGGER.info("no favicon at {}; server list will show the default icon", path);
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            if (!isPng(bytes)) {
                LOGGER.warn("favicon at {} is not a PNG; ignoring it", path);
                return null;
            }
            int[] dimensions = pngDimensions(bytes);
            if (dimensions[0] != 64 || dimensions[1] != 64) {

                LOGGER.warn("favicon at {} is {}x{}; Minecraft requires exactly 64x64, ignoring it",
                        path, dimensions[0], dimensions[1]);
                return null;
            }
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            LOGGER.warn("cannot read favicon at {}: {}", path, e.toString());
            return null;
        }
    }

    private static boolean isPng(byte[] bytes) {
        return bytes.length > 24
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
    }

    private static int[] pngDimensions(byte[] bytes) {

        int width = ((bytes[16] & 0xFF) << 24) | ((bytes[17] & 0xFF) << 16)
                | ((bytes[18] & 0xFF) << 8) | (bytes[19] & 0xFF);
        int height = ((bytes[20] & 0xFF) << 24) | ((bytes[21] & 0xFF) << 16)
                | ((bytes[22] & 0xFF) << 8) | (bytes[23] & 0xFF);
        return new int[] { width, height };
    }
}
