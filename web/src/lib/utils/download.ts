import { toast } from "@heroui/react";

/**
 * Download utility functions
 * Handles file downloads with proper cross-origin support
 */

/**
 * Download a file from URL
 * @param url - The URL of the file to download
 * @param filename - Optional filename for the downloaded file
 */
export async function downloadFile(url: string, filename?: string): Promise<void> {
  try {
    // Fetch the file as blob
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`Failed to fetch file: ${response.status}`);
    }

    const blob = await response.blob();

    // Create blob URL
    const blobUrl = URL.createObjectURL(blob);

    // Create download link
    const link = document.createElement("a");
    link.href = blobUrl;

    // Extract filename from URL if not provided
    if (!filename) {
      const urlParts = url.split("/");
      const lastPart = urlParts[urlParts.length - 1];
      // Remove query parameters
      filename = lastPart.split("?")[0] || "download";
    }

    link.download = filename;
    link.style.display = "none";

    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    // Clean up blob URL
    URL.revokeObjectURL(blobUrl);
  } catch (error) {
    console.error("Download failed:", error);
    toast.danger("下载失败");
    // Fallback: open in new tab
    window.open(url, "_blank");
  }
}

