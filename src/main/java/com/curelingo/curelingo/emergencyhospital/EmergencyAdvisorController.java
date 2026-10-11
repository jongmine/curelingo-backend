package com.curelingo.curelingo.emergencyhospital;

import com.curelingo.curelingo.emergencyhospital.dto.EmergencyBedStatus;
import com.curelingo.curelingo.emergencyhospital.dto.NearbyHospitalDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/emergency")
@RequiredArgsConstructor
public class EmergencyAdvisorController implements EmergencyAdvisorSwagger {

    private final EmergencyAdvisorService emergencyAdvisorService;

    @GetMapping("/nearby")
    public ResponseEntity<List<Map<String, Object>>> getNearbyHospitals(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "ko") String language
    ) {
        log.info("[EmergencyController] 응급실 검색 요청 - 위치: ({}, {}), 반경: 10km, 언어: {}", lat, lon, language);
        List<Map<String, Object>> hospitals = emergencyAdvisorService.findNearbyERs(lat, lon, language);
        log.info("[EmergencyController] 검색 결과: {}개 응급실", hospitals.size());
        return ResponseEntity.ok(hospitals);
    }

    @GetMapping("/beds")
    public List<EmergencyBedStatus> getNearbyEmergencyBedStatus(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam double radiusKm
    ) {
        return emergencyAdvisorService.findNearbyEmergencyBeds(lat, lng, radiusKm);
    }
}
