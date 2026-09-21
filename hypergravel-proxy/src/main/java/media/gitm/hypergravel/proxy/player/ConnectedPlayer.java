package media.gitm.hypergravel.proxy.player;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.buffer.Unpooled;
import media.gitm.hypergravel.api.permission.Tristate;
import media.gitm.hypergravel.api.player.Player;
import media.gitm.hypergravel.api.server.RegisteredServer;
import media.gitm.hypergravel.api.server.ServerConnection;
import media.gitm.hypergravel.proxy.HyperGravelProxy;
import media.gitm.hypergravel.proxy.backend.BackendConnection;
import media.gitm.hypergravel.proxy.backend.HyperGravelServer;
import media.gitm.hypergravel.proxy.network.MinecraftConnection;
import media.gitm.hypergravel.proxy.protocol.ProtocolVersion;
import media.gitm.hypergravel.proxy.protocol.packet.GameProfile;
import media.gitm.hypergravel.proxy.protocol.packet.PlayPackets;
import media.gitm.hypergravel.proxy.protocol.packet.SharedPackets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

public final class ConnectedPlayer implements Player {

    private final HyperGravelProxy proxy;
    private final MinecraftConnection connection;
    private final GameProfile profile;
    private final ProtocolVersion version;
    private final InetSocketAddress remoteAddress;
    private final String virtualHost;
    private final boolean bedrock;

    private volatile BackendConnection backend;

    private final AtomicBoolean connecting = new AtomicBoolean();

    private BackendConnection connectingBackend;
    private CompletableFuture<ConnectionResult> connectingResult;
    private final java.util.List<io.netty.buffer.ByteBuf> configBacklog = new java.util.ArrayList<>();

    private volatile long lastKeepAliveId;
    private volatile long lastKeepAliveSentNanos;
    private volatile long pingMillis = -1;

    private volatile HyperGravelServer returnTo;
    private volatile long returnToExpiresAt;

    public ConnectedPlayer(HyperGravelProxy proxy, MinecraftConnection connection, GameProfile profile,
                           ProtocolVersion version, InetSocketAddress remoteAddress,
                           String virtualHost, boolean bedrock) {
        this.proxy = proxy;
        this.connection = connection;
        this.profile = profile;
        this.version = version;
        this.remoteAddress = remoteAddress;
        this.virtualHost = virtualHost;
        this.bedrock = bedrock;
    }

    @Override
    public UUID uuid() {
        return profile.uuid();
    }

    @Override
    public String username() {
        return profile.name();
    }

    @Override
    public String name() {
        return profile.name();
    }

    public GameProfile profile() {
        return profile;
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return remoteAddress;
    }

    public String virtualHost() {
        return virtualHost;
    }

    @Override
    public int protocolVersion() {
        return version.protocol();
    }

    public ProtocolVersion version() {
        return version;
    }

    @Override
    public boolean bedrock() {
        return bedrock;
    }

    public MinecraftConnection connection() {
        return connection;
    }

    public BackendConnection backendConnection() {
        return backend;
    }

    public void setBackend(BackendConnection backend) {
        this.backend = backend;
    }

    public BackendConnection connectingBackend() {
        return connectingBackend;
    }

    public CompletableFuture<ConnectionResult> connectingResult() {
        return connectingResult;
    }

    public void beginBackendConfig(BackendConnection backend,
                                   CompletableFuture<ConnectionResult> result) {
        this.connectingBackend = backend;
        this.connectingResult = result;
        for (io.netty.buffer.ByteBuf frame : configBacklog) {
            if (backend.connection().active()) {
                backend.connection().channel().writeAndFlush(
                        frame, backend.connection().channel().voidPromise());
            } else {
                frame.release();
            }
        }
        configBacklog.clear();
    }

    public void bufferClientConfig(io.netty.buffer.ByteBuf frame) {
        configBacklog.add(frame);
    }

    private volatile byte[] clientInformation;

    public byte[] clientInformation() {
        return clientInformation;
    }

    public void rememberClientInformation(byte[] frame) {
        this.clientInformation = frame;
    }

    private BackendConnection pendingSwitchBackend;
    private CompletableFuture<ConnectionResult> pendingSwitchResult;

    public BackendConnection pendingSwitchBackend() {
        return pendingSwitchBackend;
    }

    public CompletableFuture<ConnectionResult> pendingSwitchResult() {
        return pendingSwitchResult;
    }

    public void beginSwitch(BackendConnection target, CompletableFuture<ConnectionResult> result) {
        this.pendingSwitchBackend = target;
        this.pendingSwitchResult = result;
        BackendConnection current = this.backend;
        if (current != null && current.connection().active()) {
            current.connection().channel().config().setAutoRead(false);
        }
        connection.writeAndFlush(new PlayPackets.StartConfiguration());
    }

    public void clearPendingSwitch() {
        this.pendingSwitchBackend = null;
        this.pendingSwitchResult = null;
    }

    public void clearConfigBridge() {
        this.connectingBackend = null;
        this.connectingResult = null;
        for (io.netty.buffer.ByteBuf frame : configBacklog) {
            frame.release();
        }
        configBacklog.clear();
    }

    @Override
    public Optional<ServerConnection> currentServer() {
        return Optional.ofNullable(backend);
    }

    public Optional<HyperGravelServer> currentHyperGravelServer() {
        BackendConnection current = backend;
        return current == null ? Optional.empty() : Optional.of(current.hypergravelServer());
    }

    @Override
    public CompletableFuture<ConnectionResult> connect(RegisteredServer server) {
        if (!(server instanceof HyperGravelServer target)) {
            return CompletableFuture.completedFuture(ConnectionResult.UNREACHABLE);
        }
        return proxy.connector().connect(this, target,
                media.gitm.hypergravel.api.event.PreServerConnectEvent.Reason.COMMAND);
    }

    @Override
    public CompletableFuture<ConnectionResult> transfer(String host, int port) {
        if (!version.supportsTransfer()) {
            return CompletableFuture.completedFuture(ConnectionResult.UNREACHABLE);
        }
        connection.writeAndFlush(new PlayPackets.Transfer(host, port));
        return CompletableFuture.completedFuture(ConnectionResult.SUCCESS);
    }

    public boolean beginConnect() {
        return connecting.compareAndSet(false, true);
    }

    public void endConnect() {
        connecting.set(false);
    }

    public void setReturnTo(HyperGravelServer server, long ttlMillis) {
        this.returnTo = server;
        this.returnToExpiresAt = System.currentTimeMillis() + ttlMillis;
    }

    public void clearReturnTo() {
        this.returnTo = null;
    }

    public Optional<HyperGravelServer> pendingReturn() {
        HyperGravelServer target = returnTo;
        if (target == null) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() > returnToExpiresAt) {
            returnTo = null;
            return Optional.empty();
        }
        return Optional.of(target);
    }

    public void sendKeepAlive(long id) {
        this.lastKeepAliveId = id;
        this.lastKeepAliveSentNanos = System.nanoTime();
        connection.writeAndFlush(new SharedPackets.KeepAlive(id));
    }

    public boolean acknowledgeKeepAlive(long id) {
        if (id != lastKeepAliveId) {
            return false;
        }
        this.pingMillis = (System.nanoTime() - lastKeepAliveSentNanos) / 1_000_000L;
        return true;
    }

    @Override
    public long pingMillis() {
        return pingMillis;
    }

    @Override
    public void sendMessage(net.kyori.adventure.text.Component message) {

        proxy.messenger().sendSystemMessage(this, message);
    }

    @Override
    public boolean sendPluginMessage(String channel, byte[] data) {
        if (!connection.active()) {
            return false;
        }
        connection.writeAndFlush(new SharedPackets.PluginMessage(channel,
                Unpooled.wrappedBuffer(data)));
        return true;
    }

    public boolean sendPluginMessageToBackend(String channel, byte[] data) {
        BackendConnection current = backend;
        if (current == null || !current.connection().active()) {
            return false;
        }
        current.connection().writeAndFlush(new SharedPackets.PluginMessage(channel,
                Unpooled.wrappedBuffer(data)));
        return true;
    }

    @Override
    public void disconnect(Component reason) {
        if (!connection.active()) {
            return;
        }
        String json = GsonComponentSerializer.gson().serialize(reason);

        boolean nbt = connection.state() != media.gitm.hypergravel.proxy.protocol.ProtocolState.LOGIN;
        connection.closeWith(new SharedPackets.Disconnect(json, nbt));
    }

    @Override
    public Tristate permissionValue(String permission) {
        return proxy.permissions().lookup(this, permission);
    }

    @Override
    public String toString() {
        return "ConnectedPlayer{" + profile.name() + "/" + profile.uuid()
                + " on " + currentHyperGravelServer().map(HyperGravelServer::name).orElse("none") + '}';
    }
}
