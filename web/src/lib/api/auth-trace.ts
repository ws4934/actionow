type JwtPayload = Record<string, unknown>;

function decodeJwtPayload(token: string): JwtPayload | null {
  const segments = token.split(".");
  if (segments.length < 2) return null;
  if (typeof atob !== "function") return null;

  try {
    const base64 = segments[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64.padEnd(
      base64.length + ((4 - (base64.length % 4)) % 4),
      "="
    );
    return JSON.parse(atob(padded)) as JwtPayload;
  } catch {
    return null;
  }
}

export function isAuthTraceEnabled(): boolean {
  return false;
}

export function summarizeToken(token: string | null | undefined): Record<string, unknown> {
  if (!token) return { present: false };

  const payload = decodeJwtPayload(token);
  return {
    present: true,
    prefix: token.slice(0, 16),
    jti: typeof payload?.jti === "string" ? payload.jti : undefined,
    workspaceId:
      typeof payload?.workspaceId === "string" || payload?.workspaceId === null
        ? payload.workspaceId
        : undefined,
    sessionId: typeof payload?.sessionId === "string" ? payload.sessionId : undefined,
    exp: typeof payload?.exp === "number" ? payload.exp : undefined,
  };
}

export function authTrace(event: string, payload?: Record<string, unknown>): void {
  void event;
  void payload;
}
