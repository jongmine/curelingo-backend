package com.curelingo.curelingo.clinic;

import com.curelingo.curelingo.clinic.dto.NearbyClinicDto;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@Validated
@RequestMapping("/api/clinic")
@RequiredArgsConstructor
public class ClinicController implements ClinicSwagger {

    private final ClinicService clinicService;



    @GetMapping("/department")
    public ResponseEntity<List<Map<String, Object>>> getNearbyClinicsByDepartment(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng,
            @RequestParam String department,
            @RequestParam(defaultValue = "ko") String language,
            @RequestParam(required = false) String currentTime
    ) {
        log.info("[ClinicController] 진료과별 병원 검색 요청 - 위치: ({}, {}), 반경: 3km, 진료과: {}, 언어: {}, 현재시간: {}",
                lat, lng, department, language, currentTime);

        // 현재 시간 파싱 (ISO 8601 형식으로 받거나 현재 시간 사용)
        LocalDateTime clientTime = null;
        if (currentTime != null && !currentTime.isEmpty()) {
            try {
                clientTime = LocalDateTime.parse(currentTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e) {
                log.warn("[ClinicController] 잘못된 시간 형식: {}, 현재 시간 사용", currentTime);
                clientTime = LocalDateTime.now();
            }
        } else {
            clientTime = LocalDateTime.now();
        }

        List<Map<String, Object>> clinics = clinicService.findNearbyClinicsByDepartment(lat, lng, department, language, clientTime);
        log.info("[ClinicController] 검색 결과: {}개 {} 진료과 병원", clinics.size(), department);
        return ResponseEntity.ok(clinics);
    }


}
