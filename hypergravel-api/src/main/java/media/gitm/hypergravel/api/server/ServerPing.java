package media.gitm.hypergravel.api.server;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.kyori.adventure.text.Component;

public record ServerPing(
        Version version,
        Players players,
        Component description,
        String faviconBase64,
        boolean enforcesSecureChat) {

    public record Version(int protocol, String name) {}

    public record Players(int online, int max, List<SamplePlayer> sample) {}

    public record SamplePlayer(String name, UUID id) {}

    public Optional<String> favicon() {
        return Optional.ofNullable(faviconBase64);
    }

    public Builder toBuilder() {
        return new Builder(version, players, description, faviconBase64, enforcesSecureChat);
    }

    public static final class Builder {
        private Version version;
        private Players players;
        private Component description;
        private String faviconBase64;
        private boolean enforcesSecureChat;

        Builder(Version version, Players players, Component description,
                String faviconBase64, boolean enforcesSecureChat) {
            this.version = version;
            this.players = players;
            this.description = description;
            this.faviconBase64 = faviconBase64;
            this.enforcesSecureChat = enforcesSecureChat;
        }

        public Builder version(Version version) {
            this.version = version;
            return this;
        }

        public Builder players(Players players) {
            this.players = players;
            return this;
        }

        public Builder description(Component description) {
            this.description = description;
            return this;
        }

        public Builder favicon(String base64) {
            this.faviconBase64 = base64;
            return this;
        }

        public Builder enforcesSecureChat(boolean value) {
            this.enforcesSecureChat = value;
            return this;
        }

        public ServerPing build() {
            return new ServerPing(version, players, description, faviconBase64, enforcesSecureChat);
        }
    }
}
