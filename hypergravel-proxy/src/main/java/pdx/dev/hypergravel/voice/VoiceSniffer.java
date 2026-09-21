package pdx.dev.hypergravel.voice;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class VoiceSniffer {

    private final Map<UUID, UUID> playerIds = new ConcurrentHashMap<>();

    private final Map<UUID, Integer> backendPorts = new ConcurrentHashMap<>();

    private final Map<UUID, Integer> compatibility = new ConcurrentHashMap<>();

    void rememberRequest(UUID player, int compatibilityVersion) {
        compatibility.put(player, compatibilityVersion);
    }

    int compatibilityFor(UUID player) throws IncompatibleVoiceException {
        Integer version = compatibility.get(player);
        if (version == null) {
            throw new IncompatibleVoiceException("no compatibility version seen for " + player);
        }
        return version;
    }

    void rememberSecret(UUID player, SecretPacket packet) {
        playerIds.put(packet.playerUuid(), player);
        backendPorts.put(player, packet.serverPort());
    }

    UUID resolve(UUID fromDatagram) {
        return playerIds.getOrDefault(fromDatagram, fromDatagram);
    }

    boolean ready(UUID player) {
        return backendPorts.containsKey(player);
    }

    Integer backendPort(UUID player) {
        return backendPorts.get(player);
    }

    void forget(UUID player) {
        backendPorts.remove(player);
        compatibility.remove(player);
        playerIds.values().removeIf(player::equals);
    }
}
