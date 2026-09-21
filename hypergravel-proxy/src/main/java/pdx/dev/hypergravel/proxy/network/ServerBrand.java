package pdx.dev.hypergravel.proxy.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import pdx.dev.hypergravel.proxy.protocol.ProtocolUtils;
import pdx.dev.hypergravel.proxy.protocol.packet.SharedPackets;

public final class ServerBrand {

    public static final String CHANNEL = "minecraft:brand";
    private static final String SUFFIX = " (HyperGravel)";

    private ServerBrand() {
    }

    public static SharedPackets.PluginMessage appendTag(SharedPackets.PluginMessage original) {
        String brand = ProtocolUtils.readString(original.data().duplicate(), 512);
        if (brand.contains(SUFFIX.trim())) {
            return original;
        }
        ByteBuf out = Unpooled.buffer();
        ProtocolUtils.writeString(out, brand + SUFFIX);
        original.release();
        return new SharedPackets.PluginMessage(CHANNEL, out);
    }
}
