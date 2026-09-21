package pdx.dev.hypergravel.tab;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class PlayerFaces {

    private static final Logger LOGGER = LogManager.getLogger(PlayerFaces.class);

    private static final int FIRST_CODEPOINT = 0xE100;

    private static final String UUID_LOOKUP = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String PROFILE_LOOKUP =
            "https://sessionserver.mojang.com/session/minecraft/profile/";

    private final Path cacheDirectory;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final Map<String, byte[]> faces = new LinkedHashMap<>();

    public PlayerFaces(Path cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    public void resolve(List<String> names) {
        List<String> ordered = new ArrayList<>(names.stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList());

        for (String name : ordered) {
            byte[] cached = readCache(name);
            if (cached != null) {
                faces.put(name, cached);
                continue;
            }
            byte[] fetched = fetch(name);
            if (fetched != null) {
                faces.put(name, fetched);
                writeCache(name, fetched);
            }
        }
        if (!faces.isEmpty()) {
            LOGGER.info("tab: {} player face glyph(s) ready", faces.size());
        }
    }

    public char glyphFor(String username) {
        String key = username.toLowerCase(Locale.ROOT);
        int index = 0;
        for (String name : faces.keySet()) {
            if (name.equals(key)) {
                return (char) (FIRST_CODEPOINT + index);
            }
            index++;
        }
        return 0;
    }

    public Map<Character, byte[]> glyphs() {
        Map<Character, byte[]> result = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<String, byte[]> entry : faces.entrySet()) {
            result.put((char) (FIRST_CODEPOINT + index++), entry.getValue());
        }
        return result;
    }

    public boolean isEmpty() {
        return faces.isEmpty();
    }

    private byte[] fetch(String name) {
        try {
            String uuid = json(UUID_LOOKUP + name).get("id").getAsString();
            JsonObject profile = json(PROFILE_LOOKUP + uuid);
            String value = null;
            for (var element : profile.getAsJsonArray("properties")) {
                JsonObject property = element.getAsJsonObject();
                if ("textures".equals(property.get("name").getAsString())) {
                    value = property.get("value").getAsString();
                }
            }
            if (value == null) {
                return null;
            }
            JsonObject textures = JsonParser.parseString(
                    new String(Base64.getDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("textures");
            if (!textures.has("SKIN")) {
                return null;
            }
            String url = textures.getAsJsonObject("SKIN").get("url").getAsString()
                    .replaceFirst("^http://", "https://");

            HttpResponse<byte[]> skin = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            var image = ImageIO.read(new java.io.ByteArrayInputStream(skin.body()));
            if (image == null) {
                return null;
            }

            var face = new java.awt.image.BufferedImage(8, 8,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < 8; x++) {
                for (int y = 0; y < 8; y++) {
                    int base = image.getRGB(8 + x, 8 + y);
                    int hat = image.getWidth() >= 64 ? image.getRGB(40 + x, 8 + y) : 0;
                    face.setRGB(x, y, ((hat >>> 24) > 0x40) ? hat : base);
                }
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream(512);
            ImageIO.write(face, "png", png);
            LOGGER.info("tab: fetched {}'s face", name);
            return png.toByteArray();
        } catch (Exception e) {
            LOGGER.warn("tab: could not fetch {}'s face ({}); their row keeps its badge",
                    name, e.toString());
            return null;
        }
    }

    private JsonObject json(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException(url + " answered " + response.statusCode());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private byte[] readCache(String name) {
        Path file = cacheDirectory.resolve(name + ".png");
        try {
            return Files.isReadable(file) ? Files.readAllBytes(file) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private void writeCache(String name, byte[] png) {
        try {
            Files.createDirectories(cacheDirectory);
            Files.write(cacheDirectory.resolve(name + ".png"), png);
        } catch (IOException e) {
            LOGGER.debug("tab: could not cache {}'s face", name, e);
        }
    }
}
