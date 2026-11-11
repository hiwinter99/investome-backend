# Investome Backend

## 📌 프로젝트 소개

금융·투자 정보 조회와 국내주식 모의투자 서비스인 Investome의 백엔드 저장소입니다.

Spring Boot로 회원 인증, 게시판, 자산 관리와 모의투자 API를 구현하고, 외부 금융 API에서 받은 시세를 브라우저에 전달합니다.

## 🔗 링크

- [Investome 방문하기](https://investome.vercel.app/)
- [포트폴리오](https://kimdonggyu-portfolio.vercel.app/projects/investome)
- [프론트엔드 GitHub](https://github.com/hiwinter99/investome)

## 🛠️ 사용 기술

- Language: Java 17
- Framework: Spring Boot, Spring Security, Spring Data JPA
- Database: PostgreSQL
- 인증: JWT
- 시세 전달: WebSocket, SSE
- 조회 캐시: Redis (선택적 활성화·로컬 검증)
- Build / Test: Maven, JUnit, MockMvc, Mockito, H2

## ✨ 주요 기능

- 회원가입 / 로그인 / JWT 인증
- 게시판 / 댓글 / 추천
- 관심 종목 / 포트폴리오 관리
- 외부 API를 통한 금융 데이터 조회
- KIS 실시간 시세 수신 및 브라우저 전달
- 가상 매수·매도 / 잔액·보유 종목·주문 내역 조회

## 💻 핵심 구현

- KIS WebSocket으로 받은 시세를 SSE로 브라우저에 전달했습니다.
- 모의투자 주문 시 서버에서 현재가를 조회하고, 계좌 잠금과 트랜잭션으로 잔액·보유 수량·주문 이력을 함께 처리했습니다.
- 동일 요청 ID의 주문은 기존 결과를 반환하도록 구현했습니다.
- 암호화폐 TOP 30을 Redis에 5분간 저장하고, 장애 시 메모리 캐시와 외부 조회로 대체했습니다.

모의투자는 실제 증권사에 주문을 전송하지 않는 가상 거래입니다. Redis 캐시는 코인 목록 조회에 사용하며 모의투자 주문 가격에는 사용하지 않습니다.

## 👤 담당

개인 프로젝트로 API 설계, 서버 개발, DB 연동과 배포를 진행했습니다.

## 📂 실행 및 테스트

JDK 17과 개발용 PostgreSQL이 필요합니다. DB 연결 정보와 `JWT_SECRET`을 서버 환경변수로 설정합니다. 시세 조회 등 외부 연동 기능에는 해당 API 키가 필요합니다.

```sh
# 서버 실행
./mvnw spring-boot:run

# 테스트
./mvnw test
```

Windows에서는 `mvnw.cmd`를 사용합니다. Redis는 기본 비활성화이며 `CRYPTO_REDIS_ENABLED=true`로 켤 수 있습니다.

주문 테스트에서는 동시 중복 주문, 잔액·보유 수량 초과, 주문 저장 실패 시 롤백을 확인했습니다. 외부 시세는 대체하고 H2 DB를 사용하는 테스트입니다.

- [로컬 실행 및 환경 설정](https://github.com/hiwinter99/investome/blob/main/LAPTOP_SETUP.md)
- [주문 처리 코드](src/main/java/com/investome/api/paper/PaperTradingService.java)
- [주문 테스트 코드](src/test/java/com/investome/api/paper/PaperTradingTests.java)
- [Redis 로컬 연결 안내](https://github.com/hiwinter99/investome/blob/main/docs/SPRING_REDIS_LOCAL.md)
