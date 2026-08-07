# Progress

## Current Task

T015: 工单操作记录

## Status

Complete. No required steps remain for T015.

## Branch

- `codex/t015-work-order-operation-logs`

## Completed Work

- Added unified backend operation log table:
  - `work_order_operation_logs`
  - records work order id, actor, action, optional field name, old value, new value, details JSON, and created time.
- Added backend timeline DTO:
  - `WorkOrderOperationLogResponse`
- Added read endpoint:
  - `GET /api/work-orders/{id}/logs`
  - Uses the session user and backend visibility checks.
- Added automatic backend log writes for:
  - create work order: `create`
  - edit work order fields: `update`
  - assign/reassign handler: `assign_handler`
  - status flow actions: `accept`, `submit`, `return`, `confirm`
  - cancel work order: `cancel`
- Preserved existing `work_order_assignments` and `work_order_status_transitions` tables for compatibility.
- Added future backend helper hooks for comment and attachment operations:
  - `recordCommentOperation`
  - `recordAttachmentOperation`
- Updated frontend API:
  - `WorkOrderOperationLog`
  - `fetchWorkOrderLogs`
- Updated work order detail page:
  - Loads operation logs after detail load.
  - Refreshes logs after edit, assignment, status actions, and cancellation.
  - Renders logs with Element Plus timeline.
  - Frontend only reads logs and has no log write endpoint.
- Added frontend timeline styles.
- Added backend and frontend tests for operation logs.

## Changed Files

- `backend/src/main/java/com/example/workorder/api/WorkOrderController.java`
- `backend/src/main/java/com/example/workorder/workorder/WorkOrderOperationLogResponse.java`
- `backend/src/main/java/com/example/workorder/workorder/WorkOrderService.java`
- `backend/src/main/resources/db/migration/V8__create_work_order_operation_logs.sql`
- `backend/src/test/java/com/example/workorder/api/WorkOrderControllerTests.java`
- `backend/src/test/java/com/example/workorder/workorder/WorkOrderServiceTests.java`
- `frontend/components.d.ts`
- `frontend/src/App.test.ts`
- `frontend/src/App.vue`
- `frontend/src/api/workOrders.ts`
- `frontend/src/styles.css`
- `PROGRESS.md`

## Verification

- `backend`: `mvn.cmd test` passed, 73 tests.
- `frontend`: `npm.cmd run test` passed, 21 tests.
- `frontend`: `npm.cmd run build` passed.
- Frontend production build still emits the existing non-blocking Rollup pure-annotation messages from `@vueuse/core`.

## Notes

- Logs are append-only at the application API level: no create/update/delete endpoints for logs were added.
- Database-level immutability beyond app behavior, such as triggers or restricted DB grants, is not added in this task.
