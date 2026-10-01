package com.curelingo.curelingo.publicdata.mysql.egen;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class EgenClient {
    public static final String HOSPITAL_FULL_PATH = "/HsptlAsembySearchService/getHsptlMdcncFullDown";
    public static final String DEPARTMENT_PATH = "/HsptlAsembySearchService/getHsptlMdcncListInfoInqire";
    public static final String BED_STATUS_PATH = "/ErmctInfoInqireService/getEmrrmRltmUsefulSckbdInfoInqire";

    private final EgenProperties properties;
    private final RestClient restClient;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile long lastRequestNanos;

    public EgenClient(EgenProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public void beginRun() {
        requests.set(0);
        lastRequestNanos = 0;
    }

    public int requestCount() {
        return requests.get();
    }

    public EgenPage fetch(String path, int pageNo, int pageSize, Map<String, String> filters) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("Set CURELINGO_PUBLIC_DATA_SYNC_API_KEY before starting the importer");
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            waitForRequestSlot();
            int requestNumber = requests.incrementAndGet();
            if (requestNumber > properties.maxRequestsPerRun()) {
                throw new IllegalStateException("E-Gen request budget exceeded for this run");
            }
            try {
                JsonNode root = restClient.get()
                        .uri(requestUri(path, pageNo, pageSize, filters))
                        .retrieve()
                        .body(JsonNode.class);
                return parsePage(root);
            } catch (EgenApiException e) {
                if (!"05".equals(e.resultCode()) && !"23".equals(e.resultCode())) throw e;
                if (attempt == 2) throw e;
                pause(Duration.ofSeconds(attempt + 1L));
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (status != 429 && !e.getStatusCode().is5xxServerError()) {
                    throw new EgenApiException("HTTP_" + status);
                }
                if (attempt == 2) throw new EgenApiException("HTTP_" + status);
                pause(Duration.ofSeconds(attempt + 1L));
            } catch (ResourceAccessException e) {
                if (attempt == 2) throw new EgenApiException("NETWORK_" + rootCauseName(e));
                pause(Duration.ofSeconds(attempt + 1L));
            } catch (RestClientException e) {
                throw new EgenApiException("HTTP_CLIENT_" + e.getClass().getSimpleName());
            }
        }
        throw new IllegalStateException("Unreachable E-Gen retry state");
    }

    private EgenPage parsePage(JsonNode root) {
        if (root == null) throw new EgenApiException("EMPTY_RESPONSE");
        JsonNode response = root.path("response");
        String resultCode = response.path("header").path("resultCode").asText("");
        if (!"00".equals(resultCode)) throw new EgenApiException(resultCode.isBlank() ? "MISSING_RESULT_CODE" : resultCode);

        JsonNode body = response.path("body");
        if (!body.has("totalCount")) throw new EgenApiException("MISSING_TOTAL_COUNT");
        int totalCount = body.path("totalCount").asInt(-1);
        if (totalCount < 0) throw new EgenApiException("INVALID_TOTAL_COUNT");

        JsonNode itemsNode = body.path("items").path("item");
        List<JsonNode> items = new ArrayList<>();
        if (itemsNode.isArray()) itemsNode.forEach(items::add);
        else if (itemsNode.isObject()) items.add(itemsNode);
        return new EgenPage(totalCount, List.copyOf(items));
    }

    private void waitForRequestSlot() {
        int count = requests.get();
        if (count >= properties.maxRequestsPerRun()) {
            throw new IllegalStateException("E-Gen request budget exceeded for this run");
        }
        long minimumNanos = properties.requestDelay().toNanos();
        long elapsed = System.nanoTime() - lastRequestNanos;
        if (lastRequestNanos != 0 && elapsed < minimumNanos) {
            pause(Duration.ofNanos(minimumNanos - elapsed));
        }
        lastRequestNanos = System.nanoTime();
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis(), duration.toNanosPart() % 1_000_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to call E-Gen", e);
        }
    }

    private static String rootCauseName(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getClass().getSimpleName();
    }

    private URI requestUri(String path, int pageNo, int pageSize, Map<String, String> filters) {
        StringBuilder url = new StringBuilder(properties.baseUrl())
                .append(path)
                .append("?serviceKey=").append(encode(properties.apiKey()))
                .append("&pageNo=").append(pageNo)
                .append("&numOfRows=").append(pageSize);
        filters.forEach((name, value) -> url.append('&').append(name).append('=').append(encode(value)));
        return URI.create(url.append("&_type=json").toString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
