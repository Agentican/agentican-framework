package ai.agentican.framework.tools.composio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

public class ComposioClient {

    private static final Logger LOG = LoggerFactory.getLogger(ComposioClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASE_URL = "https://backend.composio.dev";

    private final String apiKey;
    private final String userId;
    private final HttpClient httpClient;

    public static ComposioClient of(String apiKey, String userId) {

        return new ComposioClient(apiKey, userId);
    }

    public ComposioClient(String apiKey, String userId) {

        this.apiKey = apiKey;
        this.userId = userId;
        this.httpClient = HttpClient.newHttpClient();
    }

    public List<ComposioToolkit> availableToolkits() {

        var accounts = discoverConnectedAccounts();

        if (accounts.isEmpty()) {

            LOG.warn("No active Composio connected accounts found for user '{}'", userId);
            return List.of();
        }

        LOG.info("Found {} connected toolkit(s) for user '{}': {}", accounts.size(), userId, accounts.keySet());

        return accounts.values().stream()
                .map(info -> new ComposioToolkit(apiKey, userId, info.slug, info.displayName,
                        info.accountId, httpClient))
                .toList();
    }

    private record ConnectedAccount(String slug, String displayName, String accountId) {}

    private Map<String, ConnectedAccount> discoverConnectedAccounts() {

        var accounts = new LinkedHashMap<String, ConnectedAccount>();

        try {

            var url = BASE_URL + "/api/v3/connected_accounts?user_ids=" + userId + "&limit=100";

            var root = getJson(url);

            StreamSupport.stream(root.path("items").spliterator(), false)
                    .filter(item -> "ACTIVE".equalsIgnoreCase(item.path("status").asText("")))
                    .filter(item -> !item.path("toolkit").path("slug").asText("").isEmpty())
                    .filter(item -> !item.path("id").asText("").isEmpty())
                    .forEach(item -> {

                        var slug = item.path("toolkit").path("slug").asText("").toLowerCase();
                        var accountId = item.path("id").asText("");
                        var name = fetchToolkitDisplayName(slug);

                        accounts.putIfAbsent(slug, new ConnectedAccount(slug, name, accountId));
                    });
        }
        catch (Exception e) {

            LOG.error("Failed to fetch Composio connected accounts: {}", e.getMessage());
        }

        return accounts;
    }

    private String fetchToolkitDisplayName(String slug) {

        try {
            var root = getJson(BASE_URL + "/api/v3/toolkits/" + slug);
            var name = root.path("name").asText(slug);
            LOG.info("Toolkit '{}' display name: '{}'", slug, name);
            return name;
        } catch (Exception e) {
            LOG.warn("Failed to fetch toolkit display name for '{}': {}", slug, e.getMessage());
            return slug;
        }
    }

    private JsonNode getJson(String url) throws IOException, InterruptedException {

        var request = HttpRequest.newBuilder().uri(URI.create(url)).header("x-api-key", apiKey).GET().build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400)
            throw new IOException("HTTP " + response.statusCode() + ": " + (response.body().length() > 200 ? response.body().substring(0, 200) + "..." : response.body()));

        return JSON.readTree(response.body());
    }
}
