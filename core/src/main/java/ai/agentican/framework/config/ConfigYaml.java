package ai.agentican.framework.config;

import ai.agentican.framework.util.DotEnv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared YAML loading + {@code ${ENV}} resolution for {@link EngineConfig} and {@link CatalogConfig}. */
final class ConfigYaml {

    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    static final ObjectMapper YAML =
            new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());

    private ConfigYaml() {}

    static <T> T load(Path path, Class<T> type) throws IOException {

        return load(Files.readString(path), type);
    }

    static <T> T load(InputStream input, Class<T> type) throws IOException {

        return load(new String(input.readAllBytes(), StandardCharsets.UTF_8), type);
    }

    private static <T> T load(String raw, Class<T> type) throws IOException {

        return YAML.readValue(resolveEnvVars(raw), type);
    }

    private static String resolveEnvVars(String input) {

        var matcher = ENV_PATTERN.matcher(input);
        var sb = new StringBuilder();

        while (matcher.find()) {

            var name = matcher.group(1);
            var value = System.getenv(name);

            if (value == null) value = DotEnv.get(name);

            if (value == null) {

                var dotEnvInfo = DotEnv.loadedFrom() != null
                        ? "Loaded .env from: " + DotEnv.loadedFrom() + " (but it has no '" + name + "' entry)."
                        : "No .env file found. Searched:\n  " + String.join(
                                "\n  ", DotEnv.searchedPaths().stream().map(Path::toString).toList());

                throw new IllegalStateException(
                        "Environment variable '" + name + "' is not set (referenced in config as ${" + name
                                + "}).\nCWD: " + Path.of(".").toAbsolutePath().normalize()
                                + "\n" + dotEnvInfo);
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }

        matcher.appendTail(sb);
        return sb.toString();
    }
}
