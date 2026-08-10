# Progress

## Current Task

T020: 系统性补齐自动测试

## Status

Implementation complete. No required steps remain.

## Completed Work

- Counted the existing baseline before adding tests:
  - Backend baseline: 15 test classes, 98 tests, all passing.
  - Frontend baseline: 1 test file, 37 tests, all passing.
- Added backend authentication controller tests for:
  - registration delegation;
  - login failure preserving session state;
  - `/api/auth/me` authenticated and unauthenticated behavior.
- Added backend work-order service tests for:
  - blank type and priority validation on create/update;
  - assigned-handler-only accept/submit enforcement;
  - understandable not-found errors for missing work-order operations;
  - missing comment deletion without writing a deletion audit log.
- Added backend attachment service tests for:
  - empty files, invalid filenames, and missing extensions before file writes;
  - missing stored file on download without leaking filesystem paths.
- Added frontend interaction tests for:
  - login API error staying on the login page and not loading protected work orders;
  - register password-confirmation validation before any API call;
  - create-work-order API validation error without opening stale detail content.
- Production code was not changed.

## Changed Files

- `backend/src/test/java/com/example/workorder/api/AuthControllerTests.java`
- `backend/src/test/java/com/example/workorder/workorder/WorkOrderServiceTests.java`
- `backend/src/test/java/com/example/workorder/workorder/WorkOrderAttachmentServiceTests.java`
- `frontend/src/App.test.ts`
- `PROGRESS.md`

## Verification

- `mvn test` from `backend`: BUILD SUCCESS, 108 tests run, 0 failures, 0 errors, 0 skipped.
- `npm.cmd run test` from `frontend`: 1 test file passed, 40 tests passed.

## Notes

- Tests remain order-independent and use per-test setup/mocks.
- No coverage threshold or new test tooling was introduced.
- No business rules were relaxed to make tests pass.
