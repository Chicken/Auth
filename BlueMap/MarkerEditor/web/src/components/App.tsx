import { useEffect, useMemo, useRef, useState } from "preact/hooks";
import {
  ApiError,
  createMarker,
  createSet,
  deleteMarker,
  deleteSet,
  getState,
  updateMarker,
  updateSet,
} from "../api";
import { BlueMapBridge } from "../bluemap";
import {
  EditorState,
  MarkerModel,
  MarkerSetModel,
  Scope,
  cloneMarker,
  cloneSet,
  newMarker,
  newMarkerSet,
} from "../types";
import { Browse } from "./Browse";
import { DraftingRail } from "./DraftingRail";
import { Notice } from "./FormChrome";
import { MarkerForm } from "./MarkerForm";
import { SetForm } from "./SetForm";
import { DrawingState, geometryCenter, previewDrawing, previewMarker } from "./editorModel";

interface AppProps {
  bridge: BlueMapBridge;
  integrationAvailable: boolean;
}

type Screen = "browse" | "set" | "marker";

const EMPTY_STATE: EditorState = { loggedIn: false, canEditGlobal: false, markerSets: [] };

export function App({ bridge, integrationAvailable }: AppProps) {
  const [open, setOpen] = useState(false);
  const [mapId, setMapId] = useState(bridge.currentMapId);
  const [mapName, setMapName] = useState(bridge.currentMapName);
  const [state, setState] = useState<EditorState>(EMPTY_STATE);
  const [scope, setScope] = useState<Scope>("private");
  const [selectedSetId, setSelectedSetId] = useState<string>();
  const [screen, setScreen] = useState<Screen>("browse");
  const [setDraft, setSetDraft] = useState<MarkerSetModel>();
  const [markerDraft, setMarkerDraft] = useState<MarkerModel>();
  const [drawing, setDrawing] = useState<DrawingState>();
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string>();
  const [error, setError] = useState<string>();
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [privateVisibility, setPrivateVisibility] = useState<Record<string, boolean>>({});
  const [themeClass, setThemeClass] = useState("");
  const dirtyRef = useRef(dirty);
  const drawingRef = useRef(drawing);
  const drawerHeading = useRef<HTMLHeadingElement>(null);

  dirtyRef.current = dirty;
  drawingRef.current = drawing;

  const sets = useMemo(
    () => state.markerSets.filter(markerSet => markerSet.scope === scope),
    [state.markerSets, scope],
  );
  const selectedSet = state.markerSets.find(markerSet => markerSet.id === selectedSetId);
  const canEditSelected = selectedSet?.scope === "private" || Boolean(selectedSet && state.canEditGlobal);

  const discardPending = () => {
    if (!dirtyRef.current && !drawingRef.current) return true;
    return window.confirm("Discard your unsaved marker changes?");
  };

  useEffect(() => {
    const controls = installControls(() => {
      setOpen(current => {
        if (current && !discardPending()) return current;
        if (current) cancelEditing();
        return !current;
      });
    });
    return controls.remove;
  }, []);

  useEffect(() => {
    document.querySelectorAll(".marker-editor-toggle").forEach(element => {
      element.classList.toggle("active", open);
      element.setAttribute("aria-expanded", String(open));
    });
    if (open) window.setTimeout(() => drawerHeading.current?.focus(), 0);
  }, [open]);

  useEffect(() => {
    const app = document.getElementById("app");
    if (!app) return;
    const sync = () => setThemeClass(
      ["theme-light", "theme-dark", "theme-contrast"].find(name => app.classList.contains(name)) ?? "",
    );
    sync();
    const observer = new MutationObserver(sync);
    observer.observe(app, { attributes: true, attributeFilter: ["class"] });
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const changed = () => window.setTimeout(() => {
      bridge.clearPrivate();
      bridge.renderDraft(null);
      setDrawing(undefined);
      setScreen("browse");
      if (dirtyRef.current) setMessage("Unsaved changes were discarded when the map changed.");
      setDirty(false);
      setMapId(bridge.currentMapId);
      setMapName(bridge.currentMapName);
    }, 0);
    bridge.events.addEventListener("bluemapMapChanged", changed);
    return () => bridge.events.removeEventListener("bluemapMapChanged", changed);
  }, [bridge]);

  useEffect(() => {
    if (!mapId) return;
    void reload(mapId, true);
  }, [mapId, integrationAvailable]);

  useEffect(() => {
    const beforeUnload = (event: BeforeUnloadEvent) => {
      if (!dirtyRef.current && !drawingRef.current) return;
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", beforeUnload);
    return () => window.removeEventListener("beforeunload", beforeUnload);
  }, []);

  useEffect(() => {
    if (!drawing) {
      bridge.stopTerrainPicking();
      return;
    }
    return bridge.startTerrainPicking(point => {
      if (drawing.kind === "position") {
        setMarkerDraft(current => current ? { ...current, position: point, positionMode: "manual" } : current);
        setDirty(true);
        setDrawing(undefined);
        setMessage("Position selected.");
        return;
      }
      setDrawing(current => {
        if (!current || current.kind !== "geometry") return current;
        if (current.type === "line") {
          return { ...current, line: [...current.line, point], initialY: current.initialY ?? point.y };
        }
        const vertex = { x: point.x, z: point.z };
        if (current.activeRing === 0) {
          return { ...current, shape: [...current.shape, vertex], initialY: current.initialY ?? point.y };
        }
        const holes = current.holes.map((hole, index) => index === current.activeRing - 1 ? [...hole, vertex] : hole);
        return { ...current, holes, initialY: current.initialY ?? point.y };
      });
    });
  }, [drawing?.kind, bridge]);

  useEffect(() => {
    const editingMarker = screen === "marker" && markerDraft ? markerDraft : undefined;
    const preview = editingMarker
      ? drawing?.kind === "geometry" ? previewDrawing(editingMarker, drawing) : previewMarker(editingMarker)
      : null;
    const source = editingMarker?.id && selectedSet?.id
      ? { setId: selectedSet.id, markerId: editingMarker.id }
      : undefined;
    bridge.renderDraft(preview, source);
  }, [screen, drawing, markerDraft, selectedSet?.id, bridge]);

  async function reload(targetMapId = mapId, includePrivate = false) {
    setLoading(true);
    setError(undefined);
    try {
      const next = await getState(targetMapId);
      if (!integrationAvailable) next.loggedIn = false;
      setState(next);
      setSelectedSetId(current => next.markerSets.some(markerSet => markerSet.id === current)
        ? current
        : next.markerSets.find(markerSet => markerSet.scope === scope)?.id);
      if (includePrivate && integrationAvailable && next.loggedIn) {
        await bridge.refreshPrivate(targetMapId);
        setPrivateVisibility(Object.fromEntries(next.markerSets
          .filter(markerSet => markerSet.scope === "private" && markerSet.id)
          .map(markerSet => [markerSet.id!, bridge.privateSetVisible(markerSet.id!)])));
      }
      else if (!next.loggedIn) bridge.clearPrivate();
      return next;
    } catch (cause) {
      setState(EMPTY_STATE);
      setError(messageFor(cause));
      bridge.clearPrivate();
      return null;
    } finally {
      setLoading(false);
    }
  }

  function cancelEditing() {
    setDrawing(undefined);
    bridge.renderDraft(null);
    setSetDraft(undefined);
    setMarkerDraft(undefined);
    setScreen("browse");
    setDirty(false);
    setFieldErrors({});
    setError(undefined);
  }

  function goBack() {
    if (!discardPending()) return;
    cancelEditing();
  }

  function editSet(markerSet?: MarkerSetModel) {
    const value = markerSet ? cloneSet(markerSet) : newMarkerSet(scope);
    setSetDraft(value);
    setScreen("set");
    setDirty(false);
    setFieldErrors({});
    setError(undefined);
  }

  function editMarker(marker?: MarkerModel) {
    const value = marker ? cloneMarker(marker) : newMarker("poi");
    setMarkerDraft(value);
    setScreen("marker");
    setDirty(false);
    setFieldErrors({});
    setError(undefined);
  }

  async function saveSet() {
    if (!setDraft) return;
    await write(async () => {
      if (setDraft.id) await updateSet(mapId, setDraft);
      else await createSet(mapId, setDraft);
    }, setDraft.scope, setDraft.id ? "Marker set saved." : "Marker set created.");
  }

  async function removeSet(markerSet: MarkerSetModel) {
    if (!markerSet.id || !window.confirm(`Delete “${markerSet.label}” and all markers in it?`)) return;
    await write(() => deleteSet(mapId, markerSet.id!), markerSet.scope, "Marker set deleted.");
    setSelectedSetId(undefined);
  }

  async function saveMarker() {
    if (!markerDraft || !selectedSet?.id) return;
    await write(async () => {
      if (markerDraft.id) await updateMarker(mapId, selectedSet.id!, markerDraft);
      else await createMarker(mapId, selectedSet.id!, markerDraft);
    }, selectedSet.scope, markerDraft.id ? "Marker saved." : "Marker created.");
  }

  async function removeMarker(marker: MarkerModel) {
    if (!selectedSet?.id || !marker.id || !window.confirm(`Delete “${marker.label}”?`)) return;
    await write(() => deleteMarker(mapId, selectedSet.id!, marker.id!), selectedSet.scope, "Marker deleted.");
  }

  async function write(action: () => Promise<void>, changedScope: Scope, success: string) {
    setSaving(true);
    setError(undefined);
    setFieldErrors({});
    try {
      await action();
      setDirty(false);
      setDrawing(undefined);
      setScreen("browse");
      bridge.renderDraft(null);
      const refreshed = await reload(mapId, false);
      if (changedScope === "private") await bridge.refreshPrivate(mapId);
      else await bridge.refreshGlobal();
      setMessage(success);
      if (changedScope === "private" && refreshed) {
        setPrivateVisibility(Object.fromEntries(refreshed.markerSets
          .filter(markerSet => markerSet.scope === "private" && markerSet.id)
          .map(markerSet => [markerSet.id!, bridge.privateSetVisible(markerSet.id!)])));
      }
    } catch (cause) {
      if (cause instanceof ApiError) setFieldErrors(cause.fields);
      setError(messageFor(cause));
    } finally {
      setSaving(false);
    }
  }

  function finishDrawing() {
    if (!drawing || drawing.kind !== "geometry" || !markerDraft) return;
    if (drawing.type === "line" && drawing.line.length < 2) {
      setError("A line needs at least two points.");
      return;
    }
    if (drawing.type !== "line" && (drawing.shape.length < 3 || drawing.holes.some(hole => hole.length < 3))) {
      setError("The outer shape and every hole need at least three vertices.");
      return;
    }
    const initialY = drawing.initialY ?? markerDraft.position.y;
    let next = { ...markerDraft };
    if (drawing.type === "line") next = { ...next, line: drawing.line };
    else next = { ...next, shape: drawing.shape, holes: drawing.holes };
    if (drawing.type === "shape") next.shapeY = initialY;
    if (drawing.type === "extrude") {
      next.minY = initialY;
      next.maxY = initialY + 1;
    }
    if (next.positionMode === "center") next.position = geometryCenter(next);
    setMarkerDraft(next);
    setDrawing(undefined);
    setDirty(true);
    setMessage("Map geometry applied. Save changes to publish it.");
    setError(undefined);
  }

  function addHole() {
    if (!drawing || drawing.kind !== "geometry" || drawing.type === "line") return;
    const active = drawing.activeRing === 0 ? drawing.shape : drawing.holes[drawing.activeRing - 1];
    if (active.length < 3) {
      setError("Finish the current ring with at least three vertices first.");
      return;
    }
    setDrawing({ ...drawing, holes: [...drawing.holes, []], activeRing: drawing.holes.length + 1 });
    setError(undefined);
  }

  function undoPoint() {
    if (!drawing || drawing.kind !== "geometry") return;
    if (drawing.type === "line") {
      setDrawing({ ...drawing, line: drawing.line.slice(0, -1) });
      return;
    }
    if (drawing.activeRing === 0) {
      setDrawing({ ...drawing, shape: drawing.shape.slice(0, -1) });
      return;
    }
    const holeIndex = drawing.activeRing - 1;
    const active = drawing.holes[holeIndex];
    if (active.length) {
      setDrawing({ ...drawing, holes: drawing.holes.map((hole, index) => index === holeIndex ? hole.slice(0, -1) : hole) });
    } else {
      setDrawing({ ...drawing, holes: drawing.holes.slice(0, -1), activeRing: Math.max(0, drawing.activeRing - 1) });
    }
  }

  function login() {
    if (!state.authPath) return;
    const separator = state.authPath.endsWith("/") ? "" : "/";
    window.location.href = `${state.authPath}${separator}login?redirect=${encodeURIComponent(window.location.pathname + window.location.search + window.location.hash)}`;
  }

  const markerSetReadOnly = scope === "global" && !state.canEditGlobal;

  return (
    <div id="marker-editor-root" class={themeClass} onKeyDown={event => {
      event.stopPropagation();
      if (event.key !== "Escape") return;
      if (drawing) setDrawing(undefined);
      else if (screen !== "browse") goBack();
      else if (discardPending()) setOpen(false);
    }}>
      {message && <div class="me-toast" role="status"><span>{message}</span><button type="button" aria-label="Dismiss" onClick={() => setMessage(undefined)}>×</button></div>}
      {drawing && <DraftingRail drawing={drawing} onUndo={undoPoint} onAddHole={addHole} onFinish={finishDrawing} onCancel={() => setDrawing(undefined)} />}
      {open && !drawing && (
        <aside class="me-drawer" aria-label="Marker editor">
          <header class="me-header">
            <div>
              <span class="me-eyebrow">{mapName || mapId}</span>
              <h1 ref={drawerHeading} tabIndex={-1}>Marker editor</h1>
            </div>
            <button class="me-icon-button is-large" type="button" aria-label="Close marker editor" onClick={() => {
              if (discardPending()) { cancelEditing(); setOpen(false); }
            }}>×</button>
          </header>

          {!integrationAvailable ? (
            <Notice title="Editor unavailable">The Authentication Integration addon did not load. Editing is disabled.</Notice>
          ) : loading ? (
            <div class="me-loading" role="status"><span class="me-spinner" />Loading markers…</div>
          ) : !state.loggedIn ? (
            <Notice title="Log in to edit markers">
              Private markers are tied to your Minecraft account.
              {state.authPath && <button class="me-button is-primary" type="button" onClick={login}>Log in</button>}
            </Notice>
          ) : (
            <>
              <div class="me-user-line">
                <span class="me-user-dot" aria-hidden="true" />
                <span>{state.username ?? "Authenticated player"}</span>
              </div>
              {error && <div class="me-alert is-error" role="alert">{error}</div>}
              {screen === "browse" && (
                <Browse
                  scope={scope}
                  onScope={next => { setScope(next); setSelectedSetId(state.markerSets.find(set => set.scope === next)?.id); }}
                  sets={sets}
                  selected={selectedSet}
                  select={setSelectedSetId}
                  readOnly={markerSetReadOnly}
                  canEditSelected={canEditSelected}
                  onNewSet={() => editSet()}
                  onEditSet={() => selectedSet && editSet(selectedSet)}
                  onDeleteSet={() => selectedSet && void removeSet(selectedSet)}
                  onNewMarker={() => editMarker()}
                  onEditMarker={editMarker}
                  onDeleteMarker={marker => void removeMarker(marker)}
                  privateVisibility={privateVisibility}
                  onPrivateVisibility={(setId, visible) => {
                    if (bridge.setPrivateSetVisible(setId, visible)) {
                      setPrivateVisibility(current => ({ ...current, [setId]: visible }));
                    }
                  }}
                />
              )}
              {screen === "set" && setDraft && (
                <SetForm
                  value={setDraft}
                  errors={fieldErrors}
                  isNew={!setDraft.id}
                  saving={saving}
                  canCreateGlobal={state.canEditGlobal}
                  onChange={value => { setSetDraft(value); setDirty(true); }}
                  onBack={goBack}
                  onSave={() => void saveSet()}
                />
              )}
              {screen === "marker" && markerDraft && selectedSet && (
                <MarkerForm
                  value={markerDraft}
                  errors={fieldErrors}
                  isNew={!markerDraft.id}
                  saving={saving}
                  onChange={value => { setMarkerDraft(value); setDirty(true); }}
                  onBack={goBack}
                  onSave={() => void saveMarker()}
                  onPickPosition={() => setDrawing({ kind: "position", type: markerDraft.type, line: [], shape: [], holes: [], activeRing: 0 })}
                  onDraw={() => setDrawing({ kind: "geometry", type: markerDraft.type, line: [], shape: [], holes: [], activeRing: 0 })}
                />
              )}
            </>
          )}
        </aside>
      )}
    </div>
  );
}

function installControls(toggle: () => void): { remove: () => void } {
  const created: HTMLElement[] = [];
  const make = () => {
    const button = document.createElement("div");
    button.className = "svg-button marker-editor-toggle";
    button.setAttribute("role", "button");
    button.setAttribute("tabindex", "0");
    button.setAttribute("title", "Marker editor");
    button.setAttribute("aria-label", "Open marker editor");
    button.setAttribute("aria-expanded", "false");
    button.innerHTML = '<svg viewBox="0 0 30 30" aria-hidden="true"><path d="M21.8 3.8a2.7 2.7 0 0 1 3.8 3.8L11.2 22l-6 2.8L8 18.7 21.8 3.8Zm-12.5 16 2.1 2.1M19.7 6l3.8 3.8" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
    button.addEventListener("click", toggle);
    button.addEventListener("keydown", event => {
      if (event.key === "Enter" || event.key === " ") { event.preventDefault(); toggle(); }
    });
    created.push(button);
    return button;
  };
  const bar = document.querySelector(".control-bar");
  const reference = bar?.querySelector(".space.thin-hide.greedy");
  if (bar) bar.insertBefore(make(), reference ?? null);
  const zoom = document.querySelector("#zoom-buttons");
  if (zoom) zoom.insertBefore(make(), zoom.firstChild);
  return { remove: () => created.forEach(element => element.remove()) };
}

function messageFor(cause: unknown): string {
  if (cause instanceof Error) return cause.message;
  return "The marker request failed.";
}
