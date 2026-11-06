import type { Session } from "../types";
let accessToken: string | null = null;
let refreshing: Promise<Session | null> | null = null;
let sessionListener: ((session: Session | null) => void) | null = null;
export function setSession(session: Session | null) {
  accessToken = session?.accessToken ?? null;
  sessionListener?.(session);
}
export function listenSession(listener: ((session: Session | null) => void) | null) {
  sessionListener = listener;
}
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public fields: Record<string, string> = {},
  ) {
    super(message);
  }
}
export async function refreshSession(): Promise<Session | null> {
  if (!refreshing)
    refreshing = (async () => {
      try {
        const res = await fetch("/api/v1/auth/refresh", {
          method: "POST",
          credentials: "include",
        });
        if (!res.ok) {
          setSession(null);
          return null;
        }
        const session: Session = await res.json();
        setSession(session);
        return session;
      } catch {
        setSession(null);
        return null;
      } finally {
        refreshing = null;
      }
    })();
  return refreshing;
}
export async function api<T>(
  path: string,
  options: RequestInit = {},
  retry = true,
): Promise<T> {
  const headers = new Headers(options.headers);
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  if (options.body && !(options.body instanceof FormData))
    headers.set("Content-Type", "application/json");
  const res = await fetch(`/api/v1${path}`, {
    ...options,
    headers,
    credentials: "include",
  });
  if (res.status === 401 && retry && !path.startsWith("/auth/")) {
    const session = await refreshSession();
    if (session) return api<T>(path, options, false);
  }
  if (!res.ok) {
    let data: {
      message?: string;
      errorCode?: string;
      validationErrors?: Record<string, string>;
    } = {};
    try {
      data = await res.json();
    } catch {
      /* Non-JSON proxy errors have a friendly fallback. */
    }
    throw new ApiError(
      res.status,
      data.errorCode ?? "HTTP_ERROR",
      data.message ?? "Không thể kết nối. Vui lòng thử lại.",
      data.validationErrors,
    );
  }
  if (res.status === 204) return undefined as T;
  // Spring controllers returning void may respond with 200 and an empty body.
  const body = await res.text();
  if (!body.trim()) return undefined as T;
  try {
    return JSON.parse(body) as T;
  } catch {
    throw new ApiError(
      res.status,
      "INVALID_RESPONSE",
      "Máy chủ trả về dữ liệu không hợp lệ. Vui lòng tải lại trang.",
    );
  }
}
export function send<T>(path: string, method: string, data?: unknown) {
  return api<T>(path, {
    method,
    body: data === undefined ? undefined : JSON.stringify(data),
  });
}
export async function download(path: string, filename: string) {
  const headers = new Headers();
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  const res = await fetch(`/api/v1${path}`, { headers });
  if (!res.ok) throw new Error("Không thể tải tệp.");
  const url = URL.createObjectURL(await res.blob());
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export const money = (n: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(n);
export const date = (s: string | null | undefined) =>
  s
    ? new Intl.DateTimeFormat("vi-VN", {
        dateStyle: "medium",
        timeStyle: "short",
        timeZone: "Asia/Ho_Chi_Minh",
      }).format(new Date(s))
    : "—";
export const backdrop = (m: { id: number; backdrop: string }) =>
  m.id === 1 ? "/art/hero-horizon.png" : m.backdrop;
