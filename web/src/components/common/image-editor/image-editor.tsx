"use client";

import { useEffect, useRef, useCallback, useState } from "react";
import { useImageEditor } from "@ozdemircibaris/react-image-editor/core";
import { Button } from "@heroui/react";
import { Toolbar } from "./toolbar";
import type { ToolType, ImageEditorProps, ImageEditorAPI } from "./types";
import { getProxiedImageUrl } from "@/lib/utils/image-proxy";

// Alignment guidelines helper
interface GuidelineConfig {
  aligningLineColor: string;
  aligningLineWidth: number;
  aligningLineMargin: number;
}

const GUIDELINE_CONFIG: GuidelineConfig = {
  aligningLineColor: "rgba(0, 120, 255, 0.8)",
  aligningLineWidth: 1,
  aligningLineMargin: 5,
};

// Initialize alignment guidelines on a fabric canvas
// Supports both moving and scaling
function initAligningGuidelines(canvas: any, config: GuidelineConfig = GUIDELINE_CONFIG) {
  let ctx = canvas.getSelectionContext();
  let viewportTransform: number[] = canvas.viewportTransform;
  let aligningLineOffset = 5;
  let zoom = 1;

  function drawVerticalLine(coords: { x: number; y1: number; y2: number }) {
    drawLine(
      coords.x + 0.5,
      coords.y1 > coords.y2 ? coords.y2 : coords.y1,
      coords.x + 0.5,
      coords.y2 > coords.y1 ? coords.y2 : coords.y1
    );
  }

  function drawHorizontalLine(coords: { y: number; x1: number; x2: number }) {
    drawLine(
      coords.x1 > coords.x2 ? coords.x2 : coords.x1,
      coords.y + 0.5,
      coords.x2 > coords.x1 ? coords.x2 : coords.x1,
      coords.y + 0.5
    );
  }

  function drawLine(x1: number, y1: number, x2: number, y2: number) {
    ctx.save();
    ctx.lineWidth = config.aligningLineWidth;
    ctx.strokeStyle = config.aligningLineColor;
    ctx.setLineDash([4, 4]);
    ctx.beginPath();
    ctx.moveTo(x1 * zoom + viewportTransform[4], y1 * zoom + viewportTransform[5]);
    ctx.lineTo(x2 * zoom + viewportTransform[4], y2 * zoom + viewportTransform[5]);
    ctx.stroke();
    ctx.restore();
  }

  // Draw size label
  function drawSizeLabel(obj: any) {
    const bounds = obj.getBoundingRect();
    const width = Math.round(obj.getScaledWidth());
    const height = Math.round(obj.getScaledHeight());
    const text = `${width} × ${height}`;

    ctx.save();
    ctx.font = "12px sans-serif";
    ctx.fillStyle = "rgba(0, 120, 255, 0.9)";
    ctx.textAlign = "center";

    const labelX = bounds.left + bounds.width / 2;
    const labelY = bounds.top - 8;

    // Background
    const textWidth = ctx.measureText(text).width;
    ctx.fillStyle = "rgba(0, 0, 0, 0.7)";
    ctx.fillRect(labelX - textWidth / 2 - 4, labelY - 12, textWidth + 8, 16);

    // Text
    ctx.fillStyle = "white";
    ctx.fillText(text, labelX, labelY);
    ctx.restore();
  }

  function isInRange(value1: number, value2: number) {
    return Math.abs(value1 - value2) <= config.aligningLineMargin / zoom;
  }

  let verticalLines: { x: number; y1: number; y2: number }[] = [];
  let horizontalLines: { y: number; x1: number; x2: number }[] = [];
  let showSizeLabel = false;
  let activeLabelObject: any = null;

  canvas.on("mouse:down", () => {
    viewportTransform = canvas.viewportTransform;
    zoom = canvas.getZoom();
  });

  // Shared logic for calculating guidelines
  function calculateGuidelines(activeObject: any, isScaling = false) {
    if (!activeObject) return;

    const canvasObjects = canvas.getObjects().filter((obj: any) => obj !== activeObject);
    const activeObjectCenter = activeObject.getCenterPoint();
    const activeObjectLeft = activeObjectCenter.x;
    const activeObjectTop = activeObjectCenter.y;
    const activeObjectWidth = activeObject.getScaledWidth();
    const activeObjectHeight = activeObject.getScaledHeight();

    verticalLines = [];
    horizontalLines = [];

    const canvasWidth = canvas.getWidth() / zoom;
    const canvasHeight = canvas.getHeight() / zoom;

    // Canvas center
    const canvasCenterX = canvasWidth / 2;
    const canvasCenterY = canvasHeight / 2;

    // Canvas thirds
    const thirdX1 = canvasWidth / 3;
    const thirdX2 = (canvasWidth * 2) / 3;
    const thirdY1 = canvasHeight / 3;
    const thirdY2 = (canvasHeight * 2) / 3;

    // Check against canvas center
    if (isInRange(activeObjectLeft, canvasCenterX)) {
      verticalLines.push({ x: canvasCenterX, y1: 0, y2: canvasHeight });
      if (!isScaling) {
        activeObject.setPositionByOrigin({ x: canvasCenterX, y: activeObjectTop }, "center", "center");
      }
    }

    if (isInRange(activeObjectTop, canvasCenterY)) {
      horizontalLines.push({ y: canvasCenterY, x1: 0, x2: canvasWidth });
      if (!isScaling) {
        activeObject.setPositionByOrigin({ x: activeObjectLeft, y: canvasCenterY }, "center", "center");
      }
    }

    // Check against canvas thirds
    if (isInRange(activeObjectLeft, thirdX1)) {
      verticalLines.push({ x: thirdX1, y1: 0, y2: canvasHeight });
    }
    if (isInRange(activeObjectLeft, thirdX2)) {
      verticalLines.push({ x: thirdX2, y1: 0, y2: canvasHeight });
    }
    if (isInRange(activeObjectTop, thirdY1)) {
      horizontalLines.push({ y: thirdY1, x1: 0, x2: canvasWidth });
    }
    if (isInRange(activeObjectTop, thirdY2)) {
      horizontalLines.push({ y: thirdY2, x1: 0, x2: canvasWidth });
    }

    // Check against canvas edges
    const activeLeft = activeObjectLeft - activeObjectWidth / 2;
    const activeRight = activeObjectLeft + activeObjectWidth / 2;
    const activeTop = activeObjectTop - activeObjectHeight / 2;
    const activeBottom = activeObjectTop + activeObjectHeight / 2;

    // Left edge
    if (isInRange(activeLeft, 0)) {
      verticalLines.push({ x: 0, y1: 0, y2: canvasHeight });
    }
    // Right edge
    if (isInRange(activeRight, canvasWidth)) {
      verticalLines.push({ x: canvasWidth, y1: 0, y2: canvasHeight });
    }
    // Top edge
    if (isInRange(activeTop, 0)) {
      horizontalLines.push({ y: 0, x1: 0, x2: canvasWidth });
    }
    // Bottom edge
    if (isInRange(activeBottom, canvasHeight)) {
      horizontalLines.push({ y: canvasHeight, x1: 0, x2: canvasWidth });
    }

    // Check against other objects
    for (const obj of canvasObjects) {
      const objectCenter = obj.getCenterPoint();
      const objectLeft = objectCenter.x;
      const objectTop = objectCenter.y;
      const objectWidth = obj.getScaledWidth();
      const objectHeight = obj.getScaledHeight();

      // Object center alignment
      if (isInRange(activeObjectLeft, objectLeft)) {
        verticalLines.push({
          x: objectLeft,
          y1: Math.min(objectTop - objectHeight / 2, activeObjectTop - activeObjectHeight / 2) - aligningLineOffset,
          y2: Math.max(objectTop + objectHeight / 2, activeObjectTop + activeObjectHeight / 2) + aligningLineOffset,
        });
        if (!isScaling) {
          activeObject.setPositionByOrigin({ x: objectLeft, y: activeObjectTop }, "center", "center");
        }
      }

      if (isInRange(activeObjectTop, objectTop)) {
        horizontalLines.push({
          y: objectTop,
          x1: Math.min(objectLeft - objectWidth / 2, activeObjectLeft - activeObjectWidth / 2) - aligningLineOffset,
          x2: Math.max(objectLeft + objectWidth / 2, activeObjectLeft + activeObjectWidth / 2) + aligningLineOffset,
        });
        if (!isScaling) {
          activeObject.setPositionByOrigin({ x: activeObjectLeft, y: objectTop }, "center", "center");
        }
      }

      // Edge alignment
      const objLeft = objectLeft - objectWidth / 2;
      const objRight = objectLeft + objectWidth / 2;
      const objTop = objectTop - objectHeight / 2;
      const objBottom = objectTop + objectHeight / 2;

      if (isInRange(activeLeft, objLeft)) {
        verticalLines.push({
          x: objLeft,
          y1: Math.min(objTop, activeTop) - aligningLineOffset,
          y2: Math.max(objBottom, activeBottom) + aligningLineOffset,
        });
      }
      if (isInRange(activeRight, objRight)) {
        verticalLines.push({
          x: objRight,
          y1: Math.min(objTop, activeTop) - aligningLineOffset,
          y2: Math.max(objBottom, activeBottom) + aligningLineOffset,
        });
      }
      if (isInRange(activeLeft, objRight)) {
        verticalLines.push({
          x: objRight,
          y1: Math.min(objTop, activeTop) - aligningLineOffset,
          y2: Math.max(objBottom, activeBottom) + aligningLineOffset,
        });
      }
      if (isInRange(activeRight, objLeft)) {
        verticalLines.push({
          x: objLeft,
          y1: Math.min(objTop, activeTop) - aligningLineOffset,
          y2: Math.max(objBottom, activeBottom) + aligningLineOffset,
        });
      }

      if (isInRange(activeTop, objTop)) {
        horizontalLines.push({
          y: objTop,
          x1: Math.min(objLeft, activeLeft) - aligningLineOffset,
          x2: Math.max(objRight, activeRight) + aligningLineOffset,
        });
      }
      if (isInRange(activeBottom, objBottom)) {
        horizontalLines.push({
          y: objBottom,
          x1: Math.min(objLeft, activeLeft) - aligningLineOffset,
          x2: Math.max(objRight, activeRight) + aligningLineOffset,
        });
      }
      if (isInRange(activeTop, objBottom)) {
        horizontalLines.push({
          y: objBottom,
          x1: Math.min(objLeft, activeLeft) - aligningLineOffset,
          x2: Math.max(objRight, activeRight) + aligningLineOffset,
        });
      }
      if (isInRange(activeBottom, objTop)) {
        horizontalLines.push({
          y: objTop,
          x1: Math.min(objLeft, activeLeft) - aligningLineOffset,
          x2: Math.max(objRight, activeRight) + aligningLineOffset,
        });
      }

      // Size matching during scaling
      if (isScaling) {
        if (isInRange(activeObjectWidth, objectWidth)) {
          // Width matches - show indicator
        }
        if (isInRange(activeObjectHeight, objectHeight)) {
          // Height matches - show indicator
        }
      }
    }

    canvas.requestRenderAll();
  }

  canvas.on("object:moving", (e: any) => {
    showSizeLabel = false;
    calculateGuidelines(e.target, false);
  });

  canvas.on("object:scaling", (e: any) => {
    showSizeLabel = true;
    activeLabelObject = e.target;
    calculateGuidelines(e.target, true);
  });

  canvas.on("before:render", () => {
    try {
      canvas.clearContext(canvas.contextTop);
    } catch (e) {
      // Ignore errors
    }
  });

  canvas.on("after:render", () => {
    for (const line of verticalLines) {
      drawVerticalLine(line);
    }
    for (const line of horizontalLines) {
      drawHorizontalLine(line);
    }
    // Draw size label during scaling
    if (showSizeLabel && activeLabelObject) {
      drawSizeLabel(activeLabelObject);
    }
  });

  canvas.on("mouse:up", () => {
    verticalLines = [];
    horizontalLines = [];
    showSizeLabel = false;
    activeLabelObject = null;
    canvas.requestRenderAll();
  });
}

// Calculate content bounds (bounding box of all objects) in canvas coordinates
function getContentBounds(canvas: any): { left: number; top: number; width: number; height: number } | null {
  const objects = canvas.getObjects();

  if (objects.length === 0) {
    return null;
  }

  // Temporarily reset viewport transform to get absolute (canvas) coordinates
  // This prevents zoom/pan from affecting the export bounds
  const savedVpt = canvas.viewportTransform.slice();
  canvas.viewportTransform = [1, 0, 0, 1, 0, 0];

  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;

  // Include all objects (including the main image which is now an object)
  for (const obj of objects) {
    const bounds = obj.getBoundingRect();
    minX = Math.min(minX, bounds.left);
    minY = Math.min(minY, bounds.top);
    maxX = Math.max(maxX, bounds.left + bounds.width);
    maxY = Math.max(maxY, bounds.top + bounds.height);
  }

  // Restore viewport transform
  canvas.viewportTransform = savedVpt;

  if (minX === Infinity || minY === Infinity) {
    return null;
  }

  return {
    left: Math.max(0, minX),
    top: Math.max(0, minY),
    width: maxX - Math.max(0, minX),
    height: maxY - Math.max(0, minY),
  };
}

// Shared fabric object style constants
const FABRIC_OBJECT_STYLE = {
  hasBorders: false,
  borderColor: "",
  borderScaleFactor: 0,
  hasControls: true,
  cornerColor: "rgba(0, 120, 255, 0.9)",
  cornerStrokeColor: "white",
  cornerSize: 10,
  cornerStyle: "circle",
  transparentCorners: false,
} as const;

// Apply consistent selection styles to a fabric object
function applyFabricObjectStyles(obj: any) {
  Object.assign(obj, FABRIC_OBJECT_STYLE);
  if (obj.type === "image") {
    obj.stroke = "";
    obj.strokeWidth = 0;
    obj.strokeUniform = true;
  }
  obj.setCoords();
}

export function ImageEditor({
  src,
  width = "100%",
  height = 500,
  initialTool = "select",
  initialSettings,
  toolbar,
  plugins = [],
  onSave,
  onReady,
  onChange,
  className = "",
  readOnly = false,
  droppedImages = [],
  onDroppedImagesChange,
}: ImageEditorProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const canvasContainerRef = useRef<HTMLDivElement>(null);
  const [activeTool, setActiveTool] = useState<ToolType | string>(initialTool);
  const [isImageLoading, setIsImageLoading] = useState(false);
  const [isDragOver, setIsDragOver] = useState(false);
  const [isPanning, setIsPanning] = useState(false);
  const [isSpaceDown, setIsSpaceDown] = useState(false);

  // Selected object size for display
  const [selectedObjectSize, setSelectedObjectSize] = useState<{ width: number; height: number } | null>(null);

  // Track canvas container dimensions
  const [canvasSize, setCanvasSize] = useState({ width: 800, height: 600 });

  // Store previous tool for space key pan
  const previousToolRef = useRef<ToolType | string>(initialTool);

  // Ref to keep api always up-to-date for external consumers
  const apiRef = useRef<ImageEditorAPI | null>(null);

  // Pan state
  const lastPosRef = useRef({ x: 0, y: 0 });
  const isSpaceDownRef = useRef(false);
  const guidelinesInitializedRef = useRef(false);
  const isPanningRef = useRef(false);
  const activeToolRef = useRef<ToolType | string>(initialTool);

  // Settings state
  const [brushColor, setBrushColor] = useState(initialSettings?.brush?.color || "#ff0000");
  const [brushWidth, setBrushWidth] = useState(initialSettings?.brush?.width || 4);
  const [fillEnabled, setFillEnabled] = useState(initialSettings?.fill?.enabled || false);
  const [fillColor, setFillColor] = useState(initialSettings?.fill?.color || "#ffffff");

  // Proxy the image URL for CORS
  const proxiedSrc = src ? getProxiedImageUrl(src) : "";

  // Track if initial image has been loaded
  const initialImageLoadedRef = useRef(false);

  // Initialize the editor without image - we'll add it manually as a selectable object
  const editor = useImageEditor({
    imageUrl: "", // Don't load as background
    width: canvasSize.width,
    height: canvasSize.height,
    defaultColor: brushColor,
    defaultStrokeWidth: brushWidth,
    maxHistorySize: 50,
    blurIntensity: 20,
  });

  // ResizeObserver to track container size
  useEffect(() => {
    if (!canvasContainerRef.current) return;

    const resizeObserver = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (entry) {
        const { width: containerWidth, height: containerHeight } = entry.contentRect;
        if (containerWidth > 0 && containerHeight > 0) {
          setCanvasSize({ width: containerWidth, height: containerHeight });
        }
      }
    });

    resizeObserver.observe(canvasContainerRef.current);

    return () => resizeObserver.disconnect();
  }, []);

  // Resize canvas when container size changes
  useEffect(() => {
    if (editor.canvas && canvasSize.width > 0 && canvasSize.height > 0) {
      editor.canvas.setDimensions({
        width: canvasSize.width,
        height: canvasSize.height,
      });
      editor.canvas.renderAll();
    }
  }, [editor.canvas, canvasSize]);

  // Initialize alignment guidelines
  useEffect(() => {
    if (editor.canvas && !guidelinesInitializedRef.current) {
      guidelinesInitializedRef.current = true;
      initAligningGuidelines(editor.canvas);

      // Configure canvas selection styles
      editor.canvas.selectionColor = "rgba(0, 120, 255, 0.1)";
      editor.canvas.selectionBorderColor = "rgba(0, 120, 255, 0.5)";
      editor.canvas.selectionLineWidth = 1;

      // Configure default object styles - MUST assign directly to prototype
      const fabric = (window as any).fabric;
      if (fabric && fabric.Object) {
        Object.assign(fabric.Object.prototype, FABRIC_OBJECT_STYLE);
        fabric.Object.prototype.padding = 0;
        fabric.Object.prototype.rotatingPointOffset = 30;
      }

      // Apply settings to any existing objects
      editor.canvas.forEachObject((obj: any) => {
        applyFabricObjectStyles(obj);
      });
      editor.canvas.renderAll();

      // Apply settings when new objects are added
      editor.canvas.on("object:added", (e: any) => {
        const obj = e.target;
        if (obj) {
          applyFabricObjectStyles(obj);
        }
      });

      // Track selection changes to update size display
      editor.canvas.on("selection:created", (e: any) => {
        const obj = e.selected?.[0];
        if (obj) {
          setSelectedObjectSize({
            width: Math.round(obj.getScaledWidth()),
            height: Math.round(obj.getScaledHeight()),
          });
        }
      });

      editor.canvas.on("selection:updated", (e: any) => {
        const obj = e.selected?.[0];
        if (obj) {
          setSelectedObjectSize({
            width: Math.round(obj.getScaledWidth()),
            height: Math.round(obj.getScaledHeight()),
          });
        }
      });

      editor.canvas.on("selection:cleared", () => {
        setSelectedObjectSize(null);
        // Re-apply styles on all objects after deselection to prevent visual artifacts
        editor.canvas.forEachObject((obj: any) => {
          applyFabricObjectStyles(obj);
          obj.dirty = true;
        });
        editor.canvas.requestRenderAll();
      });

      // Additional safeguard: ensure styles are applied before deselection
      editor.canvas.on("before:selection:cleared", () => {
        const activeObj = editor.canvas.getActiveObject();
        if (activeObj) {
          applyFabricObjectStyles(activeObj);
          activeObj.dirty = true;
        }
      });

      editor.canvas.on("object:modified", (e: any) => {
        const obj = e.target;
        if (obj && editor.canvas.getActiveObject() === obj) {
          setSelectedObjectSize({
            width: Math.round(obj.getScaledWidth()),
            height: Math.round(obj.getScaledHeight()),
          });
        }
      });

      editor.canvas.on("object:scaling", (e: any) => {
        const obj = e.target;
        if (obj) {
          setSelectedObjectSize({
            width: Math.round(obj.getScaledWidth()),
            height: Math.round(obj.getScaledHeight()),
          });
        }
      });
    }
  }, [editor.canvas]);

  // Load initial image as a selectable fabric object
  useEffect(() => {
    if (!editor.canvas || !proxiedSrc || initialImageLoadedRef.current) return;

    initialImageLoadedRef.current = true;
    setIsImageLoading(true);

    const fabric = (window as any).fabric;
    if (fabric && fabric.Image) {
      fabric.Image.fromURL(proxiedSrc, (fabricImage: any) => {
        if (!editor.canvas) {
          setIsImageLoading(false);
          return;
        }

        const canvasWidth = editor.canvas.getWidth();
        const canvasHeight = editor.canvas.getHeight();
        const imgWidth = fabricImage.width || 100;
        const imgHeight = fabricImage.height || 100;

        // Scale image to fit within canvas with some margin
        const scale = Math.min(
          (canvasWidth * 0.9) / imgWidth,
          (canvasHeight * 0.9) / imgHeight,
          1
        );

        fabricImage.scale(scale);
        // Set position and basic properties
        fabricImage.left = (canvasWidth - imgWidth * scale) / 2;
        fabricImage.top = (canvasHeight - imgHeight * scale) / 2;
        fabricImage.selectable = true;
        fabricImage.evented = true;
        // Mark as the main/initial image
        fabricImage.isMainImage = true;
        applyFabricObjectStyles(fabricImage);

        editor.canvas.add(fabricImage);
        fabricImage.setCoords();
        editor.canvas.renderAll();
        setIsImageLoading(false);
        onChange?.();
      }, { crossOrigin: "anonymous" });
    } else {
      setIsImageLoading(false);
    }
  }, [editor.canvas, proxiedSrc, onChange]);

  // Mouse wheel zoom
  useEffect(() => {
    if (!editor.canvas) return;

    const handleWheel = (opt: any) => {
      const e = opt.e as WheelEvent;
      e.preventDefault();
      e.stopPropagation();

      const delta = e.deltaY;
      let zoom = editor.canvas.getZoom();
      zoom *= 0.999 ** delta;

      // Limit zoom range
      if (zoom > 5) zoom = 5;
      if (zoom < 0.1) zoom = 0.1;

      // Zoom to mouse pointer
      const pointer = editor.canvas.getPointer(e, true);
      editor.canvas.zoomToPoint({ x: pointer.x, y: pointer.y }, zoom);

      editor.canvas.requestRenderAll();
    };

    editor.canvas.on("mouse:wheel", handleWheel);

    return () => {
      editor.canvas.off("mouse:wheel", handleWheel);
    };
  }, [editor.canvas]);

  // Pan with spacebar + drag, middle mouse button, or hand tool
  useEffect(() => {
    if (!editor.canvas) return;

    const handleMouseDown = (opt: any) => {
      const e = opt.e as MouseEvent;

      // Middle mouse button, space + left click, or hand tool + left click
      if (e.button === 1 || (isSpaceDownRef.current && e.button === 0) || (activeToolRef.current === "hand" && e.button === 0)) {
        isPanningRef.current = true;
        setIsPanning(true);
        editor.canvas.selection = false;
        editor.canvas.discardActiveObject();
        lastPosRef.current = { x: e.clientX, y: e.clientY };
        editor.canvas.setCursor("grabbing");
      }
    };

    const handleMouseMove = (opt: any) => {
      if (!isPanningRef.current) return;

      const e = opt.e as MouseEvent;
      const vpt = editor.canvas.viewportTransform;
      if (!vpt) return;

      vpt[4] += e.clientX - lastPosRef.current.x;
      vpt[5] += e.clientY - lastPosRef.current.y;
      lastPosRef.current = { x: e.clientX, y: e.clientY };

      editor.canvas.requestRenderAll();
    };

    const handleMouseUp = () => {
      if (isPanningRef.current) {
        isPanningRef.current = false;
        setIsPanning(false);
        // Only restore selection if not in hand tool mode
        if (activeToolRef.current !== "hand") {
          editor.canvas.selection = true;
        }
        editor.canvas.setCursor(activeToolRef.current === "hand" ? "grab" : "default");
      }
    };

    editor.canvas.on("mouse:down", handleMouseDown);
    editor.canvas.on("mouse:move", handleMouseMove);
    editor.canvas.on("mouse:up", handleMouseUp);

    return () => {
      editor.canvas.off("mouse:down", handleMouseDown);
      editor.canvas.off("mouse:move", handleMouseMove);
      editor.canvas.off("mouse:up", handleMouseUp);
    };
  }, [editor.canvas]);

  // Keep activeToolRef in sync with activeTool state
  useEffect(() => {
    activeToolRef.current = activeTool;
  }, [activeTool]);

  // Sync color changes to editor
  useEffect(() => {
    editor.style.setColor(brushColor);
  }, [brushColor, editor.style]);

  // Sync stroke width changes to editor
  useEffect(() => {
    editor.style.setStrokeWidth(brushWidth);
  }, [brushWidth, editor.style]);

  // Handle tool changes
  useEffect(() => {
    if (!editor.canvas) return;

    // Disable all modes first
    editor.drawing.disable();
    editor.selection.enable();

    if (activeTool === "brush") {
      editor.drawing.enable();
    } else if (activeTool === "crop") {
      // Crop is started by handleToolAction, not here, to avoid double-start
    } else if (activeTool === "hand") {
      // Hand tool - disable selection, enable panning
      editor.selection.disable();
      editor.canvas.selection = false;
      editor.canvas.setCursor("grab");
      editor.canvas.forEachObject((obj: any) => {
        obj.set({ selectable: false, evented: false });
      });
    } else if (activeTool === "select") {
      // Re-enable selection for select tool
      editor.canvas.selection = true;
      editor.canvas.setCursor("default");
      editor.canvas.forEachObject((obj: any) => {
        obj.set({ selectable: true, evented: true });
      });
    }

    editor.canvas.renderAll();
  }, [activeTool, editor]);

  // Handle dropped images from external sources (e.g., asset browser)
  useEffect(() => {
    if (!editor.canvas || droppedImages.length === 0) return;

    // Process new images
    droppedImages.forEach((imageUrl) => {
      // Add image to canvas using fabric directly (no double-load)
      const proxiedUrl = getProxiedImageUrl(imageUrl);
      const fabric = (window as any).fabric;
      if (fabric && fabric.Image) {
        fabric.Image.fromURL(proxiedUrl, (fabricImage: any) => {
          if (!editor.canvas) return;

          // Scale image to fit within canvas
          const canvasWidth = editor.canvas.getWidth();
          const canvasHeight = editor.canvas.getHeight();
          const imgWidth = fabricImage.width || 100;
          const imgHeight = fabricImage.height || 100;

          const scale = Math.min(
            (canvasWidth * 0.5) / imgWidth,
            (canvasHeight * 0.5) / imgHeight,
            1
          );

          fabricImage.scale(scale);
          fabricImage.left = canvasWidth / 4;
          fabricImage.top = canvasHeight / 4;
          fabricImage.selectable = true;
          fabricImage.evented = true;
          applyFabricObjectStyles(fabricImage);

          editor.canvas.add(fabricImage);
          editor.canvas.setActiveObject(fabricImage);
          editor.canvas.renderAll();
          onChange?.();
        }, { crossOrigin: "anonymous" });
      }
    });

    // Clear the dropped images after processing
    if (onDroppedImagesChange && droppedImages.length > 0) {
      onDroppedImagesChange([]);
    }
  }, [editor.canvas, droppedImages, onDroppedImagesChange, onChange]);

  // Drag and drop handlers for the canvas
  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(true);
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);
  }, []);

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);

    // Get dropped image URL
    const imageUrl = e.dataTransfer.getData("text/plain");
    if (!imageUrl) return;

    // Add to dropped images for processing
    if (onDroppedImagesChange) {
      onDroppedImagesChange([imageUrl]);
    }
  }, [onDroppedImagesChange]);

  // Export only visible content bounds
  const exportContentBounds = useCallback(async (): Promise<string> => {
    if (!editor.canvas) return "";

    const bounds = getContentBounds(editor.canvas);
    if (!bounds) {
      // If no content, export entire canvas
      return editor.exportToDataURL("png", 1) || "";
    }

    // Use fabric's toDataURL with cropping
    const dataUrl = editor.canvas.toDataURL({
      format: "png",
      quality: 1,
      left: bounds.left,
      top: bounds.top,
      width: bounds.width,
      height: bounds.height,
      multiplier: 1,
    });

    return dataUrl;
  }, [editor]);

  // Create API object for external access
  const api: ImageEditorAPI = {
    getCanvas: () => editor.canvas,
    getActiveTool: () => activeTool,
    setActiveTool,
    getSettings: () => ({
      brush: { color: brushColor, width: brushWidth, opacity: 1 },
      fill: { color: fillColor, enabled: fillEnabled, opacity: 0.5 },
      text: {
        fontFamily: "Arial",
        fontSize: 24,
        fontWeight: "normal",
        fontStyle: "normal",
        color: brushColor,
      },
      mosaic: { blockSize: 10, type: "pixelate" },
      viewport: { zoom: editor.zoom.level, minZoom: 0.1, maxZoom: 5 },
    }),
    updateSettings: (settings) => {
      if (settings.brush?.color) setBrushColor(settings.brush.color);
      if (settings.brush?.width) setBrushWidth(settings.brush.width);
      if (settings.fill?.color) setFillColor(settings.fill.color);
      if (settings.fill?.enabled !== undefined) setFillEnabled(settings.fill.enabled);
    },
    undo: () => editor.history.undo(),
    redo: () => editor.history.redo(),
    canUndo: () => editor.history.canUndo,
    canRedo: () => editor.history.canRedo,
    clear: () => {
      // Clear all objects except the main image
      if (editor.canvas) {
        const objects = editor.canvas.getObjects();
        objects.forEach((obj: any) => {
          // Keep the main/initial image
          if (!obj.isMainImage) {
            editor.canvas?.remove(obj);
          }
        });
        editor.canvas.renderAll();
      }
    },
    deleteSelected: () => editor.selection.deleteSelected(),
    toDataURL: (format = "png", quality = 1) => {
      return editor.exportToDataURL(format, quality) || "";
    },
    download: async (filename = "image", format = "png") => {
      // Export only content bounds
      const dataUrl = await exportContentBounds();
      if (!dataUrl) return;

      const link = document.createElement("a");
      link.href = dataUrl;
      link.download = `${filename}.${format}`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    },
    exportContentBounds,
    loadImage: async (newSrc: string) => {
      if (!editor.canvas) return;
      const proxiedUrl = getProxiedImageUrl(newSrc);
      const fabric = (window as any).fabric;
      if (fabric && fabric.Image) {
        fabric.Image.fromURL(proxiedUrl, (fabricImage: any) => {
          if (!editor.canvas) return;
          const canvasWidth = editor.canvas.getWidth();
          const canvasHeight = editor.canvas.getHeight();
          const imgWidth = fabricImage.width || 100;
          const imgHeight = fabricImage.height || 100;
          const scale = Math.min(
            (canvasWidth * 0.9) / imgWidth,
            (canvasHeight * 0.9) / imgHeight,
            1
          );
          fabricImage.scale(scale);
          fabricImage.left = (canvasWidth - imgWidth * scale) / 2;
          fabricImage.top = (canvasHeight - imgHeight * scale) / 2;
          fabricImage.selectable = true;
          fabricImage.evented = true;
          applyFabricObjectStyles(fabricImage);
          editor.canvas.add(fabricImage);
          editor.canvas.renderAll();
        }, { crossOrigin: "anonymous" });
      }
    },
    getZoom: () => editor.zoom.level,
    setZoom: (zoom: number) => {
      if (editor.canvas) {
        const center = editor.canvas.getCenter();
        editor.canvas.zoomToPoint({ x: center.left, y: center.top }, zoom);
        editor.canvas.requestRenderAll();
      }
    },
    zoomIn: () => editor.zoom.in(),
    zoomOut: () => editor.zoom.out(),
    resetView: () => {
      if (editor.canvas) {
        // Reset zoom and pan
        editor.canvas.setViewportTransform([1, 0, 0, 1, 0, 0]);
        editor.canvas.requestRenderAll();
      }
    },
    toggleFill: () => setFillEnabled((prev) => !prev),
    getSelectedSize: () => {
      if (!editor.canvas) return null;
      const obj = editor.canvas.getActiveObject();
      if (!obj) return null;
      return {
        width: Math.round(obj.getScaledWidth()),
        height: Math.round(obj.getScaledHeight()),
      };
    },
    setSelectedSize: (width: number, height: number, lockAspectRatio = false) => {
      if (!editor.canvas) return;
      const obj = editor.canvas.getActiveObject();
      if (!obj) return;

      const currentWidth = obj.getScaledWidth();
      const currentHeight = obj.getScaledHeight();

      if (lockAspectRatio) {
        // Calculate scale based on which dimension changed more
        const widthRatio = width / currentWidth;
        const heightRatio = height / currentHeight;

        // Use the ratio that differs more from 1
        const useWidthRatio = Math.abs(widthRatio - 1) > Math.abs(heightRatio - 1);
        const scale = useWidthRatio ? widthRatio : heightRatio;

        obj.scale(obj.scaleX * scale);
      } else {
        // Set width and height independently
        const newScaleX = width / (obj.width || 1);
        const newScaleY = height / (obj.height || 1);
        obj.set({ scaleX: newScaleX, scaleY: newScaleY });
      }

      obj.setCoords();
      editor.canvas.renderAll();

      // Update size display
      setSelectedObjectSize({
        width: Math.round(obj.getScaledWidth()),
        height: Math.round(obj.getScaledHeight()),
      });
    },
    registerPlugin: () => {},
    unregisterPlugin: () => {},
  };

  // Keep apiRef in sync so external consumers always get latest state
  apiRef.current = api;

  // Notify when ready - call onReady when canvas is available
  // Note: We don't check editor.hasImage because we load images manually as fabric objects
  useEffect(() => {
    if (editor.canvas && onReady) {
      onReady(api);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editor.canvas, onReady]);

  // Handle tool actions
  const handleToolAction = useCallback(
    (tool: ToolType | string) => {
      setActiveTool(tool);

      // Handle immediate actions
      switch (tool) {
        case "rectangle":
          editor.shapes.add("rectangle");
          setActiveTool("select");
          break;
        case "circle":
          editor.shapes.add("circle");
          setActiveTool("select");
          break;
        case "text":
          editor.text.add("双击编辑文字");
          setActiveTool("select");
          break;
        case "mosaic":
          editor.blur.add();
          setActiveTool("select");
          break;
        case "crop":
          editor.crop.start();
          break;
      }

      onChange?.();
    },
    [editor, onChange]
  );

  // Keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (readOnly) return;

      // Track space key for panning
      if (e.code === "Space" && !isSpaceDownRef.current) {
        isSpaceDownRef.current = true;
        setIsSpaceDown(true);
        if (editor.canvas) {
          editor.canvas.setCursor("grab");
        }
        e.preventDefault();
        return;
      }

      // Check if typing in text input
      if (
        document.activeElement?.tagName === "INPUT" ||
        document.activeElement?.tagName === "TEXTAREA"
      ) {
        return;
      }

      const key = e.key.toLowerCase();
      const ctrlOrCmd = e.ctrlKey || e.metaKey;

      // Tool shortcuts
      if (!ctrlOrCmd) {
        switch (key) {
          case "v":
            handleToolAction("select");
            break;
          case "h":
            handleToolAction("hand");
            break;
          case "b":
            handleToolAction("brush");
            break;
          case "r":
            handleToolAction("rectangle");
            break;
          case "c":
            handleToolAction("circle");
            break;
          case "t":
            handleToolAction("text");
            break;
          case "m":
            handleToolAction("mosaic");
            break;
          case "x":
            handleToolAction("crop");
            break;
          case "=":
          case "+":
            editor.zoom.in();
            break;
          case "-":
            editor.zoom.out();
            break;
          case "0":
            // Reset view
            if (editor.canvas) {
              editor.canvas.setViewportTransform([1, 0, 0, 1, 0, 0]);
              editor.canvas.requestRenderAll();
            }
            break;
        }
      }

      // Action shortcuts
      if (ctrlOrCmd) {
        switch (key) {
          case "z":
            e.preventDefault();
            if (e.shiftKey) {
              editor.history.redo();
            } else {
              editor.history.undo();
            }
            break;
          case "y":
            e.preventDefault();
            editor.history.redo();
            break;
          case "s":
            e.preventDefault();
            if (onSave) {
              exportContentBounds().then((dataUrl) => {
                if (dataUrl) {
                  onSave(dataUrl);
                }
              });
            }
            break;
          case "0":
            e.preventDefault();
            // Reset view
            if (editor.canvas) {
              editor.canvas.setViewportTransform([1, 0, 0, 1, 0, 0]);
              editor.canvas.requestRenderAll();
            }
            break;
          case "=":
          case "+":
            e.preventDefault();
            editor.zoom.in();
            break;
          case "-":
            e.preventDefault();
            editor.zoom.out();
            break;
        }
      }

      // Delete key
      if (key === "delete" || key === "backspace") {
        if (
          document.activeElement?.tagName !== "INPUT" &&
          document.activeElement?.tagName !== "TEXTAREA"
        ) {
          editor.selection.deleteSelected();
        }
      }
    };

    const handleKeyUp = (e: KeyboardEvent) => {
      if (e.code === "Space") {
        isSpaceDownRef.current = false;
        setIsSpaceDown(false);
        if (editor.canvas && !isPanning) {
          editor.canvas.setCursor("default");
        }
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    window.addEventListener("keyup", handleKeyUp);
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
      window.removeEventListener("keyup", handleKeyUp);
    };
  }, [readOnly, editor, handleToolAction, onSave, isPanning, exportContentBounds]);

  // Store editor ref for cleanup
  const editorRef = useRef(editor);
  editorRef.current = editor;

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      editorRef.current.dispose();
    };
  }, []);

  const toolbarPosition = toolbar?.position || "top";

  // Handle crop actions
  const handleCropApply = useCallback(() => {
    editor.crop.apply();
    setActiveTool("select");
    onChange?.();
  }, [editor, onChange]);

  const handleCropCancel = useCallback(() => {
    editor.crop.cancel();
    setActiveTool("select");
  }, [editor]);

  // Handle save with content bounds
  const handleSave = useCallback(async () => {
    const dataUrl = await exportContentBounds();
    if (dataUrl && onSave) {
      onSave(dataUrl);
    }
  }, [exportContentBounds, onSave]);

  // Handle download with content bounds
  const handleDownload = useCallback(async () => {
    const dataUrl = await exportContentBounds();
    if (!dataUrl) return;

    const link = document.createElement("a");
    link.href = dataUrl;
    link.download = "image.png";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }, [exportContentBounds]);

  return (
    <div
      ref={containerRef}
      className={`relative flex overflow-hidden rounded-xl border border-border bg-background ${
        toolbarPosition === "left" || toolbarPosition === "right"
          ? "flex-row"
          : "flex-col"
      } ${className}`}
      style={{ width, height }}
    >
      {/* Toolbar - Top or Left */}
      {(toolbarPosition === "top" || toolbarPosition === "left") && !readOnly && (
        <div
          className={`shrink-0 border-border ${
            toolbarPosition === "top" ? "border-b" : "border-r"
          } p-1`}
        >
          <Toolbar
            activeTool={activeTool}
            onToolChange={handleToolAction}
            brushColor={brushColor}
            onBrushColorChange={setBrushColor}
            brushWidth={brushWidth}
            onBrushWidthChange={setBrushWidth}
            fillEnabled={fillEnabled}
            onFillToggle={() => setFillEnabled((prev) => !prev)}
            fillColor={fillColor}
            onFillColorChange={setFillColor}
            zoom={editor.zoom.level}
            onZoomIn={() => editor.zoom.in()}
            onZoomOut={() => editor.zoom.out()}
            onZoomTo={(z) => api.setZoom(z)}
            onUndo={() => editor.history.undo()}
            onRedo={() => editor.history.redo()}
            canUndo={editor.history.canUndo}
            canRedo={editor.history.canRedo}
            onDelete={() => editor.selection.deleteSelected()}
            onDownload={handleDownload}
            position={toolbarPosition}
            selectedSize={selectedObjectSize}
            onSizeChange={(width, height, lockAspectRatio) => {
              api.setSelectedSize(width, height, lockAspectRatio);
            }}
            isCropActive={editor.crop.isActive}
            onCropApply={handleCropApply}
            onCropCancel={handleCropCancel}
          />
        </div>
      )}

      {/* Canvas Container */}
      <div
        ref={canvasContainerRef}
        className={`relative min-h-[200px] min-w-[200px] flex-1 overflow-hidden bg-muted/5 transition-colors ${
          isDragOver ? "bg-accent/10 ring-2 ring-accent ring-inset" : ""
        } ${isPanning ? "cursor-grabbing" : ""}`}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
      >
        <canvas ref={editor.canvasRef} className="absolute inset-0" />

        {/* Drag over overlay */}
        {isDragOver && (
          <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-accent/5">
            <div className="rounded-xl border-2 border-dashed border-accent bg-background/80 px-6 py-4 text-center shadow-lg">
              <p className="text-sm font-medium text-accent">释放以添加图片</p>
            </div>
          </div>
        )}

        {/* Loading overlay */}
        {isImageLoading && (
          <div className="absolute inset-0 flex items-center justify-center bg-background/80">
            <div className="text-sm text-muted">加载图片中...</div>
          </div>
        )}

        {/* Crop overlay buttons */}
        {editor.crop.isActive && (
          <div className="absolute bottom-4 left-1/2 flex -translate-x-1/2 gap-2">
            <Button
              variant="primary"
              size="sm"
              onPress={handleCropApply}
              className="shadow-lg"
            >
              应用裁剪
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onPress={handleCropCancel}
              className="shadow-lg"
            >
              取消
            </Button>
          </div>
        )}

        {/* Pan hint */}
        {isSpaceDown && !isPanning && (
          <div className="pointer-events-none absolute left-2 top-2 rounded bg-black/50 px-2 py-1 text-xs text-white">
            按住拖拽平移
          </div>
        )}
      </div>

      {/* Toolbar - Bottom or Right */}
      {(toolbarPosition === "bottom" || toolbarPosition === "right") && !readOnly && (
        <div
          className={`shrink-0 border-border ${
            toolbarPosition === "bottom" ? "border-t" : "border-l"
          } p-1`}
        >
          <Toolbar
            activeTool={activeTool}
            onToolChange={handleToolAction}
            brushColor={brushColor}
            onBrushColorChange={setBrushColor}
            brushWidth={brushWidth}
            onBrushWidthChange={setBrushWidth}
            fillEnabled={fillEnabled}
            onFillToggle={() => setFillEnabled((prev) => !prev)}
            fillColor={fillColor}
            onFillColorChange={setFillColor}
            zoom={editor.zoom.level}
            onZoomIn={() => editor.zoom.in()}
            onZoomOut={() => editor.zoom.out()}
            onZoomTo={(z) => api.setZoom(z)}
            onUndo={() => editor.history.undo()}
            onRedo={() => editor.history.redo()}
            canUndo={editor.history.canUndo}
            canRedo={editor.history.canRedo}
            onDelete={() => editor.selection.deleteSelected()}
            onDownload={handleDownload}
            position={toolbarPosition}
            selectedSize={selectedObjectSize}
            onSizeChange={(width, height, lockAspectRatio) => {
              api.setSelectedSize(width, height, lockAspectRatio);
            }}
            isCropActive={editor.crop.isActive}
            onCropApply={handleCropApply}
            onCropCancel={handleCropCancel}
          />
        </div>
      )}
    </div>
  );
}

export default ImageEditor;
