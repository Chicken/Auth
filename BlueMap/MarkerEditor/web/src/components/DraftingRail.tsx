import { DrawingState, MARKER_LABELS } from "./editorModel";

interface DraftingRailProps {
  drawing: DrawingState;
  onUndo: () => void;
  onAddHole: () => void;
  onFinish: () => void;
  onCancel: () => void;
}

export function DraftingRail({ drawing, onUndo, onAddHole, onFinish, onCancel }: DraftingRailProps) {
  const count = drawing.kind === "position"
    ? "One point"
    : drawing.type === "line"
      ? `${drawing.line.length} points`
      : `${drawing.shape.length} outer · ${drawing.holes.reduce((total, hole) => total + hole.length, 0)} hole vertices`;
  return <section class="me-drafting-rail" aria-label="Map drawing controls">
    <div class="me-draft-status">
      <span class="me-draft-cursor" aria-hidden="true">⌖</span>
      <div><strong>{drawing.kind === "position" ? "Pick a map position" : `Draw ${MARKER_LABELS[drawing.type].toLowerCase()}`}</strong><span>{drawing.kind === "position" ? "Click or tap the terrain." : "Click or tap to add vertices. Finish from this rail."}</span></div>
      <output>{count}</output>
    </div>
    <div class="me-draft-actions">
      {drawing.kind === "geometry" && <button class="me-button is-quiet" type="button" onClick={onUndo} disabled={drawing.type === "line" ? !drawing.line.length : !drawing.shape.length && !drawing.holes.length}>Undo</button>}
      {drawing.kind === "geometry" && drawing.type !== "line" && <button class="me-button is-quiet" type="button" onClick={onAddHole}>New hole</button>}
      {drawing.kind === "geometry" && <button class="me-button is-primary" type="button" onClick={onFinish}>Finish</button>}
      <button class="me-button" type="button" onClick={onCancel}>Cancel</button>
    </div>
  </section>;
}
