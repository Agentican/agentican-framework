package ai.agentican.framework;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class TestEnv {

    private static final Map<String, String> ENV = load();

    private static Map<String, String> load() {

        var env = new HashMap<String, String>();

        for (var candidate : new String[]{"framework/.env", ".env"}) {

            var path = Path.of(candidate);

            if (Files.exists(path)) {

                try {

                    Files.readAllLines(path).stream()
                            .filter(line -> !line.isBlank() && !line.startsWith("#") && line.contains("="))
                            .forEach(line -> {
                                int eq = line.indexOf('=');
                                env.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                            });

                    break;
                }
                catch (Exception ignored) {}
            }
        }

        return env;
    }

    public static String get(String key) {

        var value = ENV.get(key);

        return value != null ? value : System.getenv(key);
    }

    public static String require(String key) {

        var value = get(key);

        if (value == null || value.isBlank())
            throw new IllegalStateException("Missing env var: " + key + " (set in .env or environment)");

        return value;
    }
}
