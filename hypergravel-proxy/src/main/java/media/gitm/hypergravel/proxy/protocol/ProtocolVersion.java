package media.gitm.hypergravel.proxy.protocol;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public enum ProtocolVersion {

    UNKNOWN(-1, "Unknown"),
    LEGACY(-2, "Legacy"),

    MINECRAFT_1_8(47, "1.8.x"),
    MINECRAFT_1_9(107, "1.9"),
    MINECRAFT_1_9_4(110, "1.9.4"),
    MINECRAFT_1_10(210, "1.10.x"),
    MINECRAFT_1_11(315, "1.11"),
    MINECRAFT_1_11_1(316, "1.11.2"),
    MINECRAFT_1_12(335, "1.12"),
    MINECRAFT_1_12_2(340, "1.12.2"),
    MINECRAFT_1_13(393, "1.13"),
    MINECRAFT_1_13_2(404, "1.13.2"),
    MINECRAFT_1_14(477, "1.14"),
    MINECRAFT_1_15(573, "1.15"),
    MINECRAFT_1_16(735, "1.16"),
    MINECRAFT_1_16_2(751, "1.16.2"),
    MINECRAFT_1_16_4(754, "1.16.4"),
    MINECRAFT_1_17(755, "1.17"),
    MINECRAFT_1_18(757, "1.18"),
    MINECRAFT_1_18_2(758, "1.18.2"),
    MINECRAFT_1_19(759, "1.19"),
    MINECRAFT_1_19_1(760, "1.19.2"),
    MINECRAFT_1_19_3(761, "1.19.3"),
    MINECRAFT_1_19_4(762, "1.19.4"),
    MINECRAFT_1_20(763, "1.20"),
    MINECRAFT_1_20_2(764, "1.20.2"),
    MINECRAFT_1_20_3(765, "1.20.3"),
    MINECRAFT_1_20_5(766, "1.20.5"),
    MINECRAFT_1_21(767, "1.21"),
    MINECRAFT_1_21_2(768, "1.21.2"),
    MINECRAFT_1_21_4(769, "1.21.4"),
    MINECRAFT_1_21_5(770, "1.21.5"),
    MINECRAFT_1_21_6(771, "1.21.6"),
    MINECRAFT_1_21_7(772, "1.21.7"),
    MINECRAFT_1_21_9(773, "1.21.9"),

    MINECRAFT_1_21_11(774, "1.21.11"),
    MINECRAFT_26_1(775, "26.1"),
    MINECRAFT_26_2(776, "26.2");

    public static final ProtocolVersion MINIMUM_NATIVE = MINECRAFT_1_20_2;

    public static final ProtocolVersion MAXIMUM_NATIVE = MINECRAFT_26_2;

    private static final Int2ObjectMap<ProtocolVersion> BY_ID;
    private static final Map<String, ProtocolVersion> BY_NAME;

    static {
        Int2ObjectMap<ProtocolVersion> byId = new Int2ObjectOpenHashMap<>();
        for (ProtocolVersion version : values()) {
            if (version.protocol >= 0) {
                byId.put(version.protocol, version);
            }
        }
        BY_ID = it.unimi.dsi.fastutil.ints.Int2ObjectMaps.unmodifiable(byId);
        BY_NAME = Stream.of(values())
                .collect(Collectors.toUnmodifiableMap(v -> v.name, v -> v, (a, b) -> a));
    }

    private final int protocol;
    private final String name;

    ProtocolVersion(int protocol, String name) {
        this.protocol = protocol;
        this.name = name;
    }

    public int protocol() {
        return protocol;
    }

    public String versionName() {
        return name;
    }

    public boolean hasConfigurationState() {
        return protocol >= MINECRAFT_1_20_2.protocol;
    }

    public boolean supportsTransfer() {
        return protocol >= MINECRAFT_1_20_5.protocol;
    }

    public boolean hasSignedChat() {
        return protocol >= MINECRAFT_1_19.protocol;
    }

    public boolean atLeast(ProtocolVersion other) {
        return protocol >= other.protocol;
    }

    public boolean atMost(ProtocolVersion other) {
        return protocol <= other.protocol;
    }

    public boolean nativelySupported() {
        return protocol >= MINIMUM_NATIVE.protocol && protocol <= MAXIMUM_NATIVE.protocol;
    }

    public static ProtocolVersion fromId(int protocol) {
        ProtocolVersion known = BY_ID.get(protocol);
        return known != null ? known : UNKNOWN;
    }

    public static ProtocolVersion fromName(String name) {
        return BY_NAME.getOrDefault(name, UNKNOWN);
    }

    public static String describe(int protocol) {
        ProtocolVersion known = BY_ID.get(protocol);
        return known != null ? known.name : "protocol " + protocol;
    }

    @Override
    public String toString() {
        return name + " (" + protocol + ")";
    }
}
