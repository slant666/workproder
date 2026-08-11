let csrfToken: string | null = null;

async function getCsrfToken() {
  if (csrfToken) return csrfToken;
  const response = await fetch('/api/auth/csrf');
  if (!response.ok) throw new Error('获取安全令牌失败');
  const body = (await response.json()) as { token: string };
  csrfToken = body.token;
  return csrfToken;
}

function isUnsafeMethod(method: string) {
  return !['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase());
}

export async function apiFetch(input: RequestInfo | URL, init: RequestInit = {}) {
  const method = init.method || 'GET';
  if (!isUnsafeMethod(method)) {
    return fetch(input, init);
  }
  const token = await getCsrfToken();
  const headers = new Headers(init.headers);
  headers.set('X-CSRF-Token', token);
  return fetch(input, { ...init, headers });
}

export function resetCsrfToken() {
  csrfToken = null;
}
