import { apiFetch } from './http';

export interface WorkOrder {
  id: number;
  title: string;
  description: string;
  type: string;
  priority: string;
  status: string;
  creatorId: number;
  creatorUsername: string;
  handlerId?: number | null;
  handlerUsername?: string | null;
  companyId?: number | null;
  companyName?: string | null;
  departmentId?: number | null;
  departmentName?: string | null;
  teamId?: number | null;
  teamName?: string | null;
  firstResponseDueAt?: string | null;
  resolutionDueAt?: string | null;
  firstRespondedAt?: string | null;
  resolvedAt?: string | null;
  slaStatus?: string | null;
  createdAt: string;
}

export interface WorkOrderOperationLog {
  id: number;
  workOrderId: number;
  actorId: number;
  actorUsername: string;
  actorNickname: string;
  action: string;
  fieldName?: string | null;
  oldValue?: string | null;
  newValue?: string | null;
  detailsJson?: string | null;
  createdAt: string;
}

export interface WorkOrderComment {
  id: number;
  workOrderId: number;
  authorId: number;
  authorUsername: string;
  authorNickname: string;
  authorRole: string;
  content: string;
  createdAt: string;
}

export interface WorkOrderAttachment {
  id: number;
  workOrderId: number;
  uploaderId: number;
  uploaderUsername: string;
  uploaderNickname: string;
  originalFilename: string;
  contentType: string;
  fileSize: number;
  createdAt: string;
}

export interface CreateWorkOrderRequest {
  title: string;
  description: string;
  type: string;
  priority: string;
  idempotencyKey?: string;
}

export interface CreateWorkOrderCommentRequest {
  content: string;
}

export type UpdateWorkOrderRequest = CreateWorkOrderRequest;

export interface WorkOrderListQuery {
  keyword?: string;
  status?: string;
  priority?: string;
  sort?: 'createdAtDesc' | 'createdAtAsc';
  page?: number;
  pageSize?: number;
}

export interface PagedWorkOrders {
  items: WorkOrder[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

async function readError(response: Response, fallback: string) {
  try {
    const body = (await response.json()) as { message?: string };
    return body.message || fallback;
  } catch {
    return fallback;
  }
}

async function readWorkOrderResponse(response: Response, fallback: string) {
  if (response.status === 401) {
    throw new Error('\u8bf7\u5148\u767b\u5f55');
  }

  if (!response.ok) {
    throw new Error(await readError(response, fallback));
  }
}

export async function fetchWorkOrders(query: WorkOrderListQuery = {}): Promise<PagedWorkOrders> {
  const params = new URLSearchParams();
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim());
  if (query.status) params.set('status', query.status);
  if (query.priority) params.set('priority', query.priority);
  if (query.sort) params.set('sort', query.sort);
  if (query.page !== undefined) params.set('page', String(query.page));
  if (query.pageSize !== undefined) params.set('pageSize', String(query.pageSize));
  const url = params.size > 0 ? `/api/work-orders?${params.toString()}` : '/api/work-orders';
  const response = await apiFetch(url);
  await readWorkOrderResponse(response, '\u83b7\u53d6\u5de5\u5355\u5217\u8868\u5931\u8d25');
  return response.json() as Promise<PagedWorkOrders>;
}

export async function fetchWorkOrderDetail(id: number): Promise<WorkOrder> {
  const response = await apiFetch(`/api/work-orders/${id}`);
  await readWorkOrderResponse(response, '\u83b7\u53d6\u5de5\u5355\u8be6\u60c5\u5931\u8d25');
  return response.json() as Promise<WorkOrder>;
}

export async function fetchWorkOrderLogs(id: number): Promise<WorkOrderOperationLog[]> {
  const response = await apiFetch(`/api/work-orders/${id}/logs`);
  await readWorkOrderResponse(response, '\u83b7\u53d6\u5de5\u5355\u65e5\u5fd7\u5931\u8d25');
  return response.json() as Promise<WorkOrderOperationLog[]>;
}

export async function fetchWorkOrderComments(id: number): Promise<WorkOrderComment[]> {
  const response = await apiFetch(`/api/work-orders/${id}/comments`);
  await readWorkOrderResponse(response, '\u83b7\u53d6\u5de5\u5355\u8bc4\u8bba\u5931\u8d25');
  return response.json() as Promise<WorkOrderComment[]>;
}

export async function fetchWorkOrderAttachments(id: number): Promise<WorkOrderAttachment[]> {
  const response = await apiFetch(`/api/work-orders/${id}/attachments`);
  await readWorkOrderResponse(response, '\u83b7\u53d6\u5de5\u5355\u9644\u4ef6\u5931\u8d25');
  return response.json() as Promise<WorkOrderAttachment[]>;
}

export async function createWorkOrder(request: CreateWorkOrderRequest): Promise<WorkOrder> {
  const response = await apiFetch('/api/work-orders', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  await readWorkOrderResponse(response, '\u521b\u5efa\u5de5\u5355\u5931\u8d25');
  return response.json() as Promise<WorkOrder>;
}

export async function updateWorkOrder(id: number, request: UpdateWorkOrderRequest): Promise<WorkOrder> {
  const response = await apiFetch(`/api/work-orders/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  await readWorkOrderResponse(response, '\u4fee\u6539\u5de5\u5355\u5931\u8d25');
  return response.json() as Promise<WorkOrder>;
}

export async function cancelWorkOrder(id: number): Promise<WorkOrder> {
  const response = await apiFetch(`/api/work-orders/${id}/cancel`, { method: 'POST' });
  await readWorkOrderResponse(response, '\u53d6\u6d88\u5de5\u5355\u5931\u8d25');
  return response.json() as Promise<WorkOrder>;
}

export async function confirmWorkOrder(id: number): Promise<WorkOrder> {
  const response = await apiFetch(`/api/work-orders/${id}/confirm`, { method: 'POST' });
  await readWorkOrderResponse(response, '\u786e\u8ba4\u5b8c\u6210\u5931\u8d25');
  return response.json() as Promise<WorkOrder>;
}

export async function createWorkOrderComment(id: number, request: CreateWorkOrderCommentRequest): Promise<WorkOrderComment> {
  const response = await apiFetch(`/api/work-orders/${id}/comments`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  await readWorkOrderResponse(response, '\u6dfb\u52a0\u8bc4\u8bba\u5931\u8d25');
  return response.json() as Promise<WorkOrderComment>;
}

export async function deleteWorkOrderComment(id: number, commentId: number): Promise<void> {
  const response = await apiFetch(`/api/work-orders/${id}/comments/${commentId}`, { method: 'DELETE' });
  await readWorkOrderResponse(response, '\u5220\u9664\u8bc4\u8bba\u5931\u8d25');
}

export async function uploadWorkOrderAttachment(id: number, file: File): Promise<WorkOrderAttachment> {
  const body = new FormData();
  body.append('file', file);
  const response = await apiFetch(`/api/work-orders/${id}/attachments`, {
    method: 'POST',
    body,
  });
  await readWorkOrderResponse(response, '\u4e0a\u4f20\u9644\u4ef6\u5931\u8d25');
  return response.json() as Promise<WorkOrderAttachment>;
}

export function workOrderAttachmentDownloadUrl(id: number, attachmentId: number) {
  return `/api/work-orders/${id}/attachments/${attachmentId}/download`;
}
