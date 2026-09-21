package pdx.dev.hypergravel.proxy.extension;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import pdx.dev.hypergravel.api.extension.Extension;
import pdx.dev.hypergravel.api.extension.ExtensionDescription;
import pdx.dev.hypergravel.api.extension.HyperGravelExtension;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Finds every {@link Extension @Extension}-annotated class inside one jar.
 *
 * <p>The scan is a {@link ClassLoader#loadClass(String)}-only walk: classes are
 * loaded but never initialized, so no static initialiser of a plugin runs until
 * the loader instantiates the annotated class. Inner classes, {@code module-info}
 * and anything that fails to load are skipped; load failures are reported so the
 * manager can surface a broken jar instead of silently ignoring it.
 */
public final class ExtensionScanner {

    private static final Logger LOGGER = LogManager.getLogger(ExtensionScanner.class);

    private final Path jar;
    private final ClassLoader classLoader;

    ExtensionScanner(Path jar, ClassLoader classLoader) {
        this.jar = jar;
        this.classLoader = classLoader;
    }

    public record Found(Class<?> type, ExtensionDescription description) {}

    public record ScanFailure(String className, String message) {}

    public record Result(List<Found> found, List<ScanFailure> failures, boolean hasMainClass) {}

    public Result scan() {
        List<Found> found = new ArrayList<>();
        List<ScanFailure> failures = new ArrayList<>();
        boolean hasMainClass = false;

        try (JarFile jarFile = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.endsWith("module-info.class")) {
                    continue;
                }
                if (name.endsWith("package-info.class")) {
                    continue;
                }
                if (!name.endsWith(".class")) {
                    continue;
                }

                String className = name.substring(0, name.length() - ".class".length())
                        .replace('/', '.');
                if (className.contains("$")) {
                    continue;
                }

                Class<?> type;
                try {
                    type = classLoader.loadClass(className);
                } catch (Throwable t) {
                    failures.add(new ScanFailure(className, t.getClass().getSimpleName()
                            + ": " + t.getMessage()));
                    LOGGER.debug("extension scan of {}: {} failed to load: {}",
                            jar.getFileName(), className, t.toString());
                    continue;
                }

                Extension annotation = type.getAnnotation(Extension.class);
                if (annotation == null) {
                    continue;
                }
                if (!HyperGravelExtension.class.isAssignableFrom(type)) {
                    failures.add(new ScanFailure(className, "annotated with @Extension but does "
                            + "not extend HyperGravelExtension"));
                    continue;
                }
                hasMainClass = true;
                found.add(new Found(type, ExtensionDescription.from(annotation)));
            }
        } catch (Exception e) {
            failures.add(new ScanFailure(jar.getFileName().toString(),
                    "cannot read jar: " + e.toString()));
        }

        return new Result(List.copyOf(found), List.copyOf(failures), hasMainClass);
    }
}