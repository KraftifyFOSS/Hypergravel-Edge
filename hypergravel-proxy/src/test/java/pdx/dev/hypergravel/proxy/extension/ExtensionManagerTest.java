package pdx.dev.hypergravel.proxy.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;

import pdx.dev.hypergravel.api.ProxyServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionManagerTest {

    @TempDir
    Path temp;

    private Path jarDir;
    private Path extensionsRoot;

    @BeforeEach
    void setUp() throws Exception {
        jarDir = temp.resolve("extensions");
        extensionsRoot = temp.resolve("extensions-root");
        java.nio.file.Files.createDirectories(jarDir);
        ExtensionSink.reset();
    }

    @AfterEach
    void tearDown() {
        ExtensionSink.reset();
    }

    private static ProxyServer stubProxy() {
        return (ProxyServer) Proxy.newProxyInstance(ProxyServer.class.getClassLoader(),
                new Class<?>[] { ProxyServer.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "version" -> "test";
                    case "playerCount" -> 0;
                    case "players", "servers" -> List.of();
                    default -> null;
                });
    }

    private ExtensionManager manager() {
        return new ExtensionManager(stubProxy(), jarDir, extensionsRoot,
                ExtensionManagerTest.class.getClassLoader());
    }

    @Test
    void enablesInDependencyOrderAndDisablesInReverse() throws Exception {
        TestJars.write(jarDir, "a.jar", SampleExtensionA.class);
        TestJars.write(jarDir, "b.jar", SampleExtensionB.class);
        TestJars.write(jarDir, "c.jar", SampleExtensionC.class);

        ExtensionManager manager = manager();
        manager.enableAll();

        assertEquals(List.of("enable:a", "enable:b", "enable:c"), ExtensionSink.events());
        assertEquals(3, manager.enabled().size());

        manager.disableAll();

        assertEquals(List.of("enable:a", "enable:b", "enable:c",
                "disable:c", "disable:b", "disable:a"), ExtensionSink.events());
    }

    @Test
    void pullsSoftDependentsAfterTheirTarget() throws Exception {
        TestJars.write(jarDir, "aaa.jar", SoftDependentExtension.class);
        TestJars.write(jarDir, "zzz.jar", SoftTargetExtension.class);

        ExtensionManager manager = manager();
        manager.enableAll();

        assertEquals(List.of("enable:zzz", "enable:soft"), ExtensionSink.events());
    }

    @Test
    void skipsAnExtensionWhoseHardDependencyIsMissing() throws Exception {
        TestJars.write(jarDir, "m.jar", MissingDependencyExtension.class);
        TestJars.write(jarDir, "a.jar", SampleExtensionA.class);

        ExtensionManager manager = manager();
        manager.enableAll();

        assertEquals(List.of("enable:a"), ExtensionSink.events());
        assertEquals(1, manager.enabled().size());

        var missing = find(manager, "missing-dep");
        assertEquals(LoadedExtension.State.BROKEN, missing.state());
        assertTrue(missing.failure().getMessage().contains("missing dependency 'ghost-dependency'"));
    }

    @Test
    void skipsAnExtensionInADependencyCycle() throws Exception {
        TestJars.write(jarDir, "1.jar", CycleOneExtension.class);
        TestJars.write(jarDir, "2.jar", CycleTwoExtension.class);

        ExtensionManager manager = manager();
        manager.enableAll();

        assertTrue(manager.enabled().isEmpty());
        assertEquals(LoadedExtension.State.BROKEN, find(manager, "cycle-one").state());
        assertEquals(LoadedExtension.State.BROKEN, find(manager, "cycle-two").state());
        assertTrue(find(manager, "cycle-one").failure().getMessage().contains("cycle"));
    }

    @Test
    void anExtensionThatThrowsInOnEnableIsBrokenButOthersStillLoad() throws Exception {
        TestJars.write(jarDir, "a.jar", SampleExtensionA.class);
        TestJars.write(jarDir, "f.jar", FailingExtension.class);

        ExtensionManager manager = manager();
        manager.enableAll();

        assertEquals(List.of("enable:a", "enable:failing"), ExtensionSink.events());
        assertEquals(1, manager.enabled().size());
        assertEquals(LoadedExtension.State.BROKEN, find(manager, "failing").state());
        assertTrue(find(manager, "failing").failure().getMessage().contains("boom"));
    }

    @Test
    void duplicateIdsLeaveOnlyTheFirstEnabled() throws Exception {
        TestJars.write(jarDir, "d1.jar", DuplicateExtensionOne.class);
        TestJars.write(jarDir, "d2.jar", DuplicateExtensionTwo.class);

        ExtensionManager manager = manager();
        manager.enableAll();

        assertEquals(List.of("enable:dup1"), ExtensionSink.events());
        assertEquals(1, manager.enabled().size());
        assertEquals("dup-id", manager.enabled().get(0).description().id());
        assertEquals(1, manager.loaded().stream()
                .filter(extension -> extension.state() == LoadedExtension.State.BROKEN)
                .count());
    }

    @Test
    void anEmptyDirectoryIsNotAnError() {
        ExtensionManager manager = manager();
        manager.enableAll();

        assertTrue(manager.loaded().isEmpty());
        assertTrue(manager.enabled().isEmpty());
    }

    private static LoadedExtension find(ExtensionManager manager, String id) {
        return manager.loaded().stream()
                .filter(extension -> extension.description().id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no loaded extension '" + id + "'"));
    }
}