import { NextRequest, NextResponse } from "next/server";

/**
 * Image proxy API route to bypass CORS restrictions
 * Usage: /api/proxy?url=<encoded-image-url>
 */
export async function GET(request: NextRequest) {
  const url = request.nextUrl.searchParams.get("url");

  if (!url) {
    return NextResponse.json({ error: "Missing url parameter" }, { status: 400 });
  }

  try {
    // Only allow proxying from trusted domains
    const parsedUrl = new URL(url);
    const allowedHosts = [
      "asset.actionow.ai",
      "asset.alienworm.top",
      "alienworm.top",
      "actionow.ai",
      "localhost",
    ];

    if (!allowedHosts.some((host) => parsedUrl.hostname.endsWith(host))) {
      return NextResponse.json({ error: "Domain not allowed" }, { status: 403 });
    }

    // Fetch the image
    const response = await fetch(url, {
      headers: {
        "User-Agent": "ActionOW-Proxy/1.0",
        "Accept": "image/*,*/*",
      },
      cache: "force-cache",
    });

    if (!response.ok) {
      console.error(`Proxy fetch failed: ${response.status} ${response.statusText}`);
      return NextResponse.json(
        { error: `Failed to fetch: ${response.status}` },
        { status: response.status }
      );
    }

    // Get the content type
    const contentType = response.headers.get("content-type") || "application/octet-stream";

    // Get the image data as ArrayBuffer
    const arrayBuffer = await response.arrayBuffer();

    // Return with proper headers
    return new NextResponse(arrayBuffer, {
      status: 200,
      headers: {
        "Content-Type": contentType,
        "Content-Length": String(arrayBuffer.byteLength),
        "Cache-Control": "public, max-age=31536000, immutable",
        "Access-Control-Allow-Origin": "*",
      },
    });
  } catch (error) {
    console.error("Proxy error:", error);
    return NextResponse.json(
      { error: "Failed to proxy image" },
      { status: 500 }
    );
  }
}
