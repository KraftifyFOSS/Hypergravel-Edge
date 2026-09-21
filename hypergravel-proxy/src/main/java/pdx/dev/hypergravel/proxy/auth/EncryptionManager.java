package pdx.dev.hypergravel.proxy.auth;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;

public final class EncryptionManager {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final KeyPair keyPair;
    private final byte[] encodedPublicKey;

    public EncryptionManager() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");

            generator.initialize(1024);
            this.keyPair = generator.generateKeyPair();
            this.encodedPublicKey = keyPair.getPublic().getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot generate session keypair", e);
        }
    }

    public byte[] publicKey() {
        return encodedPublicKey;
    }

    public static byte[] newVerifyToken() {
        byte[] token = new byte[4];
        RANDOM.nextBytes(token);
        return token;
    }

    public byte[] decrypt(byte[] data) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
        return cipher.doFinal(data);
    }

    public String serverIdHash(String serverId, byte[] sharedSecret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(serverId.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
            digest.update(sharedSecret);
            digest.update(encodedPublicKey);
            return new BigInteger(digest.digest()).toString(16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }
}
