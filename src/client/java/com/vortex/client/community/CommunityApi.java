package com.vortex.client.community;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches shared macros and presets from the website.
 *
 * Read only. Nothing is ever sent from the client -- no account details, no
 * settings, nothing about the servers you play on. Sharing happens on the site,
 * where you are signed in and can put a name to what you post.
 *
 * That restriction is the point rather than a shortcut: a client that can post
 * on your behalf is a client that can post without you noticing.
 */
public final class CommunityApi {

    /**
     * Short timeouts.
     *
     * The website sleeps when nobody has used it for a while and takes a moment
     * to wake up. Ten seconds is enough for that and short enough that an
     * unreachable site does not leave you staring at a spinner.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    /** Refuses replies far larger than any list could sensibly be. */
    private static final int MAX_LENGTH = 512 * 1024;

    private static HttpClient client;

    private CommunityApi() {}

    private static synchronized HttpClient http() {
        if (client == null) {
            client = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return client;
    }

    /**
     * Fetches a URL as text.
     *
     * @throws Exception if the site cannot be reached or answers with an error
     */
    public static String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(READ_TIMEOUT)
                .header("User-Agent", "VortexClient")
                .GET()
                .build();

        HttpResponse<String> response =
                http().send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Website answered " + response.statusCode());
        }
        String body = response.body();
        if (body != null && body.length() > MAX_LENGTH) {
            throw new IllegalStateException("Reply far larger than expected");
        }
        return body;
    }
}
