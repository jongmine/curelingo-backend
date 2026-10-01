package com.curelingo.curelingo.emergencyhospital.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class EmergencyBedStatus {
    private String hpid;                // 기관 ID
    private String dutyName;            // 기관명
    private String dutyTel3;            // 응급실 전화
    private Double distanceKm;          // 거리
    private LocalDateTime updatedAt;    // 정보 입력 일시

    private Beds beds;           // beds를 내부 클래스로 구성

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Beds {
        private Integer hvec;    // 일반병상 (가용)
        private Integer hvs01;
        private Integer hv28;
        private Integer hvs02;
        private Integer hvoc;
        private Integer hvs22;
        private Integer hvgc;
        private Integer hvs38;
        private Integer hv9;
        private Integer hvs14;
        private Integer hv38;
        private Integer hvs21;
        private Integer hv60;
        private Integer hvs60;
    }
}
