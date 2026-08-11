import { apiFetch } from './http';

export type HealthStatus = 'checking' | 'ok' | 'error';

export interface StatusCheck {
  status: string;
  service: string;
  timestamp: string;
}

export interface DatabaseCheck {
  status: string;
  database: string;
  validation: number;
}

async function fetchJson<T>(url: string): Promise<T> {
  const response = await apiFetch(url);

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }

  return response.json() as Promise<T>;
}

export function checkBackend(): Promise<StatusCheck> {
  return fetchJson<StatusCheck>('/api/system/status');
}

export function checkDatabase(): Promise<DatabaseCheck> {
  return fetchJson<DatabaseCheck>('/api/system/database');
}
