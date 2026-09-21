package pdx.dev.hypergravel.proxy.extension;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.electronwill.nightconfig.toml.TomlParser;
import pdx.dev.hypergravel.api.ProxyServer;
import pdx.dev.hypergravel.api.config.ConfigSection;
import pdx.dev.hypergravel.api.extension.ExtensionDescription;
import pdx.dev.hypergravel.api.extension.HyperGravelExtension;
import pdx.dev.hypergravel.proxy.config.Reader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Loads server-side plugins ("extensions") from a directory of jars.
 *
 * <p>Each jar gets its own {@link URLClassLoader} whose parent is the proxy
 * classloader, so an extension sees the API and every proxy dependency but not
 * the classes of its siblings - a broken plugin cannot poison the network
 * threads of another one. Extensions are enabled in dependency order
 * ({@link pdx.dev.hypergravel.api.extension.Extension#depends()} hard,
 * {@code softDepends()} best-effort) and disabled in reverse. A missing hard
 * dependency or a dependency cycle disables only that extension; the rest load.
 */
public final class ExtensionManager {

    private static final Logger LOGGER = LogManager.getLogger(ExtensionManager.class);

    private final ProxyServer proxy;
    private final Path jarDirectory;
    private final Path extensionsRoot;
    private final ClassLoader parentClassLoader;

    private final List<LoadedExtension> loaded = new ArrayList<>();
    private final List<LoadedExtension> enabled = new ArrayList<>();

    public ExtensionManager(ProxyServer proxy, Path jarDirectory, Path extensionsRoot,
                            ClassLoader parentClassLoader) {
        this.proxy = proxy;
        this.jarDirectory = jarDirectory;
        this.extensionsRoot = extensionsRoot;
        this.parentClassLoader = parentClassLoader;
    }

    private record Candidate(Path jar, ClassLoader classLoader, Class<?> type,
                             ExtensionDescription description) {}

    public void enableAll() {
        if (!Files.isDirectory(jarDirectory)) {
            if (Files.exists(jarDirectory)) {
                LOGGER.error("extension directory {} exists but is not a directory",
                        jarDirectory.toAbsolutePath());
            } else {
                LOGGER.info("no extension directory at {}; nothing to load",
                        jarDirectory.toAbsolutePath());
            }
            return;
        }

        List<Path> jars = listJars();
        if (jars.isEmpty()) {
            LOGGER.info("no extension jars in {}", jarDirectory.toAbsolutePath());
        }

        List<Candidate> candidates = new ArrayList<>();
        for (Path jar : jars) {
            URLClassLoader classLoader = new URLClassLoader(
                    new URL[] { toUrl(jar) }, parentClassLoader);
            ExtensionScanner.Result result = new ExtensionScanner(jar, classLoader).scan();

            for (ExtensionScanner.ScanFailure failure : result.failures()) {
                LOGGER.error("extension jar {}: {}", jar.getFileName(), failure.message());
            }
            for (ExtensionScanner.Found found : result.found()) {
                candidates.add(new Candidate(jar, classLoader, found.type(), found.description()));
            }
            if (result.found().isEmpty()) {
                closeQuietly(classLoader);
            }
        }

        for (Candidate candidate : candidates) {
            HyperGravelExtension plugin;
            try {
                plugin = (HyperGravelExtension) candidate.type()
                        .getDeclaredConstructor()
                        .newInstance();
            } catch (ReflectiveOperationException e) {
                LOGGER.error("extension '{}' (in {}) cannot be instantiated; skipped",
                        candidate.description().id(), candidate.jar().getFileName(), e);
                closeQuietly(candidate.classLoader());
                continue;
            }
            loaded.add(new LoadedExtension(candidate.jar(), candidate.description(),
                    candidate.classLoader(), plugin));
        }

        List<LoadedExtension> ordered = orderByDependencies(loaded);

        int enabledCount = 0;
        int brokenCount = 0;
        for (LoadedExtension extension : ordered) {
            if (extension.state() == LoadedExtension.State.BROKEN) {
                LOGGER.error("extension '{}' skipped: {}",
                        extension.description().id(),
                        extension.failure() == null ? "unknown reason"
                                : extension.failure().getMessage());
                brokenCount++;
                continue;
            }
            if (enableOne(extension)) {
                enabled.add(extension);
                enabledCount++;
            } else {
                brokenCount++;
            }
        }

        LOGGER.info("extensions: {} jar(s), {} loaded, {} enabled, {} failed",
                jars.size(), loaded.size(), enabledCount, brokenCount);
    }

    private boolean enableOne(LoadedExtension extension) {
        ExtensionDescription description = extension.description();
        Path dataDirectory = extensionsRoot.resolve(description.id());
        ConfigSection config = loadConfig(extensionsRoot.resolve(description.id() + ".toml"),
                description.id());

        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            LOGGER.error("extension '{}': cannot create data directory {}",
                    description.id(), dataDirectory, e);
            markBroken(extension, e);
            return false;
        }

        var context = new HyperGravelExtensionContext(extension, proxy,
                LogManager.getLogger("ext-" + description.id()),
                dataDirectory, dataDirectory, config);
        extension.plugin().setContext(context);

        try {
            extension.state(LoadedExtension.State.ENABLED);
            extension.plugin().onEnable();
            LOGGER.info("extension '{}' v{} enabled: {}",
                    description.id(), description.version(), description.name());
            return true;
        } catch (Throwable t) {
            markBroken(extension, t);
            LOGGER.error("extension '{}' threw in onEnable; disabled", description.id(), t);
            return false;
        }
    }

    public void disableAll() {
        for (int i = enabled.size() - 1; i >= 0; i--) {
            LoadedExtension extension = enabled.get(i);
            try {
                extension.plugin().onDisable();
                extension.state(LoadedExtension.State.DISABLED);
                LOGGER.info("extension '{}' v{} disabled",
                        extension.description().id(), extension.description().version());
            } catch (Throwable t) {
                extension.state(LoadedExtension.State.BROKEN);
                extension.failure(t);
                LOGGER.error("extension '{}' threw in onDisable",
                        extension.description().id(), t);
            } finally {
                closeQuietly(extension.classLoader());
            }
        }
        enabled.clear();
    }

    public List<LoadedExtension> loaded() {
        return List.copyOf(loaded);
    }

    public List<LoadedExtension> enabled() {
        return List.copyOf(enabled);
    }

    private static void markBroken(LoadedExtension extension, Throwable failure) {
        extension.state(LoadedExtension.State.BROKEN);
        extension.failure(failure);
    }

    private ConfigSection loadConfig(Path file, String id) {
        if (file == null || !Files.isReadable(file)) {
            return Reader.ofEmpty(id);
        }
        try (var reader = Files.newBufferedReader(file)) {
            return new Reader(new TomlParser().parse(reader), id);
        } catch (Exception e) {
            LOGGER.error("extension '{}': cannot parse {}: {}", id, file, e.toString());
            return Reader.ofEmpty(id);
        }
    }

    private static URL toUrl(Path jar) {
        try {
            return jar.toUri().toURL();
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot build a URL for " + jar, e);
        }
    }

    private static void closeQuietly(ClassLoader classLoader) {
        if (classLoader instanceof URLClassLoader urlClassLoader) {
            try {
                urlClassLoader.close();
            } catch (IOException ignored) {
            }
        }
    }

    private List<Path> listJars() {
        try (Stream<Path> stream = Files.list(jarDirectory)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            LOGGER.error("cannot list extension directory {}: {}",
                    jarDirectory.toAbsolutePath(), e.toString());
            return List.of();
        }
    }

    /**
     * Order the extensions so hard dependencies are enabled first. Soft
     * dependencies only influence the choice between ready candidates, so a
     * dependent prefers to follow its soft dependency without ever blocking or
     * delaying unrelated extensions.
     */
    private static List<LoadedExtension> orderByDependencies(List<LoadedExtension> loaded) {
        Map<String, LoadedExtension> byId = new LinkedHashMap<>();
        for (LoadedExtension extension : loaded) {
            LoadedExtension prior = byId.putIfAbsent(extension.description().id(), extension);
            if (prior != null) {
                markBroken(extension, new IllegalArgumentException("duplicate extension id '"
                        + extension.description().id() + "'"));
            }
        }

        for (LoadedExtension extension : loaded) {
            for (String depends : extension.description().depends()) {
                if (!byId.containsKey(depends)) {
                    markBroken(extension, new IllegalStateException(
                            "missing dependency '" + depends + "'"));
                    break;
                }
            }
        }

        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> edges = new HashMap<>();
        for (LoadedExtension extension : loaded) {
            inDegree.put(extension.description().id(), 0);
        }
        for (LoadedExtension extension : loaded) {
            if (extension.state() == LoadedExtension.State.BROKEN) {
                continue;
            }
            for (String depends : extension.description().depends()) {
                if (!byId.containsKey(depends)) {
                    continue;
                }
                edges.computeIfAbsent(depends, ignored -> new ArrayList<>())
                        .add(extension.description().id());
                inDegree.merge(extension.description().id(), 1, Integer::sum);
            }
        }

        List<String> ready = inDegree.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        List<LoadedExtension> result = new ArrayList<>();
        java.util.Set<String> emitted = new java.util.HashSet<>();
        while (!ready.isEmpty()) {
            String id = select(ready, byId, emitted);
            ready.remove(id);
            LoadedExtension extension = byId.get(id);
            if (extension != null && extension.state() != LoadedExtension.State.BROKEN) {
                result.add(extension);
            }
            emitted.add(id);
            for (String next : edges.getOrDefault(id, List.of())) {
                if (inDegree.merge(next, -1, Integer::sum) == 0) {
                    ready.add(next);
                }
            }
            ready.sort(Comparator.naturalOrder());
        }

        for (LoadedExtension extension : loaded) {
            if (extension.state() == LoadedExtension.State.BROKEN) {
                continue;
            }
            if (!result.contains(extension)) {
                markBroken(extension, new IllegalStateException("dependency cycle in "
                        + extension.description().depends()));
            }
        }
        return result;
    }

    /**
     * Among candidates that are ready (all hard dependencies already emitted),
     * prefer the one whose present soft dependencies have all been emitted. This
     * pulls soft dependents after their target without ever stalling the sort.
     */
    private static String select(List<String> ready, Map<String, LoadedExtension> byId,
                                 java.util.Set<String> emitted) {
        String best = ready.get(0);
        int bestPending = softPending(best, byId, emitted);
        for (int i = 1; i < ready.size(); i++) {
            String candidate = ready.get(i);
            int pending = softPending(candidate, byId, emitted);
            if (pending < bestPending) {
                best = candidate;
                bestPending = pending;
            }
        }
        return best;
    }

    private static int softPending(String id, Map<String, LoadedExtension> byId,
                                   java.util.Set<String> emitted) {
        LoadedExtension extension = byId.get(id);
        if (extension == null || extension.state() == LoadedExtension.State.BROKEN) {
            return 0;
        }
        int pending = 0;
        for (String softDepends : extension.description().softDepends()) {
            LoadedExtension dependency = byId.get(softDepends);
            if (dependency != null
                    && dependency.state() != LoadedExtension.State.BROKEN
                    && !emitted.contains(softDepends)) {
                pending++;
            }
        }
        return pending;
    }
}