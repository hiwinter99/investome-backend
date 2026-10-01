# 모의투자 통합

운영 저장소: Kimdonggyu26/investome-backend, main. Render 저장소/Root Directory/Dockerfile 설정은 기존 값을 유지한다.
프론트엔드 저장소 Kimdonggyu26/investome의 backend 폴더로 운영 백엔드를 교체하지 않는다.

## 보존한 운영 기능
기존 로그인/refresh/logout, JWT 필터, 사용자, 게시판, 포트폴리오, 관심종목, 알림, 자산 댓글, 뉴스, 환율, 순위 스냅샷을 유지했다.
모의투자 컨트롤러는 운영 JWT가 사용하는 Long 사용자 ID principal을 받는다.
모의투자 예외 응답 처리만 paper 패키지로 한정했다. 기존 API 오류 처리와 인증 제한을 전역으로 바꾸지 않는다.

## 추가 기능
- /api/paper/account, holdings, orders: 초기 가상자금 1천만 원, 매수/매도, 평단가, 거래내역.
- 일별 TOP30 저장과 신규 매수 제한, 기존 순위 밖 보유종목 매도 유지.
- KIS REST 조회가격으로 모의 체결, 원자적 잔고 갱신, 계좌 잠금과 requestId 중복 방지.
- TOP30 KIS 웹소켓과 인증된 SSE /api/paper/realtime/stream.
- 모의투자 현재가와 일봉 조회, 종목 파일은 JAR 리소스에 포함.
- 기존 DB 연결과 JWT/KIS 환경변수 사용. 계좌나 기존 데이터를 복사하거나 삭제하지 않는다.
- 기존 ddl-auto=update 설정으로 paper_* 테이블이 추가된다.

## 검증
`./mvnw test`: 46개 통과. 운영 UserService 로그인/refresh 토큰으로 모의투자와 기존 관심종목/알림 조회를 함께 검증.
실제 장중 틱 연속 갱신은 장중 확인 필요.
