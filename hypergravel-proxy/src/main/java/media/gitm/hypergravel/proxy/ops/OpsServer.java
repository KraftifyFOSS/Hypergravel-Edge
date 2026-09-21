package media.gitm.hypergravel.proxy.ops;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import media.gitm.hypergravel.api.server.ServerHealth;
import media.gitm.hypergravel.proxy.HyperGravelProxy;
import media.gitm.hypergravel.proxy.backend.HyperGravelServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class OpsServer {

    private static final Logger LOGGER = LogManager.getLogger(OpsServer.class);

    private final HyperGravelProxy proxy;
    private final EventLoopGroup group;
    private Channel channel;

    public OpsServer(HyperGravelProxy proxy, EventLoopGroup group) {
        this.proxy = proxy;
        this.group = group;
    }

    public void start(String host, int port) {
        try {
            channel = new ServerBootstrap()
                    .group(group)
                    .channel(proxy.transport().serverChannelType())
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline()
                                    .addLast(new HttpServerCodec())
                                    .addLast(new HttpObjectAggregator(8 * 1024 * 1024))
                                    .addLast(new Handler());
                        }
                    })
                    .bind(new InetSocketAddress(host, port))
                    .sync()
                    .channel();
            LOGGER.info("ops endpoint on http://{}:{} (/health, /ready, /metrics)", host, port);
        } catch (Exception e) {
            LOGGER.error("cannot bind the ops endpoint on {}:{}: {}", host, port, e.toString());
        }
    }

    public void stop() {
        if (channel != null) {
            channel.close();
        }
    }

    private final class Handler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            String path = request.uri();
            int query = path.indexOf('?');
            if (query >= 0) {
                path = path.substring(0, query);
            }

            if (path.startsWith("/epack/")) {
                editor(ctx, request, path);
                return;
            }

            switch (path) {
                case "/health" -> respond(ctx, HttpResponseStatus.OK, "text/plain", "ok\n");
                









                case "/announce" -> {
                    var editor = proxy.packEditor();
                    String given = request.headers().get("X-Editor-Secret");
                    if (editor == null || given == null || !given.equals(editor.secret())) {
                        respond(ctx, HttpResponseStatus.FORBIDDEN, "application/json",
                                "{\"error\":\"bad secret\"}");
                        return;
                    }
                    byte[] body = new byte[request.content().readableBytes()];
                    request.content().readBytes(body);
                    String said = new String(body, StandardCharsets.UTF_8).trim();
                    if (said.isEmpty()) {
                        respond(ctx, HttpResponseStatus.BAD_REQUEST, "application/json",
                                "{\"error\":\"nothing to say\"}");
                        return;
                    }
                    int reached = proxy.networkChat().announce(said);
                    respond(ctx, HttpResponseStatus.OK, "application/json",
                            "{\"reached\":" + reached + "}");
                }
                case "/ready" -> {
                    boolean ready = proxy.serverRegistry().all().stream()
                            .anyMatch(server -> server.health() == ServerHealth.UP);
                    respond(ctx, ready ? HttpResponseStatus.OK
                                    : HttpResponseStatus.SERVICE_UNAVAILABLE,
                            "text/plain", ready ? "ready\n" : "no backend is up\n");
                }
                case "/metrics" -> respond(ctx, HttpResponseStatus.OK,
                        "text/plain; version=0.0.4", renderMetrics());
                default -> respond(ctx, HttpResponseStatus.NOT_FOUND, "text/plain",
                        "try /health, /ready or /metrics\n");
            }
        }

        







        private void editor(ChannelHandlerContext ctx, FullHttpRequest request, String path) {
            var packEditor = proxy.packEditor();
            var packService = proxy.resourcePack();
            if (packEditor == null || packService == null) {
                respond(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "application/json",
                        "{\"error\":\"the pack is not built by this proxy\"}");
                return;
            }
            String secret = request.headers().get("X-Editor-Secret");
            if (secret == null || !secret.equals(packEditor.secret())) {
                respond(ctx, HttpResponseStatus.FORBIDDEN, "application/json",
                        "{\"error\":\"bad secret\"}");
                return;
            }

            java.util.Map<String, String> query = new java.util.LinkedHashMap<>();
            int mark = request.uri().indexOf('?');
            if (mark >= 0) {
                for (String pair : request.uri().substring(mark + 1).split("&")) {
                    int eq = pair.indexOf('=');
                    if (eq <= 0) continue;
                    query.put(java.net.URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                            java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
                }
            }

            try {
                
                
                if (path.equals("/epack/mint")) {
                    String name = query.getOrDefault("player", "");
                    var session = packEditor.mint(java.util.UUID.nameUUIDFromBytes(
                            ("ops:" + name).getBytes(StandardCharsets.UTF_8)), name);
                    if (session == null) {
                        respond(ctx, HttpResponseStatus.FORBIDDEN, "application/json",
                                "{\"error\":\"not on the editor list\"}");
                        return;
                    }
                    respond(ctx, HttpResponseStatus.OK, "application/json",
                            "{\"token\":" + pdx.dev.hypergravel.pack.PackEditor.json(session.token())
                            + ",\"name\":" + pdx.dev.hypergravel.pack.PackEditor.json(session.name())
                            + ",\"expires\":" + pdx.dev.hypergravel.pack.PackEditor.json(session.expires().toString())
                            + "}");
                    return;
                }

                var session = packEditor.session(query.get("token"));
                if (session == null) {
                    respond(ctx, HttpResponseStatus.UNAUTHORIZED, "application/json",
                            "{\"error\":\"that link has expired - run /epack again\"}");
                    return;
                }

                switch (path) {
                    case "/epack/session" -> respond(ctx, HttpResponseStatus.OK, "application/json",
                            "{\"name\":" + pdx.dev.hypergravel.pack.PackEditor.json(session.name())
                            + ",\"expires\":" + pdx.dev.hypergravel.pack.PackEditor.json(session.expires().toString())
                            + ",\"sha1\":" + pdx.dev.hypergravel.pack.PackEditor.json(String.valueOf(packService.hash()))
                            + ",\"players\":" + proxy.playerCount() + "}");
                    case "/epack/list" -> {
                        StringBuilder json = new StringBuilder("[");
                        for (var entry : packEditor.list()) {
                            if (json.length() > 1) json.append(',');
                            json.append("{\"path\":").append(pdx.dev.hypergravel.pack.PackEditor.json(entry.path()))
                                .append(",\"size\":").append(entry.size())
                                .append(",\"edited\":").append(entry.overridden()).append('}');
                        }
                        respond(ctx, HttpResponseStatus.OK, "application/json", json.append(']').toString());
                    }
                    case "/epack/file" -> {
                        byte[] bytes = packEditor.read(query.get("path"));
                        if (bytes == null) {
                            respond(ctx, HttpResponseStatus.NOT_FOUND, "application/json",
                                    "{\"error\":\"no such file in the pack\"}");
                            return;
                        }
                        respondBytes(ctx, bytes, query.getOrDefault("path", "").endsWith(".png")
                                ? "image/png" : "application/octet-stream");
                    }
                    case "/epack/put" -> {
                        byte[] body = new byte[request.content().readableBytes()];
                        request.content().readBytes(body);
                        packEditor.write(query.get("path"), body);
                        respond(ctx, HttpResponseStatus.OK, "application/json",
                                "{\"saved\":" + body.length + "}");
                    }
                    case "/epack/revert" -> respond(ctx, HttpResponseStatus.OK, "application/json",
                            "{\"reverted\":" + packEditor.revert(query.get("path")) + "}");
                    case "/epack/publish" -> {
                        String sha1 = packService.republish();
                        respond(ctx, HttpResponseStatus.OK, "application/json",
                                "{\"sha1\":" + pdx.dev.hypergravel.pack.PackEditor.json(String.valueOf(sha1))
                                + ",\"players\":" + proxy.playerCount() + "}");
                    }
                    default -> respond(ctx, HttpResponseStatus.NOT_FOUND, "application/json",
                            "{\"error\":\"no such editor route\"}");
                }
            } catch (Exception e) {
                LOGGER.warn("pack editor: {} failed: {}", path, e.toString());
                respond(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "application/json",
                        "{\"error\":" + pdx.dev.hypergravel.pack.PackEditor.json(String.valueOf(e.getMessage())) + "}");
            }
        }

        private void respondBytes(ChannelHandlerContext ctx, byte[] body, String contentType) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.copiedBuffer(body));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, body.length);
            response.headers().set(HttpHeaderNames.CONNECTION, "close");
            ctx.writeAndFlush(response).addListener(io.netty.channel.ChannelFutureListener.CLOSE);
        }

        private void respond(ChannelHandlerContext ctx, HttpResponseStatus status,
                             String contentType, String body) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, status,
                    Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH,
                    response.content().readableBytes());
            response.headers().set(HttpHeaderNames.CONNECTION, "close");
            ctx.writeAndFlush(response).addListener(io.netty.channel.ChannelFutureListener.CLOSE);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }

    private String renderMetrics() {
        StringBuilder out = new StringBuilder(2048);

        gauge(out, "hypergravel_players_online", "Players currently connected", proxy.playerCount());

        out.append("# HELP hypergravel_server_players Players per backend\n")
                .append("# TYPE hypergravel_server_players gauge\n");
        for (HyperGravelServer server : proxy.serverRegistry().all()) {
            out.append("hypergravel_server_players{server=\"").append(server.name())
                    .append("\"} ").append(server.playerCount()).append('\n');
        }

        out.append("# HELP hypergravel_server_up Backend health, 1 if routable\n")
                .append("# TYPE hypergravel_server_up gauge\n");
        for (HyperGravelServer server : proxy.serverRegistry().all()) {
            out.append("hypergravel_server_up{server=\"").append(server.name())
                    .append("\"} ").append(server.health().routable() ? 1 : 0).append('\n');
        }

        out.append("# HELP hypergravel_server_latency_ms Last successful ping round-trip\n")
                .append("# TYPE hypergravel_server_latency_ms gauge\n");
        for (HyperGravelServer server : proxy.serverRegistry().all()) {
            if (server.latencyMillis() >= 0) {
                out.append("hypergravel_server_latency_ms{server=\"").append(server.name())
                        .append("\"} ").append(server.latencyMillis()).append('\n');
            }
        }

        counter(out, "hypergravel_connections_rejected_total",
                "Connections rejected by the per-IP throttle",
                proxy.connectionThrottle().rejectedCount());
        counter(out, "hypergravel_logins_rejected_total",
                "Logins rejected by the per-IP login throttle",
                proxy.loginThrottle().rejectedCount());
        gauge(out, "hypergravel_throttle_tracked_addresses",
                "Source addresses currently tracked by the throttle",
                proxy.connectionThrottle().trackedAddresses());

        var allocatorMetric = proxy.pooledAllocator().metric();
        gauge(out, "hypergravel_buffer_pool_used_direct_bytes",
                "Direct memory held by the Netty pooled allocator",
                allocatorMetric.usedDirectMemory());
        gauge(out, "hypergravel_buffer_pool_arenas", "Direct arena count",
                allocatorMetric.numDirectArenas());

        Runtime runtime = Runtime.getRuntime();
        gauge(out, "hypergravel_jvm_heap_used_bytes", "Heap in use",
                runtime.totalMemory() - runtime.freeMemory());
        gauge(out, "hypergravel_jvm_heap_max_bytes", "Maximum heap", runtime.maxMemory());

        return out.toString();
    }

    private static void gauge(StringBuilder out, String name, String help, long value) {
        out.append("# HELP ").append(name).append(' ').append(help).append('\n')
                .append("# TYPE ").append(name).append(" gauge\n")
                .append(name).append(' ').append(value).append('\n');
    }

    private static void counter(StringBuilder out, String name, String help, long value) {
        out.append("# HELP ").append(name).append(' ').append(help).append('\n')
                .append("# TYPE ").append(name).append(" counter\n")
                .append(name).append(' ').append(value).append('\n');
    }
}
