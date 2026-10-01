package com.curelingo.curelingo.publicdata.mysql.egen;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record EgenPage(int totalCount, List<JsonNode> items) {
}
