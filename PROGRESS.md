# Progress

## Current Task

T012: 管理员工单列表。

## Status

Complete. No required steps remain for T012.

## Completed Work

- Added `GET /api/admin/work-orders` for administrator-only work-order listing.
- Ordinary users are rejected by `PermissionService.requireAdmin`; backend tests cover the `403` path.
- Reused the existing work-order paging/query flow by extending `WorkOrderListQuery` and sharing the criteria-based query execution in `WorkOrderService`.
- Preserved `GET /api/work-orders` behavior from T011: regular users see their own work orders; admins can still see all through the existing endpoint.
- Added `handler_id` to work orders with a nullable foreign key and indexes for creator, handler, status, priority, and created time.
- Extended work-order responses with `handlerId` and `handlerUsername`.
- Added admin filters for title keyword, status, priority, creator ID, handler ID, created date range, created-time sort, page, and page size.
- Added validation for creator ID, handler ID, date formats, date ranges, sort, page, and page size.
- Replaced the admin placeholder page with an administrator work-order list UI, filters, total/page/page-size display, pagination, empty state, loading state, and error handling.
- Added frontend API support for `/api/admin/work-orders`.

## Changed Files

- `backend/src/main/java/com/example/workorder/api/AdminController.java`
- `backend/src/main/java/com/example/workorder/api/WorkOrderController.java`
- `backend/src/main/java/com/example/workorder/workorder/PagedWorkOrderResponse.java`
- `backend/src/main/java/com/example/workorder/workorder/WorkOrderListQuery.java`
- `backend/src/main/java/com/example/workorder/workorder/WorkOrderResponse.java`
- `backend/src/main/java/com/example/workorder/workorder/WorkOrderService.java`
- `backend/src/main/resources/db/migration/V5__add_work_order_handler_and_indexes.sql`
- `backend/src/test/java/com/example/workorder/api/AdminControllerTests.java`
- `backend/src/test/java/com/example/workorder/api/WorkOrderControllerTests.java`
- `backend/src/test/java/com/example/workorder/workorder/WorkOrderListServiceTests.java`
- `backend/src/test/java/com/example/workorder/workorder/WorkOrderServiceTests.java`
- `frontend/src/App.vue`
- `frontend/src/App.test.ts`
- `frontend/src/api/admin.ts`
- `frontend/src/api/workOrders.ts`
- `frontend/src/styles.css`
- `PROGRESS.md`

## Verification

- `backend`: `mvn.cmd test` passed, 52 tests.
- `frontend`: `npm.cmd run test` passed, 17 tests.
- `frontend`: `npm.cmd run build` passed.

## Notes

- Frontend production build still emits the existing non-blocking Rollup pure-annotation messages from `@vueuse/core`.
- The Git repository still reports project files as untracked, so a normal tracked-file diff is not available.
