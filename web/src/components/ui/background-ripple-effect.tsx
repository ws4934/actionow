"use client";

import { useEffect, useRef, useCallback } from "react";

const CELL_SIZE = 40; // px
const MAX_RIPPLE_DIST = 7; // cells
const RIPPLE_DELAY_PER_CELL = 45; // ms per unit of distance
const RIPPLE_HOLD_MS = 500; // how long a cell stays lit
const RIPPLE_FADE_MS = 400; // CSS transition duration

// Accent colors that work on both light and dark backgrounds
const COLORS = [
  "99,102,241",   // indigo
  "139,92,246",   // violet
  "168,85,247",   // purple
  "6,182,212",    // cyan
  "59,130,246",   // blue
  "236,72,153",   // pink
  "16,185,129",   // emerald
  "245,158,11",   // amber
];

function randColor() {
  return COLORS[Math.floor(Math.random() * COLORS.length)];
}

interface Props {
  className?: string;
}

export function BackgroundRippleEffect({ className }: Props) {
  const wrapperRef = useRef<HTMLDivElement>(null);
  const gridRef = useRef<HTMLDivElement>(null);
  // flat array of cell <div> refs
  const cellRefs = useRef<(HTMLDivElement | null)[]>([]);
  // pending timers per cell index
  const timers = useRef<Map<number, ReturnType<typeof setTimeout>>>(new Map());
  const gridSize = useRef({ cols: 0, rows: 0 });

  // Build the cell grid whenever container dimensions change
  const rebuildGrid = useCallback(() => {
    const wrapper = wrapperRef.current;
    const grid = gridRef.current;
    if (!wrapper || !grid) return;

    const cols = Math.ceil(wrapper.offsetWidth / CELL_SIZE) + 2;
    const rows = Math.ceil(wrapper.offsetHeight / CELL_SIZE) + 2;

    if (cols === gridSize.current.cols && rows === gridSize.current.rows) return;
    gridSize.current = { cols, rows };

    // Clear old timers
    timers.current.forEach(clearTimeout);
    timers.current.clear();

    // Rebuild DOM
    grid.style.gridTemplateColumns = `repeat(${cols}, ${CELL_SIZE}px)`;
    grid.style.gridTemplateRows = `repeat(${rows}, ${CELL_SIZE}px)`;
    grid.innerHTML = "";
    cellRefs.current = Array.from({ length: cols * rows }, (_, i) => {
      const cell = document.createElement("div");
      cell.className = "bre-cell";
      grid.appendChild(cell);
      return cell;
    });

    // Pointer handler: calculate cell from coordinates so it works even when
    // a content layer with pointer-events-auto sits on top of the grid.
    let lastRippleTime = 0;
    const handlePointer = (e: PointerEvent) => {
      const now = Date.now();
      if (e.type !== "pointerdown" && now - lastRippleTime < 80) return;

      // Only trigger when pointer is within the wrapper bounds
      const wrapperRect = wrapper.getBoundingClientRect();
      if (
        e.clientX < wrapperRect.left || e.clientX > wrapperRect.right ||
        e.clientY < wrapperRect.top || e.clientY > wrapperRect.bottom
      ) return;

      const rect = grid.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      const c = Math.floor(x / CELL_SIZE);
      const r = Math.floor(y / CELL_SIZE);
      if (c < 0 || c >= cols || r < 0 || r >= rows) return;

      lastRippleTime = now;
      triggerRipple(r, c, cols, rows);
    };

    document.addEventListener("pointermove", handlePointer);
    document.addEventListener("pointerdown", handlePointer);

    // Store cleanup reference
    (grid as unknown as Record<string, () => void>).__cleanupPointer = () => {
      document.removeEventListener("pointermove", handlePointer);
      document.removeEventListener("pointerdown", handlePointer);
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  function triggerRipple(pivotRow: number, pivotCol: number, cols: number, rows: number) {
    const color = randColor();
    for (let r = 0; r < rows; r++) {
      for (let c = 0; c < cols; c++) {
        const dist = Math.sqrt((r - pivotRow) ** 2 + (c - pivotCol) ** 2);
        if (dist > MAX_RIPPLE_DIST) continue;

        const key = r * cols + c;
        const delay = Math.round(dist * RIPPLE_DELAY_PER_CELL);
        const opacity = (1 - dist / MAX_RIPPLE_DIST) * 0.55;

        const existing = timers.current.get(key);
        if (existing) clearTimeout(existing);

        // Light up after staggered delay
        const t1 = setTimeout(() => {
          const cell = cellRefs.current[key];
          if (!cell) return;
          cell.style.backgroundColor = `rgba(${color},${opacity.toFixed(2)})`;

          // Fade out after hold time
          const t2 = setTimeout(() => {
            const c2 = cellRefs.current[key];
            if (c2) c2.style.backgroundColor = "";
            timers.current.delete(key);
          }, RIPPLE_HOLD_MS);

          timers.current.set(key, t2);
        }, delay);

        timers.current.set(key, t1);
      }
    }
  }

  useEffect(() => {
    rebuildGrid();
    const ro = new ResizeObserver(rebuildGrid);
    if (wrapperRef.current) ro.observe(wrapperRef.current);
    return () => {
      ro.disconnect();
      timers.current.forEach(clearTimeout);
      timers.current.clear();
      // Clean up document-level pointer listeners
      const grid = gridRef.current;
      if (grid) {
        const cleanup = (grid as unknown as Record<string, (() => void) | undefined>).__cleanupPointer;
        cleanup?.();
      }
    };
  }, [rebuildGrid]);

  return (
    <div
      ref={wrapperRef}
      className={`pointer-events-none absolute inset-0 overflow-hidden${className ? ` ${className}` : ""}`}
    >
      {/* Cell grid */}
      <div
        ref={gridRef}
        className="bre-grid absolute"
        style={{
          top: `-${CELL_SIZE / 2}px`,
          left: `-${CELL_SIZE / 2}px`,
          right: `-${CELL_SIZE / 2}px`,
          bottom: `-${CELL_SIZE / 2}px`,
          display: "grid",
        }}
      />

    </div>
  );
}
