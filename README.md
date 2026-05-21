# 품앗이 (Pumasi) — Spring Boot

조용히 함께하는 실시간 품앗이. 채팅도, 커뮤니티도 아닙니다 — **존재 자체가 가치**입니다.

3단계로 끝나는 경험:

1. **발견 & 예약** — `/` 오늘의 방에서 "참여 예약"
2. **실시간 함께하기** — `/room/{id}` 서버 동기화 카운트다운 + 실시간 참여자
3. **인증샷 = 완료** — 종료 10분 전부터 사진 업로드 → 자동 완료 처리

## 기술 스택

- Spring Boot 2.6.2 (web, freemarker, websocket, security, data-jpa), Java 8, Gradle
- **Supabase Postgres** + Flyway 마이그레이션 (V1~V3)
- 실시간: Spring WebSocket (`/ws/realtime`) — 변경 신호 브로드캐스트
- 카카오 OAuth 로그인 + 신규 가입 시 닉네임/동물 이모지 선택 화면
- 카카오톡 공유 SDK (방 초대 메시지) — 3단 폴백 (Kakao SDK / Web Share / 클립보드)
- XSS 방어: Freemarker auto-escape + JSON-script 주입 + JS esc()
- CSRF: Spring Security CookieCsrfTokenRepository
- 사진 업로드: 클라이언트 NSFWJS 사전 필터 + 서버 화이트리스트(data:image/) + 시간 윈도우
- 신고 기능: 사진/유저/방 대상, 중복 신고 차단, 신고된 사진 갤러리에서 자동 숨김

## 로컬 실행

1. **Supabase 프로젝트 생성** + Postgres 연결 정보 확보 (Session pooler URL 권장)
2. **Kakao Developers 앱 생성** + JS 키, REST API 키, Client Secret, Redirect URI 설정
3. `src/main/resources/application-local.yml` 생성:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://aws-?-ap-northeast-2.pooler.supabase.com:5432/postgres?sslmode=require
    username: postgres.PROJECT_REF
    password: 'DB_PASSWORD'

app:
  kakao:
    javascript-key: KAKAO_JS_KEY
    rest-api-key: KAKAO_REST_API_KEY
    redirect-uri: http://localhost:8181/auth/kakao/callback
    client-secret: KAKAO_CLIENT_SECRET   # Kakao Developers Client Secret "사용함" 일 때만
  seed:
    enabled: false   # true 면 데모 14명+방 4개 자동 시드
```

4. 실행:

```bat
gradlew.bat bootRun
```

→ 브라우저에서 http://localhost:8181

## 배포 (Railway 기준)

PaaS 중 Spring Boot 와 가장 친화적. $5/월 무료 크레딧.

### 1) GitHub 푸시
```bash
git init
git add .
git commit -m "ready to deploy"
git branch -M main
git remote add origin https://github.com/USERNAME/pumasi.git
git push -u origin main
```
**주의**: `application-local.yml` 은 `.gitignore` 에 등록돼 있어 절대 커밋되지 않음.

### 2) Railway 새 프로젝트
- https://railway.app → New Project → Deploy from GitHub repo → `pumasi` 선택
- Railway 가 `Dockerfile` 자동 감지해 빌드 시작

### 3) 환경 변수 (Railway 대시보드의 Variables 탭)

| 키 | 값 | 비고 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | 로컬 yml 안 로드 |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://aws-?-ap-northeast-2.pooler.supabase.com:5432/postgres?sslmode=require` | Supabase Session pooler |
| `SPRING_DATASOURCE_USERNAME` | `postgres.PROJECT_REF` |  |
| `SPRING_DATASOURCE_PASSWORD` | (Supabase DB 비밀번호) |  |
| `KAKAO_JAVASCRIPT_KEY` | (Kakao JS 키) | 공유 SDK |
| `KAKAO_REST_API_KEY` | (Kakao REST API 키) | OAuth |
| `KAKAO_CLIENT_SECRET` | (Kakao Client Secret) | "사용함" 이면 필수 |
| `KAKAO_REDIRECT_URI` | `https://YOUR_RAILWAY_URL/auth/kakao/callback` | 배포 후 알게 됨 |
| `APP_SEED_ENABLED` | `false` | 운영에 시드 데이터 안 들어가게 |

`PORT` 는 Railway 가 자동 주입 (Dockerfile 에서 `${PORT}` 로 받음).

### 4) 도메인 + HTTPS
- Railway → Settings → Domains → "Generate Domain" 누르면 `https://xxx.up.railway.app` 즉시 발급 (자동 HTTPS)
- 커스텀 도메인 쓰려면 같은 화면에서 CNAME 등록

### 5) Kakao Developers 콘솔 — 배포 URL 등록
- **앱 설정 → 플랫폼 → Web** : `https://xxx.up.railway.app` 추가
- **카카오 로그인 → Redirect URI** : `https://xxx.up.railway.app/auth/kakao/callback` 추가
- Railway 환경변수 `KAKAO_REDIRECT_URI` 도 같은 URL 로 설정 → 자동 재배포

### 6) 접속 확인
- `https://xxx.up.railway.app` 에서 카카오 로그인 → welcome → 방 만들기/참여/사진까지 동작 확인

## 프로젝트 구조

```
pumasi/
├─ build.gradle · settings.gradle · gradlew(.bat)
├─ Dockerfile · .dockerignore                  # 배포용
├─ src/main/java/com/jacob/pumasi/
│  ├─ PumasiApplication.java
│  ├─ config/  SecurityConfig, WebSocketConfig
│  ├─ socket/RealtimeHandler.java              # 실시간 브로드캐스트
│  ├─ model/   AppUser, Room, Participant, ParticipantPhoto, Report, PhotoView, Category, RoomView
│  ├─ repo/    UserRepository, RoomRepository, ParticipantRepository, ParticipantPhotoRepository, ReportRepository
│  ├─ store/   DataStore (서비스 계층), Seeder (조건부 시드)
│  └─ web/     MainController (페이지), ApiController (REST), AuthController (Kakao OAuth)
└─ src/main/resources/
   ├─ application.yml                          # 공통 + env 주입
   ├─ application-local.yml.example            # 로컬 설정 템플릿
   ├─ db/migration/  V1__init.sql, V2__reports.sql, V3__kakao_oauth.sql
   ├─ templates/  login, welcome, index, create, room, profile, privacy
   └─ static/     css/app.css, js/{app, home, create, room, profile}.js
```
