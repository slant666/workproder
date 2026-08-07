# Progress

## Current Task

T016: Work order comments

## Status

Complete. No required steps remain for T016.

## Branch

- `codex/t016-work-order-comments`

## Completed Work

- Added backend comment table:
  - `work_order_comments`
  - stores work order id, author id, content, and created time.
- Added backend DTOs:
  - `CreateWorkOrderCommentRequest`
  - `WorkOrderCommentResponse`
- Added comment endpoints:
  - `GET /api/work-orders/{id}/comments`
  - `POST /api/work-orders/{id}/comments`
  - `DELETE /api/work-orders/{id}/comments/{commentId}`
- Enforced T016 business rules:
  - only users who can view a work order can list or add comments;
  - comment content is trimmed and cannot be blank;
  - comments include author username, nickname, role, and creation time;
  - comments are ordered by `created_at ASC, id ASC`;
  - no update endpoint was added;
  - only admins can delete comments;
  - cancelled work orders reject new comments;
  - comment add/delete operations write operation logs.
- Updated frontend API:
  - `WorkOrderComment`
  - `fetchWorkOrderComments`
  - `createWorkOrderComment`
  - `deleteWorkOrderComment`
- Updated work order detail page:
  - loads comments with detail;
  - displays comments in chronological order;
  - shows author, role, time, and content;
  - provides comment form only when the work order is not cancelled;
  - shows delete buttons only for admins;
  - refreshes comments and operation logs after add/delete.
- XSS safety:
  - frontend renders comment content with Vue text interpolation, not `v-html`, so script text is escaped instead of executed.
- Added backend and frontend tests for comment behavior.

## Changed Files

- `backend/src/main/java/com/example/workorder/api/WorkOrderController.java`
- `backend/src/main/java/com/example/workorder/workorder/CreateWorkOrderCommentRequest.java`
- `backend/src/main/java/com/example/workorder/workorder/WorkOrderCommentResponse.java`
- `backend/src/main/java/com/example/workorder/workorder/WorkOrderService.java`
- `backend/src/main/resources/db/migration/V9__create_work_order_comments.sql`
- `backend/src/test/java/com/example/workorder/api/WorkOrderControllerTests.java`
- `backend/src/test/java/com/example/workorder/workorder/WorkOrderServiceTests.java`
- `frontend/src/App.test.ts`
- `frontend/src/App.vue`
- `frontend/src/api/workOrders.ts`
- `frontend/src/styles.css`
- `PROGRESS.md`

## Verification

- `backend`: `mvn.cmd test` passed, 79 tests.
- `frontend`: `npm.cmd run test` passed, 26 tests.
- `frontend`: `npm.cmd run build` passed.
- Frontend production build still emits the existing non-blocking Rollup pure-annotation messages from `@vueuse/core`.

## Notes

- Comment editing is intentionally not implemented for T016.
- Comment deletion currently hard-deletes the comment row, while the delete action remains in operation logs.
