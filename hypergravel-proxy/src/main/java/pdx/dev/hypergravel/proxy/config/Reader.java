package pdx.dev.hypergravel.proxy.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.InMemoryFormat;
import pdx.dev.hypergravel.api.config.ConfigSection;

public final class Reader implements ConfigSection {

    private static final Config EMPTY = Config.of(InMemoryFormat.defaultInstance());

    public static Reader ofEmpty(String path) {
        return new Reader(EMPTY, path);
    }

    private final Config config;
    private final String path;

    public Reader(Config config, String path) {
        this.config = config == null ? EMPTY : config;
        this.path = path;
    }

    private String pathOf(String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    public Reader section(String key) {
        Object value = config.get(key);
        if (value == null) {
            return new Reader(EMPTY, pathOf(key));
        }
        if (!(value instanceof Config nested)) {
            throw new HyperGravelConfig.ConfigException(pathOf(key) + " must be a table");
        }
        return new Reader(nested, pathOf(key));
    }

    @Override
    public boolean contains(String key) {
        return config.get(key) != null;
    }

    public String string(String key, String fallback) {
        Object value = config.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof String s)) {
            throw new HyperGravelConfig.ConfigException(pathOf(key) + " must be a string");
        }
        return s;
    }

    public int integer(String key, int fallback) {
        Object value = config.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        throw new HyperGravelConfig.ConfigException(pathOf(key) + " must be an integer");
    }

    public boolean bool(String key, boolean fallback) {
        Object value = config.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        throw new HyperGravelConfig.ConfigException(pathOf(key) + " must be true or false");
    }

    public List<String> stringList(String key) {
        Object value = config.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new HyperGravelConfig.ConfigException(pathOf(key) + " must be a list");
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object element : list) {
            if (!(element instanceof String s)) {
                throw new HyperGravelConfig.ConfigException(pathOf(key) + " must contain only strings");
            }
            result.add(s);
        }
        return List.copyOf(result);
    }

    public Duration duration(String key, Duration fallback) {
        Object value = config.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n) {
            return Duration.ofMillis(n.longValue());
        }
        if (value instanceof String s) {
            return parseDuration(s, pathOf(key));
        }
        throw new HyperGravelConfig.ConfigException(pathOf(key) + " must be a duration");
    }

    static Duration parseDuration(String raw, String path) {
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            throw new HyperGravelConfig.ConfigException(path + " is an empty duration");
        }
        int split = 0;
        while (split < text.length()
                && (Character.isDigit(text.charAt(split)) || text.charAt(split) == '.')) {
            split++;
        }
        String number = text.substring(0, split);
        String unit = text.substring(split).trim();
        if (number.isEmpty()) {
            throw new HyperGravelConfig.ConfigException(path + " has no numeric part: '" + raw + "'");
        }
        double amount;
        try {
            amount = Double.parseDouble(number);
        } catch (NumberFormatException e) {
            throw new HyperGravelConfig.ConfigException(path + " is not a number: '" + raw + "'");
        }
        long millis = switch (unit) {
            case "", "ms" -> (long) amount;
            case "s", "sec", "secs" -> (long) (amount * 1000);
            case "m", "min", "mins" -> (long) (amount * 60_000);
            case "h", "hr", "hrs" -> (long) (amount * 3_600_000);
            case "d" -> (long) (amount * 86_400_000);
            default -> throw new HyperGravelConfig.ConfigException(
                    path + " has an unknown time unit '" + unit + "' in '" + raw + "'");
        };
        return Duration.ofMillis(millis);
    }

    @Override
    public List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (Config.Entry entry : config.entrySet()) {
            keys.add(entry.getKey());
        }
        return List.copyOf(keys);
    }

    @Override
    public Optional<String> getString(String key) {
        Object value = config.get(key);
        return value instanceof String s ? Optional.of(s) : Optional.empty();
    }

    @Override
    public Optional<Integer> getInt(String key) {
        Object value = config.get(key);
        return value instanceof Number n ? Optional.of(n.intValue()) : Optional.empty();
    }

    @Override
    public Optional<Long> getLong(String key) {
        Object value = config.get(key);
        return value instanceof Number n ? Optional.of(n.longValue()) : Optional.empty();
    }

    @Override
    public Optional<Double> getDouble(String key) {
        Object value = config.get(key);
        return value instanceof Number n ? Optional.of(n.doubleValue()) : Optional.empty();
    }

    @Override
    public Optional<Boolean> getBoolean(String key) {
        Object value = config.get(key);
        return value instanceof Boolean b ? Optional.of(b) : Optional.empty();
    }

    @Override
    public Optional<Duration> getDuration(String key) {
        Object value = config.get(key);
        if (value instanceof Number n) {
            return Optional.of(Duration.ofMillis(n.longValue()));
        }
        if (value instanceof String s) {
            return Optional.of(parseDuration(s, pathOf(key)));
        }
        return Optional.empty();
    }

    @Override
    public List<String> getStringList(String key) {
        return stringList(key);
    }

    @Override
    public Optional<ConfigSection> getSection(String key) {
        Object value = config.get(key);
        return value instanceof Config nested
                ? Optional.of(new Reader(nested, pathOf(key)))
                : Optional.empty();
    }
}
