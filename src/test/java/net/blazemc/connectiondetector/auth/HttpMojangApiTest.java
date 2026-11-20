package net.blazemc.connectiondetector.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import net.blazemc.connectiondetector.config.PluginConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpMojangApiTest {

    private HttpServer server;
    private String prefix;
    private volatile int status = 200;
    private volatile String body = "{\"name\":\"Blaz3Craft\",\"id\":\"11111111111141118111111111111111\"}";
    private volatile long delayMs = 0;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/users/profiles/minecraft/", exchange -> {
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
            if (status != 204) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        prefix = "http://127.0.0.1:" + server.getAddress().getPort() + "/users/profiles/minecraft/";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void http200YieldsPremiumProfile() {
        HttpMojangApi api = api(5000, 0);
        MojangLookupResult result = api.lookupByUsername("Blaz3Craft");
        assertEquals(MojangLookupResult.Status.FOUND, result.status());
        assertEquals("Blaz3Craft", result.profile().orElseThrow().canonicalUsername());
    }

    @Test
    void http404IsDefinitiveNotFound() {
        status = 404;
        body = "";
        assertEquals(MojangLookupResult.Status.NOT_FOUND, api(5000, 0).lookupByUsername("CrackedGuy").status());
    }

    @Test
    void http429IsUnavailableNotCracked() {
        status = 429;
        body = "rate";
        MojangLookupResult result = api(5000, 0).lookupByUsername("MaybePremium");
        assertEquals(MojangLookupResult.Status.RATE_LIMITED, result.status());
        assertTrue(result.isUnavailable());
    }

    @Test
    void http5xxIsUnavailableNotCracked() {
        status = 503;
        body = "down";
        MojangLookupResult result = api(5000, 0).lookupByUsername("MaybePremium");
        assertEquals(MojangLookupResult.Status.ERROR, result.status());
        assertTrue(result.isUnavailable());
    }

    @Test
    void timeoutIsUnavailableNotCracked() {
        delayMs = 1500;
        MojangLookupResult result = api(200, 0).lookupByUsername("MaybePremium");
        assertEquals(MojangLookupResult.Status.TIMEOUT, result.status());
        assertTrue(result.isUnavailable());
    }

    private HttpMojangApi api(int timeoutMs, int retries) {
        PluginConfig defaults = PluginConfig.defaults();
        PluginConfig config = new PluginConfig(
                defaults.general(),
                defaults.premiumDetection(),
                new PluginConfig.Mojang(timeoutMs, timeoutMs, 0, 0, 5, retries),
                defaults.cracked(),
                defaults.limboauth(),
                defaults.uuid(),
                defaults.database(),
                defaults.logging(),
                defaults.messages()
        );
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        return new HttpMojangApi(config, client, prefix);
    }
}
