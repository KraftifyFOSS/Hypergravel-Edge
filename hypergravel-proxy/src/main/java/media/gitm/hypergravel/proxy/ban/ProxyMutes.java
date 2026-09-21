package media.gitm.hypergravel.proxy.ban;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;









public final class ProxyMutes {

    private record MuteEntry(String reason, String mutedBy, long expiresAt) {
    }

    private static final Logger LOGGER = LogManager.getLogger(ProxyMutes.class);

    private final Path file;
    private final ConcurrentHashMap<UUID, MuteEntry> mutes = new ConcurrentHashMap<>();

    public ProxyMutes(Path dataDirectory) {
        this.file = dataDirectory.resolve("proxy-mutes.json");
        load();
    }

    public boolean isMuted(UUID uuid) {
        MuteEntry entry = mutes.get(uuid);
        if (entry == null) return false;
        if (entry.expiresAt() > 0 && System.currentTimeMillis() >= entry.expiresAt()) {
            mutes.remove(uuid);
            save();
            return false;
        }
        return true;
    }

    public String reason(UUID uuid) {
        MuteEntry e = mutes.get(uuid);
        return e != null ? e.reason() : null;
    }

    public void mute(UUID uuid, String name, String reason, String mutedBy, long expiresAt) {
        mutes.put(uuid, new MuteEntry(reason == null ? "" : reason,
                mutedBy == null ? "" : mutedBy, expiresAt));
        save();
        LOGGER.info("proxy mute: {} ({}) by {}: {}{}", name, uuid, mutedBy, reason,
                expiresAt > 0 ? " (expires " + expiresAt + ")" : " (permanent)");
    }

    public void unmute(UUID uuid) {
        MuteEntry removed = mutes.remove(uuid);
        if (removed != null) {
            save();
            LOGGER.info("proxy unmute: {}", uuid);
        }
    }

    public int size() {
        return mutes.size();
    }

    
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        var it = mutes.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getValue().expiresAt() > 0 && now >= e.getValue().expiresAt()) {
                it.remove();
                LOGGER.info("proxy mute expired: {}", e.getKey());
                changed = true;
            }
        }
        if (changed) save();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file).strip();
            if (json.isEmpty() || json.equals("{}")) return;
            json = json.substring(1, json.length() - 1);
            for (String entry : json.split("\\},\\s*\\{")) {
                entry = entry.strip();
                if (entry.isEmpty()) continue;
                if (entry.startsWith("{")) entry = entry.substring(1);
                if (entry.endsWith("}")) entry = entry.substring(0, entry.length() - 1);
                int colon = entry.indexOf("\":");
                if (colon < 0) continue;
                String uuidStr = entry.substring(1, colon).strip();
                String value = entry.substring(colon + 2).strip();
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String reason = extractJsonString(value, "reason");
                    String mutedBy = extractJsonString(value, "mutedBy");
                    long expiresAt = extractJsonLong(value, "expiresAt");
                    mutes.put(uuid, new MuteEntry(reason, mutedBy, expiresAt));
                } catch (IllegalArgumentException ignored) {
                }
            }
            LOGGER.info("proxy mutes: loaded {} mute(s)", mutes.size());
        } catch (IOException e) {
            LOGGER.warn("proxy mutes: could not load {}: {}", file, e.getMessage());
        } catch (Exception e) {
            LOGGER.warn("proxy mutes: corrupt file {}: {}", file, e.getMessage());
        }
    }

    private void save() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : mutes.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":{");
            sb.append("\"reason\":\"").append(escapeJson(entry.getValue().reason())).append("\",");
            sb.append("\"mutedBy\":\"").append(escapeJson(entry.getValue().mutedBy())).append("\",");
            sb.append("\"expiresAt\":").append(entry.getValue().expiresAt());
            sb.append("}");
        }
        sb.append("}");
        try {
            Files.writeString(file, sb.toString());
        } catch (IOException e) {
            LOGGER.warn("proxy mutes: could not save {}: {}", file, e.getMessage());
        }
    }

    private static String extractJsonString(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) return "";
        start += needle.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return "";
        return json.substring(start, end).replace("\\\"", "\"");
    }

    private static long extractJsonLong(String json, String key) {
        String needle = "\"" + key + "\":";
        int start = json.indexOf(needle);
        if (start < 0) return 0;
        start += needle.length();
        int end = json.indexOf(",", start);
        if (end < 0) end = json.indexOf("}", start);
        if (end < 0) return 0;
        try {
            return Long.parseLong(json.substring(start, end).strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
