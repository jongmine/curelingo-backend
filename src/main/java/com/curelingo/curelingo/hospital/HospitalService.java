package com.curelingo.curelingo.hospital;

import com.curelingo.curelingo.translation.TranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class HospitalService {
    private final HospitalCatalogRepository hospitals;
    private final TranslationService translationService;

    @Transactional
    public void saveHospital(HospitalDto hospital) {
        if (hospital.getHpid() == null || hospital.getHpid().isBlank()
                || hospital.getDutyName() == null || hospital.getDutyName().isBlank()) {
            throw new IllegalArgumentException("hpid and dutyName are required");
        }
        if (hospital.getWgs84Lat() != null && (hospital.getWgs84Lat() < -90 || hospital.getWgs84Lat() > 90)
                || hospital.getWgs84Lon() != null && (hospital.getWgs84Lon() < -180 || hospital.getWgs84Lon() > 180)) {
            throw new IllegalArgumentException("coordinates must be within WGS84 bounds");
        }
        if (hospital.getDutyNameEn() == null) {
            hospital.setDutyNameEn(translationService.translateToEnglish(hospital.getDutyName()));
        }
        if (hospital.getDutyAddrEn() == null) {
            hospital.setDutyAddrEn(translationService.translateToEnglish(hospital.getDutyAddr()));
        }
        hospitals.save(hospital);
    }

    public HospitalDto getHospitalDetailByHpid(String hpid, LocalDateTime currentTime) {
        HospitalDto hospital = hospitals.findByHpid(hpid);
        if (hospital != null) {
            hospital.setIsOpen(isHospitalOpen(hospital, currentTime));
            if (hospital.getDutyNameEn() == null || hospital.getDutyNameEn().isBlank()) {
                hospital.setDutyNameEn(hospital.getDutyName());
            }
            if (hospital.getDutyAddrEn() == null || hospital.getDutyAddrEn().isBlank()) {
                hospital.setDutyAddrEn(hospital.getDutyAddr());
            }
        }
        return hospital;
    }

    private boolean isHospitalOpen(HospitalDto hospital, LocalDateTime currentTime) {
        int day = switch (currentTime.getDayOfWeek()) {
            case MONDAY -> 1;
            case TUESDAY -> 2;
            case WEDNESDAY -> 3;
            case THURSDAY -> 4;
            case FRIDAY -> 5;
            case SATURDAY -> 6;
            case SUNDAY -> 7;
        };
        String open = time(hospital, day, true);
        String close = time(hospital, day, false);
        if (open == null || close == null || open.isBlank() || close.isBlank()) return false;
        try {
            LocalTime start = parseTime(open);
            LocalTime end = parseTime(close);
            if (start.equals(end) || open.equals("0000") && (close.equals("2400") || close.equals("0000"))) {
                return true;
            }
            LocalTime now = currentTime.toLocalTime();
            return end.isAfter(start)
                    ? !now.isBefore(start) && now.isBefore(end)
                    : !now.isBefore(start) || now.isBefore(end);
        } catch (RuntimeException e) {
            log.warn("병원 운영시간 파싱 실패: hpid={}, open={}, close={}", hospital.getHpid(), open, close);
            return false;
        }
    }

    private static String time(HospitalDto hospital, int day, boolean start) {
        return switch (day) {
            case 1 -> start ? hospital.getDutyTime1s() : hospital.getDutyTime1c();
            case 2 -> start ? hospital.getDutyTime2s() : hospital.getDutyTime2c();
            case 3 -> start ? hospital.getDutyTime3s() : hospital.getDutyTime3c();
            case 4 -> start ? hospital.getDutyTime4s() : hospital.getDutyTime4c();
            case 5 -> start ? hospital.getDutyTime5s() : hospital.getDutyTime5c();
            case 6 -> start ? hospital.getDutyTime6s() : hospital.getDutyTime6c();
            case 7 -> start ? hospital.getDutyTime7s() : hospital.getDutyTime7c();
            default -> throw new IllegalArgumentException("Unsupported day: " + day);
        };
    }

    private static LocalTime parseTime(String value) {
        if (value.length() != 4) throw new IllegalArgumentException("Invalid time: " + value);
        int hour = Integer.parseInt(value.substring(0, 2));
        int minute = Integer.parseInt(value.substring(2, 4));
        if (hour == 24 && minute == 0) return LocalTime.MIDNIGHT;
        return LocalTime.of(hour, minute);
    }
}
