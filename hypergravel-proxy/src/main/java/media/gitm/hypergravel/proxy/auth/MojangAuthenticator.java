package media.gitm.hypergravel.proxy.auth;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import media.gitm.hypergravel.proxy.protocol.packet.GameProfile;

public final class MojangAuthenticator {

    private static final String HAS_JOINED =
            "https://sessionserver.mojang.com/session/minecraft/hasJoined";

    private final HttpClient http;

    public MojangAuthenticator(Executor executor) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .executor(executor)
                .build();
    }

    public CompletableFuture<Result> hasJoined(String username, String serverIdHash,
                                               InetAddress clientIp) {
        StringBuilder uri = new StringBuilder(HAS_JOINED)
                .append("?username=").append(URLEncoder.encode(username, StandardCharsets.UTF_8))
                .append("&serverId=").append(URLEncoder.encode(serverIdHash, StandardCharsets.UTF_8));
        if (clientIp != null && !clientIp.isAnyLocalAddress() && !clientIp.isLoopbackAddress()) {
            uri.append("&ip=").append(URLEncoder.encode(clientIp.getHostAddress(),
                    StandardCharsets.UTF_8));
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(uri.toString()))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(MojangAuthenticator::parse);
    }

    private static Result parse(HttpResponse<String> response) {

        if (response.statusCode() == 204) {
            return Result.unauthenticated();
        }
        if (response.statusCode() != 200) {
            return Result.unavailable("session server returned HTTP " + response.statusCode());
        }
        try {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            UUID uuid = parseUndashedUuid(json.get("id").getAsString());
            String name = json.get("name").getAsString();

            List<GameProfile.Property> properties = new ArrayList<>(2);
            JsonElement propertiesElement = json.get("properties");
            if (propertiesElement != null && propertiesElement.isJsonArray()) {
                JsonArray array = propertiesElement.getAsJsonArray();
                for (JsonElement element : array) {
                    JsonObject property = element.getAsJsonObject();
                    properties.add(new GameProfile.Property(
                            property.get("name").getAsString(),
                            property.get("value").getAsString(),
                            property.has("signature")
                                    ? property.get("signature").getAsString()
                                    : null));
                }
            }
            return Result.authenticated(new GameProfile(uuid, name,
                    properties.toArray(GameProfile.Property[]::new)));
        } catch (RuntimeException e) {
            return Result.unavailable("malformed session server response: " + e);
        }
    }

    public static UUID parseUndashedUuid(String undashed) {
        if (undashed.length() != 32) {
            throw new IllegalArgumentException("not an undashed UUID: " + undashed);
        }
        return new UUID(
                Long.parseUnsignedLong(undashed.substring(0, 16), 16),
                Long.parseUnsignedLong(undashed.substring(16), 16));
    }

    public record Result(Status status, GameProfile profile, String detail) {

        public enum Status {
            AUTHENTICATED,
            UNAUTHENTICATED,
            UNAVAILABLE
        }

        static Result authenticated(GameProfile profile) {
            return new Result(Status.AUTHENTICATED, profile, null);
        }

        static Result unauthenticated() {
            return new Result(Status.UNAUTHENTICATED, null, null);
        }

        static Result unavailable(String detail) {
            return new Result(Status.UNAVAILABLE, null, detail);
        }

        public boolean ok() {
            return status == Status.AUTHENTICATED;
        }
    }

    public static IOException asIoException(Throwable t) {
        return t instanceof IOException io ? io : new IOException(t);
    }
}
