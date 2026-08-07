# Progress

## Current Task

T013: 分配工单处理人。

## Status

Complete. No required steps remain for T013.

## Completed Work

- Created branch `codex/t013-assign-work-order-handler`.
- Added administrator-only handler candidate endpoint `GET /api/admin/handlers`.
- Added administrator-only assignment endpoint `PUT /api/admin/work-orders/{id}/handler`.
- Added `AssignWorkOrderRequest` and `AdminHandlerResponse`.
- Enforced assignment rules in `WorkOrderService`:
  - Assigning actor must be an admin.
  - Handler ID must be valid.
  - Handler must exist, be enabled, and have role `ADMIN`.
  - Regular users and disabled admins cannot become handlers.
  - Completed or cancelled work orders cannot be reassigned.
  - Pending work orders support first assignment and reassignment.
- Added `work_order_assignments` migration table recording `old_handler_id`, `new_handler_id`, `assigned_by`, and `created_at`.
- Added admin detail UI for explicit handler selection and confirmation before assignment.
- Updated frontend admin API helpers for handler list and assignment.
- Added backend controller/service tests for permission and illegal assignment paths.
- Added frontend test coverage for admin assignment confirmation and regular-user visibility.

## Changed Files

- `backend/src/main/java/com/example/workorder/api/AdminController.java`
- `backend/src/main/java/com/example/workorder/workorder/AdminHandlerResponse.java`
- `backend/src/main/java/com/example/workorder/workorder/AssignWorkOrderRequest.java`
- `backend/src/main/java/com/example/workorder/workorder/WorkOrderService.java`
- `backend/src/main/resources/db/migration/V6__create_work_order_assignments.sql`
- `backend/src/test/java/com/example/workorder/api/AdminControllerTests.java`
- `backend/src/test/java/com/example/workorder/workorder/WorkOrderServiceTests.java`
- `frontend/src/App.vue`
- `frontend/src/App.test.ts`
- `frontend/src/api/admin.ts`
- `frontend/src/styles.css`
- `PROGRESS.md`

## Verification

- `backend`: `mvn.cmd test` passed, 62 tests.
- `frontend`: `npm.cmd run test` passed, 18 tests.
- `frontend`: `npm.cmd run build` passed.

## Notes

- Frontend production build still emits the existing non-blocking Rollup pure-annotation messages from `@vueuse/core`.

