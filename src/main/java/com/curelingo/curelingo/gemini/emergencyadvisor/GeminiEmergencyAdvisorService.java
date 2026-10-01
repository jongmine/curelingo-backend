package com.curelingo.curelingo.gemini.emergencyadvisor;

import com.curelingo.curelingo.gemini.GeminiRestClient;
import com.curelingo.curelingo.gemini.prompt.GeminiEmergencyAdvisorPromptBuilder;
import com.curelingo.curelingo.emergencyhospital.dto.EmergencyBedStatus;
import com.curelingo.curelingo.publicdata.mysql.db.EmergencyHospitalRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GeminiEmergencyAdvisorService {

    private final GeminiRestClient geminiRestClient;
    private final EmergencyHospitalRepository emergencyHospitalRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<EmergencyBedStatus> findNearbyEmergencyBeds(double lat, double lng, double radiusKm) {
        if (!Double.isFinite(radiusKm) || radiusKm < 0 || radiusKm > Integer.MAX_VALUE / 1000.0) {
            throw new IllegalArgumentException("radiusKm must be a finite non-negative radius");
        }
        return emergencyHospitalRepository.findNearbyEmergencyBeds(lat, lng, (int) Math.round(radiusKm * 1000.0));
    }

    public GeminiEmergencyRecommendationResponse recommendNearbyEmergency(
            double lat, double lng, double radiusKm, String language
    ) {
        List<EmergencyBedStatus> candidates = findNearbyEmergencyBeds(lat, lng, radiusKm);

        // Gemini 프롬프트 생성 (beds, hpid, distanceKm만)
        String prompt = GeminiEmergencyAdvisorPromptBuilder.buildPrompt(candidates, language);

        // Gemini API 호출
        Map<String, Object> payload = buildEmergencyRecommendationPayload(prompt);
        String rawResponse = geminiRestClient.callGeminiApi(payload);

        // Gemini 응답 파싱
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String innerJson = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            JsonNode inner = objectMapper.readTree(innerJson);

            String hpid = inner.path("hpid").asText();
            String reason = inner.path("recommendedReason").asText();

            return new GeminiEmergencyRecommendationResponse(hpid, reason);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Gemini Payload
    private Map<String, Object> buildEmergencyRecommendationPayload(String prompt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("contents", List.of(
                Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))
        ));
        Map<String, Object> responseSchema = new HashMap<>();
        responseSchema.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("hpid", Map.of("type", "string"));
        properties.put("recommendedReason", Map.of("type", "string"));
        responseSchema.put("properties", properties);
        responseSchema.put("required", List.of("hpid", "recommendedReason"));
        payload.put("generationConfig", Map.of(
                "responseMimeType", "application/json",
                "responseSchema", responseSchema
        ));
        return payload;
    }
}
