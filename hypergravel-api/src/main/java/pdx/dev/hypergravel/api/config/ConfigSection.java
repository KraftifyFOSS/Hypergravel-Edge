package pdx.dev.hypergravel.api.config;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface ConfigSection {

    boolean contains(String path);

    Optional<String> getString(String path);

    default String getString(String path, String fallback) {
        return getString(path).orElse(fallback);
    }

    Optional<Integer> getInt(String path);

    default int getInt(String path, int fallback) {
        return getInt(path).orElse(fallback);
    }

    Optional<Long> getLong(String path);

    default long getLong(String path, long fallback) {
        return getLong(path).orElse(fallback);
    }

    Optional<Double> getDouble(String path);

    default double getDouble(String path, double fallback) {
        return getDouble(path).orElse(fallback);
    }

    Optional<Boolean> getBoolean(String path);

    default boolean getBoolean(String path, boolean fallback) {
        return getBoolean(path).orElse(fallback);
    }

    Optional<Duration> getDuration(String path);

    default Duration getDuration(String path, Duration fallback) {
        return getDuration(path).orElse(fallback);
    }

    List<String> getStringList(String path);

    Optional<ConfigSection> getSection(String path);

    List<String> keys();
}
