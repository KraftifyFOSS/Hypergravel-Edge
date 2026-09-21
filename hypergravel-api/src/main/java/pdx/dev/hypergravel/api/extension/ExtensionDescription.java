package pdx.dev.hypergravel.api.extension;

import java.util.List;
import java.util.Objects;

public record ExtensionDescription(
        String id,
        String name,
        String version,
        String description,
        List<String> authors,
        List<String> depends,
        List<String> softDepends) {

    public ExtensionDescription {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        if (id.isBlank()) {
            throw new IllegalArgumentException("extension id must not be blank");
        }
        authors = authors == null ? List.of() : List.copyOf(authors);
        depends = depends == null ? List.of() : List.copyOf(depends);
        softDepends = softDepends == null ? List.of() : List.copyOf(softDepends);
    }

    public static ExtensionDescription from(Extension annotation) {
        String name = annotation.name().isBlank() ? annotation.id() : annotation.name();
        return new ExtensionDescription(
                annotation.id(),
                name,
                annotation.version(),
                annotation.description(),
                List.of(annotation.authors()),
                List.of(annotation.depends()),
                List.of(annotation.softDepends()));
    }
}