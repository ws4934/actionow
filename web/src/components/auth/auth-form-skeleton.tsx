"use client";

interface AuthFormSkeletonProps {
  showTabs?: boolean;
}

export function AuthFormSkeleton({ showTabs = false }: AuthFormSkeletonProps) {
  return (
    <div className="flex flex-col gap-4" aria-hidden="true">
      <div className="flex items-start justify-between gap-4">
        <div className="space-y-2">
          <div className="h-7 w-36 rounded bg-surface" />
          <div className="h-4 w-56 rounded bg-surface" />
        </div>
        <div className="flex gap-3">
          <div className="size-8 rounded bg-surface" />
          <div className="size-8 rounded bg-surface" />
        </div>
      </div>

      {showTabs ? (
        <div className="grid grid-cols-2 gap-2 rounded-lg bg-surface/80 p-1">
          <div className="h-10 rounded bg-surface" />
          <div className="h-10 rounded bg-surface" />
        </div>
      ) : null}

      <div className="space-y-4 pt-2">
        <div className="space-y-2">
          <div className="h-4 w-20 rounded bg-surface" />
          <div className="h-11 rounded bg-surface" />
        </div>
        <div className="space-y-2">
          <div className="h-4 w-20 rounded bg-surface" />
          <div className="h-11 rounded bg-surface" />
        </div>
        <div className="h-11 rounded bg-surface" />
      </div>
    </div>
  );
}
