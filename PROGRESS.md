# Progress

## Current Task

T018: 管理员用户管理

## Status

Implementation complete. The backend compile issue reported in `AdminUserServiceTests` has been fixed. Commit-and-push is blocked because Git commands are not returning through the local shell transport.

## Branch

- `codex/t018-admin-user-management`

## Completed Work

- Added dedicated user-management audit logging:
  - `user_management_audit_logs`
  - records actor, target user, action, changed field, old value, new value, optional details, and creation time.
- Added backend user-management DTOs and query/request models:
  - `AdminUserResponse`
  - `PagedAdminUserResponse`
  - `AdminUserListQuery`
  - `UpdateUserEnabledRequest`
  - `UpdateUserRoleRequest`
  - `AdminUserException`
- Added `AdminUserService` for identity-domain user management:
  - admin user list with keyword search;
  - pagination normalization and page-size boundary;
  - enable/disable users;
  - promote users to admin and downgrade admins to user;
  - self-disable and self-downgrade protection;
  - audit-log writes for real management changes.
- Extended admin API endpoints:
  - `GET /api/admin/users`
  - `PUT /api/admin/users/{id}/enabled`
  - `PUT /api/admin/users/{id}/role`
- Kept user deletion out of the API so users linked to business data are not physically deleted.
- Preserved disabled-user login protection in `AuthService`.
- Extended frontend admin API:
  - `AdminUser`
  - `PagedAdminUsers`
  - `AdminUserListQuery`
  - `fetchAdminUsers`
  - `updateAdminUserEnabled`
  - `updateAdminUserRole`
- Added admin user-management UI:
  - user list;
  - search;
  - pagination;
  - enable/disable actions;
  - promote/downgrade actions;
  - second confirmation before role changes;
  - current-admin self disable/downgrade actions disabled;
  - refreshes handlers after user status or role changes.
- Added backend tests for authorization, pagination boundaries, self-operation protection, role/status changes, audit logging, and disabled login behavior.
- Added frontend tests for admin user loading, search, pagination, role-change confirmation cancellation, self-operation disabled buttons, and enable/disable refresh flow.

## Changed Files

- `backend/src/main/java/com/example/workorder/api/AdminController.java`
- `backend/src/main/java/com/example/workorder/api/ApiExceptionHandler.java`
- `backend/src/main/java/com/example/workorder/auth/AdminUserException.java`
- `backend/src/main/java/com/example/workorder/auth/AdminUserListQuery.java`
- `backend/src/main/java/com/example/workorder/auth/AdminUserResponse.java`
- `backend/src/main/java/com/example/workorder/auth/AdminUserService.java`
- `backend/src/main/java/com/example/workorder/auth/PagedAdminUserResponse.java`
- `backend/src/main/java/com/example/workorder/auth/UpdateUserEnabledRequest.java`
- `backend/src/main/java/com/example/workorder/auth/UpdateUserRoleRequest.java`
- `backend/src/main/resources/db/migration/V11__create_user_management_audit_logs.sql`
- `backend/src/test/java/com/example/workorder/api/AdminControllerTests.java`
- `backend/src/test/java/com/example/workorder/auth/AdminUserServiceTests.java`
- `backend/src/test/java/com/example/workorder/auth/AuthServiceTests.java`
- `frontend/src/App.test.ts`
- `frontend/src/App.vue`
- `frontend/src/api/admin.ts`
- `frontend/src/styles.css`
- `PROGRESS.md`

## Verification

- `backend`: fixed the `AdminUserServiceTests` compile error caused by calling `updateRole` without the required `CurrentUser actor` argument.
- `backend`: `ReadLints` reported no diagnostics for `AdminUserServiceTests.java` after the fix.
- `backend`: full `mvn.cmd test` re-run could not be confirmed because the execution channel returned a shell stream error after the fix.
- `frontend`: `ReadLints` reported no diagnostics for edited frontend files.
- `frontend`: `npm.cmd run test -- --run` could not be confirmed because the local terminal transport repeatedly closed with `Missing terminal shell stream event` before returning output.
- `git`: commit-and-push could not be completed from the agent. `git status --short --branch` did not return through the local shell transport; an isolated shell attempt also returned a shell stream event error.

## Notes

- The current branch was created as `codex/t018-admin-user-management`.
- No delete-user endpoint was added by design; disabling is the supported administrative action.