export type Scope = "private" | "global";
export type MarkerType = "poi" | "html" | "line" | "shape" | "extrude";

export interface Vec2 {
  x: number;
  z: number;
}

export interface Vec3 extends Vec2 {
  y: number;
}

export interface PixelAnchor {
  x: number;
  y: number;
}

export interface MarkerColor {
  r: number;
  g: number;
  b: number;
  a: number;
}

export interface MarkerModel {
  id?: string;
  key: string;
  type: MarkerType;
  label: string;
  position: Vec3;
  positionMode: "center" | "manual";
  sorting: number;
  listed: boolean;
  minDistance: number;
  maxDistance: number | null;
  detail: string;
  icon: string;
  anchor: PixelAnchor;
  styleClasses: string[];
  html: string;
  link: string;
  newTab: boolean;
  line: Vec3[];
  shape: Vec2[];
  holes: Vec2[][];
  shapeY: number;
  minY: number;
  maxY: number;
  depthTest: boolean;
  lineWidth: number;
  lineColor: MarkerColor;
  fillColor: MarkerColor;
}

export interface MarkerSetModel {
  id?: string;
  key: string;
  scope: Scope;
  label: string;
  toggleable: boolean;
  defaultHidden: boolean;
  sorting: number;
  markers: MarkerModel[];
}

export interface EditorState {
  loggedIn: boolean;
  authPath?: string;
  uuid?: string;
  username?: string;
  canEditGlobal: boolean;
  markerSets: MarkerSetModel[];
}

export const DEFAULT_LINE_COLOR: MarkerColor = { r: 255, g: 0, b: 0, a: 1 };
export const DEFAULT_FILL_COLOR: MarkerColor = { r: 200, g: 0, b: 0, a: 0.3 };

export function newMarker(type: MarkerType): MarkerModel {
  return {
    key: "new-marker",
    type,
    label: "New marker",
    position: { x: 0, y: 64, z: 0 },
    positionMode: type === "line" || type === "shape" || type === "extrude" ? "center" : "manual",
    sorting: 0,
    listed: true,
    minDistance: 0,
    maxDistance: 10_000_000,
    detail: "",
    icon: type === "poi" ? "assets/poi.svg" : "",
    anchor: type === "poi" ? { x: 25, y: 45 } : { x: 0, y: 0 },
    styleClasses: [],
    html: "<div>Marker</div>",
    link: "",
    newTab: true,
    line: [],
    shape: [],
    holes: [],
    shapeY: 64,
    minY: 64,
    maxY: 65,
    depthTest: true,
    lineWidth: 2,
    lineColor: { ...DEFAULT_LINE_COLOR },
    fillColor: { ...DEFAULT_FILL_COLOR },
  };
}

export function newMarkerSet(scope: Scope): MarkerSetModel {
  return {
    key: "new-set",
    scope,
    label: "New marker set",
    toggleable: true,
    defaultHidden: false,
    sorting: 0,
    markers: [],
  };
}

function number(value: unknown, fallback: number): number {
  const parsed = typeof value === "number" ? value : Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function vec2(value: unknown): Vec2 {
  const data = (value ?? {}) as Partial<Vec2>;
  return { x: number(data.x, 0), z: number(data.z, 0) };
}

function vec3(value: unknown): Vec3 {
  const data = (value ?? {}) as Partial<Vec3>;
  return { x: number(data.x, 0), y: number(data.y, 0), z: number(data.z, 0) };
}

function color(value: unknown, fallback: MarkerColor): MarkerColor {
  const data = (value ?? {}) as Partial<MarkerColor>;
  return {
    r: number(data.r, fallback.r),
    g: number(data.g, fallback.g),
    b: number(data.b, fallback.b),
    a: number(data.a, fallback.a),
  };
}

export function normalizeMarker(raw: unknown, fallbackId?: string): MarkerModel {
  const data = (raw ?? {}) as Record<string, unknown>;
  const type = (["poi", "html", "line", "shape", "extrude"] as MarkerType[]).includes(data.type as MarkerType)
    ? data.type as MarkerType
    : "poi";
  const base = newMarker(type);
  const rawClasses = Array.isArray(data.styleClasses)
    ? data.styleClasses
    : Array.isArray(data.classes) ? data.classes : [];
  return {
    ...base,
    id: String(data.id ?? data.uuid ?? fallbackId ?? "") || undefined,
    key: String(data.key ?? fallbackId ?? base.key),
    type,
    label: String(data.label ?? ""),
    position: vec3(data.position),
    positionMode: data.positionMode === "manual" ? "manual" : data.positionMode === "center" ? "center" : base.positionMode,
    sorting: number(data.sorting, 0),
    listed: data.listed === undefined ? true : Boolean(data.listed),
    minDistance: number(data.minDistance, 0),
    maxDistance: data.maxDistance === null || data.maxDistance === undefined ? null : number(data.maxDistance, 0),
    detail: String(data.detail ?? ""),
    icon: String(data.icon ?? ""),
    anchor: { x: number((data.anchor as Partial<PixelAnchor>)?.x, 0), y: number((data.anchor as Partial<PixelAnchor>)?.y, 0) },
    styleClasses: rawClasses.map(String),
    html: String(data.html ?? base.html),
    link: String(data.link ?? ""),
    newTab: data.newTab === undefined ? true : Boolean(data.newTab),
    line: Array.isArray(data.line) ? data.line.map(vec3) : [],
    shape: Array.isArray(data.shape) ? data.shape.map(vec2) : [],
    holes: Array.isArray(data.holes)
      ? data.holes.filter(Array.isArray).map(hole => (hole as unknown[]).map(vec2))
      : [],
    shapeY: number(data.shapeY ?? data.height, base.shapeY),
    minY: number(data.minY ?? data.shapeMinY, base.minY),
    maxY: number(data.maxY ?? data.shapeMaxY, base.maxY),
    depthTest: data.depthTest === undefined ? true : Boolean(data.depthTest),
    lineWidth: number(data.lineWidth, 2),
    lineColor: color(data.lineColor ?? data.borderColor, DEFAULT_LINE_COLOR),
    fillColor: color(data.fillColor, DEFAULT_FILL_COLOR),
  };
}

export function normalizeSet(raw: unknown, fallbackId?: string): MarkerSetModel {
  const data = (raw ?? {}) as Record<string, unknown>;
  const markersData = data.markers;
  let markers: MarkerModel[] = [];
  if (Array.isArray(markersData)) {
    markers = markersData.map(marker => normalizeMarker(marker));
  } else if (markersData && typeof markersData === "object") {
    markers = Object.entries(markersData).map(([id, marker]) => normalizeMarker(marker, id));
  }
  return {
    id: String(data.id ?? data.uuid ?? fallbackId ?? "") || undefined,
    key: String(data.key ?? fallbackId ?? "marker-set"),
    scope: data.scope === "global" ? "global" : "private",
    label: String(data.label ?? fallbackId ?? "Marker set"),
    toggleable: data.toggleable === undefined ? true : Boolean(data.toggleable),
    defaultHidden: Boolean(data.defaultHidden ?? data.defaultHide),
    sorting: number(data.sorting, 0),
    markers,
  };
}

export function normalizeState(raw: unknown): EditorState {
  const data = (raw ?? {}) as Record<string, unknown>;
  const identity = (data.identity ?? data.user ?? {}) as Record<string, unknown>;
  const setsData = data.markerSets ?? data.sets ?? [];
  let markerSets: MarkerSetModel[] = [];
  if (Array.isArray(setsData)) {
    markerSets = setsData.map(set => normalizeSet(set));
  } else if (setsData && typeof setsData === "object") {
    markerSets = Object.entries(setsData).map(([id, set]) => normalizeSet(set, id));
  }
  return {
    loggedIn: Boolean(data.loggedIn ?? identity.uuid),
    authPath: typeof data.authPath === "string" ? data.authPath : undefined,
    uuid: typeof data.uuid === "string" ? data.uuid : typeof identity.uuid === "string" ? identity.uuid : undefined,
    username: typeof data.username === "string" ? data.username : typeof identity.username === "string" ? identity.username : undefined,
    canEditGlobal: Boolean(data.canEditGlobal),
    markerSets,
  };
}

export function cloneMarker(marker: MarkerModel): MarkerModel {
  return normalizeMarker(JSON.parse(JSON.stringify(marker)));
}

export function cloneSet(set: MarkerSetModel): MarkerSetModel {
  return normalizeSet(JSON.parse(JSON.stringify(set)));
}
