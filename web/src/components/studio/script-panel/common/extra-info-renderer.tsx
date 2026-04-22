"use client";

/**
 * Extra Info Renderer Component
 * Renders nested JSON data in a readable format
 */

interface ExtraInfoRendererProps {
  data: Record<string, unknown>;
}

export function ExtraInfoRenderer({ data }: ExtraInfoRendererProps) {
  const renderValue = (value: unknown, depth = 0): React.ReactNode => {
    if (value === null || value === undefined) {
      return <span className="text-muted/50">-</span>;
    }

    if (typeof value === "boolean") {
      return <span className={value ? "text-success" : "text-muted"}>{value ? "是" : "否"}</span>;
    }

    if (typeof value === "number") {
      return <span>{value}</span>;
    }

    if (typeof value === "string") {
      // Check if it's a URL
      if (value.startsWith("http://") || value.startsWith("https://")) {
        return (
          <a href={value} target="_blank" rel="noopener noreferrer" className="text-accent hover:underline">
            {value.length > 40 ? `${value.substring(0, 40)}...` : value}
          </a>
        );
      }
      return <span>{value}</span>;
    }

    if (Array.isArray(value)) {
      if (value.length === 0) {
        return <span className="text-muted/50">空列表</span>;
      }
      return (
        <div className={`space-y-1 ${depth > 0 ? "ml-3 border-l border-muted/20 pl-3" : ""}`}>
          {value.map((item, index) => (
            <div key={index} className="flex items-start gap-2">
              <span className="text-xs text-muted/50">{index + 1}.</span>
              {renderValue(item, depth + 1)}
            </div>
          ))}
        </div>
      );
    }

    if (typeof value === "object") {
      const entries = Object.entries(value as Record<string, unknown>);
      if (entries.length === 0) {
        return <span className="text-muted/50">空对象</span>;
      }
      return (
        <div className={`space-y-2 ${depth > 0 ? "ml-3 border-l border-muted/20 pl-3" : ""}`}>
          {entries.map(([key, val]) => (
            <div key={key}>
              <div className="text-xs font-medium text-muted">{key}</div>
              <div className="mt-0.5">{renderValue(val, depth + 1)}</div>
            </div>
          ))}
        </div>
      );
    }

    return <span>{String(value)}</span>;
  };

  const entries = Object.entries(data);
  if (entries.length === 0) {
    return <p className="text-sm text-muted/50">无数据</p>;
  }

  return (
    <div className="space-y-3">
      {entries.map(([key, value]) => (
        <div key={key}>
          <div className="text-xs font-medium text-muted">{key}</div>
          <div className="mt-0.5 text-sm">{renderValue(value)}</div>
        </div>
      ))}
    </div>
  );
}

export default ExtraInfoRenderer;
