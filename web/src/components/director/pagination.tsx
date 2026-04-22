"use client";

import { Button } from "@heroui/react";
import { ChevronLeft, ChevronRight } from "lucide-react";

interface PaginationProps {
  currentPage: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  showPageNumbers?: boolean;
  maxPageButtons?: number;
  className?: string;
}

/**
 * 分页组件
 */
export function Pagination({
  currentPage,
  totalPages,
  onPageChange,
  showPageNumbers = true,
  maxPageButtons = 5,
  className = "",
}: PaginationProps) {
  if (totalPages <= 1) return null;

  // Calculate visible page numbers
  const getPageNumbers = (): number[] => {
    if (totalPages <= maxPageButtons) {
      return Array.from({ length: totalPages }, (_, i) => i + 1);
    }

    const half = Math.floor(maxPageButtons / 2);
    let start = Math.max(1, currentPage - half);
    const end = Math.min(totalPages, start + maxPageButtons - 1);

    if (end - start + 1 < maxPageButtons) {
      start = Math.max(1, end - maxPageButtons + 1);
    }

    return Array.from({ length: end - start + 1 }, (_, i) => start + i);
  };

  const pageNumbers = getPageNumbers();

  return (
    <div className={`flex items-center gap-1 ${className}`}>
      <Button
        variant="tertiary"
        size="sm"
        isIconOnly
        isDisabled={currentPage <= 1}
        onPress={() => onPageChange(currentPage - 1)}
        aria-label="上一页"
      >
        <ChevronLeft className="size-4" />
      </Button>

      {showPageNumbers && (
        <>
          {pageNumbers[0] > 1 && (
            <>
              <Button
                variant="tertiary"
                size="sm"
                onPress={() => onPageChange(1)}
              >
                1
              </Button>
              {pageNumbers[0] > 2 && (
                <span className="px-1 text-sm text-muted">...</span>
              )}
            </>
          )}

          {pageNumbers.map((num) => (
            <Button
              key={num}
              variant={currentPage === num ? "primary" : "tertiary"}
              size="sm"
              onPress={() => onPageChange(num)}
            >
              {num}
            </Button>
          ))}

          {pageNumbers[pageNumbers.length - 1] < totalPages && (
            <>
              {pageNumbers[pageNumbers.length - 1] < totalPages - 1 && (
                <span className="px-1 text-sm text-muted">...</span>
              )}
              <Button
                variant="tertiary"
                size="sm"
                onPress={() => onPageChange(totalPages)}
              >
                {totalPages}
              </Button>
            </>
          )}
        </>
      )}

      <Button
        variant="tertiary"
        size="sm"
        isIconOnly
        isDisabled={currentPage >= totalPages}
        onPress={() => onPageChange(currentPage + 1)}
        aria-label="下一页"
      >
        <ChevronRight className="size-4" />
      </Button>
    </div>
  );
}

/**
 * 简洁分页 (只显示当前页/总页数)
 */
interface SimplePaginationProps {
  currentPage: number;
  totalPages: number;
  totalRecords?: number;
  onPageChange: (page: number) => void;
  className?: string;
}

export function SimplePagination({
  currentPage,
  totalPages,
  totalRecords,
  onPageChange,
  className = "",
}: SimplePaginationProps) {
  if (totalPages <= 1) return null;

  return (
    <div
      className={`flex items-center justify-between border-t border-border bg-surface/50 px-6 py-3 ${className}`}
    >
      {totalRecords !== undefined && (
        <span className="text-sm text-muted">共 {totalRecords} 条</span>
      )}
      <div className="flex items-center gap-2">
        <Button
          variant="tertiary"
          size="sm"
          isIconOnly
          isDisabled={currentPage <= 1}
          onPress={() => onPageChange(currentPage - 1)}
        >
          <ChevronLeft className="size-4" />
        </Button>
        <span className="min-w-16 text-center text-sm text-foreground">
          {currentPage} / {totalPages}
        </span>
        <Button
          variant="tertiary"
          size="sm"
          isIconOnly
          isDisabled={currentPage >= totalPages}
          onPress={() => onPageChange(currentPage + 1)}
        >
          <ChevronRight className="size-4" />
        </Button>
      </div>
    </div>
  );
}
