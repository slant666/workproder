import { apiFetch, resetCsrfToken } from './http';

export interface RegisterForm {
  username: string;
  nickname: string;
  email?: string;
  password: string;
  confirmPassword: string;
  companyId?: number;
  departmentId?: number;
  teamId?: number;
}

export interface LoginForm {
  username: string;
  password: string;
}

export interface CurrentUser {
  id: number;
  username: string;
  nickname: string;
  role: string;
  roles?: string[];
  permissions?: string[];
  companyId?: number | null;
  companyName?: string | null;
  departmentId?: number | null;
  departmentName?: string | null;
  teamId?: number | null;
  teamName?: string | null;
  orgConfirmed?: boolean;
}

export interface RegisterResult {
  id: number;
  username: string;
  nickname: string;
  role: string;
  companyId?: number | null;
  companyName?: string | null;
  departmentId?: number | null;
  departmentName?: string | null;
  teamId?: number | null;
  teamName?: string | null;
  orgConfirmed?: boolean;
}

export interface UpdateProfileForm {
  nickname: string;
}

export interface ChangePasswordForm {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

async function readError(response: Response, fallback: string) {
  const body = (await response.json().catch(() => null)) as { message?: string } | null;
  return body?.message || fallback;
}

export async function registerUser(form: RegisterForm): Promise<RegisterResult> {
  const response = await apiFetch('/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(form),
  });
  if (!response.ok) throw new Error(await readError(response, '注册失败，请稍后再试'));
  return response.json() as Promise<RegisterResult>;
}

export async function loginUser(form: LoginForm): Promise<CurrentUser> {
  const response = await apiFetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(form),
  });
  if (!response.ok) throw new Error(await readError(response, '用户名或密码错误'));
  resetCsrfToken();
  return response.json() as Promise<CurrentUser>;
}

export async function getCurrentUser(): Promise<CurrentUser | null> {
  const response = await apiFetch('/api/auth/me');
  if (response.status === 401) return null;
  if (!response.ok) throw new Error(await readError(response, '获取登录状态失败'));
  return response.json() as Promise<CurrentUser>;
}

export async function updateProfile(form: UpdateProfileForm): Promise<CurrentUser> {
  const response = await apiFetch('/api/auth/profile', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(form),
  });
  if (!response.ok) throw new Error(await readError(response, '修改资料失败'));
  return response.json() as Promise<CurrentUser>;
}

export async function changePassword(form: ChangePasswordForm): Promise<void> {
  const response = await apiFetch('/api/auth/password', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(form),
  });
  if (!response.ok) throw new Error(await readError(response, '修改密码失败'));
}

export async function logoutUser(): Promise<void> {
  const response = await apiFetch('/api/auth/logout', { method: 'POST' });
  resetCsrfToken();
  if (!response.ok && response.status !== 401) throw new Error(await readError(response, '退出失败，请稍后再试'));
}
