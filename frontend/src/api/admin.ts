import { apiFetch } from './http';
import type { PagedWorkOrders, WorkOrder, WorkOrderListQuery } from './workOrders';

export interface AdminWorkOrderListQuery extends WorkOrderListQuery {
  creatorId?: number;
  handlerId?: number;
  createdFrom?: string;
  createdTo?: string;
}

export interface AdminWorkOrderStatisticsQuery {
  createdFrom?: string;
  createdTo?: string;
}

export interface WorkOrderCountStatistic {
  label: string;
  count: number;
}

export interface DailyWorkOrderCountStatistic {
  date: string;
  count: number;
}

export interface AdminWorkOrderCountStatistic {
  handlerId: number;
  handlerUsername: string;
  handlerNickname: string;
  count: number;
}

export interface WorkOrderStatistics {
  total: number;
  statusCounts: WorkOrderCountStatistic[];
  priorityCounts: WorkOrderCountStatistic[];
  dailyNewCounts: DailyWorkOrderCountStatistic[];
  averageProcessingMinutes: number;
  adminProcessingCounts: AdminWorkOrderCountStatistic[];
  overdueUnhandledCount: number;
  slaNearOverdueCount?: number;
  firstResponseOverdueCount?: number;
  resolutionOverdueCount?: number;
  slaOverduePriorityCounts?: WorkOrderCountStatistic[];
  averageProcessingRule: string;
  overdueRule: string;
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
  role: 'USER' | 'ADMIN' | 'CUSTOMER_SERVICE' | 'DEPARTMENT_ADMIN' | 'AUDITOR';
  enabled: boolean;
  companyId?: number | null;
  companyName?: string | null;
  departmentId?: number | null;
  departmentName?: string | null;
  teamId?: number | null;
  teamName?: string | null;
  orgConfirmed?: boolean;
  departmentAdmin?: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface OrganizationItem {
  id: number;
  name: string;
  enabled: boolean;
  parentId?: number | null;
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

export interface UpdateAdminUserOrganizationRequest {
  companyId?: number | null;
  departmentId?: number | null;
  teamId?: number | null;
  orgConfirmed: boolean;
}

export interface CreateOrganizationRequest {
  name: string;
  companyId?: number | null;
  departmentId?: number | null;
}

export interface FileJob {
  id: number;
  type: string;
  status: string;
  originalFilename?: string | null;
  totalCount: number;
  successCount: number;
  failedCount: number;
  hasResultFile: boolean;
  hasErrorReport: boolean;
  errorMessage?: string | null;
  createdAt: string;
  finishedAt?: string | null;
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
  const response = await apiFetch('/api/admin/overview');

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
  const response = await apiFetch(url);

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
  const response = await apiFetch(`/api/admin/users/${id}/enabled`, {
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
  const response = await apiFetch(`/api/admin/users/${id}/role`, {
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

function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

async function downloadAdminFile(url: string, filename: string): Promise<void> {
  const response = await apiFetch(url);
  if (response.status === 401) throw new Error('\u8bf7\u5148\u767b\u5f55');
  if (response.status === 403) throw new Error(await readAdminError(response, 'Access denied'));
  if (!response.ok) throw new Error(await readAdminError(response, '\u6587\u4ef6\u4e0b\u8f7d\u5931\u8d25'));
  saveBlob(await response.blob(), filename);
}

export function downloadUserImportTemplate(): Promise<void> {
  return downloadAdminFile('/api/admin/users/import-template', 'user-import-template.xlsx');
}

export async function importUsers(file: File): Promise<FileJob> {
  const body = new FormData();
  body.append('file', file);
  const response = await apiFetch('/api/admin/users/import-jobs', { method: 'POST', body });
  if (response.status === 401) throw new Error('\u8bf7\u5148\u767b\u5f55');
  if (response.status === 403) throw new Error(await readAdminError(response, 'Access denied'));
  if (!response.ok) throw new Error(await readAdminError(response, '\u5bfc\u5165\u7528\u6237\u5931\u8d25'));
  return response.json() as Promise<FileJob>;
}

export function downloadImportErrorReport(jobId: number): Promise<void> {
  return downloadAdminFile(`/api/admin/file-jobs/${jobId}/error-report`, `user-import-errors-${jobId}.xlsx`);
}

export async function fetchFileJob(jobId: number): Promise<FileJob> {
  const response = await apiFetch(`/api/admin/file-jobs/${jobId}`);
  if (response.status === 401) throw new Error('\u8bf7\u5148\u767b\u5f55');
  if (response.status === 403) throw new Error(await readAdminError(response, 'Access denied'));
  if (!response.ok) throw new Error(await readAdminError(response, '\u83b7\u53d6\u6587\u4ef6\u4efb\u52a1\u5931\u8d25'));
  return response.json() as Promise<FileJob>;
}

export function downloadFileJobResult(jobId: number, filename: string): Promise<void> {
  return downloadAdminFile(`/api/admin/file-jobs/${jobId}/result`, filename);
}

export async function updateAdminUserOrganization(id: number, request: UpdateAdminUserOrganizationRequest): Promise<AdminUser> {
  const response = await apiFetch(`/api/admin/users/${id}/organization`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  if (response.status === 401) throw new Error('\u8bf7\u5148\u767b\u5f55');
  if (response.status === 403) throw new Error(await readAdminError(response, 'Access denied'));
  if (!response.ok) throw new Error(await readAdminError(response, '\u66f4\u65b0\u7528\u6237\u7ec4\u7ec7\u5f52\u5c5e\u5931\u8d25'));
  return response.json() as Promise<AdminUser>;
}

export async function updateDepartmentAdmin(userId: number, departmentId: number, enabled: boolean): Promise<AdminUser> {
  const response = await apiFetch(`/api/admin/users/${userId}/department-admin`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ departmentId, departmentAdmin: enabled }),
  });
  if (response.status === 401) throw new Error('\u8bf7\u5148\u767b\u5f55');
  if (response.status === 403) throw new Error(await readAdminError(response, 'Access denied'));
  if (!response.ok) throw new Error(await readAdminError(response, '\u66f4\u65b0\u90e8\u95e8\u7ba1\u7406\u5458\u6388\u6743\u5931\u8d25'));
  return response.json() as Promise<AdminUser>;
}

async function readOrganizationResponse(response: Response, fallback: string) {
  if (response.status === 401) throw new Error('\u8bf7\u5148\u767b\u5f55');
  if (response.status === 403) throw new Error(await readAdminError(response, 'Access denied'));
  if (!response.ok) throw new Error(await readAdminError(response, fallback));
}

export async function fetchCompanies(): Promise<OrganizationItem[]> {
  const response = await apiFetch('/api/organizations/companies');
  await readOrganizationResponse(response, '\u83b7\u53d6\u516c\u53f8\u5217\u8868\u5931\u8d25');
  return response.json() as Promise<OrganizationItem[]>;
}

export async function fetchDepartments(companyId?: number): Promise<OrganizationItem[]> {
  const params = new URLSearchParams();
  if (companyId) params.set('companyId', String(companyId));
  const response = await apiFetch(params.size ? `/api/organizations/departments?${params}` : '/api/organizations/departments');
  await readOrganizationResponse(response, '\u83b7\u53d6\u90e8\u95e8\u5217\u8868\u5931\u8d25');
  return response.json() as Promise<OrganizationItem[]>;
}

export async function fetchTeams(departmentId?: number): Promise<OrganizationItem[]> {
  const params = new URLSearchParams();
  if (departmentId) params.set('departmentId', String(departmentId));
  const response = await apiFetch(params.size ? `/api/organizations/teams?${params}` : '/api/organizations/teams');
  await readOrganizationResponse(response, '\u83b7\u53d6\u56e2\u961f\u5217\u8868\u5931\u8d25');
  return response.json() as Promise<OrganizationItem[]>;
}

async function createOrganization(path: string, request: CreateOrganizationRequest, fallback: string): Promise<OrganizationItem> {
  const response = await apiFetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  await readOrganizationResponse(response, fallback);
  return response.json() as Promise<OrganizationItem>;
}

async function updateOrganizationEnabled(path: string, enabled: boolean, fallback: string): Promise<OrganizationItem> {
  const response = await apiFetch(path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  });
  await readOrganizationResponse(response, fallback);
  return response.json() as Promise<OrganizationItem>;
}

export function createCompany(name: string): Promise<OrganizationItem> {
  return createOrganization('/api/admin/companies', { name }, '\u521b\u5efa\u516c\u53f8\u5931\u8d25');
}

export function updateCompanyEnabled(id: number, enabled: boolean): Promise<OrganizationItem> {
  return updateOrganizationEnabled(`/api/admin/companies/${id}/enabled`, enabled, '\u66f4\u65b0\u516c\u53f8\u72b6\u6001\u5931\u8d25');
}

export function createDepartment(companyId: number, name: string): Promise<OrganizationItem> {
  return createOrganization('/api/admin/departments', { companyId, name }, '\u521b\u5efa\u90e8\u95e8\u5931\u8d25');
}

export function updateDepartmentEnabled(id: number, enabled: boolean): Promise<OrganizationItem> {
  return updateOrganizationEnabled(`/api/admin/departments/${id}/enabled`, enabled, '\u66f4\u65b0\u90e8\u95e8\u72b6\u6001\u5931\u8d25');
}

export function createTeam(departmentId: number, name: string): Promise<OrganizationItem> {
  return createOrganization('/api/admin/teams', { departmentId, name }, '\u521b\u5efa\u56e2\u961f\u5931\u8d25');
}

export function updateTeamEnabled(id: number, enabled: boolean): Promise<OrganizationItem> {
  return updateOrganizationEnabled(`/api/admin/teams/${id}/enabled`, enabled, '\u66f4\u65b0\u56e2\u961f\u72b6\u6001\u5931\u8d25');
}

export async function fetchAdminWorkOrderStatistics(query: AdminWorkOrderStatisticsQuery = {}): Promise<WorkOrderStatistics> {
  const params = new URLSearchParams();
  if (query.createdFrom) params.set('createdFrom', query.createdFrom);
  if (query.createdTo) params.set('createdTo', query.createdTo);
  const url = params.size > 0 ? `/api/admin/work-orders/statistics?${params.toString()}` : '/api/admin/work-orders/statistics';
  const response = await apiFetch(url);

  if (response.status === 401) {
    throw new Error('\u8bf7\u5148\u767b\u5f55');
  }
  if (response.status === 403) {
    throw new Error(await readAdminError(response, 'Access denied'));
  }
  if (!response.ok) {
    throw new Error(await readAdminError(response, '\u83b7\u53d6\u5de5\u5355\u7edf\u8ba1\u5931\u8d25'));
  }

  return response.json() as Promise<WorkOrderStatistics>;
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
  const response = await apiFetch(url);

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

export async function exportAdminWorkOrders(query: AdminWorkOrderListQuery = {}): Promise<FileJob> {
  const params = new URLSearchParams();
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim());
  if (query.status) params.set('status', query.status);
  if (query.priority) params.set('priority', query.priority);
  if (query.creatorId !== undefined) params.set('creatorId', String(query.creatorId));
  if (query.handlerId !== undefined) params.set('handlerId', String(query.handlerId));
  if (query.createdFrom) params.set('createdFrom', query.createdFrom);
  if (query.createdTo) params.set('createdTo', query.createdTo);
  if (query.sort) params.set('sort', query.sort);
  const response = await apiFetch(params.size ? `/api/admin/work-orders/export-jobs?${params}` : '/api/admin/work-orders/export-jobs', {
    method: 'POST',
  });
  if (response.status === 401) throw new Error('\u8bf7\u5148\u767b\u5f55');
  if (response.status === 403) throw new Error(await readAdminError(response, 'Access denied'));
  if (!response.ok) throw new Error(await readAdminError(response, '\u521b\u5efa\u5de5\u5355\u5bfc\u51fa\u4efb\u52a1\u5931\u8d25'));
  return response.json() as Promise<FileJob>;
}

export async function fetchAdminHandlers(): Promise<AdminHandler[]> {
  const response = await apiFetch('/api/admin/handlers');

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
  const response = await apiFetch(`/api/admin/work-orders/${id}/handler`, {
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
  const response = await apiFetch(`/api/admin/work-orders/${id}/${action}`, { method: 'PUT' });

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
