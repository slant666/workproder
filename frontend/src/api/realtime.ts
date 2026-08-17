export interface RealtimeEvent {
  eventId: number;
  type: string;
  entityId?: number | null;
  notificationId?: number | null;
  unreadCount?: number | null;
  occurredAt: string;
  payload?: Record<string, unknown>;
}

interface RealtimeClientOptions {
  onEvent: (event: RealtimeEvent) => void;
  onAuthExpired?: () => void;
}

const channelName = 'work-order-realtime';
const leaderKey = 'work-order-realtime-leader';
const tabId = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
const leaderTtlMs = 8000;

export function createRealtimeClient(options: RealtimeClientOptions) {
  let socket: WebSocket | null = null;
  let closed = false;
  let reconnectTimer: number | undefined;
  let heartbeatTimer: number | undefined;
  let leaderTimer: number | undefined;
  let reconnectAttempt = 0;
  const seenEventIds = new Set<number>();
  const channel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(channelName);

  channel?.addEventListener('message', (message) => {
    const data = message.data as { source?: string; type?: string; event?: RealtimeEvent };
    if (data.source === tabId) return;
    if (data.type === 'event' && data.event) {
      dispatch(data.event);
    }
  });

  function start() {
    closed = false;
    claimLeadership();
    leaderTimer = window.setInterval(claimLeadership, 3000);
  }

  function stop() {
    closed = true;
    window.clearTimeout(reconnectTimer);
    window.clearInterval(heartbeatTimer);
    window.clearInterval(leaderTimer);
    if (isLeader()) {
      localStorage.removeItem(leaderKey);
    }
    socket?.close();
    socket = null;
    channel?.close();
  }

  function claimLeadership() {
    if (closed) return;
    const now = Date.now();
    const leader = readLeader();
    if (!leader || leader.expiresAt < now || leader.id === tabId) {
      localStorage.setItem(leaderKey, JSON.stringify({ id: tabId, expiresAt: now + leaderTtlMs }));
      ensureConnected();
      return;
    }
    if (socket) {
      socket.close();
      socket = null;
    }
  }

  function ensureConnected() {
    if (closed || !isLeader() || socket?.readyState === WebSocket.OPEN || socket?.readyState === WebSocket.CONNECTING) return;
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    socket = new WebSocket(`${protocol}//${window.location.host}/ws/realtime`);

    socket.addEventListener('open', () => {
      reconnectAttempt = 0;
      heartbeatTimer = window.setInterval(() => {
        if (isLeader()) {
          localStorage.setItem(leaderKey, JSON.stringify({ id: tabId, expiresAt: Date.now() + leaderTtlMs }));
        }
      }, 3000);
    });

    socket.addEventListener('message', (message) => {
      const event = parseEvent(message.data);
      if (!event) return;
      channel?.postMessage({ source: tabId, type: 'event', event });
      dispatch(event);
    });

    socket.addEventListener('close', (event) => {
      window.clearInterval(heartbeatTimer);
      socket = null;
      if (event.code === 1008 || event.reason === 'UNAUTHORIZED') {
        options.onAuthExpired?.();
        return;
      }
      scheduleReconnect();
    });

    socket.addEventListener('error', () => {
      socket?.close();
    });
  }

  function scheduleReconnect() {
    if (closed || !isLeader()) return;
    const delay = Math.min(30000, 1000 * 2 ** reconnectAttempt);
    reconnectAttempt += 1;
    reconnectTimer = window.setTimeout(ensureConnected, delay);
  }

  function dispatch(event: RealtimeEvent) {
    if (seenEventIds.has(event.eventId)) return;
    seenEventIds.add(event.eventId);
    if (seenEventIds.size > 300) {
      const first = seenEventIds.values().next().value as number | undefined;
      if (first !== undefined) seenEventIds.delete(first);
    }
    options.onEvent(event);
  }

  function parseEvent(data: unknown) {
    if (typeof data !== 'string') return null;
    try {
      const event = JSON.parse(data) as RealtimeEvent;
      return typeof event.eventId === 'number' && typeof event.type === 'string' ? event : null;
    } catch {
      return null;
    }
  }

  function readLeader() {
    try {
      const value = localStorage.getItem(leaderKey);
      return value ? (JSON.parse(value) as { id: string; expiresAt: number }) : null;
    } catch {
      return null;
    }
  }

  function isLeader() {
    return readLeader()?.id === tabId;
  }

  return { start, stop };
}
