import { MarkerModel, MarkerType, Vec2, Vec3, cloneMarker, newMarker } from "../types";

export interface DrawingState {
  kind: "position" | "geometry";
  type: MarkerType;
  line: Vec3[];
  shape: Vec2[];
  holes: Vec2[][];
  activeRing: number;
  initialY?: number;
}

export const MARKER_LABELS: Record<MarkerType, string> = {
  poi: "POI",
  html: "HTML",
  line: "Line",
  shape: "Shape",
  extrude: "Extrude",
};

export function markerTypeIcon(type: MarkerType): string {
  if (type === "poi") return "●";
  if (type === "html") return "<>";
  if (type === "line") return "╱";
  if (type === "shape") return "◇";
  return "⬡";
}

export function centerOf(points: Vec3[]): Vec3 {
  if (!points.length) return { x: 0, y: 0, z: 0 };
  const xs = points.map(point => point.x);
  const ys = points.map(point => point.y);
  const zs = points.map(point => point.z);
  return {
    x: (Math.min(...xs) + Math.max(...xs)) / 2,
    y: (Math.min(...ys) + Math.max(...ys)) / 2,
    z: (Math.min(...zs) + Math.max(...zs)) / 2,
  };
}

export function geometryCenter(marker: MarkerModel): Vec3 {
  if (marker.type === "line") return centerOf(marker.line);
  if (!marker.shape.length) return marker.position;
  const xs = marker.shape.map(point => point.x);
  const zs = marker.shape.map(point => point.z);
  return {
    x: (Math.min(...xs) + Math.max(...xs)) / 2,
    y: marker.type === "shape" ? marker.shapeY : (marker.minY + marker.maxY) / 2,
    z: (Math.min(...zs) + Math.max(...zs)) / 2,
  };
}

export function previewDrawing(source: MarkerModel, drawing: DrawingState): MarkerModel[] | null {
  const marker = cloneMarker(source);
  marker.listed = false;
  marker.minDistance = 0;
  marker.maxDistance = null;
  if (drawing.kind === "position") return null;
  if (drawing.type === "line") {
    if (!drawing.line.length) return null;
    if (drawing.line.length === 1) {
      const point = newMarker("poi");
      point.position = drawing.line[0];
      point.label = "Draft point";
      point.listed = false;
      return [point];
    }
    marker.type = "line";
    marker.line = drawing.line;
    marker.position = centerOf(drawing.line);
    return [marker];
  }

  const y = drawing.initialY ?? marker.position.y;
  if (drawing.shape.length < 3) {
    if (!drawing.shape.length) return null;
    if (drawing.shape.length === 1) {
      const point = newMarker("poi");
      point.position = { ...drawing.shape[0], y };
      point.label = "Draft point";
      point.listed = false;
      return [point];
    }
    marker.type = "line";
    marker.line = drawing.shape.map(point => ({ ...point, y }));
    marker.position = centerOf(marker.line);
    return [marker];
  }

  marker.type = drawing.type;
  marker.shape = drawing.shape;
  marker.holes = drawing.holes.filter(hole => hole.length >= 3);
  if (marker.type === "shape") marker.shapeY = y;
  else { marker.minY = y; marker.maxY = y + 1; }
  marker.position = geometryCenter(marker);

  const previews = [marker];
  const activeHole = drawing.activeRing > 0 ? drawing.holes[drawing.activeRing - 1] : undefined;
  if (activeHole && activeHole.length > 0 && activeHole.length < 3) {
    if (activeHole.length === 1) {
      const point = newMarker("poi");
      point.position = { ...activeHole[0], y };
      point.label = "Draft hole point";
      point.listed = false;
      previews.push(point);
    } else {
      const line = newMarker("line");
      line.line = activeHole.map(point => ({ ...point, y }));
      line.position = centerOf(line.line);
      line.label = "Draft hole";
      line.listed = false;
      previews.push(line);
    }
  }
  return previews;
}

export function previewMarker(source: MarkerModel): MarkerModel[] | null {
  const marker = cloneMarker(source);
  marker.listed = false;
  marker.minDistance = 0;
  marker.maxDistance = null;

  if (marker.type === "poi" || marker.type === "html") return [marker];
  if (marker.type === "line") {
    if (marker.line.length >= 2) {
      if (marker.positionMode === "center") marker.position = centerOf(marker.line);
      return [marker];
    }
    return previewDrawing(marker, {
      kind: "geometry",
      type: "line",
      line: marker.line,
      shape: [],
      holes: [],
      activeRing: 0,
    });
  }
  if (marker.shape.length >= 3) {
    marker.holes = marker.holes.filter(hole => hole.length >= 3);
    if (marker.positionMode === "center") marker.position = geometryCenter(marker);
    return [marker];
  }
  return previewDrawing(marker, {
    kind: "geometry",
    type: marker.type,
    line: [],
    shape: marker.shape,
    holes: marker.holes,
    activeRing: 0,
    initialY: marker.type === "shape" ? marker.shapeY : marker.minY,
  });
}
