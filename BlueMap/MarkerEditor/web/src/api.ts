import { EditorState, MarkerModel, MarkerSetModel, normalizeState } from "./types";

export class ApiError extends Error {
  readonly status: number;
  readonly fields: Record<string, string>;

  constructor(status: number, message: string, fields: Record<string, string> = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fields = fields;
  }
}

function root(mapId: string): string {
  return `./addons/marker-editor/${encodeURIComponent(mapId)}`;
}

async function request(path: string, init?: RequestInit): Promise<unknown> {
  const response = await fetch(path, {
    credentials: "same-origin",
    ...init,
    headers: {
      Accept: "application/json",
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
  });

  const contentType = response.headers.get("content-type") ?? "";
  const payload = contentType.includes("application/json")
    ? await response.json().catch(() => null)
    : await response.text().catch(() => "");

  if (!response.ok) {
    const body = payload && typeof payload === "object" ? payload as Record<string, unknown> : {};
    const nestedError = body.error && typeof body.error === "object" ? body.error as Record<string, unknown> : {};
    const fields = body.fields && typeof body.fields === "object" ? body.fields as Record<string, string> : {};
    if (typeof nestedError.field === "string" && typeof nestedError.message === "string") fields[nestedError.field] = nestedError.message;
    const message = String(nestedError.message ?? body.message ?? (typeof body.error === "string" ? body.error : null) ?? `Request failed (${response.status})`);
    throw new ApiError(response.status, message, fields);
  }

  return payload;
}

export async function getState(mapId: string): Promise<EditorState> {
  return normalizeState(await request(`${root(mapId)}/state`));
}

export async function getPrivateMarkers(mapId: string): Promise<unknown> {
  return request(`${root(mapId)}/private-markers`, { cache: "no-store" });
}

export async function createSet(mapId: string, set: MarkerSetModel): Promise<void> {
  await request(`${root(mapId)}/marker-sets`, { method: "POST", body: JSON.stringify(setPayload(set)) });
}

export async function updateSet(mapId: string, set: MarkerSetModel): Promise<void> {
  if (!set.id) throw new Error("Marker set has no id");
  await request(`${root(mapId)}/marker-sets/${encodeURIComponent(set.id)}`, {
    method: "PUT",
    body: JSON.stringify(setPayload(set)),
  });
}

export async function deleteSet(mapId: string, setId: string): Promise<void> {
  await request(`${root(mapId)}/marker-sets/${encodeURIComponent(setId)}`, { method: "DELETE" });
}

export async function createMarker(mapId: string, setId: string, marker: MarkerModel): Promise<void> {
  await request(`${root(mapId)}/marker-sets/${encodeURIComponent(setId)}/markers`, {
    method: "POST",
    body: JSON.stringify(markerPayload(marker)),
  });
}

export async function updateMarker(mapId: string, setId: string, marker: MarkerModel): Promise<void> {
  if (!marker.id) throw new Error("Marker has no id");
  await request(`${root(mapId)}/marker-sets/${encodeURIComponent(setId)}/markers/${encodeURIComponent(marker.id)}`, {
    method: "PUT",
    body: JSON.stringify(markerPayload(marker)),
  });
}

export async function deleteMarker(mapId: string, setId: string, markerId: string): Promise<void> {
  await request(`${root(mapId)}/marker-sets/${encodeURIComponent(setId)}/markers/${encodeURIComponent(markerId)}`, {
    method: "DELETE",
  });
}

function setPayload(set: MarkerSetModel): Omit<MarkerSetModel, "id" | "markers"> {
  const { id: _id, markers: _markers, ...payload } = set;
  return payload;
}

function markerPayload(marker: MarkerModel): Record<string, unknown> {
  const base: Record<string, unknown> = {
    key: marker.key,
    type: marker.type,
    label: marker.label,
    positionMode: marker.positionMode,
    position: marker.position,
    sorting: marker.sorting,
    listed: marker.listed,
    minDistance: marker.minDistance,
    maxDistance: marker.maxDistance,
  };
  if (marker.type === "poi") {
    return { ...base, detail: marker.detail, icon: marker.icon, anchor: marker.anchor, styleClasses: marker.styleClasses };
  }
  if (marker.type === "html") {
    return { ...base, html: marker.html, anchor: marker.anchor, styleClasses: marker.styleClasses };
  }
  const object = { ...base, detail: marker.detail, link: marker.link, newTab: marker.newTab };
  if (marker.type === "line") {
    return { ...object, line: marker.line, depthTest: marker.depthTest, lineWidth: marker.lineWidth, lineColor: marker.lineColor };
  }
  const shape = {
    ...object,
    shape: marker.shape,
    holes: marker.holes,
    depthTest: marker.depthTest,
    lineWidth: marker.lineWidth,
    lineColor: marker.lineColor,
    fillColor: marker.fillColor,
  };
  return marker.type === "shape"
    ? { ...shape, shapeY: marker.shapeY }
    : { ...shape, minY: marker.minY, maxY: marker.maxY };
}
