package com.curelingo.curelingo.emergencyhospital;

import com.curelingo.curelingo.emergencyhospital.dto.EmergencyBedStatus;
import com.curelingo.curelingo.publicdata.mysql.db.EmergencyHospitalRepository;
import com.curelingo.curelingo.publicdata.mysql.db.NearbyEmergencyHospital;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmergencyAdvisorService {
    private static final int NEARBY_RADIUS_METERS = 10_000;

    private final EmergencyHospitalRepository emergencyHospitalRepository;

    public List<Map<String, Object>> findNearbyERs(double lat, double lon, String language) {
        List<NearbyEmergencyHospital> nearby = emergencyHospitalRepository.findNearbyEmergencyHospitals(
                lat, lon, NEARBY_RADIUS_METERS);
        List<Map<String, Object>> result = new ArrayList<>(nearby.size());
        for (NearbyEmergencyHospital hospital : nearby) {
            Map<String, Object> item = new LinkedHashMap<>();
            if ("en".equals(language)) {
                item.put("nameEn", firstNonBlank(hospital.nameEn(), hospital.name()));
                item.put("addressEn", firstNonBlank(hospital.addressEn(), hospital.address()));
            } else {
                item.put("name", hospital.name());
                item.put("address", hospital.address());
            }
            item.put("hpid", hospital.hpid());
            item.put("lat", hospital.latitude());
            item.put("lng", hospital.longitude());
            item.put("distanceKm", hospital.distanceKm());
            result.add(item);
        }
        log.info("[Emergency] 반경 10km 내 응급실 검색 결과: {}개", result.size());
        return result;
    }

    public List<EmergencyBedStatus> findNearbyEmergencyBeds(double lat, double lng, double radiusKm) {
        int radiusMeters = toRadiusMeters(radiusKm);
        List<EmergencyBedStatus> result = emergencyHospitalRepository.findNearbyEmergencyBeds(lat, lng, radiusMeters);
        log.info("[BedSearch] 반경 {}km 내 응급 병상 검색 결과: {}개", radiusKm, result.size());
        return result;
    }

    private static int toRadiusMeters(double radiusKm) {
        if (!Double.isFinite(radiusKm) || radiusKm < 0 || radiusKm > Integer.MAX_VALUE / 1000.0) {
            throw new IllegalArgumentException("radiusKm must be a finite non-negative radius");
        }
        return (int) Math.round(radiusKm * 1000.0);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }
}
