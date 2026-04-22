/**
 * Image Editor Component
 *
 * A comprehensive image editing component built on @ozdemircibaris/react-image-editor
 * with the following features:
 * - Drawing tools: brush, rectangle, circle, text, blur
 * - Crop tool with apply/cancel
 * - Actions: undo, redo, delete, download
 * - Object manipulation: select, move, resize, delete shapes
 * - Zoom and pan support
 * - Configurable toolbar
 * - Full-screen modal with asset browser
 * - Drag and drop images from asset browser to canvas
 *
 * @example Basic usage
 * ```tsx
 * import { ImageEditor } from "@/components/common/image-editor";
 *
 * function MyComponent() {
 *   return (
 *     <ImageEditor
 *       src="/path/to/image.jpg"
 *       width="100%"
 *       height={600}
 *       onSave={(dataUrl) => console.log("Saved:", dataUrl)}
 *       onReady={(api) => console.log("Editor ready:", api)}
 *     />
 *   );
 * }
 * ```
 *
 * @example Full-screen modal with asset browser
 * ```tsx
 * import { ImageEditorModal } from "@/components/common/image-editor";
 *
 * function MyComponent() {
 *   const [isOpen, setIsOpen] = useState(false);
 *
 *   return (
 *     <ImageEditorModal
 *       isOpen={isOpen}
 *       onOpenChange={setIsOpen}
 *       src="/path/to/image.jpg"
 *       refImages={["/ref1.jpg", "/ref2.jpg"]}
 *       scriptId="script-123"
 *       workspaceId="workspace-456"
 *       onSave={async (dataUrl) => {
 *         // Upload the edited image
 *       }}
 *     />
 *   );
 * }
 * ```
 */

export { ImageEditor } from "./image-editor";
export { ImageEditorModal } from "./image-editor-modal";
export { AssetBrowser } from "./asset-browser";
export { Toolbar } from "./toolbar";
export * from "./types";
