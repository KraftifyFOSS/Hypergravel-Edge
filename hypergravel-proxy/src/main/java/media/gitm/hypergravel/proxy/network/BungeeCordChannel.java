package media.gitm.hypergravel.proxy.network;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import media.gitm.hypergravel.api.server.ServerHealth;
import media.gitm.hypergravel.proxy.HyperGravelProxy;
import media.gitm.hypergravel.proxy.backend.HyperGravelServer;
import media.gitm.hypergravel.proxy.player.ConnectedPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class BungeeCordChannel {

    private static final Logger LOGGER = LogManager.getLogger(BungeeCordChannel.class);

    public static final String CHANNEL = "bungeecord:main";

    public static final String LEGACY_CHANNEL = "BungeeCord";

    private BungeeCordChannel() {
    }

    public static boolean isBungeeChannel(String channel) {
        return CHANNEL.equals(channel) || LEGACY_CHANNEL.equals(channel);
    }

    public static boolean handle(HyperGravelProxy proxy, ConnectedPlayer player,
                                 HyperGravelServer origin, String channel, ByteBuf data) {
        String subchannel;
        try (DataInputStream in = new DataInputStream(new ByteBufInputStream(data.duplicate()))) {
            subchannel = in.readUTF();
            dispatch(proxy, player, origin, channel, subchannel, in);
        } catch (IOException e) {

            LOGGER.warn("malformed {} message from {}: {}", channel,
                    origin == null ? "?" : origin.name(), e.toString());
        }
        return true;
    }

    private static void dispatch(HyperGravelProxy proxy, ConnectedPlayer player,
                                 HyperGravelServer origin, String channel,
                                 String subchannel, DataInputStream in) throws IOException {
        switch (subchannel) {
            case "Connect" -> {
                String target = in.readUTF();
                
                
                
                
                if (!reachable(proxy, player, target)) return;
                connect(proxy, player, target);
            }
            case "ConnectOther" -> {
                String who = in.readUTF();
                String target = in.readUTF();
                proxy.playerRegistry().byName(who)
                        .ifPresent(other -> connect(proxy, other, target));
            }
            case "IP" -> {
                InetSocketAddress addr = player.remoteAddress();
                reply(player, channel, out -> {
                    out.writeUTF("IP");
                    out.writeUTF(addr.getAddress().getHostAddress());
                    out.writeInt(addr.getPort());
                });
            }
            case "IPOther" -> {
                String who = in.readUTF();
                proxy.playerRegistry().byName(who).ifPresent(other -> {
                    InetSocketAddress addr = other.remoteAddress();
                    reply(player, channel, out -> {
                        out.writeUTF("IPOther");
                        out.writeUTF(other.username());
                        out.writeUTF(addr.getAddress().getHostAddress());
                        out.writeInt(addr.getPort());
                    });
                });
            }
            case "PlayerCount" -> {
                String target = in.readUTF();
                int count = "ALL".equals(target)
                        ? proxy.playerRegistry().count()
                        : proxy.serverRegistry().get(target)
                                .map(HyperGravelServer::playerCount).orElse(0);
                reply(player, channel, out -> {
                    out.writeUTF("PlayerCount");
                    out.writeUTF(target);
                    out.writeInt(count);
                });
            }
            case "PlayerList" -> {
                String target = in.readUTF();
                String names = "ALL".equals(target)
                        ? proxy.playerRegistry().all().stream()
                                .map(ConnectedPlayer::username).collect(Collectors.joining(", "))
                        : proxy.serverRegistry().get(target)
                                .map(s -> s.connectedPlayers().stream()
                                        .map(ConnectedPlayer::username)
                                        .collect(Collectors.joining(", ")))
                                .orElse("");
                reply(player, channel, out -> {
                    out.writeUTF("PlayerList");
                    out.writeUTF(target);
                    out.writeUTF(names);
                });
            }
            case "GetServers" -> {
                String names = proxy.serverRegistry().all().stream()
                        .map(HyperGravelServer::name).collect(Collectors.joining(", "));
                reply(player, channel, out -> {
                    out.writeUTF("GetServers");
                    out.writeUTF(names);
                });
            }
            case "GetServer" -> {
                String name = player.currentHyperGravelServer()
                        .map(HyperGravelServer::name).orElse("");
                reply(player, channel, out -> {
                    out.writeUTF("GetServer");
                    out.writeUTF(name);
                });
            }
            case "GetPlayerServer" -> {
                String who = in.readUTF();
                String name = proxy.playerRegistry().byName(who)
                        .flatMap(ConnectedPlayer::currentHyperGravelServer)
                        .map(HyperGravelServer::name).orElse("");
                reply(player, channel, out -> {
                    out.writeUTF("GetPlayerServer");
                    out.writeUTF(who);
                    out.writeUTF(name);
                });
            }
            case "UUID" -> reply(player, channel, out -> {
                out.writeUTF("UUID");
                out.writeUTF(undashed(player.uuid()));
            });
            case "UUIDOther" -> {
                String who = in.readUTF();
                proxy.playerRegistry().byName(who).ifPresent(other -> reply(player, channel, out -> {
                    out.writeUTF("UUIDOther");
                    out.writeUTF(other.username());
                    out.writeUTF(undashed(other.uuid()));
                }));
            }
            case "ServerIP" -> {
                String target = in.readUTF();
                proxy.serverRegistry().get(target).ifPresent(server -> {
                    InetSocketAddress addr = server.address();
                    reply(player, channel, out -> {
                        out.writeUTF("ServerIP");
                        out.writeUTF(target);
                        out.writeUTF(addr.getAddress() == null
                                ? addr.getHostString() : addr.getAddress().getHostAddress());
                        out.writeShort(addr.getPort());
                    });
                });
            }
            case "Message", "MessageRaw" -> {
                String target = in.readUTF();
                String body = in.readUTF();
                Component component = "MessageRaw".equals(subchannel)
                        ? GsonComponentSerializer.gson().deserialize(body)
                        : LegacyComponentSerializer.legacySection().deserialize(body);
                if ("ALL".equals(target)) {
                    for (ConnectedPlayer p : proxy.playerRegistry().all()) {
                        p.sendMessage(component);
                    }
                } else {
                    proxy.playerRegistry().byName(target)
                            .ifPresent(p -> p.sendMessage(component));
                }
            }
            case "KickPlayer" -> {
                String who = in.readUTF();
                String reason = in.readUTF();
                proxy.playerRegistry().byName(who).ifPresent(other -> other.disconnect(
                        LegacyComponentSerializer.legacySection().deserialize(reason)));
            }
            case "Forward", "ForwardToPlayer" -> forward(proxy, player, origin, channel, subchannel, in);
            default -> LOGGER.debug("unhandled {} subchannel '{}' from {}",
                    channel, subchannel, origin == null ? "?" : origin.name());
        }
    }

    private static void forward(HyperGravelProxy proxy, ConnectedPlayer player,
                                HyperGravelServer origin, String channel,
                                String subchannel, DataInputStream in) throws IOException {
        String target = in.readUTF();
        String forwardChannel = in.readUTF();
        int length = in.readUnsignedShort();
        byte[] payload = new byte[length];
        in.readFully(payload);

        byte[] framed = build(out -> {
            out.writeUTF(forwardChannel);
            out.writeShort(payload.length);
            out.write(payload);
        });
        if (framed.length == 0) {
            return;
        }

        if ("ForwardToPlayer".equals(subchannel)) {
            proxy.playerRegistry().byName(target)
                    .ifPresent(other -> other.sendPluginMessageToBackend(channel, framed));
            return;
        }

        List<HyperGravelServer> targets = new ArrayList<>();
        switch (target.toUpperCase(Locale.ROOT)) {
            case "ALL" -> proxy.serverRegistry().all().stream()
                    .filter(s -> s != origin).forEach(targets::add);
            case "ONLINE" -> proxy.serverRegistry().all().stream()
                    .filter(s -> s != origin && s.health() == ServerHealth.UP)
                    .forEach(targets::add);
            default -> proxy.serverRegistry().get(target).ifPresent(targets::add);
        }
        for (HyperGravelServer server : targets) {
            server.sendPluginMessage(channel, framed);
        }
    }

    private static void connect(HyperGravelProxy proxy, ConnectedPlayer player, String target) {
        Optional<HyperGravelServer> server = proxy.serverRegistry().get(target);
        if (server.isEmpty()) {
            LOGGER.warn("{} asked to Connect {} to unknown server '{}'",
                    player.currentHyperGravelServer().map(HyperGravelServer::name).orElse("?"),
                    player.username(), target);
            return;
        }
        player.connect(server.get());
    }

    




    private static boolean reachable(HyperGravelProxy proxy, ConnectedPlayer player, String target) {
        Optional<HyperGravelServer> server = proxy.serverRegistry().get(target);
        if (server.isEmpty()) return true;   
        media.gitm.hypergravel.api.server.ServerInfo info = server.get().info();
        if (player.permissionValue("hypergravel.server.bypass") == media.gitm.hypergravel.api.permission.Tristate.TRUE) {
            return true;
        }
        return !info.restricted();
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    private static void reply(ConnectedPlayer player, String channel, Writer writer) {
        byte[] bytes = build(writer);
        if (bytes.length > 0) {
            player.sendPluginMessageToBackend(channel, bytes);
        }
    }

    private static byte[] build(Writer writer) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            writer.write(out);
        } catch (IOException e) {

            LOGGER.error("failed to build a {} reply", CHANNEL, e);
            return new byte[0];
        }
        return buffer.toByteArray();
    }

    private static String undashed(UUID uuid) {
        return uuid.toString().replace("-", "");
    }
}
