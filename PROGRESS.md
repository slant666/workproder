# Progress

## Current Task

T023: 项目交付和面试准备。

## Status

交付文档和面试准备材料已完成。无正在执行的命令；本轮主要是文档变更，未修改业务代码。

## Completed Work

- Added session-backed CSRF protection for all unsafe `/api/**` methods.
- Added `/api/auth/csrf` token endpoint and frontend `apiFetch` wrapper.
- Rotated the CSRF token and servlet session id after successful login to reduce session fixation risk.
- Added login brute-force protection: 5 failed attempts lock the username for 15 minutes.
- Fixed the login-failure counter bug that previously reset non-locked attempts before they could accumulate.
- Kept login failure messages unified for wrong password, missing user, and disabled user.
- Added `X-Content-Type-Options: nosniff` to attachment downloads.
- Stopped Nginx from proxying `/actuator/` publicly; Compose still uses backend actuator health inside the Docker network.
- Updated deployment docs to clarify actuator is internal-only.
- Updated frontend tests so each test resets CSRF cache and mocks the CSRF endpoint independently.
- Replaced the garbled root `README.md` with a complete project delivery README.
- Added `docs/interview-prep.md` with interview pitch, common questions, and integrated answers.

## Changed Files

- `backend/src/main/java/com/example/workorder/api/ApiExceptionHandler.java`
- `backend/src/main/java/com/example/workorder/api/AuthController.java`
- `backend/src/main/java/com/example/workorder/api/WorkOrderController.java`
- `backend/src/main/java/com/example/workorder/auth/AuthService.java`
- `backend/src/main/java/com/example/workorder/auth/CsrfException.java`
- `backend/src/main/java/com/example/workorder/auth/CsrfTokenResponse.java`
- `backend/src/main/java/com/example/workorder/auth/CsrfTokenService.java`
- `backend/src/main/java/com/example/workorder/auth/LoginRateLimitException.java`
- `backend/src/main/java/com/example/workorder/config/CsrfInterceptor.java`
- `backend/src/main/java/com/example/workorder/config/WebConfig.java`
- `backend/src/test/java/com/example/workorder/api/AuthControllerTests.java`
- `backend/src/test/java/com/example/workorder/api/WorkOrderControllerTests.java`
- `backend/src/test/java/com/example/workorder/auth/AuthServiceTests.java`
- `backend/src/test/java/com/example/workorder/config/CsrfInterceptorTests.java`
- `frontend/src/api/http.ts`
- `frontend/src/api/admin.ts`
- `frontend/src/api/auth.ts`
- `frontend/src/api/health.ts`
- `frontend/src/api/workOrders.ts`
- `frontend/src/App.test.ts`
- `frontend/nginx.conf`
- `deploy/README.md`
- `PROGRESS.md`
- `README.md`
- `docs/interview-prep.md`

## Verification

- `mvn test` from `backend`: BUILD SUCCESS, 115 tests run, 0 failures, 0 errors, 0 skipped.
- `npm.cmd run test` from `frontend`: 1 test file passed, 41 tests passed.
- T023 changed documentation only; no additional automated tests were required for this documentation update.

## Remaining Review Items

- File upload still mostly trusts declared content type; deeper magic-number validation can be handled in a follow-up.
- Performance items from the audit remain mostly review findings: pagination strategy, index tuning, statistics query consolidation, and frontend detail request batching.
- Mockito emits a future-JDK warning about dynamic agent loading during tests; it does not fail the build.
