package media.gitm.hypergravel.proxy.player;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerRegistry {

    private final Map<UUID, ConnectedPlayer> byUuid = new ConcurrentHashMap<>();
    private final Map<String, ConnectedPlayer> byName = new ConcurrentHashMap<>();

    public boolean tryRegister(ConnectedPlayer player) {
        if (byUuid.putIfAbsent(player.uuid(), player) != null) {
            return false;
        }
        String key = player.username().toLowerCase(Locale.ROOT);
        ConnectedPlayer existing = byName.putIfAbsent(key, player);
        if (existing != null && existing != player) {
            byUuid.remove(player.uuid(), player);
            return false;
        }
        return true;
    }

    public void unregister(ConnectedPlayer player) {
        byUuid.remove(player.uuid(), player);
        byName.remove(player.username().toLowerCase(Locale.ROOT), player);
    }

    public Optional<ConnectedPlayer> byUuid(UUID uuid) {
        return Optional.ofNullable(byUuid.get(uuid));
    }

    public Optional<ConnectedPlayer> byName(String username) {
        return Optional.ofNullable(byName.get(username.toLowerCase(Locale.ROOT)));
    }

    public Collection<ConnectedPlayer> all() {
        return Collections.unmodifiableCollection(byUuid.values());
    }

    public int count() {
        return byUuid.size();
    }
}
