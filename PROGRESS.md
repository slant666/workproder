# Progress

## Standing Self-Check Rule

- For any permission, role, menu, workflow, or status-action change, do not rely only on unit tests.
- Before reporting completion, perform or explicitly reason through a role matrix self-check:
  - `USER`
  - `CUSTOMER_SERVICE`
  - `DEPARTMENT_ADMIN`
  - `ADMIN`
  - `AUDITOR`
- For each affected role, verify the end-to-end path:
  - `/api/auth/me` returns expected `roles` and `permissions`.
  - Frontend menu/button visibility matches those permissions.
  - Controller-level permission checks allow/deny correctly.
  - Service-layer data permissions still allow/deny correctly.
  - Existing data/role assignment flows actually update effective permissions after logout/login.
- If a role is seeded but not fully usable, state that limitation clearly instead of presenting it as complete.

## Latest Note - RBAC Plan Analysis

- User asked for an RBAC implementation plan covering menu permissions, API permissions, data permissions, role/permission configuration, and privilege-escalation prevention.
- Current code has `Role.USER` / `Role.ADMIN`, session-based auth in `PermissionService`, department-admin mapping via `department_admins`, and work-order data visibility in `WorkOrderService`.
- No business code was changed for this analysis turn.

## Latest Note - RBAC Implementation

- Implemented RBAC foundation on top of the current organization/department permission work.
- Added Flyway migration `V15__add_rbac_permissions.sql` with `roles`, `permissions`, `role_permissions`, and `user_roles`.
- Seeded roles: `USER`, `CUSTOMER_SERVICE`, `DEPARTMENT_ADMIN`, `ADMIN`, `AUDITOR`.
- Seeded permission codes for ticket, user, role, organization, and statistics actions, including button-level codes such as `ticket:create`, `ticket:assign`, `user:disable`, and `statistics:view`.
- Added `RbacPermission` constants and `RbacService` for loading user roles/permissions, with legacy fallback when RBAC tables are absent in old tests/schemas.
- Extended `CurrentUser` to return `roles` and `permissions` while keeping legacy constructors compatible.
- Extended `PermissionService` with `requirePermission` and `requireAnyPermission`.
- Wired API permission checks into work-order and admin controllers.
- Admin work-order processing/list endpoints now require processing permissions and still use work-order visibility/data-scope checks.
- User role updates now sync the primary `user_roles` record when RBAC tables exist.
- Frontend `CurrentUser` type now accepts `roles` and `permissions`.
- Frontend menu/button visibility now uses permission codes with legacy role fallback for old responses/tests.
- Updated controller tests to expect RBAC controller-level denial for regular users on admin processing endpoints.
- Verification passed:
  - Backend `mvn test`: BUILD SUCCESS, 118 tests run, 0 failures, 0 errors, 0 skipped.
  - Frontend `npm.cmd run test`: 1 test file passed, 41 tests passed.
- Remaining optional follow-up: build a full role/permission configuration UI and API for editing `roles`/`role_permissions` beyond the seeded defaults.

## Latest Note - Department Admin RBAC Sync Fix

- Fixed a gap where granting a user as department admin only wrote `department_admins` but did not grant the RBAC `DEPARTMENT_ADMIN` role.
- `AdminUserService.updateDepartmentAdmin` now adds `DEPARTMENT_ADMIN` to `user_roles` when granting and removes it when revoking.
- Added migration `V16__sync_department_admin_roles.sql` to backfill existing `department_admins` records into `user_roles`.
- Frontend current-user role display now shows all role codes, e.g. `USER / DEPARTMENT_ADMIN`, so users can verify the effective RBAC roles after logging in.
- Verification:
  - Backend `mvn test`: BUILD SUCCESS, 118 tests run, 0 failures, 0 errors, 0 skipped.
  - Frontend `npm.cmd run test`: 1 test file passed, 41 tests passed.

## Latest Note - Customer Service Role Usability Fix

- Fixed `CUSTOMER_SERVICE` being seeded but not visibly usable in the frontend.
- Admin overview and admin work-order list now accept processing permissions (`ticket:accept`, `ticket:submit`, `ticket:return`) in addition to `ticket:assign`.
- Frontend management entry now appears for users with customer-service processing permissions.
- Frontend admin work-order list loads for users with processing permissions, while handler list/assignment remains limited to `ticket:assign`.
- Service-layer work-order processing now also checks RBAC permissions directly:
  - `accept` requires `ticket:accept` plus existing data visibility.
  - `submitForConfirmation` requires `ticket:submit` and handler ownership.
  - `returnToProcessing` requires `ticket:return` and handler ownership.
- Customer service users can now enter the processing workspace and operate on visible/assigned work orders according to permissions and existing data scope rules.
- Verification:
  - Backend `mvn test`: BUILD SUCCESS, 118 tests run, 0 failures, 0 errors, 0 skipped.
  - Frontend `npm.cmd run test`: 1 test file passed, 41 tests passed.

## Latest Note - Customer Service Legacy Role Fallback Fix

- User reported that setting `cyyy1` to `CUSTOMER_SERVICE` in the database still showed the user as `USER` after login.
- Root cause: RBAC is primarily driven by `user_roles`; if someone directly changes legacy `users.role`, fallback permissions only handled `USER`/`ADMIN` correctly.
- Fixed fallback mappings for legacy `users.role` values:
  - `CUSTOMER_SERVICE`
  - `DEPARTMENT_ADMIN`
  - `AUDITOR`
- Added test coverage: logging in with legacy `users.role = CUSTOMER_SERVICE` now returns role `CUSTOMER_SERVICE`, includes role in `roles`, grants `ticket:accept`/`ticket:submit`, and does not grant `ticket:create`.
- Verification:
  - Backend `mvn test`: BUILD SUCCESS, 119 tests run, 0 failures, 0 errors, 0 skipped.
  - Frontend `npm.cmd run test`: 1 test file passed, 41 tests passed.

## Current Task

T024: 组织架构与部门级工单权限。

## Status

已完成本轮实现与验证。新增公司、部门、团队、部门管理员映射、用户组织归属确认、工单所属部门，以及部门级工单可见性和处理人分配规则。没有为了测试通过而削弱业务规则。

## Completed Work

- Added Flyway migration `V13__add_organization_model.sql` for `companies`, `departments`, `teams`, `department_admins`, user org fields, work order org fields, and related indexes.
- Added organization service/controller APIs for listing public organization options and admin creation/enabled management.
- Extended auth/register/current user/admin user/work order response DTOs with organization fields.
- Registration accepts requested company/department/team, but stores `org_confirmed=false` until admin confirmation.
- Work order creation now derives organization from the confirmed creator and rejects creation before department confirmation.
- Work order visibility now allows global ADMIN, same confirmed department members, department admins, creator, and handler.
- Comments, attachments, logs, and downloads continue to reuse work order visibility through existing service checks.
- Handler assignment now requires the actor to be global ADMIN or department admin for the order department, and the handler must be enabled plus global ADMIN or department admin for that department.
- Frontend API types/functions were extended for organization data, user org updates, department admin grants, and basic org management.
- Frontend registration now submits organization application fields; lists/detail/user management show organization and confirmation state.
- Frontend user management now supports confirming/canceling user organization and granting/revoking department admin permission.
- Added default organization seed data so the registration organization selectors are not empty on a fresh database.
- Fixed frontend organization creation payloads to use backend fields `companyId` and `departmentId`.
- Fixed department-admin grant payload to send backend field `departmentAdmin`.
- Added/updated tests for organization schema, department visibility, department admin assignment, unconfirmed org creation rejection, and test fixture isolation.

## Changed Files

- `backend/src/main/resources/db/migration/V13__add_organization_model.sql`
- `backend/src/main/java/com/example/workorder/api/AdminController.java`
- `backend/src/main/java/com/example/workorder/api/ApiExceptionHandler.java`
- `backend/src/main/java/com/example/workorder/api/OrganizationController.java`
- `backend/src/main/java/com/example/workorder/auth/*`
- `backend/src/main/java/com/example/workorder/organization/*`
- `backend/src/main/java/com/example/workorder/workorder/WorkOrderResponse.java`
- `backend/src/main/java/com/example/workorder/workorder/WorkOrderService.java`
- `backend/src/test/java/com/example/workorder/api/AdminControllerTests.java`
- `backend/src/test/java/com/example/workorder/auth/*Tests.java`
- `backend/src/test/java/com/example/workorder/workorder/*Tests.java`
- `frontend/src/App.vue`
- `frontend/src/api/admin.ts`
- `frontend/src/api/auth.ts`
- `frontend/src/api/workOrders.ts`
- `PROGRESS.md`

## Verification

- `mvn test` from `backend`: BUILD SUCCESS, 118 tests run, 0 failures, 0 errors, 0 skipped.
- `npm.cmd run test` from `frontend`: 1 test file passed, 41 tests passed.

## Remaining Review Items

- Frontend has API functions for creating/enabling companies/departments/teams, but a richer organization maintenance panel can be polished in a follow-up.
- Existing frontend authorization still primarily distinguishes global ADMIN for showing management buttons; department-admin-specific UX can be made more explicit once backend returns department-admin flags for the current user.

## Latest Note - SLA and Notifications

- Implemented SLA deadline support and in-app notifications.
- Added Flyway migration `V17__add_sla_and_notifications.sql`:
  - `work_orders` now stores first-response deadline, resolution deadline, first response time, resolved time, and SLA status.
  - `work_order_sla_events` deduplicates scheduled SLA notifications.
  - `notifications` stores recipient, type, title, content, related work order, read time, and created time.
- Work order creation now calculates SLA deadlines by priority:
  - Low: first response 24h, resolution 72h.
  - Medium: first response 8h, resolution 24h.
  - High: first response 1h, resolution 4h.
- Work order state transitions now update SLA timestamps:
  - Accepting records first response time.
  - Confirming completion records resolved time and marks SLA completed.
- Added scheduled SLA scanner with `@EnableScheduling`:
  - Marks near-overdue and overdue work orders.
  - Sends deduplicated notifications to admins, department admins, and assigned handlers.
- Added notification backend API:
  - `GET /api/notifications`
  - `GET /api/notifications/unread-count`
  - `PUT /api/notifications/{id}/read`
  - `PUT /api/notifications/read-all`
- Notification generation now covers:
  - Handler assignment.
  - Work order status changes.
  - Comments to related participants.
  - SLA near-overdue/overdue scanner alerts.
- Statistics dashboard now includes SLA near-overdue count, first-response overdue count, resolution overdue count, and overdue priority distribution.
- Frontend now shows:
  - Top navigation notification entry with unread count.
  - Notification list, mark-all-read, and click-to-open-work-order behavior.
  - SLA summary in user/admin work order lists.
  - SLA timestamps and status in work order detail.
  - SLA cards and priority chart in admin statistics.
- Added backend test coverage for scheduled SLA scanning, SLA status update, notification recipient fanout, near-overdue notification type, and duplicate event suppression.
- Self-check:
  - `USER`: can see own work order SLA fields and personal notifications; no admin statistics.
  - `CUSTOMER_SERVICE`: keeps processing permissions; can receive assignment/SLA notifications and see SLA in work order processing views.
  - `DEPARTMENT_ADMIN`: department data scope remains through existing RBAC/data checks; receives department SLA alerts.
  - `ADMIN`: receives global SLA alerts, can view SLA statistics and notification list.
  - `AUDITOR`: still limited by RBAC read/statistics permissions; notification APIs only expose the current user's own notifications.
- Verification:
  - Backend `mvn test`: BUILD SUCCESS, 121 tests run, 0 failures, 0 errors, 0 skipped.
  - Frontend `npm.cmd run test`: 1 test file passed, 41 tests passed.
  - Frontend residual bad-character scan for `�`: no matches.
- Frontend residual bad-character scan for `锟絗: no matches.

## Latest Note - Email Notification Plan Analysis

- User asked to analyze adding email on top of in-app notifications for registration verification, password reset, work-order assignment, SLA timeout, and status changes.
- No business code was changed in this analysis turn.
- Current code has in-app notification persistence and SLA event deduplication, but no mail dependency and no user email field yet.
- Recommended direction: introduce a database-backed email outbox first, enqueue email intents inside existing business transactions, and deliver asynchronously with retry/idempotency so email failures do not break work-order operations.

## Latest Note - Email Notification Implementation

- Implemented database-backed email outbox foundation.
- Added Flyway migration `V18__add_email_outbox.sql`:
  - `users.email`
  - `users.email_verified_at`
  - `email_verification_tokens`
  - `password_reset_tokens`
  - `email_outbox` with `dedupe_key`, delivery status, retry count, next retry time, last error, and sent time.
- Added mail dependency and email configuration under `app.email`.
- Added `EmailOutboxService`:
  - Generates 6-digit verification/reset codes.
  - Stores only SHA-256 code hashes in token tables.
  - Enqueues email rows with unique `dedupe_key`.
  - Enqueues work-order related emails only for verified recipient emails.
  - Degrades gracefully for notification emails if outbox/email columns are absent in older test schemas.
- Added `EmailDeliveryScheduler`:
  - Polls pending/failed outbox rows.
  - Claims rows with `PENDING/FAILED -> SENDING`.
  - Marks success as `SENT`.
  - Marks failures as `FAILED` with exponential backoff, then `DEAD` after max attempts.
- Added `MailEmailSender` for real SMTP delivery when `app.email.delivery-enabled=true`.
- Added `NoopEmailSender` as default when delivery is disabled, so local/test environments do not need a real SMTP account.
- Registration now accepts optional `email`; when present it creates a verification token and email outbox row in the registration transaction.
- Added auth APIs:
  - `POST /api/auth/email/verify`
  - `POST /api/auth/password-reset`
  - `POST /api/auth/password-reset/confirm`
- Password reset only sends reset mail for enabled users with verified email, while keeping the request response indistinguishable to avoid account enumeration.
- Existing in-app notifications now also enqueue email for:
  - `WORK_ORDER_ASSIGNED`
  - `WORK_ORDER_STATUS_CHANGED`
  - `SLA_NEAR_OVERDUE`
  - `SLA_OVERDUE`
- Work-order operations are not blocked by email enqueue/delivery failure; station notifications and business state changes remain the source of truth.
- Frontend registration form now includes an optional email field and submits it to the backend.
- Added backend tests for email outbox dedupe, delivery success, and retry-on-failure behavior.
- Self-check:
  - Email send failure does not roll back work-order assignment/status/SLA operations because those only attempt notification email enqueue and delivery is asynchronous.
  - Registration/password-reset security flows create token + outbox in service transactions, so the verification/reset path is not split across unrelated writes.
  - Duplicate sending is limited by `email_outbox.dedupe_key` and scheduler row claiming.
  - Real SMTP is opt-in; default local behavior records/logs intended sends without external dependency.
- Verification:
  - Backend `mvn test`: BUILD SUCCESS, 124 tests run, 0 failures, 0 errors, 0 skipped.
  - Frontend `npm.cmd run test`: 1 test file passed, 41 tests passed.

## Latest Note - Registration Duplicate Email Message Fix

- User reported registration showed "username already used" even when the username was not used.
- Root cause: after adding unique `users.email`, `RegistrationService` caught all duplicate-key errors and mapped them to the old username-only message.
- Fixed `RegistrationService` to normalize/check email separately and return `邮箱已被使用` for duplicate emails while preserving `用户名已被使用` for duplicate usernames.
- Also rewrote the registration service messages touched by this flow as readable Chinese instead of historical mojibake.
- Verification:
  - Backend targeted `mvn test -Dtest=RegistrationServiceTests`: BUILD SUCCESS, 3 tests run, 0 failures, 0 errors, 0 skipped.
  - Backend full `mvn test`: BUILD SUCCESS, 124 tests run, 0 failures, 0 errors, 0 skipped.

## Latest Note - Registration Organization Retention Check

- User reported registration appeared to lose other fields such as department selections after the email changes.
- Inspected current frontend submit payload and backend registration insert path:
  - Frontend still submits `companyId`, `departmentId`, and `teamId`.
  - Backend `RegistrationService` still inserts `company_id`, `department_id`, and `team_id`.
  - `RegisterResponse`/`UserSql.mapRegister` still return organization fields.
- Added backend regression test `registersUserWithEmailAndOrganizationApplication` to verify registering with email plus company/department/team preserves those fields in both response and database.
- Verification:
  - Backend targeted `mvn test -Dtest=RegistrationServiceTests`: BUILD SUCCESS, 4 tests run, 0 failures, 0 errors, 0 skipped.
  - Frontend `npm.cmd run test`: 1 test file passed, 41 tests passed.
- Docker Desktop was not reachable in this session, so live container DB/logs could not be inspected.

## Latest Note - Shared Email Registration Rule

- User clarified email should not be unique; multiple users may use the same email address.
- Removed registration-time duplicate email validation from `RegistrationService`; username remains unique.
- Removed `CREATE UNIQUE INDEX uk_users_email` from `V18__add_email_outbox.sql` for fresh databases.
- Added migration `V19__allow_shared_user_email.sql` to drop `uk_users_email` for databases that already applied V18.
- Added test `allowsMultipleUsersToShareSameEmail` to verify two different users can register with the same email.
- Verification:
  - Backend targeted `mvn test -Dtest=RegistrationServiceTests`: BUILD SUCCESS, 5 tests run, 0 failures, 0 errors, 0 skipped.
  - Backend full `mvn test`: BUILD SUCCESS, 126 tests run, 0 failures, 0 errors, 0 skipped.

## Latest Note - Flyway V18 Checksum Fix

- User hit Flyway validation failure after `V18__add_email_outbox.sql` had been edited post-application.
- Corrected by restoring `V18__add_email_outbox.sql` to include the original `uk_users_email` creation.
- Kept `V19__allow_shared_user_email.sql` as the forward migration that drops `uk_users_email`.
- Important rule: do not edit already-applied Flyway migrations; add a new migration instead.
- Verification:
  - Backend targeted `mvn test -Dtest=RegistrationServiceTests`: BUILD SUCCESS, 5 tests run, 0 failures, 0 errors, 0 skipped.

## Latest Note - Excel Import/Export Implementation

- Implemented Excel import/export foundation for enterprise admin workflows.
- Added Apache POI dependency and Flyway migration `V20__add_file_jobs.sql`.
- Added `file_jobs` tracking for file task type, status, creator, original filename, generated result path, error-report path, row counts, filters, and error message.
- Added backend Excel module:
  - Work-order export writes `.xlsx` files using the current admin work-order filters and existing `WorkOrderService.listVisible` data scope.
  - User import template download generates a reusable `.xlsx` template.
  - Batch user import supports partial success/partial failure.
  - Import validates username length, duplicate usernames in Excel/database, required nickname, role codes, optional shared email, organization names, enabled flag, and writes successful users plus `user_roles`.
  - Failed rows generate a downloadable error report instead of rolling back successful rows.
  - Email remains shareable; import does not enforce unique email.
- Added admin endpoints:
  - `GET /api/admin/users/import-template`
  - `POST /api/admin/users/import-jobs`
  - `GET /api/admin/file-jobs/{id}/error-report`
  - `GET /api/admin/work-orders/export`
- Added frontend admin actions:
  - Download user import template.
  - Upload user import `.xlsx`.
  - Download last import error report when failures exist.
  - Export admin work orders by the current filter conditions.
- Self-check:
  - `USER`: no user import/export admin entry; controller denies admin Excel endpoints without required permissions.
  - `CUSTOMER_SERVICE`: can export visible processing work orders through existing processing permissions and service data scope; cannot import users.
  - `DEPARTMENT_ADMIN`: can export visible department-scoped work orders through existing data scope; user import depends on `user:update`, not granted by default seeded department-admin permissions.
  - `ADMIN`: can import users, download templates/error reports, and export work orders.
  - `AUDITOR`: currently not allowed to export from the admin work-order endpoint because export uses processing permissions; statistics/audit read remains unchanged.
- Self-check found and fixed one compatibility issue: H2 can return multiple generated-key columns, so `ExcelService` now extracts generated IDs from the returned key map instead of assuming `GeneratedKeyHolder.getKey()` is always single-column.
- Verification:
  - Backend targeted `mvn test -Dtest=ExcelServiceTests`: BUILD SUCCESS, 2 tests run, 0 failures, 0 errors, 0 skipped.
  - Backend full `mvn test`: BUILD SUCCESS, 128 tests run, 0 failures, 0 errors, 0 skipped.
  - Frontend `npm.cmd run test`: 1 test file passed, 41 tests passed.
  - Frontend `npm.cmd run build`: BUILD SUCCESS.

## Latest Note - Redis Suitability Analysis

- User asked to analyze the most suitable Redis usage before implementation, with emphasis on avoiding forced resume-driven Redis usage.
- No business code was changed for this analysis turn.
- Current project facts:
  - `WorkOrderStatisticsService.dashboard` runs multiple aggregate SQL queries per statistics request, including grouped counts, daily counts, average processing time, handler counts, and SLA counts.
  - `AuthService` stores login failure counters in an in-memory `ConcurrentHashMap`, so limits reset on restart and are not shared across multiple backend instances.
  - Email verification and password reset codes are already persisted in database token tables as SHA-256 hashes with expiry and used flags.
  - RBAC permissions are loaded from DB and are security-sensitive; stale permission cache would risk showing or allowing outdated access after role changes.
- Recommended Redis priority:
  1. Cache statistics dashboard results for a short TTL, e.g. 5 minutes, keyed by date filter and a cache schema version.
  2. Move login failure counters to Redis with TTL for distributed rate limiting.
  3. Optionally use Redis for duplicate-submit tokens if a concrete repeated-submit problem appears.
  4. Do not cache permissions in Redis initially; rely on DB/session behavior until there is measured permission-query pressure.
  5. Do not replace database-backed verification/reset tokens with Redis-only storage because DB persistence and auditability are more appropriate for account recovery/security flows.
- Key design points for statistics caching:
  - Cache only admin/global statistics endpoint responses, not data-scoped lists.
  - TTL should be short, around 5 minutes.
  - Invalidate or version-bump cache on work-order create/update/status/assignment and SLA scanner changes.
  - Redis failure should degrade to DB query path.
  - Prevent stale cache issues by using short TTL plus explicit eviction/versioning on writes.

## Latest Note - Redis Statistics Cache and Login Rate Limit Implementation

- Implemented Redis as an optional accelerator, not a hard dependency.
- Added `spring-boot-starter-data-redis` and Redis connection settings in `application.yml`.
- Added `RedisSupportService`:
  - Caches work-order statistics responses for 5 minutes.
  - Builds statistics cache keys from normalized `createdFrom`/`createdTo` query conditions.
  - Catches Redis read/write/delete failures and returns control to the database/business path.
  - Stores login failure counters in Redis with a 15-minute refreshed TTL.
  - Clears Redis login counters on successful login.
- `WorkOrderStatisticsService.dashboard` now:
  - Checks Redis first after validating/normalizing query dates.
  - Falls back to SQL aggregation when Redis misses or fails.
  - Writes successful SQL aggregation results back to Redis.
- Statistics cache invalidation is triggered after:
  - Work-order creation.
  - Work-order update.
  - Handler assignment.
  - Status transitions.
  - SLA scanner updates.
- `AuthService` now uses Redis login failure counters when Redis is available; if Redis is absent/failing, it falls back to the existing in-memory counter behavior.
- Docker Compose now includes a Redis 7 service and points the backend at it through `WORK_ORDER_REDIS_HOST`.
- `.env.example` now includes Redis host port and timeout settings.
- Added tests for:
  - Statistics cache hit bypassing SQL response generation.
  - Statistics SQL fallback writing results to cache.
  - Redis login lock denial.
  - Redis login failure record/clear behavior.
- Verification:
  - Targeted backend tests: `mvn test "-Dtest=WorkOrderStatisticsServiceTests,AuthServiceTests"` passed, 14 tests run, 0 failures, 0 errors, 0 skipped.
  - Full backend tests: `mvn test` passed, 132 tests run, 0 failures, 0 errors, 0 skipped.
- Frontend tests were not run because no frontend code was changed in this Redis implementation turn.

## Latest Note - Docker Backend Health Fix

- User reported `docker compose up -d --build` failed because `work-order-backend` was unhealthy.
- Root cause from backend logs:
  - Backend process was running.
  - Docker healthcheck called `/actuator/health`.
  - Spring Boot mail health indicator tried to connect to `smtp.qq.com:465`.
  - SMTP SSL handshake failed/timed out, so healthcheck failed and Compose treated backend as unhealthy.
- Fix:
  - Disabled Spring Boot mail health indicator with `management.health.mail.enabled=false`.
  - Changed backend Docker healthcheck to call lightweight `/api/system/status` instead of `/actuator/health`.
- Verification:
  - Targeted backend tests: `mvn test "-Dtest=SystemControllerTests,WorkOrderSystemApplicationTests"` passed.
  - Rebuilt/restarted containers with `docker compose up -d --build backend frontend`.
  - `docker compose ps` showed mysql, redis, backend, and frontend all healthy.
  - `docker exec work-order-backend wget -qO- http://127.0.0.1:8080/api/system/status` returned `{"status":"ok",...}`.

## Latest Note - MQ Suitability Analysis

- User asked to analyze RabbitMQ/Kafka suitability for the current project before implementation.
- No business code was changed for this analysis turn.
- Current project facts:
  - Backend is a Spring Boot monolith using JDBC/MySQL, Flyway, scheduled tasks, optional Redis, and Docker Compose.
  - Email is already decoupled from business flows with a database-backed `email_outbox` plus scheduled delivery/retry/dead status.
  - In-app notifications are still inserted synchronously during work-order operations, and email enqueue is triggered from notification creation.
  - SLA timeout scanning is scheduled in-process and deduplicated by `work_order_sla_events`.
  - Work-order export/import jobs are persisted in `file_jobs`, but export/import currently run inside the HTTP request path.
- Recommendation:
  - Initial MQ choice should be RabbitMQ, not Kafka, because the project needs task queues, delayed/retry/dead-letter behavior, and simpler operational cost rather than high-throughput event streaming.
  - Keep MySQL as the source of truth for business state and task state; use RabbitMQ as the delivery/dispatch mechanism.
  - Prefer a transactional outbox pattern for important domain events so DB commit and message publication do not split business consistency.
  - Do not remove the existing `email_outbox` immediately; either keep it as durable email task state and publish email job IDs to RabbitMQ, or generalize into a `message_outbox`/`async_tasks` layer in a later migration.
- Recommended first implementation scope:
  1. Add RabbitMQ service/config and Spring AMQP.
  2. Convert large work-order export to async: HTTP creates `file_jobs(PENDING)` and publishes job ID; consumer generates file and marks success/failure.
  3. Then route email delivery through RabbitMQ by publishing email outbox row IDs while retaining DB retry/dead status.
  4. Then move notification fanout/SLA reminder dispatch to MQ if synchronous insert cost or coupling becomes noticeable.
- Key design requirements for implementation:
  - Durable exchanges/queues, persistent messages, manual ack, bounded retry with backoff, DLQ.
  - Idempotent consumers keyed by DB job/outbox/event IDs.
  - Consumers must re-read current DB state before doing work instead of trusting full message payloads.
  - MQ publish failure should not roll back successful work-order creation; use outbox relay/recovery to republish.

## Latest Note - RabbitMQ Async Implementation

- Implemented the initial RabbitMQ task-queue foundation.
- Added `spring-boot-starter-amqp`, RabbitMQ connection settings, and Docker Compose `rabbitmq` service with management UI exposed on host port `15672` by default.
- Added async messaging package:
  - Durable direct exchange `work-order.async`.
  - DLX `work-order.async.dlx`.
  - Queues: `work-order.file.export`, `work-order.email.send`.
  - DLQs: `work-order.file.export.dlq`, `work-order.email.send.dlq`.
  - JSON messages carrying only DB IDs: `FileExportMessage(jobId)` and `EmailSendMessage(emailOutboxId)`.
  - Rabbit publisher plus no-op fallback when Rabbit is disabled/unavailable in tests.
- Work-order export is now available asynchronously:
  - `POST /api/admin/work-orders/export-jobs` creates `file_jobs(PENDING)` and publishes the job ID.
  - `FileExportConsumer` consumes the RabbitMQ message and calls `ExcelService.processWorkOrderExportJob`.
  - Consumer claims only `PENDING` jobs, switches to `RUNNING`, re-reads the creator/filter data from MySQL, generates the Excel file, and marks `SUCCESS`/`FAILED`.
  - `GET /api/admin/file-jobs/{id}` polls job status.
  - `GET /api/admin/file-jobs/{id}/result` downloads the generated file.
  - Existing synchronous `GET /api/admin/work-orders/export` remains for compatibility.
  - Added scheduled recovery relay `publishPendingWorkOrderExportJobs` to re-publish pending export jobs if the initial publish failed.
- Email delivery now publishes `email_outbox` IDs to RabbitMQ when a publisher is available:
  - Existing `email_outbox` remains the durable source of truth for retry/dead status.
  - `EmailSendConsumer` consumes `EmailSendMessage` and calls `deliverOneById`.
  - If publishing fails or Rabbit is disabled, the existing scheduler still performs direct delivery, so local/test behavior remains usable.
- Frontend admin work-order export now:
  - Creates an export job.
  - Polls `/api/admin/file-jobs/{id}` briefly.
  - Downloads `/api/admin/file-jobs/{id}/result` when the job succeeds.
  - Shows failure/timeout messages instead of blocking the HTTP request on Excel generation.
- Self-check:
  - `USER`: no admin export entry; cannot call admin export endpoints without processing/user-update permissions.
  - `CUSTOMER_SERVICE`: can create/export visible processing work orders through existing processing permissions and service data scope.
  - `DEPARTMENT_ADMIN`: can create/export visible department-scoped work orders through existing data-scope logic; exported file is tied to the creating user via `file_jobs.created_by`.
  - `ADMIN`: can use async export and user import/template/error-report flows.
  - `AUDITOR`: still not granted the processing permissions required by the admin work-order export endpoint; statistics/audit behavior unchanged.
- Verification:
  - Backend `mvn test`: BUILD SUCCESS, 133 tests run, 0 failures, 0 errors, 0 skipped.
  - Frontend `npm.cmd run test`: 1 test file passed, 41 tests passed.
  - Frontend `npm.cmd run build`: BUILD SUCCESS.
  - `docker compose config --quiet`: passed.
- Notes:
  - RabbitMQ management UI defaults to `http://localhost:15672` with credentials from `.env` / `.env.example`.
  - Dead-letter queues exist for poison messages; email business retry/dead state is still tracked in `email_outbox`.

## Latest Note - Idempotency Suitability Analysis

- User asked to analyze the most suitable idempotency / duplicate-submit prevention plan for the current project.
- No business code was changed for this analysis turn.
- Current project facts:
  - Frontend work-order create form posts directly through `submitWorkOrder`; the create button currently has no dedicated loading/disabled guard.
  - Backend `WorkOrderService.create` validates fields and directly inserts into `work_orders`, then records operation logs and evicts statistics cache.
  - `work_orders` currently has no request ID/idempotency key column and no uniqueness constraint that can distinguish retries from genuinely separate same-content tickets.
  - Redis already exists and is optional, but relying on Redis alone for idempotent work-order creation would be weaker than a database-backed guarantee.
- Recommended direction:
  - Add frontend button guard for user experience, but treat it only as a convenience.
  - Add a client-generated `idempotencyKey` for create-work-order requests.
  - Persist the key in MySQL, tied to `creator_id`, with a unique constraint.
  - On duplicate key, return the existing work order instead of creating a second one.
  - Do not use a broad content-only unique constraint on title/description/type/priority because users may legitimately create two similar tickets later.
- Suggested first implementation scope:
  1. Add migration with `work_order_idempotency_keys` or `work_orders.idempotency_key`; prefer a separate table if future APIs will share the same idempotency mechanism.
  2. Extend `CreateWorkOrderRequest` and frontend API payload with `idempotencyKey`.
  3. Frontend generates one key per form draft and reuses it while retrying the same submit.
  4. Backend wraps creation + idempotency record in one transaction.
  5. Add tests for double-click/same-key retry returning one work order, different key creating another, and same key by another user not colliding.

## Latest Note - Work Order Create Idempotency Implementation

- Implemented database-backed idempotency for work-order creation.
- Added Flyway migration `V21__add_work_order_idempotency.sql`:
  - New table `work_order_idempotency_keys`.
  - Unique constraint `uk_work_order_idempotency_creator_key` on `(creator_id, idempotency_key)`.
  - Stores the created `work_order_id` after successful creation.
- Extended `CreateWorkOrderRequest` with optional `idempotencyKey`, while keeping the old 4-argument constructor for test/backward compatibility.
- Updated `WorkOrderService.create`:
  - Normalizes and validates the optional key.
  - If a key exists, first inserts a placeholder idempotency row inside the transaction.
  - If the same creator/key already exists with a work order, returns that existing work order.
  - Creates the work order once, then binds the idempotency row to the new work order ID.
  - Keeps compatibility for old schemas/tests where the idempotency table is absent.
- Updated frontend:
  - `CreateWorkOrderRequest` includes optional `idempotencyKey`.
  - Work-order create form generates one key per form draft.
  - The key is reused for retries of the same submit and regenerated only after successful creation.
  - Create button now shows loading and ignores duplicate clicks while submitting.
- Added backend tests:
  - Same creator + same idempotency key returns the same work order and creates only one row.
  - Same creator + different keys can still create same-content work orders.
- Self-check:
  - `USER`: create path now prevents duplicate same-key submits; legitimate separate tickets still work with different keys.
  - `CUSTOMER_SERVICE`: default seeded role does not have `ticket:create`, so no create-path behavior change unless granted create permission.
  - `DEPARTMENT_ADMIN`: create behavior is unchanged except duplicate-submit protection when they have/create as a user with confirmed department.
  - `ADMIN`: create behavior is unchanged except duplicate-submit protection.
  - `AUDITOR`: no create permission by default, so no create-path behavior change.
- Verification:
  - Targeted backend `mvn test -Dtest=WorkOrderServiceTests`: BUILD SUCCESS, 44 tests run, 0 failures, 0 errors, 0 skipped.
  - Full backend `mvn test`: BUILD SUCCESS, 135 tests run, 0 failures, 0 errors, 0 skipped.
  - Frontend `npm.cmd run test`: 1 test file passed, 41 tests passed.
  - Frontend `npm.cmd run build`: BUILD SUCCESS.

## Latest Note - Object Storage Suitability Analysis

- User asked to analyze the most suitable object-storage operation plan before implementation, with MinIO preferred for local practice.
- No business code was changed for this analysis turn.
- Current project facts:
  - Attachments are stored on local disk through `WorkOrderAttachmentService`.
  - `WorkOrderAttachmentService.upload` already checks work-order visibility, validates file size, sanitizes original filename, blocks dangerous extensions, validates allowed extensions/content types, writes the file, inserts `work_order_attachments`, and records an operation log.
  - `WorkOrderAttachmentService.download` re-checks work-order visibility before returning a backend-streamed `UrlResource`.
  - `work_order_attachments` currently stores `stored_filename` as the local file name, not a bucket/object-key pair.
  - Docker Compose currently has MySQL, Redis, RabbitMQ, backend, and frontend, but no MinIO.
- Recommended direction:
  - Start with MinIO via Docker Compose.
  - Keep backend as the permission gate; do not expose public buckets.
  - Introduce a storage abstraction, e.g. `AttachmentStorageService`, with local filesystem and MinIO implementations.
  - Store provider/object-key metadata in DB while keeping old local fields compatible during migration.
  - For the first implementation, keep download proxied through the backend to preserve existing private-file permission behavior.
  - Add optional presigned download URLs only after the permission model is stable.
- Suggested first implementation scope:
  1. Add MinIO service to Docker Compose and `.env.example`.
  2. Add MinIO/S3 client dependency.
  3. Add attachment storage properties: provider, bucket, endpoint, access key, secret key, region/path-style flag.
  4. Add migration extending `work_order_attachments` with `storage_provider`, `bucket_name`, `object_key`, and optional `deleted_at`.
  5. Refactor `WorkOrderAttachmentService` to delegate put/get/delete to a storage service.
  6. Keep existing validation and work-order visibility checks unchanged.
  7. Add tests for upload success, DB rollback/delete cleanup on failure, download permission still enforced, and storage failure handling.
- Later follow-ups:
  - Presigned temporary URLs with short TTL.
  - Delete attachment API and object deletion/soft-delete cleanup.
  - Large-file multipart upload if attachment size grows beyond the current 10MB limit.

## Latest Note - MinIO Object Storage Implementation

- Implemented MinIO-backed attachment storage while preserving the existing backend permission gate.
- Added MinIO Java client dependency.
- Added Docker Compose `minio` service with console on host port `9001`, API on host port `9000`, persistent `minio-data` volume, and a healthcheck.
- Added `.env.example` MinIO settings and backend environment variables for provider, endpoint, bucket, region, access key, and secret key.
- Added attachment storage configuration under `app.attachments`.
- Added Flyway migration `V22__add_attachment_object_storage.sql`:
  - `storage_provider`
  - `bucket_name`
  - `object_key`
  - `deleted_at`
  - storage lookup index
- Refactored attachment storage behind `AttachmentStorageService`.
- Added `LocalAttachmentStorageService` for old/local behavior and `MinioAttachmentStorageService` for MinIO.
- `WorkOrderAttachmentService` now:
  - Keeps the same file validation and work-order visibility checks.
  - Stores objects with keys like `work-orders/{workOrderId}/{uuid}.{ext}`.
  - Writes storage metadata when the new DB columns exist.
  - Falls back to the old schema path in tests/older local schemas.
  - Deletes the stored object if the database insert fails.
  - Streams downloads through the backend, so MinIO objects remain private.
- Self-check:
  - `USER`: attachment upload/download still requires visible work order; direct MinIO access is not part of the user workflow.
  - `CUSTOMER_SERVICE`: unchanged data-scope behavior; can only use attachments on visible/processable work orders according to existing permissions.
  - `DEPARTMENT_ADMIN`: unchanged department data-scope behavior; attachment download still goes through backend visibility checks.
  - `ADMIN`: can access visible/admin-scoped attachments through backend; MinIO bucket is not made public.
  - `AUDITOR`: default seeded role still lacks attachment permission; no new MinIO bypass was added.
- Verification:
  - Targeted backend `mvn test -Dtest=WorkOrderAttachmentServiceTests`: BUILD SUCCESS, 5 tests run, 0 failures, 0 errors, 0 skipped.
  - Full backend `mvn test`: BUILD SUCCESS, 135 tests run, 0 failures, 0 errors, 0 skipped.
  - `docker compose config --quiet`: passed.
  - `docker compose up -d minio` succeeded.
  - `docker compose ps minio` showed `work-order-minio` healthy.
- Note:
  - `rg` could not be executed in this Codex WindowsApps environment due to OS access denial, so inspection relied on targeted file reads plus compile/test/container checks.

## Latest Note - WebSocket Real-Time Suitability Analysis

- User asked to analyze the most suitable WebSocket plan for real-time work orders, assignment notifications, comments, and unread counts, while considering disconnects, auto reconnect, duplicate messages, multiple windows, login expiry, and permission changes.
- No business code was changed for this analysis turn.
- Current project facts:
  - Backend is Spring Boot 3.5 with session-based auth, JDBC/MySQL, persisted in-app notifications, optional Redis, RabbitMQ async task queues, and RBAC/data-scope checks.
  - Frontend is Vue 3 with existing REST APIs for notifications, unread count, and work-order data.
  - Notification persistence already exists and should remain the source of truth; WebSocket should be a real-time delivery/refresh channel, not the only storage of events.
- Recommended direction:
  - Add Spring WebSocket with STOMP user destinations for the first implementation rather than raw WebSocket.
  - Send lightweight events after DB commits: `WORK_ORDER_CREATED`, `WORK_ORDER_ASSIGNED`, `COMMENT_CREATED`, `NOTIFICATION_CREATED`, `UNREAD_COUNT_CHANGED`, and `AUTH_CONTEXT_CHANGED`.
  - Each event should carry a monotonic `eventId`, `eventType`, `entityId`, `occurredAt`, and small summary only; clients re-fetch detail through existing REST endpoints when needed.
  - Persist or derive replayable events from existing notification/comment/work-order rows so reconnect can repair missed messages.
  - Use per-user destinations for private notifications/unread counts and role/data-scope-based fanout for new work orders.
- Required reliability rules:
  - On disconnect/reconnect, frontend reconnects with exponential backoff and then calls REST sync endpoints using `lastEventId` or timestamp to catch up.
  - Frontend deduplicates by `eventId` and also by entity/action pair for defensive UI updates.
  - Multi-window should use one leader WebSocket connection per browser profile where practical, coordinated by `BroadcastChannel`; follower tabs receive events from the leader and fall back to their own connection if leadership is lost.
  - Login expiry should close the socket on server auth failure and make the frontend call `/api/auth/me`; if unauthorized, clear user state and route to login.
  - Permission or organization changes should publish `AUTH_CONTEXT_CHANGED`; clients reload `/api/auth/me`, refetch visible lists/counts, and remove details that are no longer visible.
- Suggested first implementation scope:
  1. Add WebSocket/STOMP config and session-auth handshake/interceptor.
  2. Add `RealtimeEvent` DTO and `RealtimeEventPublisher`.
  3. Emit events from existing work-order creation, assignment, comment, and notification paths only after successful DB transaction.
  4. Add a frontend realtime client module with reconnect, heartbeat, dedupe, multi-tab coordination, and REST catch-up.
  5. Keep REST polling as a degraded fallback when WebSocket is unavailable.
  6. Add backend tests for recipient fanout/permission filtering and frontend tests for dedupe/reconnect state handling.

## Latest Note - WebSocket Real-Time Implementation

- Implemented the initial WebSocket real-time channel using Spring's native WebSocket support and JSON events, without adding a frontend socket dependency.
- Added backend dependency `spring-boot-starter-websocket`.
- Added backend realtime package:
  - `RealtimeEvent` with `eventId`, `type`, entity/notification IDs, unread count, timestamp, and lightweight payload.
  - `RealtimeSessionHandler` tracks authenticated WebSocket sessions by user ID.
  - `RealtimeWebSocketConfig` exposes `/ws/realtime` and copies the HTTP session into the WebSocket handshake.
  - `RealtimeEventPublisher` sends events after DB transaction commit when a transaction is active.
- Wired persisted notifications to realtime:
  - `NOTIFICATION_CREATED` is sent to the recipient with current unread count.
  - `UNREAD_COUNT_CHANGED` is sent after mark-one-read and mark-all-read.
  - Notification ID extraction now handles H2 returning multiple generated keys.
- Wired work-order changes to realtime:
  - `WORK_ORDER_CREATED`
  - `WORK_ORDER_ASSIGNED`
  - `WORK_ORDER_STATUS_CHANGED`
  - `COMMENT_CREATED`
  - Work-order events are lightweight refresh signals; frontend still reloads real data through existing REST endpoints.
- Wired user permission/context changes to realtime:
  - `AUTH_CONTEXT_CHANGED` after admin changes enabled state, role, organization, or department-admin grant.
  - Frontend reloads `/api/auth/me` and refreshes visible screens when received.
- Added frontend realtime client `frontend/src/api/realtime.ts`:
  - Connects to `/ws/realtime`.
  - Auto reconnects with exponential backoff.
  - Deduplicates by `eventId`.
  - Uses `BroadcastChannel` plus a short-lived local leader lock to reduce duplicate connections across multiple browser tabs/windows.
- Updated `App.vue`:
  - Starts realtime after login/session restore and stops it on logout/password reset/unmount.
  - Shows Element Plus notifications for persisted notification events, including assignment popups.
  - Updates unread count in real time.
  - Refreshes work-order lists, selected details, comments, logs, and statistics through REST when relevant realtime events arrive.
  - Handles auth expiry/context changes by reloading current user state or returning to login.
- Self-check:
  - `USER`: receives own notification/unread updates; visible work-order list/detail/comments refresh via REST data scope.
  - `CUSTOMER_SERVICE`: receives assignment/comment/status notifications and processing list refreshes according to existing permissions.
  - `DEPARTMENT_ADMIN`: receives auth-context changes after department-admin grant/revoke and refreshes scoped screens.
  - `ADMIN`: receives generic work-order refresh signals and can refresh admin lists/statistics through existing permission checks.
  - `AUDITOR`: receives only authenticated refresh signals; REST data/permission checks still determine what is visible.
- Verification:
  - Backend `mvn test`: BUILD SUCCESS, 135 tests run, 0 failures, 0 errors, 0 skipped.
  - Frontend `npm.cmd run build`: BUILD SUCCESS.
  - Frontend `npm.cmd run test`: 1 test file passed, 41 tests passed.
- Notes:
  - WebSocket events intentionally carry minimal data to avoid leaking unauthorized work-order details on broadcast refresh events.
  - Existing REST endpoints and database notifications remain the source of truth; WebSocket is a realtime refresh/notification transport.

## Latest Note - GitHub Actions CI Implementation

- Implemented the first automated delivery pipeline for the project.
- Added `.github/workflows/ci.yml`.
- Pipeline runs on pushes and pull requests targeting `main`.
- Workflow stages:
  - Checkout source.
  - Set up Java 21 with Maven cache.
  - Set up Node 22 with npm cache.
  - Install frontend dependencies with `npm ci`.
  - Run frontend tests with `npm run test`.
  - Run frontend type check with `npx vue-tsc --noEmit`.
  - Build frontend with `npm run build`.
  - Run backend tests with `mvn -B test`.
  - Run frontend dependency security check with `npm audit --audit-level=critical`.
  - Run Trivy filesystem security scan for critical vulnerabilities/secrets/misconfigurations.
  - Validate Docker Compose config.
  - Build Docker images with `docker compose build`.
  - Run Trivy critical vulnerability scans on backend and frontend images.
  - Deploy a temporary test environment with `docker compose up -d`.
  - Wait for the frontend service to become healthy and fail early on unhealthy services.
  - Health-check backend `/api/system/status` from inside the backend container.
  - Health-check frontend through `http://127.0.0.1:8088/`.
  - Collect Docker logs on failure and tear the environment down with volumes.
- CI uses explicit non-secret test environment variables so GitHub's clean runner does not depend on the local `.env`.
- Local verification:
  - `docker compose config --quiet`: passed.
- Note:
  - GitHub Actions itself will only run after this workflow file is pushed to GitHub.
  - The first security gate is intentionally critical-only to keep the initial pipeline useful without making it noisy; it can be tightened to high/critical later.

## Latest Note - GitHub Actions Trivy Version Fix

- The first GitHub Actions run failed during action resolution before project tests started.
- Root cause from run `32039774680`: `Unable to resolve action aquasecurity/trivy-action@0.28.0, unable to find version 0.28.0`.
- Fixed `.github/workflows/ci.yml` to use `aquasecurity/trivy-action@v0.36.0`.
