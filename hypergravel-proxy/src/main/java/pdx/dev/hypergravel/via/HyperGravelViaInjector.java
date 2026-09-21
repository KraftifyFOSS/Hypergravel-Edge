package pdx.dev.hypergravel.via;

import com.viaversion.viaversion.api.platform.ViaInjector;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.libs.gson.JsonObject;
import com.viaversion.viaversion.platform.ViaDecodeHandler;
import com.viaversion.viaversion.platform.ViaEncodeHandler;

final class HyperGravelViaInjector implements ViaInjector {

    private final ProtocolVersion serverVersion;

    HyperGravelViaInjector(ProtocolVersion serverVersion) {
        this.serverVersion = serverVersion;
    }

    @Override
    public void inject() {

    }

    @Override
    public void uninject() {
    }

    @Override
    public ProtocolVersion getServerProtocolVersion() {
        return serverVersion;
    }

    @Override
    public String getDecoderName() {
        return ViaDecodeHandler.NAME;
    }

    @Override
    public String getEncoderName() {
        return ViaEncodeHandler.NAME;
    }

    @Override
    public JsonObject getDump() {
        JsonObject dump = new JsonObject();
        dump.addProperty("serverProtocol", serverVersion.getVersion());
        dump.addProperty("serverVersion", serverVersion.getName());
        return dump;
    }
}
