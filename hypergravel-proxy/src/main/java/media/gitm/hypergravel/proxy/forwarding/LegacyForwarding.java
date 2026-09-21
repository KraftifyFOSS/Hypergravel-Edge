package media.gitm.hypergravel.proxy.forwarding;

import java.util.StringJoiner;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import media.gitm.hypergravel.proxy.protocol.packet.GameProfile;

public final class LegacyForwarding {

    public static final String BUNGEEGUARD_PROPERTY = "bungeeguard-token";

    private LegacyForwarding() {
    }

    public static String createAddress(String originalHost, String clientIp, GameProfile profile) {
        StringJoiner joiner = new StringJoiner("\0");
        joiner.add(originalHost);
        joiner.add(clientIp);
        joiner.add(undashed(profile.uuid()));
        joiner.add(propertiesJson(profile));
        return joiner.toString();
    }

    public static String createGuardedAddress(String originalHost, String clientIp,
                                              GameProfile profile, String token) {
        GameProfile guarded = profile.withProperty(
                new GameProfile.Property(BUNGEEGUARD_PROPERTY, token, null));
        return createAddress(originalHost, clientIp, guarded);
    }

    private static String propertiesJson(GameProfile profile) {
        JsonArray array = new JsonArray();
        for (GameProfile.Property property : profile.properties()) {
            JsonObject object = new JsonObject();
            object.addProperty("name", property.name());
            object.addProperty("value", property.value());
            if (property.signature() != null) {
                object.addProperty("signature", property.signature());
            }
            array.add(object);
        }
        return array.toString();
    }

    public static String undashed(java.util.UUID uuid) {
        return uuid.toString().replace("-", "");
    }
}
