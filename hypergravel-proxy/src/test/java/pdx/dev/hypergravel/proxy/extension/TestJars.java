package pdx.dev.hypergravel.proxy.extension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** Packs classes already compiled on the test classpath into temp jars. */
final class TestJars {

    private TestJars() {
    }

    static Path write(Path directory, String fileName, Class<?>... classes)
            throws IOException {
        Path jar = directory.resolve(fileName);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Class<?> type : classes) {
                String resource = "/" + type.getName().replace('.', '/') + ".class";
                byte[] bytes;
                try (var in = TestJars.class.getResourceAsStream(resource)) {
                    if (in == null) {
                        throw new IOException("no class resource for " + type);
                    }
                    bytes = in.readAllBytes();
                }
                String entryName = type.getName().replace('.', '/') + ".class";
                out.putNextEntry(new JarEntry(entryName));
                out.write(bytes);
                out.closeEntry();
            }
        }
        return jar;
    }
}