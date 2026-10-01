package com.curelingo.curelingo.clinic;

import com.curelingo.curelingo.clinic.domain.Clinic;
import com.curelingo.curelingo.publicdata.mysql.db.HospitalSearchRepository;
import com.curelingo.curelingo.publicdata.mysql.db.NearbyClinicResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicService {

    private final HospitalSearchRepository hospitalSearchRepository;

    /**
     * 진료과별 필터링된 인근 병원 검색 (반경 3km 고정)
     *
     * @param lat        현재 위도
     * @param lng        현재 경도
     * @param department 진료과 코드 (D001~D032)
     * @param language   언어 (ko: 한국어, en: 영어)
     * @param clientTime 클라이언트 현재 시간
     * @return 해당 진료과가 있는 인근 병원 목록
     */
    public List<Map<String, Object>> findNearbyClinicsByDepartment(double lat, double lng, String department, String language, LocalDateTime clientTime) {
        List<NearbyClinicResult> nearby = hospitalSearchRepository.findNearby(lat, lng, 3000, department);
        List<Map<String, Object>> result = new ArrayList<>(nearby.size());
        for (NearbyClinicResult entry : nearby) {
            Clinic clinic = entry.clinic();
            Map<String, Object> hospitalData = new LinkedHashMap<>();
            hospitalData.put("hpid", clinic.getHpid());
            if ("en".equals(language)) {
                hospitalData.put("nameEn", firstNonBlank(entry.nameEn(), clinic.getName()));
                hospitalData.put("addressEn", firstNonBlank(entry.addressEn(), clinic.getAddr()));
            } else {
                hospitalData.put("name", clinic.getName());
                hospitalData.put("address", clinic.getAddr());
            }
            hospitalData.put("tel", clinic.getTel());
            hospitalData.put("lat", clinic.getLat());
            hospitalData.put("lng", clinic.getLng());
            hospitalData.put("distanceKm", entry.distanceKm());
            hospitalData.put("isOpen", isClinicOpen(clinic, clientTime));
            putDutyTimes(hospitalData, clinic);
            result.add(hospitalData);
        }
        log.info("[Clinic] 공간 검색 결과 - 진료과: {}, 결과 수: {}", department, result.size());
        return result;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static void putDutyTimes(Map<String, Object> hospitalData, Clinic clinic) {
        for (int day = 1; day <= 8; day++) {
            String suffix = Integer.toString(day);
            hospitalData.put("dutyTime" + suffix + "s", dutyTime(clinic, day, true));
            hospitalData.put("dutyTime" + suffix + "c", dutyTime(clinic, day, false));
        }
    }

    private static String dutyTime(Clinic clinic, int day, boolean start) {
        return switch (day) {
            case 1 -> start ? clinic.getDutyTime1s() : clinic.getDutyTime1c();
            case 2 -> start ? clinic.getDutyTime2s() : clinic.getDutyTime2c();
            case 3 -> start ? clinic.getDutyTime3s() : clinic.getDutyTime3c();
            case 4 -> start ? clinic.getDutyTime4s() : clinic.getDutyTime4c();
            case 5 -> start ? clinic.getDutyTime5s() : clinic.getDutyTime5c();
            case 6 -> start ? clinic.getDutyTime6s() : clinic.getDutyTime6c();
            case 7 -> start ? clinic.getDutyTime7s() : clinic.getDutyTime7c();
            case 8 -> start ? clinic.getDutyTime8s() : clinic.getDutyTime8c();
            default -> throw new IllegalArgumentException("Unsupported weekday: " + day);
        };
    }

    /**
     * 클리닉 운영 여부 확인
     *
     * @param clinic 클리닉 정보
     * @param currentTime 현재 시간
     * @return 운영 중이면 true, 아니면 false
     */
    private boolean isClinicOpen(Clinic clinic, LocalDateTime currentTime) {
        DayOfWeek dayOfWeek = currentTime.getDayOfWeek();
        LocalTime currentLocalTime = currentTime.toLocalTime();

        String openTime = null;
        String closeTime = null;

        // 요일별 운영시간 가져오기
        switch (dayOfWeek) {
            case MONDAY:
                openTime = clinic.getDutyTime1s();
                closeTime = clinic.getDutyTime1c();
                break;
            case TUESDAY:
                openTime = clinic.getDutyTime2s();
                closeTime = clinic.getDutyTime2c();
                break;
            case WEDNESDAY:
                openTime = clinic.getDutyTime3s();
                closeTime = clinic.getDutyTime3c();
                break;
            case THURSDAY:
                openTime = clinic.getDutyTime4s();
                closeTime = clinic.getDutyTime4c();
                break;
            case FRIDAY:
                openTime = clinic.getDutyTime5s();
                closeTime = clinic.getDutyTime5c();
                break;
            case SATURDAY:
                openTime = clinic.getDutyTime6s();
                closeTime = clinic.getDutyTime6c();
                break;
            case SUNDAY:
                openTime = clinic.getDutyTime7s();
                closeTime = clinic.getDutyTime7c();
                break;
        }

        // 운영시간이 없으면 휴무
        if (openTime == null || closeTime == null || openTime.isEmpty() || closeTime.isEmpty()) {
            return false;
        }

        try {
            // 시간 문자열을 LocalTime으로 변환 ("0900" -> 09:00)
            LocalTime openLocalTime = parseTimeString(openTime);
            LocalTime closeLocalTime = parseTimeString(closeTime);

            // 24시간 운영인 경우 (예: "0000" - "2400" 또는 같은 시간)
            if (openLocalTime.equals(closeLocalTime) ||
                (openTime.equals("0000") && (closeTime.equals("2400") || closeTime.equals("0000")))) {
                return true;
            }

            // 일반적인 경우: 시작 시간 <= 현재 시간 < 종료 시간
            if (closeLocalTime.isAfter(openLocalTime)) {
                // 같은 날 내에서 운영 (예: 09:00 - 18:00)
                return !currentLocalTime.isBefore(openLocalTime) && currentLocalTime.isBefore(closeLocalTime);
            } else {
                // 자정을 넘어 운영 (예: 22:00 - 06:00)
                return !currentLocalTime.isBefore(openLocalTime) || currentLocalTime.isBefore(closeLocalTime);
            }

        } catch (Exception e) {
            log.warn("[Clinic] 운영시간 파싱 오류 - 병원: {}, 시간: {} - {}", clinic.getName(), openTime, closeTime);
            return false;
        }
    }

    /**
     * 시간 문자열을 LocalTime으로 변환
     *
     * @param timeString "0900", "1830" 형식의 시간 문자열
     * @return LocalTime 객체
     */
    private LocalTime parseTimeString(String timeString) {
        if (timeString == null || timeString.length() != 4) {
            throw new IllegalArgumentException("Invalid time format: " + timeString);
        }

        int hour = Integer.parseInt(timeString.substring(0, 2));
        int minute = Integer.parseInt(timeString.substring(2, 4));

        // 2400을 0000으로 처리
        if (hour == 24) {
            hour = 0;
        }

        return LocalTime.of(hour, minute);
    }
}
