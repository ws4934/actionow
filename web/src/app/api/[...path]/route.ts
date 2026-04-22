import { NextRequest, NextResponse } from "next/server";

/**
 * Catch-all API proxy route
 * Proxies all /api/* requests (except /api/proxy) to the backend API
 * This is more reliable than Next.js rewrites in Cloudflare Workers environment
 */

// Prefer a server-only backend origin while keeping the legacy public variable as fallback.
const getApiBaseUrl = (): string => {
  const configuredBaseUrl =
    process.env.API_BASE_URL || process.env.NEXT_PUBLIC_API_BASE_URL;

  if (configuredBaseUrl) {
    return configuredBaseUrl;
  }

  return "http://127.0.0.1:8080";
};

const API_BASE_URL = getApiBaseUrl();

// Headers to forward from the incoming request
const FORWARDED_HEADERS = [
  "authorization",
  "content-type",
  "accept",
];

// Headers to NOT forward to the client (hop-by-hop headers)
const HOP_BY_HOP_HEADERS = [
  "connection",
  "content-encoding",
  "content-length",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailers",
  "transfer-encoding",
  "upgrade",
];

type ProxyFetchOptions = RequestInit & {
  duplex?: "half";
  dispatcher?: unknown;
};

async function createProxyFetchOptions(
  request: NextRequest,
  headers: Record<string, string>,
  targetUrl: URL
): Promise<ProxyFetchOptions> {
  const method = request.method.toUpperCase();
  const canHaveBody = method !== "GET" && method !== "HEAD";
  const options: ProxyFetchOptions = {
    method,
    headers,
  };

  if (!headers["user-agent"]) {
    headers["user-agent"] = "ActionOW-API-Proxy/1.0";
  }

  if (canHaveBody && request.body) {
    options.body = request.body;
    options.duplex = "half";
  }

  const allowInsecureTls =
    process.env.API_ALLOW_INSECURE_TLS === "true" &&
    process.env.NODE_ENV !== "production" &&
    targetUrl.protocol === "https:";

  if (!allowInsecureTls) {
    return options;
  }

  const { Agent } = await import("undici");
  options.dispatcher = new Agent({
    connect: {
      rejectUnauthorized: false,
    },
  });

  return options;
}

async function handleRequest(
  request: NextRequest,
  { params }: { params: Promise<{ path: string[] }> }
) {
  const { path } = await params;
  const pathString = path.join("/");

  // Build the target URL
  const targetUrl = new URL(`/api/${pathString}`, API_BASE_URL);

  // Copy query parameters
  request.nextUrl.searchParams.forEach((value, key) => {
    targetUrl.searchParams.append(key, value);
  });

  // Build headers to forward
  const headers: Record<string, string> = {};
  FORWARDED_HEADERS.forEach((header) => {
    const value = request.headers.get(header);
    if (value) {
      headers[header] = value;
    }
  });

  // Add X-Forwarded headers
  headers["x-forwarded-for"] =
    request.headers.get("x-forwarded-for") ||
    request.headers.get("cf-connecting-ip") ||
    "unknown";
  headers["x-forwarded-host"] = request.headers.get("host") || "";
  headers["x-forwarded-proto"] = request.nextUrl.protocol.replace(":", "");

  try {
    const fetchOptions = await createProxyFetchOptions(request, headers, targetUrl);

    // Make the request to the backend
    const response = await fetch(targetUrl.toString(), fetchOptions);

    // Build response headers
    const responseHeaders = new Headers();
    response.headers.forEach((value, key) => {
      // Skip hop-by-hop headers
      if (!HOP_BY_HOP_HEADERS.includes(key.toLowerCase())) {
        responseHeaders.set(key, value);
      }
    });

    // Allow CORS
    responseHeaders.set("access-control-allow-origin", "*");
    responseHeaders.set(
      "access-control-allow-methods",
      "GET, POST, PUT, PATCH, DELETE, OPTIONS"
    );
    responseHeaders.set(
      "access-control-allow-headers",
      "Content-Type, Authorization"
    );

    // Return the response
    return new NextResponse(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: responseHeaders,
    });
  } catch (error) {
    console.error("[API Proxy] Error:", error);
    const errorDetails =
      error && typeof error === "object"
        ? {
            name: "name" in error ? String(error.name) : undefined,
            message: "message" in error ? String(error.message) : undefined,
            cause:
              "cause" in error && error.cause && typeof error.cause === "object"
                ? {
                    code: "code" in error.cause ? String(error.cause.code) : undefined,
                    errno: "errno" in error.cause ? String(error.cause.errno) : undefined,
                    syscall: "syscall" in error.cause ? String(error.cause.syscall) : undefined,
                    message:
                      "message" in error.cause ? String(error.cause.message) : undefined,
                  }
                : undefined,
          }
        : undefined;

    return NextResponse.json(
      {
        code: 500,
        message: "Failed to proxy request to backend",
        error: error instanceof Error ? error.message : "Unknown error",
        targetUrl: targetUrl.toString(),
        details: process.env.NODE_ENV !== "production" ? errorDetails : undefined,
      },
      { status: 500 }
    );
  }
}

export async function GET(
  request: NextRequest,
  context: { params: Promise<{ path: string[] }> }
) {
  return handleRequest(request, context);
}

export async function POST(
  request: NextRequest,
  context: { params: Promise<{ path: string[] }> }
) {
  return handleRequest(request, context);
}

export async function PUT(
  request: NextRequest,
  context: { params: Promise<{ path: string[] }> }
) {
  return handleRequest(request, context);
}

export async function PATCH(
  request: NextRequest,
  context: { params: Promise<{ path: string[] }> }
) {
  return handleRequest(request, context);
}

export async function DELETE(
  request: NextRequest,
  context: { params: Promise<{ path: string[] }> }
) {
  return handleRequest(request, context);
}

export async function OPTIONS() {
  return new NextResponse(null, {
    status: 204,
    headers: {
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
      "access-control-allow-headers":
        "Content-Type, Authorization",
      "access-control-max-age": "86400",
    },
  });
}
