import type { TokenDTO } from "./dto";

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object";
}

function toNumber(value: unknown, fallback: number): number {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string") {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return fallback;
}

function extractTokenCandidate(payload: unknown): Record<string, unknown> | null {
  if (!isRecord(payload)) return null;

  const nestedToken = payload.token;
  if (isRecord(nestedToken)) return nestedToken;

  return payload;
}

export function normalizeToken(payload: unknown): TokenDTO {
  const candidate = extractTokenCandidate(payload);
  if (!candidate) {
    throw new Error("Invalid token payload");
  }

  const accessToken = candidate.accessToken;
  const refreshToken = candidate.refreshToken;
  const sessionId = candidate.sessionId;
  const workspaceId = candidate.workspaceId;

  if (
    typeof accessToken !== "string" ||
    typeof refreshToken !== "string" ||
    typeof sessionId !== "string"
  ) {
    throw new Error("Invalid token payload");
  }

  return {
    accessToken,
    refreshToken,
    tokenType: "Bearer",
    expiresIn: toNumber(candidate.expiresIn, 7200),
    refreshExpiresIn: toNumber(candidate.refreshExpiresIn, 604800),
    sessionId,
    workspaceId: typeof workspaceId === "string" || workspaceId === null ? workspaceId : null,
  };
}

