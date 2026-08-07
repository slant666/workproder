# Progress

## Current Task

T014: 工单状态流转。

## Status

Complete. No required steps remain for T014.

## Completed Work

- Created branch `codex/t014-work-order-state-flow` from updated `main`.
- Expanded work order statuses to:
  - `待处理`
  - `处理中`
  - `待确认`
  - `已完成`
  - `已取消`
- Added backend state transition endpoints:
  - `PUT /api/admin/work-orders/{id}/accept`
  - `PUT /api/admin/work-orders/{id}/submit`
  - `PUT /api/admin/work-orders/{id}/return`
  - `POST /api/work-orders/{id}/confirm`
- Kept `POST /api/work-orders/{id}/cancel`, now restricted to the creator and only from `待处理`.
- Added backend state machine rules:
  - Admin handler accepts: `待处理 -> 处理中`
  - Handler submits completion: `处理中 -> 待确认`
  - Creator confirms: `待确认 -> 已完成`
  - Creator cancels: `待处理 -> 已取消`
  - Handler returns: `待确认 -> 处理中`
  - `已完成` and `已取消` reject further transitions.
- Admin accept auto-assigns the work order to the current admin when no handler exists and records assignment history.
- Added `work_order_status_transitions` migration table to record status history.
- Updated frontend status filters to include all five statuses.
- Added frontend detail actions for accept, submit, return, confirm, and creator-only cancel.
- Added backend tests for legal transitions, illegal transitions, role restrictions, terminal states, and controller routing.
- Added frontend tests for state action buttons and creator confirmation.

## Changed Files

- `backend/src/main/java/com/example/workorder/api/AdminController.java`
- `backend/src/main/java/com/example/workorder/api/WorkOrderController.java`
- `backend/src/main/java/com/example/workorder/workorder/WorkOrderService.java`
- `backend/src/main/resources/db/migration/V7__create_work_order_status_transitions.sql`
- `backend/src/test/java/com/example/workorder/api/AdminControllerTests.java`
- `backend/src/test/java/com/example/workorder/api/WorkOrderControllerTests.java`
- `backend/src/test/java/com/example/workorder/workorder/WorkOrderServiceTests.java`
- `frontend/src/App.vue`
- `frontend/src/App.test.ts`
- `frontend/src/api/admin.ts`
- `frontend/src/api/workOrders.ts`
- `PROGRESS.md`

## Verification

- `backend`: `mvn.cmd test` passed, 69 tests.
- `frontend`: `npm.cmd run test` passed, 20 tests.
- `frontend`: `npm.cmd run build` passed.

## Notes

- Frontend production build still emits the existing non-blocking Rollup pure-annotation messages from `@vueuse/core`.

