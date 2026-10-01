package com.curelingo.curelingo.clinic.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Builder;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Clinic {
    private String hpid;         // 병원 고유 ID
    private String name;         // 병원명
    private String nameEn;       // 병원명 (영문)
    private String tel;          // 전화번호
    private String addr;         // 주소
    private String addrEn;       // 주소 (영문)
    private double lat;          // 위도
    private double lng;          // 경도
    private String type;         // 병원 종류 (예: 병원, 의원, 치과 등)
    private List<String> departments; // 진료과목 목록 (D001~D032)
    
    // 운영시간 필드 추가
    private String dutyTime1s; // 진료시간(월요일) open
    private String dutyTime1c; // 진료시간(월요일) close
    private String dutyTime2s; // 진료시간(화요일) open
    private String dutyTime2c; // 진료시간(화요일) close
    private String dutyTime3s; // 진료시간(수요일) open
    private String dutyTime3c; // 진료시간(수요일) close
    private String dutyTime4s; // 진료시간(목요일) open
    private String dutyTime4c; // 진료시간(목요일) close
    private String dutyTime5s; // 진료시간(금요일) open
    private String dutyTime5c; // 진료시간(금요일) close
    private String dutyTime6s; // 진료시간(토요일) open
    private String dutyTime6c; // 진료시간(토요일) close
    private String dutyTime7s; // 진료시간(일요일) open
    private String dutyTime7c; // 진료시간(일요일) close
    private String dutyTime8s; // 진료시간(공휴일) open
    private String dutyTime8c; // 진료시간(공휴일) close
}
