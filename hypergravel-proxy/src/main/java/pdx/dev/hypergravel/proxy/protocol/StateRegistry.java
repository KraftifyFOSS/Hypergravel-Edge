package pdx.dev.hypergravel.proxy.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class StateRegistry {

    private static final String MAPPINGS_RESOURCE = "/protocol/mappings.json";

    private static final Map<ProtocolState, StateRegistry> REGISTRIES =
            new EnumMap<>(ProtocolState.class);

    static {
        load();
    }

    private final ProtocolState state;
    private final Map<PacketDirection, DirectionRegistry> directions =
            new EnumMap<>(PacketDirection.class);

    private StateRegistry(ProtocolState state) {
        this.state = state;
    }

    public static StateRegistry of(ProtocolState state) {
        StateRegistry registry = REGISTRIES.get(state);
        if (registry == null) {
            throw new IllegalStateException("no registry for state " + state);
        }
        return registry;
    }

    public ProtocolState state() {
        return state;
    }

    public DirectionRegistry direction(PacketDirection direction) {
        DirectionRegistry registry = directions.get(direction);
        return registry != null ? registry : DirectionRegistry.EMPTY;
    }

    private static void load() {
        JsonObject root;
        try (InputStream in = StateRegistry.class.getResourceAsStream(MAPPINGS_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + MAPPINGS_RESOURCE);
            }
            root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + MAPPINGS_RESOURCE, e);
        }

        for (ProtocolState state : ProtocolState.values()) {
            StateRegistry registry = new StateRegistry(state);
            REGISTRIES.put(state, registry);

            JsonElement stateElement = root.get(state.name());
            if (stateElement == null) {
                continue;
            }
            JsonObject stateObject = stateElement.getAsJsonObject();

            for (PacketDirection direction : PacketDirection.values()) {
                JsonElement directionElement = stateObject.get(direction.name());
                if (directionElement == null) {
                    continue;
                }
                registry.directions.put(direction,
                        DirectionRegistry.parse(state, direction, directionElement.getAsJsonObject()));
            }
        }
    }

    public static final class DirectionRegistry {

        static final DirectionRegistry EMPTY =
                new DirectionRegistry(List.of(), new VersionTable(0, new PacketType[0], Map.of()));

        private final List<VersionTable> tables;
        private final VersionTable fallback;

        private DirectionRegistry(List<VersionTable> tables, VersionTable fallback) {
            this.tables = tables;
            this.fallback = fallback;
        }

        public VersionTable forVersion(ProtocolVersion version) {
            int protocol = version.protocol();
            VersionTable best = fallback;
            for (VersionTable table : tables) {
                if (table.fromProtocol <= protocol) {
                    best = table;
                } else {
                    break;
                }
            }
            return best;
        }

        static DirectionRegistry parse(ProtocolState state, PacketDirection direction,
                                       JsonObject mappings) {

            record Entry(PacketType type, int fromProtocol, int id, boolean encodeOnly) {}
            List<Entry> entries = new ArrayList<>();
            List<Integer> versionBoundaries = new ArrayList<>();

            for (Map.Entry<String, JsonElement> mapping : mappings.entrySet()) {
                PacketType type = PacketType.byKey(mapping.getKey());
                if (type == null) {
                    throw new IllegalStateException("mappings.json references unknown packet '"
                            + mapping.getKey() + "' in " + state + "/" + direction);
                }
                JsonArray ranges = mapping.getValue().getAsJsonArray();
                for (JsonElement rangeElement : ranges) {
                    JsonObject range = rangeElement.getAsJsonObject();
                    int from = range.get("from").getAsInt();
                    int id = range.get("id").getAsInt();
                    boolean encodeOnly = range.has("encodeOnly")
                            && range.get("encodeOnly").getAsBoolean();
                    entries.add(new Entry(type, from, id, encodeOnly));
                    if (!versionBoundaries.contains(from)) {
                        versionBoundaries.add(from);
                    }
                }
            }

            versionBoundaries.sort(null);
            List<VersionTable> tables = new ArrayList<>(versionBoundaries.size());

            for (int boundary : versionBoundaries) {

                Map<PacketType, Integer> effective = new EnumMap<>(PacketType.class);
                Map<PacketType, Integer> effectiveFrom = new EnumMap<>(PacketType.class);
                Map<PacketType, Boolean> encodeOnly = new EnumMap<>(PacketType.class);
                for (Entry entry : entries) {
                    if (entry.fromProtocol() > boundary) {
                        continue;
                    }
                    Integer currentFrom = effectiveFrom.get(entry.type());
                    if (currentFrom == null || entry.fromProtocol() >= currentFrom) {
                        effectiveFrom.put(entry.type(), entry.fromProtocol());
                        effective.put(entry.type(), entry.id());
                        encodeOnly.put(entry.type(), entry.encodeOnly());
                    }
                }

                int maxId = -1;
                for (Map.Entry<PacketType, Integer> e : effective.entrySet()) {
                    if (!encodeOnly.get(e.getKey())) {
                        maxId = Math.max(maxId, e.getValue());
                    }
                }
                PacketType[] byId = new PacketType[maxId + 1];
                for (Map.Entry<PacketType, Integer> e : effective.entrySet()) {
                    if (encodeOnly.get(e.getKey())) {

                        continue;
                    }
                    int id = e.getValue();
                    if (byId[id] != null) {
                        throw new IllegalStateException("duplicate packet id 0x"
                                + Integer.toHexString(id) + " in " + state + "/" + direction
                                + " at protocol " + boundary + ": " + byId[id] + " and " + e.getKey());
                    }
                    byId[id] = e.getKey();
                }
                tables.add(new VersionTable(boundary, byId, effective));
            }

            VersionTable fallback = tables.isEmpty()
                    ? new VersionTable(0, new PacketType[0], Map.of())
                    : tables.get(0);
            return new DirectionRegistry(List.copyOf(tables), fallback);
        }
    }

    public static final class VersionTable {

        private final int fromProtocol;
        private final PacketType[] byId;
        private final int[] idByType;

        VersionTable(int fromProtocol, PacketType[] byId, Map<PacketType, Integer> allIds) {
            this.fromProtocol = fromProtocol;
            this.byId = byId;
            this.idByType = new int[PacketType.values().length];
            java.util.Arrays.fill(this.idByType, -1);

            for (Map.Entry<PacketType, Integer> entry : allIds.entrySet()) {
                this.idByType[entry.getKey().ordinal()] = entry.getValue();
            }
        }

        public PacketType lookup(int id) {
            return id >= 0 && id < byId.length ? byId[id] : null;
        }

        public int idOf(PacketType type) {
            return idByType[type.ordinal()];
        }

        public boolean has(PacketType type) {
            return idByType[type.ordinal()] >= 0;
        }

        public int fromProtocol() {
            return fromProtocol;
        }
    }
}
