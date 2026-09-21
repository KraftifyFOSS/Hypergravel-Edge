package pdx.dev.hypergravel.tab;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import media.gitm.hypergravel.proxy.protocol.packet.GameProfile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class HeadTextures {

    private static final Logger LOGGER = LogManager.getLogger(HeadTextures.class);

    private static final String UPLOAD_ENDPOINT = "https://api.mineskin.org/generate/upload";

    private static final Duration BETWEEN_UPLOADS = Duration.ofSeconds(9);
    private static final int ATTEMPTS_PER_ICON = 3;

    private final Path cacheFile;
    private final Map<HeadIcon, GameProfile.Property[]> textures = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private volatile boolean stopped;

    HeadTextures(Path cacheFile) {
        this.cacheFile = cacheFile;
        load();
    }

    GameProfile.Property[] propertiesFor(HeadIcon icon) {
        GameProfile.Property[] cached = textures.get(icon);
        return cached != null ? cached : GameProfile.NO_PROPERTIES;
    }

    boolean has(HeadIcon icon) {
        return textures.containsKey(icon);
    }

    private static final Duration RETRY_ROUND = Duration.ofMinutes(5);

    void warmUp() {
        Thread worker = new Thread(() -> {
            while (!stopped) {
                renderMissing();
                if (complete()) {
                    return;
                }
                sleep(RETRY_ROUND);
            }
        }, "hypergravel-tab-heads");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean complete() {
        for (HeadIcon icon : HeadIcon.values()) {
            if (!textures.containsKey(icon)) {
                return false;
            }
        }
        return true;
    }

    void stop() {
        stopped = true;
    }

    private void renderMissing() {

        System.setProperty("java.awt.headless", "true");

        int rendered = 0;
        for (HeadIcon icon : HeadIcon.values()) {
            if (stopped) {
                return;
            }
            if (textures.containsKey(icon)) {
                continue;
            }
            if (rendered > 0) {
                sleep(BETWEEN_UPLOADS);
            }
            if (upload(icon)) {
                rendered++;
                save();
            }
        }
        if (rendered > 0) {
            LOGGER.info("tab: {} head icon(s) rendered and cached", rendered);
        }
        if (!complete() && !stopped) {
            LOGGER.warn("tab: {} head icon(s) still have no art; trying again in {} minutes",
                    HeadIcon.values().length - textures.size(), RETRY_ROUND.toMinutes());
        }
    }

    private boolean upload(HeadIcon icon) {
        byte[] png;
        try {
            png = IconRenderer.render(icon);
        } catch (Throwable t) {
            LOGGER.warn("tab: cannot draw the {} head icon", icon, t);
            return false;
        }

        for (int attempt = 1; attempt <= ATTEMPTS_PER_ICON && !stopped; attempt++) {
            try {
                GameProfile.Property property = post(icon, png);
                if (property != null) {
                    textures.put(icon, new GameProfile.Property[] {property});
                    return true;
                }
            } catch (Exception e) {
                LOGGER.debug("tab: head upload for {} failed on attempt {}", icon, attempt, e);
            }
            sleep(BETWEEN_UPLOADS.multipliedBy(attempt));
        }
        LOGGER.debug("tab: no texture for the {} head icon this round", icon);
        return false;
    }

    private GameProfile.Property post(HeadIcon icon, byte[] png) throws IOException, InterruptedException {
        String boundary = "hypergravel" + Long.toHexString(System.nanoTime());
        byte[] body = multipart(boundary, icon, png);

        HttpRequest request = HttpRequest.newBuilder(URI.create(UPLOAD_ENDPOINT))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("User-Agent", "HyperGravel/TabHeads")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            LOGGER.debug("tab: MineSkin answered {} for {}", response.statusCode(), icon);
            return null;
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject texture = root.getAsJsonObject("data").getAsJsonObject("texture");
        String value = texture.get("value").getAsString();
        String signature = texture.has("signature") ? texture.get("signature").getAsString() : null;
        return new GameProfile.Property("textures", value, signature);
    }

    private static byte[] multipart(String boundary, HeadIcon icon, byte[] png) throws IOException {
        var out = new java.io.ByteArrayOutputStream(png.length + 512);
        out.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + icon + ".png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(png);
        out.write(("\r\n--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"name\"\r\n\r\n"
                + "hypergravel_" + icon.name().toLowerCase(java.util.Locale.ROOT) + "\r\n"
                + "--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private void load() {
        if (!Files.isReadable(cacheFile)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(cacheFile)).getAsJsonObject();
            for (HeadIcon icon : HeadIcon.values()) {
                if (!root.has(icon.name())) {
                    continue;
                }
                JsonObject entry = root.getAsJsonObject(icon.name());
                String value = entry.get("value").getAsString();
                String signature = entry.has("signature") && !entry.get("signature").isJsonNull()
                        ? entry.get("signature").getAsString()
                        : null;
                textures.put(icon, new GameProfile.Property[] {
                        new GameProfile.Property("textures", value, signature)});
            }
            LOGGER.info("tab: {} head icon(s) loaded from cache", textures.size());
        } catch (Exception e) {
            LOGGER.warn("tab: head cache at {} is unreadable, rendering again", cacheFile, e);
        }
    }

    private synchronized void save() {
        JsonObject root = new JsonObject();
        Map<HeadIcon, GameProfile.Property[]> snapshot = new EnumMap<>(textures);
        for (Map.Entry<HeadIcon, GameProfile.Property[]> entry : snapshot.entrySet()) {
            GameProfile.Property property = entry.getValue()[0];
            JsonObject value = new JsonObject();
            value.addProperty("value", property.value());
            if (property.signature() != null) {
                value.addProperty("signature", property.signature());
            }
            root.add(entry.getKey().name(), value);
        }
        try {
            Files.createDirectories(cacheFile.getParent());
            Files.writeString(cacheFile, root.toString());
        } catch (IOException e) {
            LOGGER.warn("tab: cannot write the head cache to {}", cacheFile, e);
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopped = true;
        }
    }
}
