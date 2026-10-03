<h1 align="center">

Curelingo - Backend

</h1>

<p align="center">
  <b>외국인을 위한 AI 응급 의료 가이드 서비스</b><br>
  <i>AI-powered Emergency Medical Guide for Foreigners in Korea</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring_Boot-3.4.5-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white"/>
  <img src="https://img.shields.io/badge/MySQL-8.4-4479A1?style=for-the-badge&logo=mysql&logoColor=white"/>
  <img src="https://img.shields.io/badge/Gemini_AI-8E75B2?style=for-the-badge&logo=googlegemini&logoColor=white"/>
  <img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white"/>
</p>

---

## 📋 프로젝트 소개

**Curelingo**는 한국에서 의료 서비스가 필요한 외국인들을 위한 AI 기반 응급 의료 가이드 서비스입니다.

언어 장벽으로 인해 적절한 의료 서비스를 받기 어려운 외국인들이 **AI 챗봇**을 통해 증상을 상담하고, **위치 기반**으로 주변 응급실과 병원을 찾을 수 있습니다. 또한 **실시간 병상 현황**을 확인하고 **AI가 최적의 응급실을 추천**해줍니다.

<br>

## ✨ 주요 기능

### 1. AI 의료 챗봇
- Gemini AI 기반 의료 상담 챗봇
- 증상 분석 및 적절한 진료과 추천
- 응급 상황 판단 및 응급실 방문 권고
- **다국어 지원** (한국어/영어 자동 감지)

### 2. 주변 응급실 검색
- GPS 기반 주변 응급실 검색
- 실시간 응급실 가용 병상 현황 조회
- 거리순 정렬 및 상세 정보 제공

### 3. AI 응급실 추천
- 거리와 가용 병상을 종합 분석
- Gemini AI가 최적의 응급실 추천
- 추천 이유와 함께 결과 제공

### 4. 진료과별 병원 검색
- 13개 진료과별 주변 병원 검색
- 운영 시간 기반 필터링
- 병원 상세 정보 및 위치 제공

### 5. 다국어 지원
- Google Translate API 연동
- 한국어 ↔ 영어 실시간 번역
- 사용자 언어 자동 감지 및 응답

<br>

## 🏥 지원 진료과

| 진료과 | Department |
|--------|------------|
| 내과 | Internal Medicine |
| 소아청소년과 | Pediatrics |
| 피부과 | Dermatology |
| 정형외과 | Orthopedics |
| 안과 | Ophthalmology |
| 이비인후과 | ENT |
| 산부인과 | Gynecology |
| 정신건강의학과 | Psychiatry |
| 외과 | General Surgery |
| 비뇨의학과 | Urology |
| 치과 | Dentistry |
| 응급의학과 | Emergency Medicine |
| 가정의학과 | Family Medicine |

<br>

## 🛠 기술 스택

| 분류 | 기술 |
|------|------|
| **Language** | Java 17 |
| **Framework** | Spring Boot 3.4.5 |
| **Database** | MySQL 8.4, JDBC |
| **AI** | Google Gemini API |
| **Translation** | Google Translate API |
| **Location** | MySQL Spatial Index, `ST_Distance_Sphere` |
| **API Docs** | Swagger (SpringDoc OpenAPI) |
| **DevOps** | Docker, Docker Compose, Nginx |
| **SSL** | Let's Encrypt (Certbot) |
| **Code Quality** | SonarQube |

<br>

## 📡 API Endpoints

### Gemini AI
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/gemini/chatbot` | AI 의료 챗봇 대화 |
| `GET` | `/api/gemini/recommend-emergency` | AI 응급실 추천 |

### Emergency (응급실)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/emergency/nearby` | 주변 응급실 검색 |
| `GET` | `/api/emergency/beds` | 응급실 병상 현황 조회 |

### Clinic (병원)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/clinic/department` | 진료과별 병원 검색 |

<br>

## 🚀 실행 방법

### 사전 요구사항
- Java 17+
- Docker & Docker Compose
- E-Gen 공공데이터 API 키
- Gemini 및 Google Translate API 키

### 환경 변수 설정
```bash
# .env 파일 생성
SPRING_PROFILES_ACTIVE=dev
GEMINI_API_KEY=your_gemini_api_key
GOOGLE_TRANSLATION_API_KEY=your_google_translate_api_key
EGEN_API_KEY=your_egen_api_key
MYSQL_DATABASE=curelingo
MYSQL_USER=curelingo
MYSQL_PASSWORD=local_mysql_password
MYSQL_ROOT_PASSWORD=local_mysql_root_password
```

### 개발 환경 실행
```bash
# 저장소 클론
git clone https://github.com/Unithon-INU/2025_UNITHON_TEAM_5_BE.git
cd 2025_UNITHON_TEAM_5_BE

# DB 시작
docker compose -f docker-compose.dev.yaml up -d mysql

# 공공 병원·진료과·응급 병상 snapshot 적재
docker compose -f docker-compose.dev.yaml --profile mysql-tools run --rm mysql-importer

# API 서버 시작
docker compose -f docker-compose.dev.yaml up -d backend

# 또는 Gradle로 직접 실행
docker compose -f docker-compose.dev.yaml up -d mysql
./gradlew bootRun
```

병원 검색은 MySQL의 공간 인덱스로 사각형 후보를 좁힌 다음 `ST_Distance_Sphere`로 실제 반경을 판정합니다. 병원 ID와 진료과 관계는 정규화된 테이블의 기본 키와 외래 키로 관리합니다.

초기 snapshot 적재는 별도 일회성 컨테이너로 실행합니다. E-Gen API 키가 설정되어 있어야 합니다.

```bash
docker compose -f docker-compose.dev.yaml --profile mysql-tools run --rm mysql-importer
```

적재기는 병원·진료과·응급 병상 feed의 건수와 `hpid` 관계를 검증한 다음 snapshot을 트랜잭션으로 교체합니다. 적재 실패 시 staging 행을 버리고 마지막 성공 snapshot을 유지합니다. 실시간 병상 feed는 애플리케이션 시작 시와 5분마다 갱신합니다. 개발용 MySQL은 Docker Compose가 스키마를 초기화합니다. 프로덕션은 별도 MySQL VM 또는 관리형 인스턴스를 사용할 수 있으며, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`를 앱에 설정하고 스키마 SQL을 먼저 적용합니다. DB와 Actuator 포트는 사설 네트워크에서만 접근할 수 있도록 구성합니다.

### 프로덕션 환경 실행
```bash
# SSL 인증서 초기화
chmod +x init-letsencrypt.sh
./init-letsencrypt.sh

# Docker Compose로 실행 (프로덕션)
docker-compose -f docker-compose.prod.yaml up -d

# MySQL이 초기화된 뒤 최초 공공데이터 snapshot 적재
docker compose -f docker-compose.prod.yaml --profile mysql-tools run --rm mysql-importer
```

<br>

## 📁 프로젝트 구조

```
src/main/java/com/curelingo/curelingo/
├── config/                 # 설정 (CORS, RestClient, OpenAPI)
├── gemini/                 # Gemini AI 연동
│   ├── chatbot/           # AI 챗봇 서비스
│   ├── emergencyadvisor/  # AI 응급실 추천
│   └── prompt/            # 프롬프트 빌더
├── emergencyhospital/      # 응급실 검색 서비스
├── clinic/                 # 병원 검색 서비스
├── egen/                   # 공공데이터 API 연동
├── hospital/               # 병원 상세정보 API
├── publicdata/mysql/       # 공공데이터 적재 및 공간 조회
└── translation/            # 번역 서비스
```

<br>

## 🌐 배포 환경

- **Domain**: `api.cure-lingo.com`
- **SSL**: Let's Encrypt 자동 갱신
- **Reverse Proxy**: Nginx
- **Container**: Docker

<br>

## 📖 API 문서

서버 실행 후 Swagger UI에서 API 문서를 확인할 수 있습니다:
- 개발: `http://localhost:8080/swagger-ui.html`
- 프로덕션: `https://api.cure-lingo.com/swagger-ui.html`

<br>

## 👥 팀원

| 역할 | 이름 | GitHub |
|------|------|--------|
| Backend | 권유리 | https://github.com/yuripbong |
| Backend | 최종민 | https://github.com/jongmine |
| Frontend | 현승곤 | https://github.com/invalidhuman |
| Frontend | 변상현 |  |

<br>

## 📄 License

This project is licensed under the MIT License.
