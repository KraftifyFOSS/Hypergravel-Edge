package pdx.dev.hypergravel.proxy.backend;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import pdx.dev.hypergravel.api.server.ServerHealth;
import pdx.dev.hypergravel.api.server.ServerInfo;
import pdx.dev.hypergravel.proxy.config.HyperGravelConfig;

public final class ServerRegistry {

    private final Map<String, HyperGravelServer> servers = new ConcurrentHashMap<>();
    private volatile List<String> tryOrder = List.of();
    private volatile String fallbackName;
    private volatile String limboName;

    public void apply(HyperGravelConfig config) {
        for (ServerInfo info : config.servers()) {
            servers.compute(info.name(), (name, existing) -> {
                if (existing == null) {
                    return new HyperGravelServer(info);
                }
                if (existing.info().equals(info)) {
                    return existing;
                }

                HyperGravelServer replacement = new HyperGravelServer(info);
                replacement.connectedPlayers().addAll(existing.connectedPlayers());
                replacement.setHealth(existing.health());
                return replacement;
            });
        }

        servers.entrySet().removeIf(entry -> {
            boolean stillConfigured = config.servers().stream()
                    .anyMatch(info -> info.name().equals(entry.getKey()));
            return !stillConfigured && entry.getValue().playerCount() == 0;
        });

        this.tryOrder = List.copyOf(config.routing().tryOrder());
        this.fallbackName = config.routing().fallback();
        this.limboName = config.routing().limbo();
    }

    public Optional<HyperGravelServer> get(String name) {
        return name == null
                ? Optional.empty()
                : Optional.ofNullable(servers.get(name.toLowerCase(Locale.ROOT)));
    }

    public Collection<HyperGravelServer> all() {
        return servers.values();
    }

    public HyperGravelServer register(ServerInfo info) {
        return servers.computeIfAbsent(info.name(), name -> new HyperGravelServer(info));
    }

    public boolean unregister(String name) {
        HyperGravelServer server = servers.get(name.toLowerCase(Locale.ROOT));
        if (server == null || server.playerCount() > 0) {
            return false;
        }
        return servers.remove(name.toLowerCase(Locale.ROOT)) != null;
    }

    public int totalPlayers() {
        int total = 0;
        for (HyperGravelServer server : servers.values()) {
            total += server.playerCount();
        }
        return total;
    }

    public List<HyperGravelServer> routableTryOrder() {
        List<HyperGravelServer> result = new ArrayList<>(tryOrder.size());
        for (String name : tryOrder) {
            HyperGravelServer server = servers.get(name);
            if (server != null && server.health().routable()) {
                result.add(server);
            }
        }
        return result;
    }

    public List<HyperGravelServer> tryOrder() {
        List<HyperGravelServer> result = new ArrayList<>(tryOrder.size());
        for (String name : tryOrder) {
            HyperGravelServer server = servers.get(name);
            if (server != null) {
                result.add(server);
            }
        }
        return result;
    }

    public Optional<HyperGravelServer> fallback() {
        return get(fallbackName).filter(server -> server.health().routable());
    }

    public Optional<HyperGravelServer> limbo() {
        return get(limboName);
    }

    public Optional<HyperGravelServer> holdingFor(HyperGravelServer from) {
        Optional<HyperGravelServer> onDown = get(from.info().onDown())
                .filter(server -> server.health().routable());
        if (onDown.isPresent()) {
            return onDown;
        }
        Optional<HyperGravelServer> fallback = fallback();
        if (fallback.isPresent()) {
            return fallback;
        }
        return limbo().filter(server -> server.health() != ServerHealth.DOWN);
    }
}
