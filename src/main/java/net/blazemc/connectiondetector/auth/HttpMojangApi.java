package net.blazemc.connectiondetector.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import net.blazemc.connectiondetector.PluginConstants;
import net.blazemc.connectiondetector.config.PluginConfig;

public final class HttpMojangApi implements MojangApi {

    public static final String PROFILE_ENDPOINT = "https://api.mojang.com/users/profiles/minecraft/";

    private final HttpClient client;
    private final PluginConfig config;

    public HttpMojangApi(PluginConfig config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.mojang().apiTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public HttpMojangApi(PluginConfig config, HttpClient client) {
        this.config = config;
        this.client = client;
    }

    @Override
    public MojangLookupResult lookupByUsername(String username) {
        if (!UsernameValidator.isValid(username)) {
            return MojangLookupResult.error("invalid username", 400);
        }

        MojangLookupResult lastError = MojangLookupResult.error("unknown", -1);
        int attempts = config.mojang().retryCount() + 1;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            MojangLookupResult result = executeOnce(username);
            if (result.status() == MojangLookupResult.Status.FOUND
                    || result.status() == MojangLookupResult.Status.NOT_FOUND) {
                return result;
            }
            lastError = result;
            if (result.status() != MojangLookupResult.Status.RATE_LIMITED
                    && result.status() != MojangLookupResult.Status.TIMEOUT
                    && result.status() != MojangLookupResult.Status.ERROR) {
                return result;
            }
        }
        return lastError;
    }

    private MojangLookupResult executeOnce(String username) {
        String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(PROFILE_ENDPOINT + encoded))
                .timeout(Duration.ofMillis(config.mojang().apiTimeoutMs()))
                .header("Accept", "application/json")
                .header("User-Agent", PluginConstants.NAME + "/" + PluginConstants.VERSION)
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 200) {
                return MojangProfileParser.parse(response.body())
                        .map(MojangLookupResult::found)
                        .orElseGet(() -> MojangLookupResult.invalidResponse("malformed Mojang profile payload", status));
            }
            if (status == 204 || status == 404) {
                return MojangLookupResult.notFound();
            }
            if (status == 429) {
                return MojangLookupResult.rateLimited(status);
            }
            return MojangLookupResult.error("HTTP " + status, status);
        } catch (java.net.http.HttpTimeoutException ex) {
            return MojangLookupResult.timeout(ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return MojangLookupResult.error("interrupted", -1);
        } catch (IOException ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            if (message.toLowerCase().contains("timed out") || message.toLowerCase().contains("timeout")) {
                return MojangLookupResult.timeout(message);
            }
            return MojangLookupResult.error(message, -1);
        }
    }
}
