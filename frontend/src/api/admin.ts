import type { PagedWorkOrders, WorkOrder, WorkOrderListQuery } from './workOrders';

export interface AdminWorkOrderListQuery extends WorkOrderListQuery {
  creatorId?: number;
  handlerId?: number;
  createdFrom?: string;
  createdTo?: string;
}

export interface AdminHandler {
  id: number;
  username: string;
  nickname: string;
}

export interface AdminUser {
  id: number;
  username: string;
  nickname: string;
  role: 'USER' | 'ADMIN';
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PagedAdminUsers {
  items: AdminUser[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export interface AdminUserListQuery {
  keyword?: string;
  page?: number;
  pageSize?: number;
}

async function readAdminError(response: Response, fallback: string) {
  try {
    const body = (await response.json()) as { message?: string };
    return body.message || fallback;
  } catch {
    return fallback;
  }
}

export async function fetchAdminOverview(): Promise<{ status: string; area: string }> {
  const response = await fetch('/api/admin/overview');

  if (response.status === 401) {
    throw new Error('请先登录');
  }

  if (response.status === 403) {
    const body = (await response.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message || 'Access denied');
  }

  if (!response.ok) {
    throw new Error('获取管理页面失败');
  }

  return response.json() as Promise<{ status: string; area: string }>;
}

export async function fetchAdminUsers(query: AdminUserListQuery = {}): Promise<PagedAdminUsers> {
  const params = new URLSearchParams();
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim());
  if (query.page !== undefined) params.set('page', String(query.page));
  if (query.pageSize !== undefined) params.set('pageSize', String(query.pageSize));
  const url = params.size > 0 ? `/api/admin/users?${params.toString()}` : '/api/admin/users';
  const response = await fetch(url);

  if (response.status === 401) {
    throw new Error('\u8bf7\u5148\u767b\u5f55');
  }
  if (response.status === 403) {
    throw new Error(await readAdminError(response, 'Access denied'));
  }
  if (!response.ok) {
    throw new Error(await readAdminError(response, '\u83b7\u53d6\u7528\u6237\u5217\u8868\u5931\u8d25'));
  }

  return response.json() as Promise<PagedAdminUsers>;
}

export async function updateAdminUserEnabled(id: number, enabled: boolean): Promise<AdminUser> {
  const response = await fetch(`/api/admin/users/${id}/enabled`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  });

  if (response.status === 401) {
    throw new Error('\u8bf7\u5148\u767b\u5f55');
  }
  if (response.status === 403) {
    throw new Error(await readAdminError(response, 'Access denied'));
  }
  if (!response.ok) {
    throw new Error(await readAdminError(response, '\u66f4\u65b0\u7528\u6237\u72b6\u6001\u5931\u8d25'));
  }

  return response.json() as Promise<AdminUser>;
}

export async function updateAdminUserRole(id: number, role: 'USER' | 'ADMIN'): Promise<AdminUser> {
  const response = await fetch(`/api/admin/users/${id}/role`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ role }),
  });

  if (response.status === 401) {
    throw new Error('\u8bf7\u5148\u767b\u5f55');
  }
  if (response.status === 403) {
    throw new Error(await readAdminError(response, 'Access denied'));
  }
  if (!response.ok) {
    throw new Error(await readAdminError(response, '\u66f4\u65b0\u7528\u6237\u89d2\u8272\u5931\u8d25'));
  }

  return response.json() as Promise<AdminUser>;
}

export async function fetchAdminWorkOrders(query: AdminWorkOrderListQuery = {}): Promise<PagedWorkOrders> {
  const params = new URLSearchParams();
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim());
  if (query.status) params.set('status', query.status);
  if (query.priority) params.set('priority', query.priority);
  if (query.creatorId !== undefined) params.set('creatorId', String(query.creatorId));
  if (query.handlerId !== undefined) params.set('handlerId', String(query.handlerId));
  if (query.createdFrom) params.set('createdFrom', query.createdFrom);
  if (query.createdTo) params.set('createdTo', query.createdTo);
  if (query.sort) params.set('sort', query.sort);
  if (query.page !== undefined) params.set('page', String(query.page));
  if (query.pageSize !== undefined) params.set('pageSize', String(query.pageSize));
  const url = params.size > 0 ? `/api/admin/work-orders?${params.toString()}` : '/api/admin/work-orders';
  const response = await fetch(url);

  if (response.status === 401) {
    throw new Error('\u8bf7\u5148\u767b\u5f55');
  }
  if (response.status === 403) {
    throw new Error(await readAdminError(response, 'Access denied'));
  }
  if (!response.ok) {
    throw new Error(await readAdminError(response, '\u83b7\u53d6\u7ba1\u7406\u5458\u5de5\u5355\u5217\u8868\u5931\u8d25'));
  }

  return response.json() as Promise<PagedWorkOrders>;
}

export async function fetchAdminHandlers(): Promise<AdminHandler[]> {
  const response = await fetch('/api/admin/handlers');

  if (response.status === 401) {
    throw new Error('\u8bf7\u5148\u767b\u5f55');
  }
  if (response.status === 403) {
    throw new Error(await readAdminError(response, 'Access denied'));
  }
  if (!response.ok) {
    throw new Error(await readAdminError(response, '\u83b7\u53d6\u5904\u7406\u4eba\u5217\u8868\u5931\u8d25'));
  }

  return response.json() as Promise<AdminHandler[]>;
}

export async function assignWorkOrderHandler(id: number, handlerId: number): Promise<WorkOrder> {
  const response = await fetch(`/api/admin/work-orders/${id}/handler`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ handlerId }),
  });

  if (response.status === 401) {
    throw new Error('\u8bf7\u5148\u767b\u5f55');
  }
  if (response.status === 403) {
    throw new Error(await readAdminError(response, 'Access denied'));
  }
  if (!response.ok) {
    throw new Error(await readAdminError(response, '\u5206\u914d\u5904\u7406\u4eba\u5931\u8d25'));
  }

  return response.json() as Promise<WorkOrder>;
}

async function updateAdminWorkOrderState(id: number, action: 'accept' | 'submit' | 'return', fallback: string): Promise<WorkOrder> {
  const response = await fetch(`/api/admin/work-orders/${id}/${action}`, { method: 'PUT' });

  if (response.status === 401) {
    throw new Error('\u8bf7\u5148\u767b\u5f55');
  }
  if (response.status === 403) {
    throw new Error(await readAdminError(response, 'Access denied'));
  }
  if (!response.ok) {
    throw new Error(await readAdminError(response, fallback));
  }

  return response.json() as Promise<WorkOrder>;
}

export function acceptWorkOrder(id: number): Promise<WorkOrder> {
  return updateAdminWorkOrderState(id, 'accept', '\u63a5\u5355\u5931\u8d25');
}

export function submitWorkOrderForConfirmation(id: number): Promise<WorkOrder> {
  return updateAdminWorkOrderState(id, 'submit', '\u63d0\u4ea4\u786e\u8ba4\u5931\u8d25');
}

export function returnWorkOrderToProcessing(id: number): Promise<WorkOrder> {
  return updateAdminWorkOrderState(id, 'return', '\u9000\u56de\u5904\u7406\u4e2d\u5931\u8d25');
}
