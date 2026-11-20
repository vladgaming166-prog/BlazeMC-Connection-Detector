package net.blazemc.connectiondetector.auth;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import net.blazemc.connectiondetector.PluginConstants;
import net.blazemc.connectiondetector.config.PluginConfig;

public final class HttpMojangApi implements MojangApi {

    public static final String PROFILE_ENDPOINT = "https://api.mojang.com/users/profiles/minecraft/";

    private final HttpClient client;
    private final PluginConfig config;
    private final Semaphore inflight;
    private final String endpointPrefix;
    private static final Executor LIMITER_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "blazemc-mojang-limiter");
        thread.setDaemon(true);
        return thread;
    });

    private final Executor limiterExecutor = LIMITER_EXECUTOR;

    public HttpMojangApi(PluginConfig config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.mojang().apiTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), PROFILE_ENDPOINT);
    }

    public HttpMojangApi(PluginConfig config, HttpClient client) {
        this(config, client, PROFILE_ENDPOINT);
    }

    public HttpMojangApi(PluginConfig config, HttpClient client, String endpointPrefix) {
        this.config = config;
        this.client = client;
        this.inflight = new Semaphore(config.mojang().maximumConcurrentRequests());
        this.endpointPrefix = endpointPrefix.endsWith("/") ? endpointPrefix : endpointPrefix + "/";
    }

    @Override
    public CompletableFuture<MojangLookupResult> lookupByUsernameAsync(String username) {
        if (!UsernameValidator.isValid(username)) {
            return CompletableFuture.completedFuture(MojangLookupResult.error("invalid username", 400));
        }
        return attempt(username, 1);
    }

    private CompletableFuture<MojangLookupResult> attempt(String username, int attempt) {
        return acquirePermitAsync().thenCompose(ignored ->
                executeOnceAsync(username).whenComplete((result, error) -> inflight.release())
                        .thenCompose(result -> {
                            if (shouldRetry(result) && attempt <= config.mojang().retryCount()) {
                                long delayMs = (long) Math.pow(2, attempt - 1) * 200L;
                                CompletableFuture<MojangLookupResult> delayed = new CompletableFuture<>();
                                CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
                                        .execute(() -> attempt(username, attempt + 1)
                                                .whenComplete((retried, retryError) -> {
                                                    if (retryError != null) {
                                                        delayed.complete(mapFailure(retryError));
                                                    } else {
                                                        delayed.complete(retried);
                                                    }
                                                }));
                                return delayed;
                            }
                            return CompletableFuture.completedFuture(result);
                        })
                        .exceptionally(HttpMojangApi::mapFailure)
        );
    }

    private CompletableFuture<Void> acquirePermitAsync() {
        if (inflight.tryAcquire()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                inflight.acquire();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted waiting for Mojang API permit", ex);
            }
        }, limiterExecutor);
    }

    private boolean shouldRetry(MojangLookupResult result) {
        return result.status() == MojangLookupResult.Status.RATE_LIMITED
                || result.status() == MojangLookupResult.Status.TIMEOUT
                || result.status() == MojangLookupResult.Status.ERROR;
    }

    private CompletableFuture<MojangLookupResult> executeOnceAsync(String username) {
        String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpointPrefix + encoded))
                .timeout(Duration.ofMillis(config.mojang().requestTimeoutMs()))
                .header("Accept", "application/json")
                .header("User-Agent", PluginConstants.NAME + "/" + PluginConstants.VERSION)
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(this::toResult)
                .exceptionally(HttpMojangApi::mapFailure);
    }

    MojangLookupResult toResult(HttpResponse<String> response) {
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
        if (status == 403) {
            return MojangLookupResult.error("HTTP 403", status);
        }
        return MojangLookupResult.error("HTTP " + status, status);
    }

    static MojangLookupResult mapFailure(Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        if (cause instanceof java.net.http.HttpTimeoutException
                || message.toLowerCase().contains("timed out")
                || message.toLowerCase().contains("timeout")) {
            return MojangLookupResult.timeout(message);
        }
        return MojangLookupResult.error(message, -1);
    }
}
