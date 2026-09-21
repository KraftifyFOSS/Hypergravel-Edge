package pdx.dev.hypergravel.pack;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import media.gitm.hypergravel.proxy.HyperGravelProxy;
import media.gitm.hypergravel.proxy.player.ConnectedPlayer;
import media.gitm.hypergravel.proxy.protocol.PacketType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;














public final class ResourcePackService {

    private static final Logger LOGGER = LogManager.getLogger(ResourcePackService.class);

    private final HyperGravelProxy proxy;
    private final String url;
    private volatile String hash;
    private final boolean required;
    private final Component prompt;
    private final java.time.Duration delay;
    private final boolean kickOnDecline;
    private final Component notice;
    private final Component declineReason;

    private final AtomicInteger loaded = new AtomicInteger();
    private final AtomicInteger declined = new AtomicInteger();

    
    private final java.util.Set<UUID> saidNo = java.util.concurrent.ConcurrentHashMap.newKeySet();

    





    public record Variant(String name, String url, java.nio.file.Path file,
                          java.nio.file.Path extra, java.util.Set<String> servers,
                          boolean fallback) {}

    private final java.util.List<Variant> variants = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.Map<String, String> variantHash =
            new java.util.concurrent.ConcurrentHashMap<>();

    
    private record Worn(String variant, UUID offer) {}

    
    private record Offer(String variant, UUID id) {}

    private final java.util.Map<UUID, Worn> wearing =
            new java.util.concurrent.ConcurrentHashMap<>();

    







    private final java.util.Map<UUID, Offer> pending =
            new java.util.concurrent.ConcurrentHashMap<>();

    public boolean hasPack(UUID player) {
        return wearing.containsKey(player);
    }

    
    public String wearing(UUID player) {
        Worn worn = wearing.get(player);
        return worn == null ? null : worn.variant();
    }

    public void addVariant(Variant variant) {
        variants.add(variant);
    }

    public java.util.List<Variant> variants() {
        return java.util.List.copyOf(variants);
    }

    private Variant variantFor(String server) {
        String key = server == null ? "" : server.toLowerCase(java.util.Locale.ROOT);
        Variant fallback = null;
        for (Variant variant : variants) {
            if (variant.servers().contains(key)) {
                return variant;
            }
            if (variant.fallback() && fallback == null) {
                fallback = variant;
            }
        }
        return fallback;
    }

    





    public void applyFor(ConnectedPlayer player, String server) {
        if (variants.isEmpty()) {
            
            if (!hasPack(player.uuid())) {
                offerTo(player);
            }
            return;
        }
        Variant want = variantFor(server);
        if (want == null) {
            
            
            undress(player);
            return;
        }
        Worn worn = wearing.get(player.uuid());
        if (worn != null && worn.variant().equals(want.name())) {
            return;
        }
        swap(player, want);
    }

    
    public void undress(ConnectedPlayer player) {
        Worn worn = wearing.remove(player.uuid());
        if (worn == null) {
            return;
        }
        pop(player, worn.offer());
        LOGGER.info("pack: took {} off {}", worn.variant(), player.username());
    }

    private void swap(ConnectedPlayer player, Variant want) {
        if (saidNo.contains(player.uuid())) {
            return;
        }
        Worn worn = wearing.remove(player.uuid());
        if (worn != null) {
            pop(player, worn.offer());
        }
        if (delay.isZero() || delay.isNegative()) {
            push(player, want);
            return;
        }
        proxy.scheduler().delay(this, delay, () -> {
            var connection = player.connection();
            if (connection.active()) {
                connection.channel().eventLoop().execute(() -> push(player, want));
            }
        });
    }

    private void pop(ConnectedPlayer player, UUID offer) {
        var connection = player.connection();
        if (!connection.active()
                || connection.state() != media.gitm.hypergravel.proxy.protocol.ProtocolState.PLAY
                || !connection.supports(PacketType.RESOURCE_PACK_POP)) {
            return;
        }
        connection.channel().eventLoop().execute(() ->
                connection.writeAndFlush(new ResourcePackPackets.Pop(offer)));
    }

    public void offerTo(ConnectedPlayer player) {
        if (!variants.isEmpty()) {
            player.currentHyperGravelServer().ifPresentOrElse(
                    server -> applyFor(player, server.info().name()),
                    () -> applyFor(player, null));
            return;
        }
        if (url.isBlank() || saidNo.contains(player.uuid())) {
            return;
        }
        if (delay.isZero() || delay.isNegative()) {
            push(player, null);
            return;
        }
        proxy.scheduler().delay(this, delay, () -> {
            var connection = player.connection();
            if (connection.active()) {
                connection.channel().eventLoop().execute(() -> push(player, null));
            }
        });
    }

    
    public void offerNow(ConnectedPlayer player) {
        Variant want = null;
        if (!variants.isEmpty()) {
            want = variantFor(player.currentHyperGravelServer()
                    .map(server -> server.info().name()).orElse(null));
            if (want == null) {
                return;
            }
            
            Worn worn = wearing.remove(player.uuid());
            if (worn != null) {
                pop(player, worn.offer());
            }
        } else if (url.isBlank()) {
            return;
        }
        var connection = player.connection();
        Variant target = want;
        if (connection.active()) {
            connection.channel().eventLoop().execute(() -> push(player, target));
        }
    }

    private void push(ConnectedPlayer player, Variant variant) {
        var connection = player.connection();
        
        
        if (!connection.active()
                || connection.state() != media.gitm.hypergravel.proxy.protocol.ProtocolState.PLAY
                || !connection.supports(PacketType.RESOURCE_PACK_PUSH)) {
            return;
        }
        String name = variant == null ? "" : variant.name();
        String where = variant == null ? url : variant.url();
        String sha1 = variant == null ? hash : variantHash.getOrDefault(variant.name(), "");
        if (where == null || where.isBlank()) {
            return;
        }
        UUID offer = UUID.randomUUID();
        pending.put(player.uuid(), new Offer(name, offer));
        LOGGER.info("pack: offered {}{} to {} (sha1 {})", name.isEmpty() ? "" : name + " ",
                where, player.username(),
                sha1.isBlank() ? "unset" : sha1.substring(0, Math.min(8, sha1.length())));
        connection.writeAndFlush(new ResourcePackPackets.Push(
                offer, where, sha1, required, prompt));

        
        
        
        
        if (notice != null) {
            proxy.messenger().sendSystemMessage(player, notice);
            proxy.messenger().sendActionBar(player, notice);
        }
    }

    public boolean handleResponse(ConnectedPlayer player, ResourcePackPackets.Response response) {
        Offer offer = pending.get(player.uuid());
        if (offer == null || !offer.id().equals(response.id())) {
            return false;
        }
        if (response.settled()) {
            pending.remove(player.uuid());
            if (response.result() == 0) {
                loaded.incrementAndGet();
                
                
                wearing.put(player.uuid(), new Worn(offer.variant(), offer.id()));
                saidNo.remove(player.uuid());
                LOGGER.info("pack: {} has {} now", player.username(),
                        offer.variant().isEmpty() ? "it" : offer.variant());
            } else {
                declined.incrementAndGet();
                wearing.remove(player.uuid());
                LOGGER.info("pack: {} - {}", player.username(), response.describe());
                
                
                
                if (kickOnDecline && response.result() == 1) {
                    LOGGER.info("pack: {} declined it", player.username());
                    player.disconnect(declineReason);
                } else if (response.result() == 1) {
                    saidNo.add(player.uuid());
                }
            }
        }
        return true;
    }

    
    public void forget(UUID player) {
        wearing.remove(player);
        pending.remove(player);
        saidNo.remove(player);
    }

    public void logSummary() {
        if (variants.isEmpty()) {
            LOGGER.info("pack: offering {} (sha1 {}), {} loaded / {} not",
                    url, hash.isBlank() ? "unset" : hash.substring(0, Math.min(8, hash.length())),
                    loaded.get(), declined.get());
            return;
        }
        for (Variant variant : variants) {
            String sha1 = variantHash.getOrDefault(variant.name(), "");
            LOGGER.info("pack: {} -> {} (sha1 {}) for {}", variant.name(), variant.url(),
                    sha1.isBlank() ? "unset" : sha1.substring(0, Math.min(8, sha1.length())),
                    variant.servers().isEmpty() ? "anything unclaimed" : variant.servers());
        }
        LOGGER.info("pack: {} loaded / {} not", loaded.get(), declined.get());
    }

    public void buildInto(java.nio.file.Path destination) {
        buildInto(destination, java.util.Map.of());
    }

    private java.nio.file.Path builtInto;
    private java.util.Map<Character, byte[]> builtFaces = java.util.Map.of();

    








    public String republish() {
        if (builtInto == null) return null;
        buildInto(builtInto, builtFaces);
        for (ConnectedPlayer player : proxy.players().stream()
                .filter(ConnectedPlayer.class::isInstance)
                .map(ConnectedPlayer.class::cast)
                .toList()) {
            
            
            Worn worn = wearing.remove(player.uuid());
            if (worn != null) {
                pop(player, worn.offer());
            }
            offerTo(player);
        }
        LOGGER.info("pack: rebuilt and pushed to {} player(s)", proxy.playerCount());
        return hash;
    }

    






    public void buildInto(java.nio.file.Path destination, java.util.Map<Character, byte[]> faces) {
        this.builtInto = destination;
        this.builtFaces = faces;
        java.util.Map<String, byte[]> shared = PackEditor.overridesIn(destination);
        try {
            PackBuilder.overrides(shared);
            this.hash = PackBuilder.publish(destination, faces);
        } catch (Exception e) {
            LOGGER.error("pack: could not write {} - offering the existing file instead",
                    destination, e);
        }
        for (Variant variant : variants) {
            try {
                java.util.Map<String, byte[]> layered = new java.util.LinkedHashMap<>(shared);
                layered.putAll(PackEditor.filesIn(variant.extra()));
                PackBuilder.overrides(layered);
                variantHash.put(variant.name(), PackBuilder.publish(variant.file(), faces));
            } catch (Exception e) {
                LOGGER.error("pack: could not write the {} pack at {}",
                        variant.name(), variant.file(), e);
            }
        }
        PackBuilder.overrides(shared);
    }

    public ResourcePackService(HyperGravelProxy proxy, String url, String hash, boolean required,
                               String prompt, java.time.Duration delay, boolean kickOnDecline,
                               String notice) {
        this.kickOnDecline = kickOnDecline;
        this.notice = notice == null || notice.isBlank()
                ? null
                : MiniMessage.miniMessage().deserialize(notice);
        
        
        
        this.declineReason = MiniMessage.miniMessage().deserialize(
                "<white><bold>This server needs its resource pack.</bold>\n\n"
                + "<gray>It draws the terrain, the items, the menus and the board.\n"
                + "<gray>Without it you would be looking at a different game.\n\n"
                + "<white>Turn it on and it will never ask you again:\n"
                + "<gray>Multiplayer <dark_gray>-</dark_gray> click this server <dark_gray>-</dark_gray> "
                + "<white>Edit\n"
                + "<gray>Set <white>Server Resource Packs<gray> to <white>Enabled\n"
                + "<gray>then join back.");
        this.proxy = proxy;
        this.delay = delay == null ? java.time.Duration.ZERO : delay;
        this.url = url;
        this.hash = hash.toLowerCase(java.util.Locale.ROOT);
        this.required = required;
        this.prompt = prompt == null || prompt.isBlank()
                ? null
                : MiniMessage.miniMessage().deserialize(prompt);
    }

    
    public String hash() {
        return hash;
    }

    public String hash(String variant) {
        return variantHash.getOrDefault(variant, hash);
    }

    public boolean configured() {
        return !url.isBlank() || !variants.isEmpty();
    }

    public HyperGravelProxy proxy() {
        return proxy;
    }
}
