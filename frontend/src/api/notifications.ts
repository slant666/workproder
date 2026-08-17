import { apiFetch } from './http';

export interface NotificationItem {
  id: number;
  type: string;
  title: string;
  content: string;
  workOrderId?: number | null;
  read: boolean;
  readAt?: string | null;
  createdAt: string;
}

export interface PagedNotifications {
  items: NotificationItem[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

async function readError(response: Response, fallback: string) {
  const body = (await response.json().catch(() => null)) as { message?: string } | null;
  return body?.message || fallback;
}

export async function fetchNotifications(page = 1, pageSize = 20): Promise<PagedNotifications> {
  const response = await apiFetch(`/api/notifications?page=${page}&pageSize=${pageSize}`);
  if (!response.ok) throw new Error(await readError(response, '获取通知失败'));
  return response.json() as Promise<PagedNotifications>;
}

export async function fetchUnreadNotificationCount(): Promise<number> {
  const response = await apiFetch('/api/notifications/unread-count');
  if (!response.ok) throw new Error(await readError(response, '获取未读通知失败'));
  const body = (await response.json()) as { count: number };
  return body.count;
}

export async function markNotificationRead(id: number): Promise<void> {
  const response = await apiFetch(`/api/notifications/${id}/read`, { method: 'PUT' });
  if (!response.ok) throw new Error(await readError(response, '标记通知已读失败'));
}

export async function markAllNotificationsRead(): Promise<void> {
  const response = await apiFetch('/api/notifications/read-all', { method: 'PUT' });
  if (!response.ok) throw new Error(await readError(response, '标记全部已读失败'));
}
