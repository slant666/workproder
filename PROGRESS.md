# Progress

## Current Task

T019: 工单统计看板

## Status

Implementation complete. The reported backend compile error in `WorkOrderStatisticsService` was fixed. The later Spring Boot startup failure caused by `No default constructor found` was also fixed by marking the production `JdbcTemplate` constructor with `@Autowired` while preserving the package-private test constructor that injects `Clock`. No required implementation steps remain.

## Branch

- Current workspace is on `main`, tracking `origin/main`.
- T019 was implemented and is ready to commit from `main`.

## Completed Work

- Added an admin-only work-order statistics API:
  - `GET /api/admin/work-orders/statistics`
  - supports `createdFrom` and `createdTo` date range query parameters.
- Added backend statistics calculation in a dedicated service rather than overloading the paged list query.
- Defined statistics rules:
  - average processing duration = first admin accept time to user confirmation completion time;
  - overdue unhandled count = work orders still in `待处理` after 48 hours from creation.
- Added statistics output for:
  - total work orders;
  - counts by status;
  - counts by priority;
  - daily new work-order trend;
  - average processing duration in minutes;
  - counts by current admin handler;
  - overdue unhandled count;
  - rule text returned with the response for UI transparency.
- Added database performance support for statistics joins:
  - index on `work_order_status_transitions(new_status, action, work_order_id)`.
- Added frontend admin dashboard:
  - date range filters;
  - summary cards;
  - simple horizontal bar charts;
  - empty-state display when the selected range has no data;
  - refresh behavior after relevant work-order state changes.
- Added/updated tests:
  - backend service aggregation tests;
  - backend admin controller authorization and date-range parameter tests;
  - frontend admin statistics rendering, date-range request, and empty-state tests.
- Fixed backend issues reported during startup:
  - `WorkOrderStatisticsService.groupedCounts` uses explicit `RowCallbackHandler` for `JdbcTemplate.query` to avoid overload ambiguity.
  - `WorkOrderStatisticsService(JdbcTemplate)` is annotated with `@Autowired` so Spring selects it instead of looking for a no-arg constructor.

## Changed Files

- `backend/src/main/java/com/example/workorder/api/AdminController.java`
- `backend/src/main/java/com/example/workorder/workorder/AdminWorkOrderCountResponse.java`
- `backend/src/main/java/com/example/workorder/workorder/DailyWorkOrderCountResponse.java`
- `backend/src/main/java/com/example/workorder/workorder/WorkOrderCountResponse.java`
- `backend/src/main/java/com/example/workorder/workorder/WorkOrderStatisticsQuery.java`
- `backend/src/main/java/com/example/workorder/workorder/WorkOrderStatisticsResponse.java`
- `backend/src/main/java/com/example/workorder/workorder/WorkOrderStatisticsService.java`
- `backend/src/main/resources/db/migration/V12__add_work_order_statistics_indexes.sql`
- `backend/src/test/java/com/example/workorder/api/AdminControllerTests.java`
- `backend/src/test/java/com/example/workorder/workorder/WorkOrderStatisticsServiceTests.java`
- `frontend/src/App.test.ts`
- `frontend/src/App.vue`
- `frontend/src/api/admin.ts`
- `frontend/src/styles.css`
- `PROGRESS.md`

## Verification

- `ReadLints` on `WorkOrderStatisticsService.java` returned no diagnostics.
- User-side logs confirmed Flyway reached schema version 12 successfully before the constructor selection failure.
- Previous attempts were blocked from agent shell before Cursor setting/restart:
  - `mvn test`
  - `npm run test -- --run`
  - `mvn test-compile`
- 2026-08-09 shell-channel diagnosis: agent-side minimal `pwd` failed with `Missing terminal shell stream event`; terminal records showed `pwd`, `git status`, and `git fetch` all failed within ~14-23ms. This pointed to Cursor local shell transport/bridge failure, not project path, Git, Maven, Node, or permissions.
- 2026-08-09 after enabling the legacy terminal tool and restarting Cursor, agent shell recovered:
  - `echo hello` succeeded.
  - `pwd` succeeded in `D:\CodexWork\projects\work-order-system\backend`.
  - `mvn --version` succeeded with Maven 3.9.9 and Java 21.0.5.
  - `mvn test-compile` from `backend` succeeded with `BUILD SUCCESS`.

## Notes

- Query performance was handled by using grouped SQL aggregations on indexed `work_orders` columns (`created_at`, `status`, `priority`, `handler_id`) and by adding a transition-table index for accept/completion lookup.
- `adminProcessingCounts` currently means count by current `work_orders.handler_id`. Historical multi-handler attribution is not calculated because the current domain model does not store a final/primary completed handler separately.
- Follow-up command for user: run `mvn spring-boot:run` from `D:\CodexWork\projects\work-order-system\backend`.