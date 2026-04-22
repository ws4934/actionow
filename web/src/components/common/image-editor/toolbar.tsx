"use client";

import { useState, useEffect } from "react";
import { Button, Tooltip, Popover, Slider } from "@heroui/react";
import {
  MousePointer2,
  Hand,
  Pencil,
  Square,
  Circle,
  Type,
  Crop,
  Undo2,
  Redo2,
  Download,
  Trash2,
  ZoomIn,
  ZoomOut,
  SquareDashed,
  Droplets,
  Lock,
  Unlock,
} from "lucide-react";
import type { ToolType } from "./types";
import { COLOR_PRESETS, STROKE_WIDTH_PRESETS, ZOOM_PRESETS } from "./types";

interface ToolbarProps {
  activeTool: ToolType | string;
  onToolChange: (tool: ToolType | string) => void;
  brushColor: string;
  onBrushColorChange: (color: string) => void;
  brushWidth: number;
  onBrushWidthChange: (width: number) => void;
  fillEnabled: boolean;
  onFillToggle: () => void;
  fillColor: string;
  onFillColorChange: (color: string) => void;
  zoom: number;
  onZoomIn: () => void;
  onZoomOut: () => void;
  onUndo: () => void;
  onRedo: () => void;
  canUndo: boolean;
  canRedo: boolean;
  onDelete: () => void;
  onDownload: () => void;
  position?: "top" | "bottom" | "left" | "right";
  isCropActive?: boolean;
  onCropApply?: () => void;
  onCropCancel?: () => void;
  /** Selected object size for display and control */
  selectedSize?: { width: number; height: number } | null;
  /** Callback when size changes */
  onSizeChange?: (width: number, height: number, lockAspectRatio: boolean) => void;
  /** Directly set zoom to a specific level */
  onZoomTo?: (zoom: number) => void;
}

const TOOLS: { id: ToolType; name: string; icon: React.ReactNode; shortcut?: string }[] = [
  { id: "select", name: "选择", icon: <MousePointer2 className="size-4" />, shortcut: "V" },
  { id: "hand", name: "拖拽画布", icon: <Hand className="size-4" />, shortcut: "H" },
  { id: "brush", name: "画笔", icon: <Pencil className="size-4" />, shortcut: "B" },
  { id: "rectangle", name: "矩形", icon: <Square className="size-4" />, shortcut: "R" },
  { id: "circle", name: "圆形", icon: <Circle className="size-4" />, shortcut: "C" },
  { id: "text", name: "文字", icon: <Type className="size-4" />, shortcut: "T" },
  { id: "mosaic", name: "模糊", icon: <Droplets className="size-4" />, shortcut: "M" },
  { id: "crop", name: "裁剪", icon: <Crop className="size-4" />, shortcut: "X" },
];

export function Toolbar({
  activeTool,
  onToolChange,
  brushColor,
  onBrushColorChange,
  brushWidth,
  onBrushWidthChange,
  fillEnabled,
  onFillToggle,
  fillColor,
  onFillColorChange,
  zoom,
  onZoomIn,
  onZoomOut,
  onUndo,
  onRedo,
  canUndo,
  canRedo,
  onDelete,
  onDownload,
  position = "top",
  isCropActive = false,
  onCropApply,
  onCropCancel,
  selectedSize,
  onSizeChange,
  onZoomTo,
}: ToolbarProps) {
  const isHorizontal = position === "top" || position === "bottom";

  const zoomPercent = Math.round(zoom * 100);

  // Size control state
  const [lockAspectRatio, setLockAspectRatio] = useState(true);
  const [tempWidth, setTempWidth] = useState(selectedSize?.width?.toString() || "");
  const [tempHeight, setTempHeight] = useState(selectedSize?.height?.toString() || "");

  // Update temp values when selectedSize changes
  useEffect(() => {
    if (selectedSize) {
      setTempWidth(selectedSize.width.toString());
      setTempHeight(selectedSize.height.toString());
    } else {
      setTempWidth("");
      setTempHeight("");
    }
  }, [selectedSize]);

  // Handle width change
  const handleWidthChange = (value: string) => {
    setTempWidth(value);
    const width = parseInt(value, 10);
    if (!isNaN(width) && width > 0 && selectedSize && onSizeChange) {
      if (lockAspectRatio) {
        const ratio = selectedSize.height / selectedSize.width;
        const height = Math.round(width * ratio);
        setTempHeight(height.toString());
        onSizeChange(width, height, lockAspectRatio);
      } else {
        onSizeChange(width, selectedSize.height, lockAspectRatio);
      }
    }
  };

  // Handle height change
  const handleHeightChange = (value: string) => {
    setTempHeight(value);
    const height = parseInt(value, 10);
    if (!isNaN(height) && height > 0 && selectedSize && onSizeChange) {
      if (lockAspectRatio) {
        const ratio = selectedSize.width / selectedSize.height;
        const width = Math.round(height * ratio);
        setTempWidth(width.toString());
        onSizeChange(width, height, lockAspectRatio);
      } else {
        onSizeChange(selectedSize.width, height, lockAspectRatio);
      }
    }
  };

  return (
    <div
      className={`flex items-center gap-1 rounded-lg bg-muted/10 p-1.5 ${
        isHorizontal ? "flex-row flex-wrap" : "flex-col"
      }`}
    >
      {/* Tool Buttons */}
      <div className={`flex gap-0.5 ${isHorizontal ? "flex-row" : "flex-col"}`}>
        {TOOLS.map((tool) => (
          <Tooltip key={tool.id} delay={0}>
            <Tooltip.Trigger>
              <Button
                variant={activeTool === tool.id ? "primary" : "ghost"}
                size="sm"
                isIconOnly
                className="size-8"
                onPress={() => onToolChange(tool.id)}
              >
                {tool.icon}
              </Button>
            </Tooltip.Trigger>
            <Tooltip.Content placement={isHorizontal ? "bottom" : "right"}>
              {tool.name}
              {tool.shortcut && (
                <span className="ml-2 text-muted">({tool.shortcut})</span>
              )}
            </Tooltip.Content>
          </Tooltip>
        ))}
      </div>

      {/* Separator */}
      <div
        className={`bg-border/50 ${
          isHorizontal ? "mx-1 h-6 w-px" : "my-1 h-px w-6"
        }`}
      />

      {/* Color Picker */}
      <Popover>
        <Tooltip delay={0}>
          <Tooltip.Trigger>
            <Popover.Trigger>
              <Button variant="ghost" size="sm" isIconOnly className="size-8">
                <div
                  className="size-5 rounded border border-border"
                  style={{ backgroundColor: brushColor }}
                />
              </Button>
            </Popover.Trigger>
          </Tooltip.Trigger>
          <Tooltip.Content placement={isHorizontal ? "bottom" : "right"}>
            颜色
          </Tooltip.Content>
        </Tooltip>
        <Popover.Content className="p-3">
          <div className="space-y-3">
            <div>
              <p className="mb-2 text-xs font-medium text-muted">预设颜色</p>
              <div className="grid grid-cols-5 gap-1.5">
                {COLOR_PRESETS.map((color) => (
                  <button
                    key={color}
                    className={`size-6 rounded border transition-transform hover:scale-110 ${
                      brushColor === color
                        ? "ring-2 ring-accent ring-offset-1"
                        : "border-border"
                    }`}
                    style={{ backgroundColor: color }}
                    onClick={() => onBrushColorChange(color)}
                  />
                ))}
              </div>
            </div>
            <div>
              <p className="mb-2 text-xs font-medium text-muted">自定义颜色</p>
              <div className="relative h-9 w-full overflow-hidden rounded-lg border border-border">
                <input
                  type="color"
                  value={brushColor}
                  onChange={(e) => onBrushColorChange(e.target.value)}
                  className="absolute inset-0 size-full cursor-pointer border-0 p-0"
                  style={{ opacity: 0 }}
                />
                <div
                  className="pointer-events-none flex h-full items-center gap-2 px-2"
                >
                  <div className="size-5 shrink-0 rounded border border-border" style={{ backgroundColor: brushColor }} />
                  <span className="text-xs text-muted">{brushColor}</span>
                </div>
              </div>
            </div>
          </div>
        </Popover.Content>
      </Popover>

      {/* Stroke Width */}
      <Popover>
        <Tooltip delay={0}>
          <Tooltip.Trigger>
            <Popover.Trigger>
              <Button variant="ghost" size="sm" isIconOnly className="size-8">
                <div className="flex items-center justify-center">
                  <div
                    className="rounded-full bg-current"
                    style={{
                      width: Math.min(brushWidth, 16),
                      height: Math.min(brushWidth, 16),
                    }}
                  />
                </div>
              </Button>
            </Popover.Trigger>
          </Tooltip.Trigger>
          <Tooltip.Content placement={isHorizontal ? "bottom" : "right"}>
            笔触大小
          </Tooltip.Content>
        </Tooltip>
        <Popover.Content className="p-3">
          <div className="space-y-3">
            <p className="text-xs font-medium text-muted">笔触大小</p>
            <div className="flex items-center gap-2">
              {STROKE_WIDTH_PRESETS.map((width) => (
                <button
                  key={width}
                  className={`flex size-8 items-center justify-center rounded border transition-colors ${
                    brushWidth === width
                      ? "border-accent bg-accent/10"
                      : "border-border hover:bg-muted/10"
                  }`}
                  onClick={() => onBrushWidthChange(width)}
                >
                  <div
                    className="rounded-full bg-current"
                    style={{
                      width: Math.min(width, 16),
                      height: Math.min(width, 16),
                    }}
                  />
                </button>
              ))}
            </div>
            <Slider
              minValue={1}
              maxValue={24}
              step={1}
              value={brushWidth}
              onChange={(val) => onBrushWidthChange(val as number)}
              className="w-full"
              aria-label="笔触大小"
            >
              <Slider.Track>
                <Slider.Fill />
                <Slider.Thumb />
              </Slider.Track>
            </Slider>
          </div>
        </Popover.Content>
      </Popover>

      {/* Fill Toggle */}
      <Tooltip delay={0}>
        <Tooltip.Trigger>
          <Button
            variant={fillEnabled ? "secondary" : "ghost"}
            size="sm"
            isIconOnly
            className="size-8"
            onPress={onFillToggle}
          >
            <SquareDashed className="size-4" />
          </Button>
        </Tooltip.Trigger>
        <Tooltip.Content placement={isHorizontal ? "bottom" : "right"}>
          {fillEnabled ? "填充开启" : "填充关闭"}
        </Tooltip.Content>
      </Tooltip>

      {/* Fill Color Picker (shown when fill is enabled) */}
      {fillEnabled && (
        <Popover>
          <Tooltip delay={0}>
            <Tooltip.Trigger>
              <Popover.Trigger>
                <Button variant="ghost" size="sm" isIconOnly className="size-8">
                  <div
                    className="size-4 rounded border border-border"
                    style={{ backgroundColor: fillColor }}
                  />
                </Button>
              </Popover.Trigger>
            </Tooltip.Trigger>
            <Tooltip.Content placement={isHorizontal ? "bottom" : "right"}>
              填充颜色
            </Tooltip.Content>
          </Tooltip>
          <Popover.Content className="p-3">
            <div className="space-y-3">
              <div>
                <p className="mb-2 text-xs font-medium text-muted">填充颜色</p>
                <div className="grid grid-cols-5 gap-1.5">
                  {COLOR_PRESETS.map((color) => (
                    <button
                      key={color}
                      className={`size-6 rounded border transition-transform hover:scale-110 ${
                        fillColor === color
                          ? "ring-2 ring-accent ring-offset-1"
                          : "border-border"
                      }`}
                      style={{ backgroundColor: color }}
                      onClick={() => onFillColorChange(color)}
                    />
                  ))}
                </div>
              </div>
              <div>
                <p className="mb-2 text-xs font-medium text-muted">自定义颜色</p>
                <div className="relative h-9 w-full overflow-hidden rounded-lg border border-border">
                  <input
                    type="color"
                    value={fillColor}
                    onChange={(e) => onFillColorChange(e.target.value)}
                    className="absolute inset-0 size-full cursor-pointer border-0 p-0"
                    style={{ opacity: 0 }}
                  />
                  <div
                    className="pointer-events-none flex h-full items-center gap-2 px-2"
                  >
                    <div className="size-5 shrink-0 rounded border border-border" style={{ backgroundColor: fillColor }} />
                    <span className="text-xs text-muted">{fillColor}</span>
                  </div>
                </div>
              </div>
            </div>
          </Popover.Content>
        </Popover>
      )}

      {/* Separator */}
      <div
        className={`bg-border/50 ${
          isHorizontal ? "mx-1 h-6 w-px" : "my-1 h-px w-6"
        }`}
      />

      {/* Size Controls - Only show when object is selected */}
      {selectedSize && onSizeChange && (
        <>
          <div className={`flex items-center gap-1 ${isHorizontal ? "flex-row" : "flex-col"}`}>
            <div className="flex items-center gap-1">
              <span className="text-xs text-muted">W</span>
              <input
                type="number"
                value={tempWidth}
                onChange={(e) => handleWidthChange(e.target.value)}
                className="h-7 w-14 rounded-md border border-border bg-muted/10 px-1.5 text-center text-xs transition-colors focus:border-accent focus:bg-transparent focus:outline-none focus:ring-1 focus:ring-accent/30"
                min="1"
              />
            </div>

            {/* Lock Aspect Ratio Button */}
            <Tooltip delay={0}>
              <Tooltip.Trigger>
                <Button
                  variant={lockAspectRatio ? "secondary" : "ghost"}
                  size="sm"
                  isIconOnly
                  className="size-6"
                  onPress={() => setLockAspectRatio(!lockAspectRatio)}
                >
                  {lockAspectRatio ? (
                    <Lock className="size-3" />
                  ) : (
                    <Unlock className="size-3" />
                  )}
                </Button>
              </Tooltip.Trigger>
              <Tooltip.Content placement={isHorizontal ? "bottom" : "right"}>
                {lockAspectRatio ? "锁定比例" : "解锁比例"}
              </Tooltip.Content>
            </Tooltip>

            <div className="flex items-center gap-1">
              <span className="text-xs text-muted">H</span>
              <input
                type="number"
                value={tempHeight}
                onChange={(e) => handleHeightChange(e.target.value)}
                className="h-7 w-14 rounded-md border border-border bg-muted/10 px-1.5 text-center text-xs transition-colors focus:border-accent focus:bg-transparent focus:outline-none focus:ring-1 focus:ring-accent/30"
                min="1"
              />
            </div>
          </div>

          {/* Separator */}
          <div
            className={`bg-border/50 ${
              isHorizontal ? "mx-1 h-6 w-px" : "my-1 h-px w-6"
            }`}
          />
        </>
      )}

      {/* Zoom Controls */}
      <div className={`flex items-center gap-0.5 ${isHorizontal ? "flex-row" : "flex-col"}`}>
        <Tooltip delay={0}>
          <Tooltip.Trigger>
            <Button
              variant="ghost"
              size="sm"
              isIconOnly
              className="size-8"
              onPress={onZoomOut}
            >
              <ZoomOut className="size-4" />
            </Button>
          </Tooltip.Trigger>
          <Tooltip.Content placement={isHorizontal ? "bottom" : "right"}>
            缩小
          </Tooltip.Content>
        </Tooltip>

        {/* Zoom Percentage */}
        <Popover>
          <Tooltip delay={0}>
            <Tooltip.Trigger>
              <Popover.Trigger>
                <Button variant="ghost" size="sm" className="h-8 w-14 px-1 text-xs">
                  {zoomPercent}%
                </Button>
              </Popover.Trigger>
            </Tooltip.Trigger>
            <Tooltip.Content placement={isHorizontal ? "bottom" : "right"}>
              缩放级别
            </Tooltip.Content>
          </Tooltip>
          <Popover.Content className="w-48 p-3">
            <div className="space-y-3">
              <p className="text-xs font-medium text-muted">缩放级别</p>
              <div className="flex flex-wrap gap-1">
                {ZOOM_PRESETS.map((z) => (
                  <Button
                    key={z}
                    variant={Math.abs(zoom - z) < 0.01 ? "primary" : "ghost"}
                    size="sm"
                    className="h-7 px-2 text-xs"
                    onPress={() => {
                      if (onZoomTo) {
                        onZoomTo(z);
                      }
                    }}
                  >
                    {Math.round(z * 100)}%
                  </Button>
                ))}
              </div>
            </div>
          </Popover.Content>
        </Popover>

        <Tooltip delay={0}>
          <Tooltip.Trigger>
            <Button
              variant="ghost"
              size="sm"
              isIconOnly
              className="size-8"
              onPress={onZoomIn}
            >
              <ZoomIn className="size-4" />
            </Button>
          </Tooltip.Trigger>
          <Tooltip.Content placement={isHorizontal ? "bottom" : "right"}>
            放大
          </Tooltip.Content>
        </Tooltip>
      </div>

      {/* Separator */}
      <div
        className={`bg-border/50 ${
          isHorizontal ? "mx-1 h-6 w-px" : "my-1 h-px w-6"
        }`}
      />

      {/* Actions */}
      <div className={`flex gap-0.5 ${isHorizontal ? "flex-row" : "flex-col"}`}>
        <Tooltip delay={0}>
          <Tooltip.Trigger>
            <Button
              variant="ghost"
              size="sm"
              isIconOnly
              className="size-8"
              onPress={onUndo}
              isDisabled={!canUndo}
            >
              <Undo2 className="size-4" />
            </Button>
          </Tooltip.Trigger>
          <Tooltip.Content placement={isHorizontal ? "bottom" : "right"}>
            撤销 (Ctrl+Z)
          </Tooltip.Content>
        </Tooltip>

        <Tooltip delay={0}>
          <Tooltip.Trigger>
            <Button
              variant="ghost"
              size="sm"
              isIconOnly
              className="size-8"
              onPress={onRedo}
              isDisabled={!canRedo}
            >
              <Redo2 className="size-4" />
            </Button>
          </Tooltip.Trigger>
          <Tooltip.Content placement={isHorizontal ? "bottom" : "right"}>
            重做 (Ctrl+Shift+Z)
          </Tooltip.Content>
        </Tooltip>

        <Tooltip delay={0}>
          <Tooltip.Trigger>
            <Button
              variant="ghost"
              size="sm"
              isIconOnly
              className="size-8"
              onPress={onDelete}
            >
              <Trash2 className="size-4" />
            </Button>
          </Tooltip.Trigger>
          <Tooltip.Content placement={isHorizontal ? "bottom" : "right"}>
            删除选中 (Delete)
          </Tooltip.Content>
        </Tooltip>

        <Tooltip delay={0}>
          <Tooltip.Trigger>
            <Button
              variant="ghost"
              size="sm"
              isIconOnly
              className="size-8"
              onPress={onDownload}
            >
              <Download className="size-4" />
            </Button>
          </Tooltip.Trigger>
          <Tooltip.Content placement={isHorizontal ? "bottom" : "right"}>
            下载图片
          </Tooltip.Content>
        </Tooltip>
      </div>
    </div>
  );
}

export default Toolbar;
