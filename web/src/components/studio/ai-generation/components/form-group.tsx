"use client";

import { useState } from "react";
import { ChevronDown } from "lucide-react";

interface FormGroupProps {
  name: string;
  label: string;
  defaultCollapsed?: boolean;
  children: React.ReactNode;
}

export function FormGroup({
  label,
  defaultCollapsed = false,
  children,
}: FormGroupProps) {
  const [isCollapsed, setIsCollapsed] = useState(defaultCollapsed);

  return (
    <div className="rounded-lg border border-border bg-muted/5">
      <button
        type="button"
        className="flex w-full items-center justify-between px-3 py-2.5 text-left"
        onClick={() => setIsCollapsed(!isCollapsed)}
      >
        <span className="text-sm font-medium">{label}</span>
        <ChevronDown
          className={`size-4 text-muted transition-transform ${
            isCollapsed ? "" : "rotate-180"
          }`}
        />
      </button>

      <div
        className={`overflow-hidden transition-all duration-300 ${
          isCollapsed ? "max-h-0" : "max-h-[2000px]"
        }`}
      >
        <div className="flex flex-col gap-4 border-t border-border px-3 py-3">
          {children}
        </div>
      </div>
    </div>
  );
}
