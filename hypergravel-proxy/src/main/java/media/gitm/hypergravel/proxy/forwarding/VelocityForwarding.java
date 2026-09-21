package media.gitm.hypergravel.proxy.forwarding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import media.gitm.hypergravel.proxy.protocol.ProtocolUtils;
import media.gitm.hypergravel.proxy.protocol.ProtocolVersion;
import media.gitm.hypergravel.proxy.protocol.packet.GameProfile;

public final class VelocityForwarding {

    public static final String CHANNEL = "velocity:player_info";

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SIGNATURE_LENGTH = 32;

    public static final int VERSION_DEFAULT = 1;

    public static final int VERSION_WITH_KEY = 2;

    public static final int VERSION_WITH_KEY_V2 = 3;

    public static final int VERSION_LAZY_SESSION = 4;

    private VelocityForwarding() {
    }

    public static byte[] createPayload(String secret, String address, GameProfile profile,
                                       ProtocolVersion version) {
        int forwardingVersion = version.atLeast(ProtocolVersion.MINECRAFT_1_19_3)
                ? VERSION_LAZY_SESSION
                : VERSION_DEFAULT;

        ByteBuf buf = Unpooled.buffer(256);
        try {

            buf.writeZero(SIGNATURE_LENGTH);
            int payloadStart = buf.writerIndex();

            ProtocolUtils.writeVarInt(buf, forwardingVersion);
            ProtocolUtils.writeString(buf, address);
            ProtocolUtils.writeUuid(buf, profile.uuid());
            ProtocolUtils.writeString(buf, profile.name());
            GameProfile.writeProperties(buf, profile.properties());

            if (forwardingVersion >= VERSION_LAZY_SESSION) {

                buf.writeBoolean(false);
            }

            byte[] payload = new byte[buf.writerIndex() - payloadStart];
            buf.getBytes(payloadStart, payload);
            byte[] signature = sign(secret, payload);
            buf.setBytes(0, signature);

            byte[] result = new byte[buf.writerIndex()];
            buf.getBytes(0, result);
            return result;
        } finally {
            buf.release();
        }
    }

    private static byte[] sign(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(payload);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("cannot sign forwarding payload", e);
        }
    }

    public static boolean verify(String secret, byte[] signed) {
        if (signed.length < SIGNATURE_LENGTH) {
            return false;
        }
        byte[] signature = new byte[SIGNATURE_LENGTH];
        System.arraycopy(signed, 0, signature, 0, SIGNATURE_LENGTH);
        byte[] payload = new byte[signed.length - SIGNATURE_LENGTH];
        System.arraycopy(signed, SIGNATURE_LENGTH, payload, 0, payload.length);

        return MessageDigest.isEqual(signature, sign(secret, payload));
    }
}
