import type { PagedWorkOrders, WorkOrderListQuery } from './workOrders';

export interface AdminWorkOrderListQuery extends WorkOrderListQuery {
  creatorId?: number;
  handlerId?: number;
  createdFrom?: string;
  createdTo?: string;
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
