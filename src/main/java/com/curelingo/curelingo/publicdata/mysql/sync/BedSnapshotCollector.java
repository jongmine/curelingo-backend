package com.curelingo.curelingo.publicdata.mysql.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.curelingo.curelingo.publicdata.mysql.egen.EgenClient;
import com.curelingo.curelingo.publicdata.mysql.egen.EgenPage;
import com.curelingo.curelingo.publicdata.mysql.egen.EgenProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Collects and validates every page before current-state rows are changed. */
@Component
public class BedSnapshotCollector {
    private final EgenClient client;
    private final EgenProperties properties;

    public BedSnapshotCollector(EgenClient client, EgenProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public List<JsonNode> collect() {
        int pageSize = properties.pageSize();
        if (pageSize < 1) throw new IllegalStateException("Invalid E-Gen page size");
        List<JsonNode> rows = new ArrayList<>();
        Set<String> identifiers = new HashSet<>();
        int expected = -1;
        for (int pageNumber = 1; ; pageNumber++) {
            EgenPage page = client.fetch(EgenClient.BED_STATUS_PATH, pageNumber, pageSize, Map.of());
            if (expected == -1) {
                expected = page.totalCount();
                if (expected == 0 || expected > 10_000) {
                    throw new IllegalStateException("Unexpected bed snapshot size");
                }
            } else if (expected != page.totalCount()) {
                throw new IllegalStateException("Bed totalCount changed during pagination");
            }
            int expectedPageRows = Math.min(pageSize, expected - rows.size());
            if (page.items().size() != expectedPageRows) {
                throw new IllegalStateException("Incomplete bed snapshot page");
            }
            for (JsonNode item : page.items()) {
                String hpid = item.path("hpid").asText("").trim();
                if (hpid.isEmpty() || hpid.length() > 32 || !identifiers.add(hpid)) {
                    throw new IllegalStateException("Missing or duplicate bed hpid");
                }
                rows.add(item);
            }
            if (rows.size() == expected) return List.copyOf(rows);
        }
    }
}
