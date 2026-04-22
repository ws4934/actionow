"use client";

import { useState, useRef } from "react";
import ReactMarkdown from "react-markdown";
import type { Components } from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import rehypeKatex from "rehype-katex";
import rehypeHighlight from "rehype-highlight";
import { Copy, Check } from "lucide-react";
import { preprocessStreamingMarkdown } from "../utils";

// ---- Code block: language badge + copy button ----
function CodeBlock({ children }: { children: React.ReactNode }) {
  const [copied, setCopied] = useState(false);
  const preRef = useRef<HTMLPreElement>(null);

  const codeEl = children as React.ReactElement<{ className?: string }>;
  const lang = codeEl?.props?.className?.replace(/language-/, "").split(" ")[0] ?? "text";

  const handleCopy = async () => {
    const text = preRef.current?.textContent ?? "";
    await navigator.clipboard.writeText(text.trimEnd());
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="not-prose my-2 overflow-hidden rounded-lg border border-border/40 bg-[#1e1e2e] sm:my-3">
      <div className="flex items-center justify-between border-b border-border/30 px-3 py-1.5">
        <span className="text-[11px] text-muted/60">{lang}</span>
        <button
          onClick={handleCopy}
          className="flex items-center gap-1 rounded p-1 text-[11px] text-muted/60 transition-colors hover:text-white"
          aria-label="Copy code"
        >
          {copied ? (
            <Check className="size-3 text-green-400" />
          ) : (
            <Copy className="size-3" />
          )}
        </button>
      </div>
      <pre
        ref={preRef}
        className="overflow-x-auto p-3 text-[13px] leading-relaxed sm:p-4"
      >
        {children}
      </pre>
    </div>
  );
}

// ---- Shared component map ----
const COMPONENTS: Components = {
  pre: CodeBlock as Components["pre"],
  a: ({ href, children, ...props }) => (
    <a
      href={href}
      target={href?.startsWith("http") ? "_blank" : undefined}
      rel={href?.startsWith("http") ? "noopener noreferrer" : undefined}
      {...props}
    >
      {children}
    </a>
  ),
  // img sizing is controlled by .markdown-chat in globals.css
  img: ({ src, alt, ...props }) => (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={src}
      alt={alt ?? ""}
      loading="lazy"
      {...(props as React.ImgHTMLAttributes<HTMLImageElement>)}
    />
  ),
};

const REMARK_PLUGINS = [remarkGfm, remarkMath];
const REHYPE_PLUGINS = [rehypeKatex, rehypeHighlight];

// ---- Public component ----
interface MarkdownRendererProps {
  content: string;
  isStreaming?: boolean;
  className?: string;
}

export function MarkdownRenderer({ content, isStreaming, className }: MarkdownRendererProps) {
  const processed = isStreaming ? preprocessStreamingMarkdown(content) : content;

  return (
    <div
      className={[
        // prose supplies dark-mode color tokens; markdown-chat handles all spacing/sizing
        "prose prose-sm dark:prose-invert max-w-none",
        "markdown-chat",
        className,
      ]
        .filter(Boolean)
        .join(" ")}
    >
      <ReactMarkdown
        remarkPlugins={REMARK_PLUGINS}
        rehypePlugins={REHYPE_PLUGINS}
        components={COMPONENTS}
      >
        {processed}
      </ReactMarkdown>
    </div>
  );
}
