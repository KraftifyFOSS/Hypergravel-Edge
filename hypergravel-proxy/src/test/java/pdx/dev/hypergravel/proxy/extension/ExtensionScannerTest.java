package pdx.dev.hypergravel.proxy.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionScannerTest {

    @TempDir
    Path temp;

    private ExtensionScanner.Result scan(Class<?>... classes) throws Exception {
        Path jar = TestJars.write(temp, "extension.jar", classes);
        URLClassLoader loader = new URLClassLoader(
                new URL[] { jar.toUri().toURL() }, ExtensionScannerTest.class.getClassLoader());
        return new ExtensionScanner(jar, loader).scan();
    }

    @Test
    void findsAnnotatedClasses() throws Exception {
        ExtensionScanner.Result result = scan(SampleExtensionA.class);

        assertEquals(1, result.found().size());
        ExtensionScanner.Found found = result.found().get(0);
        assertEquals(SampleExtensionA.class.getName(), found.type().getName());
        assertEquals("a", found.description().id());
        assertEquals("A", found.description().name());
        assertEquals("1.0.0", found.description().version());
        assertTrue(result.failures().isEmpty());
        assertTrue(result.hasMainClass());
    }

    @Test
    void ignoresClassesWithoutTheAnnotation() throws Exception {
        ExtensionScanner.Result result = scan(ExtensionSink.class);

        assertTrue(result.found().isEmpty());
        assertTrue(!result.hasMainClass());
        assertTrue(result.failures().isEmpty());
    }

    @Test
    void reportsClassesAnnotatedButNotExtendingTheBase() throws Exception {
        ExtensionScanner.Result result = scan(NotAnExtensionClass.class);

        assertTrue(result.found().isEmpty());
        assertTrue(!result.failures().isEmpty());
        assertTrue(result.failures().get(0).message().contains("HyperGravelExtension"));
    }

    @Test
    void reportsAnUnreadableJarInsteadOfThrowing() throws Exception {
        Path notAJar = temp.resolve("broken.jar");
        Files.writeString(notAJar, "this is definitely not a zip");

        ExtensionScanner.Result result = new ExtensionScanner(notAJar,
                ExtensionScannerTest.class.getClassLoader()).scan();

        assertTrue(result.found().isEmpty());
        assertTrue(!result.failures().isEmpty());
    }
}