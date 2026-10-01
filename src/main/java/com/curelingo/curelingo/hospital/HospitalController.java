package com.curelingo.curelingo.hospital;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/hospitals")
@Tag(name = "Hospital API", description = "병원 기준정보 관리")
public class HospitalController {
    private final HospitalService hospitalService;

    public HospitalController(HospitalService hospitalService) {
        this.hospitalService = hospitalService;
    }

    @PostMapping
    @Operation(summary = "병원 정보 저장")
    public ResponseEntity<String> saveHospital(@RequestBody HospitalDto dto) {
        hospitalService.saveHospital(dto);
        return ResponseEntity.ok("병원 정보가 저장되었습니다.");
    }

    @GetMapping("/{hpid}")
    @Operation(summary = "병원 상세정보 조회")
    public ResponseEntity<HospitalDto> getHospitalDetail(@PathVariable String hpid,
                                                          @RequestParam(required = false) String currentTime) {
        LocalDateTime time = parseTime(currentTime);
        HospitalDto result = hospitalService.getHospitalDetailByHpid(hpid, time);
        return result == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(result);
    }

    private static LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (RuntimeException e) {
            return LocalDateTime.now();
        }
    }
}
