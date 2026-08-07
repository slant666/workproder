# Progress

## Current Task

T017: Work order attachments

## Status

Complete. No required steps remain for T017.

## Branch

- `codex/t017-work-order-attachments`

## Completed Work

- Added backend attachment persistence:
  - `work_order_attachments`
  - stores work order id, uploader id, original filename, random stored filename, content type, file size, and creation time.
- Added attachment configuration:
  - `app.attachments.upload-dir`
  - `app.attachments.max-size-bytes`
  - Spring multipart max file/request size.
- Added backend attachment DTOs and service:
  - `WorkOrderAttachmentResponse`
  - `WorkOrderAttachmentDownload`
  - `WorkOrderAttachmentProperties`
  - `WorkOrderAttachmentService`
- Added attachment endpoints:
  - `GET /api/work-orders/{id}/attachments`
  - `POST /api/work-orders/{id}/attachments`
  - `GET /api/work-orders/{id}/attachments/{attachmentId}/download`
- Enforced T017 business rules:
  - only users who can view the work order can list/upload/download attachments;
  - download checks work order permission again;
  - allowed images, PDF, and common office/text documents;
  - rejects dangerous extensions and mismatched MIME types;
  - enforces single-file size limit;
  - duplicate original filenames do not overwrite existing files;
  - server stores files under random UUID-based filenames;
  - upload writes `attachment_add` operation logs;
  - upload directory is ignored by Git.
- Updated frontend API:
  - `WorkOrderAttachment`
  - `fetchWorkOrderAttachments`
  - `uploadWorkOrderAttachment`
  - `workOrderAttachmentDownloadUrl`
- Updated work order detail page:
  - loads attachments with detail;
  - displays original filename, size, uploader, and upload time;
  - supports file upload and download;
  - refreshes attachments and operation logs after upload.
- Added backend and frontend tests for attachment behavior.

## Changed Files

- `.gitignore`
- `backend/src/main/java/com/example/workorder/WorkOrderSystemApplication.java`
- `backend/src/main/java/com/example/workorder/api/WorkOrderController.java`
- `backend/src/main/java/com/example/workorder/workorder/WorkOrderAttachmentDownload.java`
- `backend/src/main/java/com/example/workorder/workorder/WorkOrderAttachmentProperties.java`
- `backend/src/main/java/com/example/workorder/workorder/WorkOrderAttachmentResponse.java`
- `backend/src/main/java/com/example/workorder/workorder/WorkOrderAttachmentService.java`
- `backend/src/main/java/com/example/workorder/workorder/WorkOrderService.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/V10__create_work_order_attachments.sql`
- `backend/src/test/java/com/example/workorder/api/WorkOrderControllerTests.java`
- `backend/src/test/java/com/example/workorder/workorder/WorkOrderAttachmentServiceTests.java`
- `frontend/src/App.test.ts`
- `frontend/src/App.vue`
- `frontend/src/api/workOrders.ts`
- `frontend/src/styles.css`
- `PROGRESS.md`

## Verification

- `backend`: `mvn.cmd test` passed, 83 tests.
- `frontend`: `npm.cmd run test` passed, 28 tests.
- `frontend`: `npm.cmd run build` passed.
- Frontend production build still emits the existing non-blocking Rollup pure-annotation messages from `@vueuse/core`.

## Notes

- Attachment deletion is intentionally not implemented for T017 because it was not in the requested business rules.
- Office MIME checks allow common browser fallbacks such as `application/octet-stream` for legacy Office files.
