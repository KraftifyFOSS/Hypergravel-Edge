package pdx.dev.hypergravel.pack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;














public final class PackEditor {

    private static final Logger LOGGER = LogManager.getLogger(PackEditor.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    
    private static final long TOKEN_MINUTES = 120;

    public record Session(String token, UUID player, String name, Instant expires) {
        boolean live() {
            return Instant.now().isBefore(expires);
        }
    }

    private final Path packFile;
    private final Path overrides;
    private final Path secretFile;
    private final Path editorsFile;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    private String secret;
    private Set<String> editors = new LinkedHashSet<>();

    public PackEditor(Path packFile) {
        this.packFile = packFile;
        this.overrides = packFile.getParent().resolve("overrides");
        this.secretFile = packFile.getParent().resolve("editor-secret.txt");
        this.editorsFile = packFile.getParent().resolve("editors.txt");
        load();
    }

    private void load() {
        try {
            Files.createDirectories(overrides);
            if (Files.exists(secretFile)) {
                secret = Files.readString(secretFile).trim();
            }
            if (secret == null || secret.length() < 32) {
                secret = random(48);
                Files.writeString(secretFile, secret + "\n");
                LOGGER.info("pack editor: wrote a new shared secret to {}", secretFile);
            }
            if (Files.exists(editorsFile)) {
                for (String line : Files.readAllLines(editorsFile)) {
                    String name = line.trim();
                    if (!name.isEmpty() && !name.startsWith("#")) editors.add(name.toLowerCase(Locale.ROOT));
                }
            }
            if (editors.isEmpty()) {
                editors.add("pdxd3v");
                Files.writeString(editorsFile,
                        "# Who may open the live pack editor - one name per line.\npdxd3v\n");
            }
        } catch (IOException e) {
            LOGGER.error("pack editor: could not set itself up: {}", e.toString());
        }
    }

    
    public String secret() {
        return secret;
    }

    public boolean mayEdit(String playerName) {
        return editors.contains(playerName.toLowerCase(Locale.ROOT));
    }

    
    public Session mint(UUID player, String name) {
        if (!mayEdit(name)) return null;
        sessions.values().removeIf(session -> !session.live());
        Session session = new Session(random(40), player, name,
                Instant.now().plusSeconds(TOKEN_MINUTES * 60));
        sessions.put(session.token(), session);
        LOGGER.info("pack editor: {} opened a session, good for {} minutes", name, TOKEN_MINUTES);
        return session;
    }

    public Session session(String token) {
        if (token == null || token.isEmpty()) return null;
        Session session = sessions.get(token);
        if (session == null) return null;
        if (!session.live()) {
            sessions.remove(token);
            return null;
        }
        return session;
    }

    

    public record Entry(String path, int size, boolean overridden) { }

    
    public List<Entry> list() throws IOException {
        List<Entry> out = new ArrayList<>();
        if (!Files.exists(packFile)) return out;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(packFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                int size = 0;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) > 0) size += read;
                out.add(new Entry(entry.getName(), size, Files.exists(overrideOf(entry.getName()))));
            }
        }
        
        
        
        for (java.util.Map.Entry<String, Path> variant : variants().entrySet()) {
            Path dir = variant.getValue();
            try (var walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile).forEach(file -> {
                    try {
                        out.add(new Entry(variant.getKey() + "/"
                                + dir.relativize(file).toString().replace('\\', '/'),
                                (int) Files.size(file), true));
                    } catch (IOException ignored) {
                        
                    }
                });
            }
        }
        out.sort((a, b) -> a.path().compareTo(b.path()));
        return out;
    }

    







    private java.util.Map<String, Path> variants() {
        java.util.Map<String, Path> out = new java.util.LinkedHashMap<>();
        Path root = packFile.getParent();
        for (String name : new String[] {"rpg", "hub"}) {
            Path dir = root.resolve(name);
            if (Files.isDirectory(dir)) {
                out.put(name, dir);
            }
        }
        return out;
    }

    
    private Path variantPath(String path) {
        if (path == null) return null;
        int slash = path.indexOf('/');
        if (slash <= 0) return null;
        Path dir = variants().get(path.substring(0, slash));
        if (dir == null) return null;
        
        Path target = dir.resolve(path.substring(slash + 1)).normalize();
        return target.startsWith(dir) ? target : null;
    }

    
    public byte[] read(String path) throws IOException {
        Path inVariant = variantPath(path);
        if (inVariant != null) {
            return Files.exists(inVariant) ? Files.readAllBytes(inVariant) : null;
        }
        Path override = overrideOf(path);
        if (override != null && Files.exists(override)) return Files.readAllBytes(override);
        if (!Files.exists(packFile)) return null;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(packFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.getName().equals(path)) continue;
                return zip.readAllBytes();
            }
        }
        return null;
    }

    public void write(String path, byte[] content) throws IOException {
        Path target = variantPath(path);
        if (target == null) {
            target = overrideOf(path);
        }
        if (target == null) throw new IOException("that path is not inside the pack");
        Files.createDirectories(target.getParent());
        Files.write(target, content);
        LOGGER.info("pack editor: {} now overridden ({} bytes)", path, content.length);
    }

    public boolean revert(String path) throws IOException {
        Path target = variantPath(path);
        if (target == null) {
            target = overrideOf(path);
        }
        if (target == null || !Files.exists(target)) return false;
        Files.delete(target);
        LOGGER.info("pack editor: {} back to the built-in art", path);
        return true;
    }

    



    private Path overrideOf(String path) {
        if (path == null || path.isEmpty() || path.contains("\0")) return null;
        Path target = overrides.resolve(path).normalize();
        if (!target.startsWith(overrides)) return null;
        return target;
    }

    
    public static Map<String, byte[]> overridesIn(Path packFile) {
        return filesIn(packFile.getParent().resolve("overrides"));
    }

    





    public static Map<String, byte[]> filesIn(Path root) {
        Map<String, byte[]> out = new ConcurrentHashMap<>();
        if (root == null || !Files.isDirectory(root)) return out;
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                try {
                    out.put(root.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
                } catch (IOException ignored) {
                    
                }
            });
        } catch (IOException e) {
            LOGGER.warn("pack editor: could not read the overrides: {}", e.toString());
        }
        return out;
    }

    public static String random(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, length);
    }

    public static String json(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
