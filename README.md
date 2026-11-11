# Investome Backend

금융·투자 정보 조회와 국내주식 모의투자 서비스인 **Investome**의 Java / Spring Boot API 서버입니다.
회원 인증, 게시판, 관심 종목, 포트폴리오와 모의계좌를 관리하고, 외부 금융 API에서 받은 시세를 화면에 전달합니다.

- [서비스](https://investome.vercel.app/)
- [포트폴리오 — 화면과 구현 설명](https://kimdonggyu-portfolio.vercel.app/projects/investome)
- [프론트엔드 및 Vercel Functions 저장소](https://github.com/hiwinter99/investome)

## 사용 기술

Java 17 · Spring Boot 3.5 · Spring Data JPA · Spring Security · JWT · PostgreSQL · Maven

실시간 시세 전달에 WebSocket과 SSE를 사용하며, 암호화폐 조회용 Redis 캐시는 선택적으로 활성화합니다. 테스트에는 JUnit, MockMvc, Mockito, H2를 사용합니다.

## 주요 기능

- 회원가입·로그인과 JWT 인증
- 게시판·댓글·추천, 관심 종목, 개인 포트폴리오 관리
- 외부 API를 이용한 금융 데이터 조회와 공통 응답 변환
- KIS WebSocket 시세 수신 및 SSE를 통한 브라우저 전달
- 당일 코스피 TOP 30 대상 가상 매수·매도, 잔액·보유 종목·주문 이력 관리

모의투자는 실제 증권사에 주문을 전송하지 않는 가상 거래입니다.

## 핵심 구현과 코드

### 모의투자 주문 처리

서버에서 KIS API로 현재가를 조회한 뒤, 계좌에 비관적 잠금을 걸고 잔액·보유 수량·주문 이력을 하나의 트랜잭션으로 변경합니다. 외부 현재가 조회는 쓰기 트랜잭션 밖에서 처리하고, 계좌 잠금을 얻은 뒤 시세 조회 시각을 다시 확인합니다.

같은 요청 ID로 다시 주문하면 기존 결과를 반환합니다. 같은 ID에 종목·수량·매수/매도 구분이 달라지면 충돌로 처리합니다.

- [PaperTradingService — 주문 처리](src/main/java/com/investome/api/paper/PaperTradingService.java)
- [PaperAccountRepository — 계좌 잠금](src/main/java/com/investome/api/paper/PaperAccountRepository.java)
- [PaperQuoteProvider — 서버 현재가 조회](src/main/java/com/investome/api/paper/PaperQuoteProvider.java)

### 실시간 시세와 인증

서버 프로세스 안에서 KIS WebSocket 연결을 공유하고, 받은 시세를 SSE로 브라우저에 전달합니다. JWT 필터에서 토큰을 검증하고, 인증된 사용자 ID를 기준으로 본인의 자산과 모의계좌에 접근하도록 처리합니다.

- [PaperRealtimeService — 시세 수신과 SSE 전달](src/main/java/com/investome/api/paper/PaperRealtimeService.java)
- [JwtAuthenticationFilter — JWT 인증](src/main/java/com/investome/api/config/JwtAuthenticationFilter.java)

### Redis 조회 캐시

암호화폐 TOP 30을 Redis에 5분간 저장합니다. Redis를 사용할 수 없으면 메모리 캐시와 외부 조회로 대체하고, 외부 조회까지 실패하면 메모리에 남아 있는 이전 목록을 반환합니다. 모의투자 주문 체결 가격에 이 코인 목록 캐시를 사용하지 않습니다.

Redis는 기본 비활성화이며, 로컬에서 저장·재사용을 확인했습니다. Nginx 요청 분배와 k6 실험도 별도의 로컬 검증입니다.

- [CryptoRedisCache — Redis 읽기·쓰기](src/main/java/com/investome/api/market/CryptoRedisCache.java)
- [CryptoMarketService — 코인 조회와 대체 처리](src/main/java/com/investome/api/market/CryptoMarketService.java)
- [Redis 연결 안내](https://github.com/hiwinter99/investome/blob/main/docs/SPRING_REDIS_LOCAL.md)
- [Nginx 로컬 실험](https://github.com/hiwinter99/investome/blob/main/docs/NGINX_LOCAL.md)

## 로컬 실행

JDK 17과 개발용 PostgreSQL이 필요합니다. 저장소를 받은 뒤 아래 환경변수를 설정합니다.

| 환경변수 | 용도 |
| --- | --- |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC 주소 |
| `SPRING_DATASOURCE_USERNAME` | 개발용 DB 사용자 |
| `SPRING_DATASOURCE_PASSWORD` | 개발용 DB 비밀번호 |
| `JWT_SECRET` | JWT 서명 키. 32자 이상의 충분히 긴 임의 값 |
| `KIS_APP_KEY`, `KIS_APP_SECRET` | KIS 시세 조회·수신 기능 |
| `TURNSTILE_SECRET_KEY` | Turnstile 검증이 필요한 회원 기능 |
| `FMP_API_KEY` | FMP를 사용하는 시장 데이터 조회 기능 |

API 키에 따라 사용할 수 있는 외부 조회 기능이 달라집니다. 비밀 값은 서버 환경변수로 설정하고 커밋하지 않습니다. 기본 JPA 설정이 `ddl-auto: update`이므로 로컬 실행에는 개발용 DB를 사용합니다.

```sh
./mvnw spring-boot:run
```

기본 포트는 `8080`이며 `PORT` 환경변수로 변경할 수 있습니다. Windows에서는 `mvnw.cmd`를 사용합니다.

Redis를 사용하려면 실행 중인 Redis와 함께 `CRYPTO_REDIS_ENABLED=true`를 설정합니다. `REDIS_HOST`와 `REDIS_PORT`의 기본값은 `127.0.0.1`, `6379`입니다.

PostgreSQL 없이 별도 H2 DB로 모의투자를 확인하는 방법과 프론트 연동은 [통합 로컬 실행 안내](https://github.com/hiwinter99/investome/blob/main/LAPTOP_SETUP.md)에 정리했습니다.

## 테스트

```sh
./mvnw test
```

주문 테스트에서는 외부 시세를 대체하고 H2 DB를 사용해 다음 상황을 확인합니다.

- 같은 주문을 동시에 요청해도 한 번만 처리되는지
- 서로 다른 동시 주문이 잔액이나 보유 수량을 초과하지 않는지
- 주문 저장 실패 시 잔액·보유 수량·주문 이력이 함께 롤백되는지
- 동일 요청의 재시도 결과와 인증·입력값 검증

[PaperTradingTests](src/test/java/com/investome/api/paper/PaperTradingTests.java) · [CryptoCacheTests](src/test/java/com/investome/api/market/CryptoCacheTests.java)

테스트는 해당 조건에서의 동작 검증이며, 운영 PostgreSQL의 부하 처리량이나 실제 금융 매매시스템의 안정성을 보장하는 결과는 아닙니다.
