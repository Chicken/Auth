import { MarkerModel, Vec3 } from "./types";
import { getPrivateMarkers } from "./api";

interface VectorLike {
  x: number;
  y: number;
  z: number;
  clone(): VectorLike;
}

interface IntersectionLike {
  point?: VectorLike;
  pointOnLine?: VectorLike;
  distance?: number;
}

interface MarkerLike {
  data: { id: string };
  visible: boolean;
}

interface MarkerSetLike {
  data: { id: string; toggleable?: boolean; visible?: boolean; saveState?: () => void };
  visible: boolean;
  markerSets: Map<string, MarkerSetLike>;
  markers: Map<string, MarkerLike>;
  updateMarkerSetsFromData(data?: Record<string, unknown>, ignore?: string[]): void;
  updateMarkerSetFromData(id: string, data: unknown): void;
  updateMarkersFromData(data?: Record<string, unknown>): void;
  add(...objects: MarkerSetLike[]): void;
  remove(...objects: MarkerSetLike[]): void;
  clear(): void;
}

interface BlueMapGlobal {
  MarkerSet: new (id: string, data?: unknown) => MarkerSetLike;
  Three: {
    Vector2: new (x: number, y: number) => unknown;
  };
}

interface BlueMapAppLike {
  events: EventTarget;
  appState: { theme?: string | null };
  popupMarkerSet: MarkerSetLike;
  markerFileManager?: { update(): Promise<unknown> };
  mapViewer: {
    data: { map?: { id?: string; data?: { id?: string; name?: string } } };
    map?: {
      data: { id: string; name?: string };
      hiresTileManager?: { scene: Object3DLike };
      lowresTileManager?: Array<{ scene: Object3DLike }>;
    };
    renderer: { domElement: HTMLCanvasElement };
    markers: MarkerSetLike;
    rootElement: HTMLElement;
    camera: unknown;
    raycaster: {
      setFromCamera(position: unknown, camera: unknown): void;
      intersectObjects(objects: Object3DLike[], recursive?: boolean): IntersectionLike[];
    };
    redraw?(): void;
  };
}

interface Object3DLike {
  position?: { x: number; z: number };
  updateMatrixWorld?(): void;
}

declare global {
  interface Window {
    bluemap?: BlueMapAppLike;
    BlueMap?: BlueMapGlobal;
    bluemapAuth?: { loggedIn: boolean; uuid?: string; username?: string };
  }
}

const PRIVATE_SET_PREFIX = "marker-editor:private:";
const MANAGED_SET_PREFIX = "marker-editor:";
const MANAGED_MARKER_PREFIX = "marker-editor:marker:";
const DRAFT_ROOT_ID = "marker-editor-draft-root";
const privateIdsByRoot = new WeakMap<MarkerSetLike, Set<string>>();

export async function waitForBlueMap(timeout = 3000): Promise<BlueMapAppLike> {
  const started = Date.now();
  while (!window.bluemap || !window.BlueMap) {
    if (Date.now() - started > timeout) throw new Error("BlueMap did not finish loading");
    await new Promise(resolve => window.setTimeout(resolve, 20));
  }
  return window.bluemap;
}

export async function waitForAuthentication(timeout = 2200): Promise<boolean> {
  const started = Date.now();
  while (!window.bluemapAuth) {
    if (Date.now() - started > timeout) return false;
    await new Promise(resolve => window.setTimeout(resolve, 20));
  }
  return true;
}

export class BlueMapBridge {
  private readonly app: BlueMapAppLike;
  private readonly markerRoot: MarkerSetLike;
  private readonly privateIds: Set<string>;
  private readonly draftRoot: MarkerSetLike;
  private hiddenPreviewSource?: { key: string; marker: MarkerLike; visible: boolean };
  private stopPicking?: () => void;

  constructor(app: BlueMapAppLike) {
    this.app = app;
    this.markerRoot = app.mapViewer.markers;
    this.privateIds = preservePrivateSets(this.markerRoot);
    this.draftRoot = this.ensurePopupRoot(DRAFT_ROOT_ID);
  }

  get events(): EventTarget {
    return this.app.events;
  }

  get currentMapId(): string {
    const map = this.app.mapViewer.map;
    return map?.data.id ?? this.app.mapViewer.data.map?.data?.id ?? this.app.mapViewer.data.map?.id ?? "";
  }

  get currentMapName(): string {
    return this.app.mapViewer.map?.data.name ?? this.currentMapId;
  }

  async refreshPrivate(mapId = this.currentMapId): Promise<void> {
    if (!mapId || !window.bluemapAuth?.loggedIn) {
      this.clearPrivate();
      return;
    }
    const payload = await getPrivateMarkers(mapId);
    if (mapId !== this.currentMapId) return;
    const data = payload && typeof payload === "object" ? payload as Record<string, unknown> : {};
    const markerSets = data.markerSets && typeof data.markerSets === "object"
      ? data.markerSets as Record<string, unknown>
      : data;
    const entries = Object.entries(markerSets)
      .filter(([id, markerSet]) => id.startsWith(PRIVATE_SET_PREFIX) && markerSet && typeof markerSet === "object");
    const nextIds = new Set(entries.map(([id]) => id));

    for (const id of this.privateIds) {
      if (nextIds.has(id)) continue;
      const markerSet = this.markerRoot.markerSets.get(id);
      if (markerSet) this.markerRoot.remove(markerSet);
    }
    this.privateIds.clear();
    for (const id of nextIds) this.privateIds.add(id);
    for (const [id, markerSet] of entries) this.markerRoot.updateMarkerSetFromData(id, markerSet);
    this.app.mapViewer.redraw?.();
  }

  clearPrivate(): void {
    for (const id of this.privateIds) {
      const markerSet = this.markerRoot.markerSets.get(id);
      if (markerSet) this.markerRoot.remove(markerSet);
    }
    this.privateIds.clear();
    this.app.mapViewer.redraw?.();
  }

  privateSetVisible(setId: string): boolean {
    return this.findPrivateSet(setId)?.visible ?? true;
  }

  setPrivateSetVisible(setId: string, visible: boolean): boolean {
    const markerSet = this.findPrivateSet(setId);
    if (!markerSet) return false;
    markerSet.visible = visible;
    markerSet.data.saveState?.();
    this.app.mapViewer.redraw?.();
    return true;
  }

  refreshGlobal(): Promise<unknown> {
    return this.app.markerFileManager?.update() ?? Promise.resolve();
  }

  renderDraft(marker: MarkerModel | MarkerModel[] | null, source?: { setId: string; markerId: string }): void {
    this.syncPreviewSource(source);
    if (!marker) {
      this.draftRoot.updateMarkersFromData({});
      this.app.mapViewer.redraw?.();
      return;
    }
    const markers = Array.isArray(marker) ? marker : [marker];
    this.draftRoot.updateMarkersFromData(Object.fromEntries(
      markers.map((entry, index) => [`marker-editor-draft-${index}`, markerForBlueMap(entry)]),
    ));
    this.app.mapViewer.redraw?.();
  }

  startTerrainPicking(onPick: (position: Vec3) => void): () => void {
    this.stopTerrainPicking();
    const canvas = this.app.mapViewer.renderer.domElement;
    let start: { x: number; y: number; pointerId: number } | null = null;

    const pointerDown = (event: PointerEvent) => {
      if (event.button !== 0) return;
      start = { x: event.clientX, y: event.clientY, pointerId: event.pointerId };
    };
    const pointerUp = (event: PointerEvent) => {
      if (!start || start.pointerId !== event.pointerId) return;
      const distance = Math.hypot(event.clientX - start.x, event.clientY - start.y);
      start = null;
      if (distance > 7) return;
      const hit = this.pickTerrain(event.clientX, event.clientY);
      if (hit) onPick(hit);
    };
    const pointerCancel = () => { start = null; };
    const suppressCoordinatePopup = (event: Event) => {
      const detail = (event as CustomEvent<{ hiresHit?: IntersectionLike; lowresHits?: IntersectionLike[] }>).detail;
      if (!detail) return;
      // BlueMap's popup reads these hits in a timeout after dispatching the event.
      detail.hiresHit = undefined;
      detail.lowresHits = [];
    };

    canvas.addEventListener("pointerdown", pointerDown);
    canvas.addEventListener("pointerup", pointerUp);
    canvas.addEventListener("pointercancel", pointerCancel);
    this.app.events.addEventListener("bluemapMapInteraction", suppressCoordinatePopup);
    canvas.classList.add("marker-editor-picking");
    this.stopPicking = () => {
      canvas.removeEventListener("pointerdown", pointerDown);
      canvas.removeEventListener("pointerup", pointerUp);
      canvas.removeEventListener("pointercancel", pointerCancel);
      this.app.events.removeEventListener("bluemapMapInteraction", suppressCoordinatePopup);
      canvas.classList.remove("marker-editor-picking");
    };
    return this.stopPicking;
  }

  stopTerrainPicking(): void {
    this.stopPicking?.();
    this.stopPicking = undefined;
  }

  dispose(): void {
    this.stopTerrainPicking();
    this.clearPrivate();
    this.renderDraft(null);
  }

  private ensurePopupRoot(id: string): MarkerSetLike {
    const popupRoot = this.app.popupMarkerSet;
    const existing = popupRoot.markerSets.get(id);
    if (existing) return existing;
    const root = new window.BlueMap!.MarkerSet(id);
    root.data.toggleable = false;
    popupRoot.add(root);
    return root;
  }

  private findPrivateSet(setId: string): MarkerSetLike | undefined {
    const direct = this.markerRoot.markerSets.get(setId);
    if (direct) return direct;
    for (const renderId of this.privateIds) {
      const markerSet = this.markerRoot.markerSets.get(renderId);
      if (!markerSet) continue;
      if (renderId.endsWith(`:${setId}`) || renderId.endsWith(`/${setId}`) || markerSet.data.id === setId) return markerSet;
    }
    return undefined;
  }

  private syncPreviewSource(source?: { setId: string; markerId: string }): void {
    const key = source ? `${source.setId}:${source.markerId}` : undefined;
    if (source && key && this.hiddenPreviewSource?.key === key) {
      const current = this.findManagedMarker(source.setId, source.markerId);
      if (current === this.hiddenPreviewSource.marker) return;
    }

    if (this.hiddenPreviewSource) {
      this.hiddenPreviewSource.marker.visible = this.hiddenPreviewSource.visible;
      this.hiddenPreviewSource = undefined;
    }
    if (!source) return;

    const marker = this.findManagedMarker(source.setId, source.markerId);
    if (!marker) return;
    this.hiddenPreviewSource = { key: `${source.setId}:${source.markerId}`, marker, visible: marker.visible };
    marker.visible = false;
  }

  private findManagedMarker(setId: string, markerId: string): MarkerLike | undefined {
    for (const [renderSetId, markerSet] of this.markerRoot.markerSets) {
      if (!renderSetId.startsWith(MANAGED_SET_PREFIX) || !renderSetId.endsWith(`:${setId}`)) continue;
      const direct = markerSet.markers.get(markerId);
      if (direct) return direct;
      for (const [renderMarkerId, marker] of markerSet.markers) {
        if (renderMarkerId.startsWith(MANAGED_MARKER_PREFIX) && renderMarkerId.endsWith(`:${markerId}`)) return marker;
      }
    }
    return undefined;
  }

  private pickTerrain(clientX: number, clientY: number): Vec3 | null {
    const viewer = this.app.mapViewer;
    const map = viewer.map;
    if (!map) return null;
    const rect = viewer.renderer.domElement.getBoundingClientRect();
    const normalized = new window.BlueMap!.Three.Vector2(
      ((clientX - rect.left) / rect.width) * 2 - 1,
      -((clientY - rect.top) / rect.height) * 2 + 1,
    );
    viewer.raycaster.setFromCamera(normalized, viewer.camera);

    const scenes: Object3DLike[] = [];
    if (map.hiresTileManager?.scene) scenes.push(map.hiresTileManager.scene);
    for (const manager of map.lowresTileManager ?? []) scenes.push(manager.scene);
    for (const scene of scenes) {
      if (scene.position) {
        scene.position.x = 0;
        scene.position.z = 0;
      }
      scene.updateMatrixWorld?.();
      const hit = viewer.raycaster.intersectObjects([scene], true)[0];
      const point = hit?.pointOnLine ?? hit?.point;
      if (point) return { x: round(point.x), y: round(point.y), z: round(point.z) };
    }
    return null;
  }
}

function preservePrivateSets(root: MarkerSetLike): Set<string> {
  const existing = privateIdsByRoot.get(root);
  if (existing) return existing;

  const privateIds = new Set<string>();
  const updateMarkerSetsFromData = root.updateMarkerSetsFromData.bind(root);
  root.updateMarkerSetsFromData = (data = {}, ignore = []) => {
    updateMarkerSetsFromData(data, [...new Set([...ignore, ...privateIds])]);
  };
  privateIdsByRoot.set(root, privateIds);
  return privateIds;
}

function round(value: number): number {
  return Math.round(value * 100) / 100;
}

function markerForBlueMap(marker: MarkerModel): Record<string, unknown> {
  return {
    ...marker,
    id: undefined,
    key: undefined,
    positionMode: undefined,
    maxDistance: marker.maxDistance ?? Number.MAX_VALUE,
    classes: marker.styleClasses ?? [],
    styleClasses: undefined,
    shapeMinY: marker.minY,
    shapeMaxY: marker.maxY,
    minY: undefined,
    maxY: undefined,
  };
}
