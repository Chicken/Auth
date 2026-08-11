import { MarkerModel, MarkerSetModel, Scope } from "../types";
import { MARKER_LABELS, markerTypeIcon } from "./editorModel";

interface BrowseProps {
  scope: Scope;
  onScope: (scope: Scope) => void;
  sets: MarkerSetModel[];
  selected?: MarkerSetModel;
  select: (id?: string) => void;
  readOnly: boolean;
  canEditSelected: boolean;
  onNewSet: () => void;
  onEditSet: () => void;
  onDeleteSet: () => void;
  onNewMarker: () => void;
  onEditMarker: (marker: MarkerModel) => void;
  onDeleteMarker: (marker: MarkerModel) => void;
  privateVisibility: Record<string, boolean>;
  onPrivateVisibility: (setId: string, visible: boolean) => void;
}

export function Browse(props: BrowseProps) {
  return (
    <div class="me-browser">
      <div class="me-scope-tabs" role="tablist" aria-label="Marker visibility">
        <button type="button" role="tab" aria-selected={props.scope === "private"} onClick={() => props.onScope("private")}>Private</button>
        <button type="button" role="tab" aria-selected={props.scope === "global"} onClick={() => props.onScope("global")}>Global</button>
      </div>
      {props.readOnly && <div class="me-alert">Global markers are read-only. The <code>bluemap.markereditor.global</code> permission is required to edit them.</div>}
      <section class="me-list-section">
        <div class="me-section-heading">
          <h2>Marker sets</h2>
          <button class="me-button is-compact" type="button" disabled={props.readOnly} onClick={props.onNewSet}>New set</button>
        </div>
        {props.sets.length ? (
          <div class="me-set-strip" role="list">
            {props.sets.map(set => (
              <div key={set.id ?? set.key} role="listitem" class={`me-set-chip ${set.id === props.selected?.id ? "active" : ""}`}>
                <button class="me-set-select" type="button" onClick={() => props.select(set.id)}><span>{set.label}</span><small>{set.markers.length}</small></button>
                {props.scope === "private" && set.id && <button class="me-set-visibility" type="button" title={props.privateVisibility[set.id] === false ? "Show marker set" : "Hide marker set"} aria-label={`${props.privateVisibility[set.id] === false ? "Show" : "Hide"} ${set.label}`} aria-pressed={props.privateVisibility[set.id] !== false} onClick={() => props.onPrivateVisibility(set.id!, props.privateVisibility[set.id!] === false)}>{props.privateVisibility[set.id] === false ? "○" : "●"}</button>}
              </div>
            ))}
          </div>
        ) : <p class="me-empty">No {props.scope} marker sets yet.</p>}
      </section>
      {props.selected && (
        <section class="me-list-section is-markers">
          <div class="me-set-summary">
            <div>
              <span class="me-eyebrow">{props.selected.key}</span>
              <h2>{props.selected.label}</h2>
            </div>
            {props.canEditSelected && <div class="me-overflow-actions">
              <button class="me-button is-quiet" type="button" onClick={props.onEditSet}>Set properties</button>
              <button class="me-button is-danger-text" type="button" onClick={props.onDeleteSet}>Delete set</button>
            </div>}
          </div>
          <div class="me-section-heading">
            <h3>Markers</h3>
            <button class="me-button is-compact" type="button" disabled={!props.canEditSelected} onClick={props.onNewMarker}>New marker</button>
          </div>
          {props.selected.markers.length ? (
            <ul class="me-marker-list">
              {props.selected.markers.map(marker => (
                <li key={marker.id ?? marker.key}>
                  <span class={`me-type-icon is-${marker.type}`} aria-hidden="true">{markerTypeIcon(marker.type)}</span>
                  <span class="me-marker-name"><strong>{marker.label || marker.key}</strong><small>{MARKER_LABELS[marker.type]} · {marker.key}</small></span>
                  {props.canEditSelected && <>
                    <button class="me-icon-button" type="button" title="Edit marker" aria-label={`Edit ${marker.label}`} onClick={() => props.onEditMarker(marker)}>✎</button>
                    <button class="me-icon-button" type="button" title="Delete marker" aria-label={`Delete ${marker.label}`} onClick={() => props.onDeleteMarker(marker)}>×</button>
                  </>}
                </li>
              ))}
            </ul>
          ) : <p class="me-empty">This set has no markers.</p>}
        </section>
      )}
    </div>
  );
}
