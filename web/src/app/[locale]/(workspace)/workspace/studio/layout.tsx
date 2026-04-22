"use client";

import { Card } from "@heroui/react";
import { MaterialSidebar } from "@/components/material-room";

export default function StudioLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex h-full w-full gap-0 p-3">
      <Card className="h-full shrink-0 overflow-hidden p-0">
        <MaterialSidebar />
      </Card>
      <div className="h-full min-w-0 flex-1 pl-3">
        <Card className="flex h-full flex-col overflow-hidden">
          {children}
        </Card>
      </div>
    </div>
  );
}
