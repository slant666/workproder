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
  createdAt: string;
}

export interface CreateWorkOrderRequest {
  title: string;
  description: string;
  type: string;
  priority: string;
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
  const response = await fetch(url);
  await readWorkOrderResponse(response, '\u83b7\u53d6\u5de5\u5355\u5217\u8868\u5931\u8d25');
  return response.json() as Promise<PagedWorkOrders>;
}

export async function fetchWorkOrderDetail(id: number): Promise<WorkOrder> {
  const response = await fetch(`/api/work-orders/${id}`);
  await readWorkOrderResponse(response, '\u83b7\u53d6\u5de5\u5355\u8be6\u60c5\u5931\u8d25');
  return response.json() as Promise<WorkOrder>;
}

export async function createWorkOrder(request: CreateWorkOrderRequest): Promise<WorkOrder> {
  const response = await fetch('/api/work-orders', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  await readWorkOrderResponse(response, '\u521b\u5efa\u5de5\u5355\u5931\u8d25');
  return response.json() as Promise<WorkOrder>;
}

export async function updateWorkOrder(id: number, request: UpdateWorkOrderRequest): Promise<WorkOrder> {
  const response = await fetch(`/api/work-orders/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  await readWorkOrderResponse(response, '\u4fee\u6539\u5de5\u5355\u5931\u8d25');
  return response.json() as Promise<WorkOrder>;
}

export async function cancelWorkOrder(id: number): Promise<WorkOrder> {
  const response = await fetch(`/api/work-orders/${id}/cancel`, { method: 'POST' });
  await readWorkOrderResponse(response, '\u53d6\u6d88\u5de5\u5355\u5931\u8d25');
  return response.json() as Promise<WorkOrder>;
}

export async function confirmWorkOrder(id: number): Promise<WorkOrder> {
  const response = await fetch(`/api/work-orders/${id}/confirm`, { method: 'POST' });
  await readWorkOrderResponse(response, '\u786e\u8ba4\u5b8c\u6210\u5931\u8d25');
  return response.json() as Promise<WorkOrder>;
}
