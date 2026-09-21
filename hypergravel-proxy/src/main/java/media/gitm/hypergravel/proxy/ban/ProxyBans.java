package media.gitm.hypergravel.proxy.ban;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;








public final class ProxyBans {

    private record BanEntry(String reason, String bannedBy, long expiresAt) {
    }

    private static final Logger LOGGER = LogManager.getLogger(ProxyBans.class);

    private final Path file;
    private final ConcurrentHashMap<UUID, BanEntry> bans = new ConcurrentHashMap<>();

    public ProxyBans(Path dataDirectory) {
        this.file = dataDirectory.resolve("proxy-bans.json");
        load();
    }

    
    public boolean isBanned(UUID uuid) {
        BanEntry entry = bans.get(uuid);
        if (entry == null) return false;
        if (entry.expiresAt() > 0 && System.currentTimeMillis() >= entry.expiresAt()) {
            bans.remove(uuid);
            save();
            return false;
        }
        return true;
    }

    
    public Component denyReason(UUID uuid) {
        BanEntry entry = bans.get(uuid);
        String reason = entry != null && !entry.reason().isEmpty()
                ? entry.reason() : "You are banned from this network.";
        return MiniMessage.miniMessage().deserialize(
                "\n<red><bold>You are banned\n\n<gray>Reason: <white>" + reason
                        + "\n<gray>Contact staff to appeal.");
    }

    
    public void ban(UUID uuid, String name, String reason, String bannedBy, long expiresAt) {
        bans.put(uuid, new BanEntry(reason == null ? "" : reason,
                bannedBy == null ? "" : bannedBy, expiresAt));
        save();
        LOGGER.info("proxy ban: {} ({}) by {}: {}{}", name, uuid, bannedBy, reason,
                expiresAt > 0 ? " (expires " + expiresAt + ")" : " (permanent)");
    }

    
    public void unban(UUID uuid) {
        BanEntry removed = bans.remove(uuid);
        if (removed != null) {
            save();
            LOGGER.info("proxy unban: {}", uuid);
        }
    }

    
    public int size() {
        return bans.size();
    }

    
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        var it = bans.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getValue().expiresAt() > 0 && now >= e.getValue().expiresAt()) {
                it.remove();
                LOGGER.info("proxy ban expired: {}", e.getKey());
                changed = true;
            }
        }
        if (changed) save();
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            String json = Files.readString(file);
            
            
            json = json.strip();
            if (json.isEmpty() || json.equals("{}")) {
                return;
            }
            
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
                    String bannedBy = extractJsonString(value, "bannedBy");
                    long expiresAt = extractJsonLong(value, "expiresAt");
                    bans.put(uuid, new BanEntry(reason, bannedBy, expiresAt));
                } catch (IllegalArgumentException ignored) {
                }
            }
            LOGGER.info("proxy bans: loaded {} ban(s)", bans.size());
        } catch (IOException e) {
            LOGGER.warn("proxy bans: could not load {}: {}", file, e.getMessage());
        } catch (Exception e) {
            LOGGER.warn("proxy bans: corrupt file {}: {}", file, e.getMessage());
        }
    }

    private void save() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : bans.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":{");
            sb.append("\"reason\":\"").append(escapeJson(entry.getValue().reason())).append("\",");
            sb.append("\"bannedBy\":\"").append(escapeJson(entry.getValue().bannedBy())).append("\",");
            sb.append("\"expiresAt\":").append(entry.getValue().expiresAt());
            sb.append("}");
        }
        sb.append("}");
        try {
            Files.writeString(file, sb.toString());
        } catch (IOException e) {
            LOGGER.warn("proxy bans: could not save {}: {}", file, e.getMessage());
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
