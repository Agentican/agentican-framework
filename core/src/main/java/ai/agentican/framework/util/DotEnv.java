package ai.agentican.framework.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a {@code .env} file once (starting in the current working directory and
 * walking upward up to 5 parent levels) and exposes its entries as a fallback
 * for {@code System.getenv()} lookups.
 *
 * <p>Format: one {@code KEY=VALUE} per line. Blank lines and lines starting with
 * {@code #} are ignored. An optional {@code export } prefix is stripped. Values
 * may be wrapped in single or double quotes; inline {@code #} comments are
 * stripped only from unquoted values.
 */
public final class DotEnv {

    private static final int MAX_PARENTS = 8;

    private static final List<Path> SEARCHED = new ArrayList<>();
    private static volatile Path LOADED_FROM;

    private static final Map<String, String> VALUES = load();

    public static String get(String name) {

        return VALUES.get(name);
    }

    public static Map<String, String> all() {

        return Map.copyOf(VALUES);
    }

    public static Path loadedFrom() {

        return LOADED_FROM;
    }

    public static List<Path> searchedPaths() {

        return List.copyOf(SEARCHED);
    }

    private static Map<String, String> load() {

        var path = findDotEnv();

        if (path == null) return Map.of();

        LOADED_FROM = path;

        var map = new LinkedHashMap<String, String>();

        try {

            for (var line : Files.readAllLines(path))
                parseLine(line, map);
        }
        catch (IOException _) {
            return Map.of();
        }

        return map;
    }

    private static Path findDotEnv() {

        var cwd = Path.of(".").toAbsolutePath().normalize();

        for (int i = 0; i <= MAX_PARENTS && cwd != null; i++) {

            var candidate = cwd.resolve(".env");

            SEARCHED.add(candidate);

            if (Files.isRegularFile(candidate)) return candidate;

            cwd = cwd.getParent();
        }

        return null;
    }

    private static void parseLine(String line, Map<String, String> into) {

        var trimmed = line.trim();

        if (trimmed.isEmpty() || trimmed.startsWith("#")) return;

        if (trimmed.startsWith("export "))
            trimmed = trimmed.substring("export ".length()).trim();

        var eq = trimmed.indexOf('=');

        if (eq <= 0) return;

        var key = trimmed.substring(0, eq).trim();
        var value = trimmed.substring(eq + 1).trim();

        if (!isQuoted(value)) {

            var hash = value.indexOf('#');
            if (hash >= 0) value = value.substring(0, hash).trim();
        }

        if (isQuoted(value)) value = value.substring(1, value.length() - 1);

        into.put(key, value);
    }

    private static boolean isQuoted(String s) {

        return s.length() >= 2
                && ((s.startsWith("\"") && s.endsWith("\""))
                        || (s.startsWith("'") && s.endsWith("'")));
    }

    private DotEnv() {}
}
