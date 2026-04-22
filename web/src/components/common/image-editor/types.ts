/* eslint-disable @typescript-eslint/no-explicit-any */

/**
 * Fabric Canvas type (from @ozdemircibaris/react-image-editor)
 */
export type FabricCanvas = any;

/**
 * Available tool types for the image editor
 */
export type ToolType =
  | "select"
  | "hand"
  | "brush"
  | "rectangle"
  | "circle"
  | "text"
  | "mosaic"
  | "crop";

/**
 * Tool configuration
 */
export interface ToolConfig {
  /** Tool identifier */
  id: ToolType | string;
  /** Display name */
  name: string;
  /** Icon component or element */
  icon: React.ReactNode;
  /** Keyboard shortcut */
  shortcut?: string;
  /** Whether the tool is enabled */
  enabled?: boolean;
  /** Tool-specific options */
  options?: Record<string, unknown>;
}

/**
 * Brush/stroke settings
 */
export interface BrushSettings {
  /** Stroke color */
  color: string;
  /** Stroke width */
  width: number;
  /** Opacity (0-1) */
  opacity: number;
}

/**
 * Fill settings for shapes
 */
export interface FillSettings {
  /** Fill color */
  color: string;
  /** Whether to fill the shape */
  enabled: boolean;
  /** Opacity (0-1) */
  opacity: number;
}

/**
 * Text settings
 */
export interface TextSettings {
  /** Font family */
  fontFamily: string;
  /** Font size */
  fontSize: number;
  /** Font weight */
  fontWeight: "normal" | "bold";
  /** Font style */
  fontStyle: "normal" | "italic";
  /** Text color */
  color: string;
}

/**
 * Mosaic settings
 */
export interface MosaicSettings {
  /** Block size for pixelation */
  blockSize: number;
  /** Mosaic type */
  type: "pixelate" | "blur";
}

/**
 * Zoom/viewport settings
 */
export interface ViewportSettings {
  /** Current zoom level (1 = 100%) */
  zoom: number;
  /** Minimum zoom level */
  minZoom: number;
  /** Maximum zoom level */
  maxZoom: number;
}

/**
 * Editor settings
 */
export interface EditorSettings {
  brush: BrushSettings;
  fill: FillSettings;
  text: TextSettings;
  mosaic: MosaicSettings;
  viewport: ViewportSettings;
}

/**
 * History state for undo/redo
 */
export interface HistoryState {
  /** JSON representation of canvas state */
  json: string;
  /** Timestamp */
  timestamp: number;
}

/**
 * Plugin interface for extending the editor
 */
export interface ImageEditorPlugin {
  /** Unique plugin identifier */
  id: string;
  /** Plugin name */
  name: string;
  /** Plugin version */
  version?: string;
  /** Called when plugin is initialized */
  onInit?: (canvas: FabricCanvas, editor: ImageEditorAPI) => void;
  /** Called when plugin is destroyed */
  onDestroy?: () => void;
  /** Custom tools provided by the plugin */
  tools?: ToolConfig[];
  /** Custom toolbar items */
  toolbarItems?: ToolbarItem[];
  /** Called when active tool changes */
  onToolChange?: (tool: ToolType | string) => void;
  /** Called when canvas changes */
  onCanvasChange?: (canvas: FabricCanvas) => void;
}

/**
 * Toolbar item configuration
 */
export interface ToolbarItem {
  /** Item type */
  type: "tool" | "action" | "separator" | "custom";
  /** Tool or action ID */
  id?: string;
  /** Display name */
  name?: string;
  /** Icon */
  icon?: React.ReactNode;
  /** Click handler for actions */
  onClick?: () => void;
  /** Custom render function */
  render?: (editor: ImageEditorAPI) => React.ReactNode;
  /** Position in toolbar */
  position?: "left" | "center" | "right";
}

/**
 * Toolbar configuration
 */
export interface ToolbarConfig {
  /** Position of the toolbar */
  position: "top" | "bottom" | "left" | "right";
  /** Items to display in the toolbar */
  items: ToolbarItem[];
  /** Whether to show tool options panel */
  showOptions?: boolean;
}

/**
 * Image editor API exposed to plugins and external code
 */
export interface ImageEditorAPI {
  /** Get the fabric canvas instance */
  getCanvas: () => FabricCanvas | null;
  /** Get current active tool */
  getActiveTool: () => ToolType | string;
  /** Set active tool */
  setActiveTool: (tool: ToolType | string) => void;
  /** Get editor settings */
  getSettings: () => EditorSettings;
  /** Update editor settings */
  updateSettings: (settings: Partial<EditorSettings>) => void;
  /** Undo last action */
  undo: () => void;
  /** Redo last undone action */
  redo: () => void;
  /** Check if can undo */
  canUndo: () => boolean;
  /** Check if can redo */
  canRedo: () => boolean;
  /** Clear the canvas */
  clear: () => void;
  /** Delete selected objects */
  deleteSelected: () => void;
  /** Export canvas as image */
  toDataURL: (format?: "png" | "jpeg" | "webp", quality?: number) => string;
  /** Download canvas as image */
  download: (filename?: string, format?: "png" | "jpeg" | "webp") => void;
  /** Export only the content bounds (bounding box of all objects) as data URL */
  exportContentBounds: () => Promise<string>;
  /** Load image onto canvas */
  loadImage: (src: string) => Promise<void>;
  /** Register a plugin */
  registerPlugin: (plugin: ImageEditorPlugin) => void;
  /** Unregister a plugin */
  unregisterPlugin: (pluginId: string) => void;
  /** Get current zoom level */
  getZoom: () => number;
  /** Set zoom level */
  setZoom: (zoom: number) => void;
  /** Zoom in */
  zoomIn: () => void;
  /** Zoom out */
  zoomOut: () => void;
  /** Reset view (fit image to canvas) */
  resetView: () => void;
  /** Toggle fill for shapes */
  toggleFill: () => void;
  /** Get selected object size */
  getSelectedSize: () => { width: number; height: number } | null;
  /** Set selected object size with optional aspect ratio lock */
  setSelectedSize: (width: number, height: number, lockAspectRatio?: boolean) => void;
}

/**
 * Props for the ImageEditor component
 */
export interface ImageEditorProps {
  /** Image source to edit */
  src?: string;
  /** Width of the editor */
  width?: number | string;
  /** Height of the editor */
  height?: number | string;
  /** Initial tool */
  initialTool?: ToolType;
  /** Initial settings */
  initialSettings?: Partial<EditorSettings>;
  /** Toolbar configuration */
  toolbar?: Partial<ToolbarConfig>;
  /** Plugins to load */
  plugins?: ImageEditorPlugin[];
  /** Callback when image is saved/exported */
  onSave?: (dataUrl: string) => void;
  /** Callback when editor is ready */
  onReady?: (api: ImageEditorAPI) => void;
  /** Callback when canvas changes */
  onChange?: () => void;
  /** Custom class name */
  className?: string;
  /** Whether the editor is read-only */
  readOnly?: boolean;
  /** Images dropped from external source to add to canvas */
  droppedImages?: string[];
  /** Callback when dropped images are processed */
  onDroppedImagesChange?: (images: string[]) => void;
}

/**
 * Props for the ImageEditorModal component
 */
export interface ImageEditorModalProps {
  /** Whether the modal is open */
  isOpen: boolean;
  /** Callback when modal open state changes */
  onOpenChange?: (isOpen: boolean) => void;
  /** Main image source to edit */
  src?: string;
  /** Reference images to show in the asset browser */
  refImages?: string[];
  /** Entity type for filtering assets */
  entityType?: "CHARACTER" | "SCENE" | "PROP" | "STORYBOARD";
  /** Entity ID for filtering assets */
  entityId?: string;
  /** Script ID for loading assets */
  scriptId?: string;
  /** Workspace ID for API calls */
  workspaceId?: string;
  /** Callback when save is clicked, receives the edited image data URL */
  onSave?: (dataUrl: string) => Promise<void>;
  /** Callback when cancel is clicked */
  onCancel?: () => void;
  /** Modal title */
  title?: string;
}

/**
 * Default editor settings
 */
export const DEFAULT_SETTINGS: EditorSettings = {
  brush: {
    color: "#ff0000",
    width: 4,
    opacity: 1,
  },
  fill: {
    color: "#ffffff",
    enabled: false,
    opacity: 0.5,
  },
  text: {
    fontFamily: "Arial",
    fontSize: 24,
    fontWeight: "normal",
    fontStyle: "normal",
    color: "#000000",
  },
  mosaic: {
    blockSize: 10,
    type: "pixelate",
  },
  viewport: {
    zoom: 1,
    minZoom: 0.1,
    maxZoom: 5,
  },
};

/**
 * Color presets
 */
export const COLOR_PRESETS = [
  "#000000",
  "#ffffff",
  "#ff0000",
  "#ff6b00",
  "#ffeb3b",
  "#4caf50",
  "#2196f3",
  "#9c27b0",
  "#e91e63",
  "#795548",
];

/**
 * Stroke width presets
 */
export const STROKE_WIDTH_PRESETS = [2, 4, 6, 8, 12, 16];

/**
 * Zoom presets
 */
export const ZOOM_PRESETS = [0.25, 0.5, 0.75, 1, 1.5, 2, 3];
