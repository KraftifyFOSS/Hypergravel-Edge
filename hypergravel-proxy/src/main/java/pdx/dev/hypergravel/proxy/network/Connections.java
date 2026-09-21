package pdx.dev.hypergravel.proxy.network;

import io.netty.util.AttributeKey;
import pdx.dev.hypergravel.proxy.player.ConnectedPlayer;

public final class Connections {

    public static final String PROXY_PROTOCOL = "haproxy";
    public static final String READ_TIMEOUT = "read-timeout";
    public static final String CIPHER_DECODER = "cipher-decoder";
    public static final String CIPHER_ENCODER = "cipher-encoder";
    public static final String FRAME_DECODER = "frame-decoder";
    public static final String FRAME_ENCODER = "frame-encoder";
    public static final String COMPRESSION_DECODER = "compression-decoder";
    public static final String COMPRESSION_ENCODER = "compression-encoder";
    public static final String MINECRAFT_DECODER = "minecraft-decoder";
    public static final String MINECRAFT_ENCODER = "minecraft-encoder";
    public static final String HANDLER = "handler";

    public static final AttributeKey<ConnectedPlayer> PLAYER =
            AttributeKey.valueOf("hypergravel:player");

    private Connections() {
    }
}
