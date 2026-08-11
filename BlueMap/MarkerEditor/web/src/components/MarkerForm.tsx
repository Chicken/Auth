import { MarkerModel, MarkerType, Vec2, newMarker } from "../types";
import { Checkbox, ColorField, Field, NumberField, TextField, Vec2List, Vec3Fields, Vec3List } from "./Fields";
import { FormActions, FormHeading, RawHtmlWarning } from "./FormChrome";
import { MARKER_LABELS, centerOf, geometryCenter } from "./editorModel";

interface MarkerFormProps {
  value: MarkerModel;
  onChange: (value: MarkerModel) => void;
  onSave: () => void;
  onBack: () => void;
  onPickPosition: () => void;
  onDraw: () => void;
  errors: Record<string, string>;
  isNew: boolean;
  saving: boolean;
}

export function MarkerForm(props: MarkerFormProps) {
  const patch = (value: Partial<MarkerModel>) => props.onChange({ ...props.value, ...value });
  const geometry = props.value.type === "line" || props.value.type === "shape" || props.value.type === "extrude";

  function changeType(type: MarkerType) {
    const defaults = newMarker(type);
    props.onChange({
      ...defaults,
      id: props.value.id,
      key: props.value.key,
      label: props.value.label,
      position: props.value.position,
      sorting: props.value.sorting,
      listed: props.value.listed,
      minDistance: props.value.minDistance,
      maxDistance: props.value.maxDistance,
    });
  }

  return (
    <form class="me-form" onSubmit={event => { event.preventDefault(); props.onSave(); }}>
      <FormHeading title={props.isNew ? "New marker" : "Edit marker"} onBack={props.onBack} />
      <Field label="Marker type" error={props.errors.type}>
        <select class="me-input" value={props.value.type} onChange={event => changeType(event.currentTarget.value as MarkerType)}>
          {Object.entries(MARKER_LABELS).map(([type, label]) => <option value={type} key={type}>{label}</option>)}
        </select>
      </Field>
      <TextField label="Key" required mono value={props.value.key} error={props.errors.key} onInput={key => patch({ key })} />
      <TextField label="Label" required value={props.value.label} error={props.errors.label} onInput={label => patch({ label })} />

      <fieldset class="me-fieldset">
        <legend>Position</legend>
        {geometry && <Field label="Reference position">
          <select class="me-input" value={props.value.positionMode} onChange={event => patch({ positionMode: event.currentTarget.value as "center" | "manual" })}>
            <option value="center">Center of geometry</option>
            <option value="manual">Manual position</option>
          </select>
        </Field>}
        {(!geometry || props.value.positionMode === "manual") && <>
          <Vec3Fields value={props.value.position} onInput={position => patch({ position })} />
          <button class="me-button is-map-action" type="button" onClick={props.onPickPosition}><span aria-hidden="true">⌖</span> Pick on map</button>
        </>}
      </fieldset>

      {props.value.type === "poi" && <>
        <RawHtmlWarning>POI detail is rendered as HTML by BlueMap.</RawHtmlWarning>
        <TextField label="Detail (HTML)" multiline rows={4} value={props.value.detail} error={props.errors.detail} onInput={detail => patch({ detail })} />
        <TextField label="Icon address" value={props.value.icon} error={props.errors.icon} hint="A URL or path such as assets/poi.svg." onInput={icon => patch({ icon })} />
        <AnchorFields value={props.value.anchor} onChange={anchor => patch({ anchor })} />
        <TextField label="CSS classes" value={props.value.styleClasses.join(" ")} error={props.errors.styleClasses} hint="Space-separated class names." onInput={classes => patch({ styleClasses: splitClasses(classes) })} />
      </>}

      {props.value.type === "html" && <>
        <RawHtmlWarning>HTML markers are inserted into the map without sanitizing. Scripts and event handlers can run on this site.</RawHtmlWarning>
        <TextField label="HTML" multiline rows={7} mono spellcheck={false} value={props.value.html} error={props.errors.html} onInput={html => patch({ html })} />
        <AnchorFields value={props.value.anchor} onChange={anchor => patch({ anchor })} />
        <TextField label="CSS classes" value={props.value.styleClasses.join(" ")} error={props.errors.styleClasses} hint="Space-separated class names." onInput={classes => patch({ styleClasses: splitClasses(classes) })} />
      </>}

      {geometry && <>
        <RawHtmlWarning>Marker detail is rendered as HTML by BlueMap.</RawHtmlWarning>
        <TextField label="Detail (HTML)" multiline rows={4} value={props.value.detail} error={props.errors.detail} onInput={detail => patch({ detail })} />
        <div class="me-inline-fields">
          <TextField label="Link" value={props.value.link} error={props.errors.link} placeholder="https://…" onInput={link => patch({ link })} />
          <Checkbox label="Open in new tab" checked={props.value.newTab} onInput={newTab => patch({ newTab })} />
        </div>
      </>}

      {props.value.type === "line" && <GeometryLine value={props.value} patch={patch} onDraw={props.onDraw} errors={props.errors} />}
      {(props.value.type === "shape" || props.value.type === "extrude") && <GeometryShape value={props.value} patch={patch} onDraw={props.onDraw} errors={props.errors} />}

      <details class="me-advanced">
        <summary>Advanced display properties</summary>
        <NumberField label="Sorting" value={props.value.sorting} error={props.errors.sorting} onInput={sorting => patch({ sorting: sorting ?? 0 })} />
        <Checkbox label="Listed in marker menu" checked={props.value.listed} onInput={listed => patch({ listed })} />
        <div class="me-inline-fields">
          <NumberField label="Minimum distance" value={props.value.minDistance} min={0} step={1} error={props.errors.minDistance} onInput={minDistance => patch({ minDistance: minDistance ?? 0 })} />
          <NumberField label="Maximum distance" value={props.value.maxDistance} min={0} step={1} allowEmpty hint="Empty uses BlueMap's 10,000,000-block default." error={props.errors.maxDistance} onInput={maxDistance => patch({ maxDistance })} />
        </div>
      </details>
      <FormActions saving={props.saving} isNew={props.isNew} onCancel={props.onBack} />
    </form>
  );
}

function GeometryLine({ value, patch, onDraw, errors }: { value: MarkerModel; patch: (value: Partial<MarkerModel>) => void; onDraw: () => void; errors: Record<string, string> }) {
  return <fieldset class="me-fieldset">
    <legend>Line</legend>
    <div class="me-geometry-toolbar"><span>{value.line.length} points</span><button class="me-button is-map-action" type="button" onClick={onDraw}>Draw new line</button></div>
    {errors.line && <div class="me-field-error" role="alert">{errors.line}</div>}
    <Vec3List value={value.line} onInput={line => patch({ line, ...(value.positionMode === "center" ? { position: centerOf(line) } : {}) })} />
    <GeometryStyle value={value} patch={patch} />
  </fieldset>;
}

function GeometryShape({ value, patch, onDraw, errors }: { value: MarkerModel; patch: (value: Partial<MarkerModel>) => void; onDraw: () => void; errors: Record<string, string> }) {
  const updateGeometry = (shape: Vec2[], holes = value.holes) => patch({ shape, holes, ...(value.positionMode === "center" ? { position: geometryCenter({ ...value, shape, holes }) } : {}) });
  return <fieldset class="me-fieldset">
    <legend>{value.type === "shape" ? "Shape" : "Extrusion"}</legend>
    <div class="me-geometry-toolbar"><span>{value.shape.length} vertices · {value.holes.length} holes</span><button class="me-button is-map-action" type="button" onClick={onDraw}>Draw new shape</button></div>
    {(errors.shape || errors.holes) && <div class="me-field-error" role="alert">{errors.shape ?? errors.holes}</div>}
    <details class="me-coordinates">
      <summary>Outer ring coordinates</summary>
      <Vec2List value={value.shape} onInput={shape => updateGeometry(shape)} />
    </details>
    {value.holes.map((hole, index) => <details class="me-coordinates" key={index}>
      <summary>Hole {index + 1}</summary>
      <Vec2List value={hole} label="Vertex" onInput={next => updateGeometry(value.shape, value.holes.map((entry, i) => i === index ? next : entry))} />
      <button class="me-button is-danger-text" type="button" onClick={() => updateGeometry(value.shape, value.holes.filter((_, i) => i !== index))}>Remove hole</button>
    </details>)}
    <button class="me-button is-quiet" type="button" onClick={() => updateGeometry(value.shape, [...value.holes, []])}>Add hole manually</button>
    {value.type === "shape" ? (
      <NumberField label="Shape Y" value={value.shapeY} step={0.01} error={errors.shapeY} onInput={shapeY => patch({ shapeY: shapeY ?? 0 })} />
    ) : (
      <div class="me-inline-fields">
        <NumberField label="Minimum Y" value={value.minY} step={0.01} error={errors.minY} onInput={minY => patch({ minY: minY ?? 0 })} />
        <NumberField label="Maximum Y" value={value.maxY} step={0.01} error={errors.maxY} onInput={maxY => patch({ maxY: maxY ?? 0 })} />
      </div>
    )}
    <GeometryStyle value={value} patch={patch} fill />
  </fieldset>;
}

function GeometryStyle({ value, patch, fill }: { value: MarkerModel; patch: (value: Partial<MarkerModel>) => void; fill?: boolean }) {
  return <div class="me-geometry-style">
    <Checkbox label="Depth test" checked={value.depthTest} hint="Hide geometry behind terrain." onInput={depthTest => patch({ depthTest })} />
    <NumberField label="Line width" value={value.lineWidth} min={0} max={64} step={1} onInput={lineWidth => patch({ lineWidth: lineWidth ?? 0 })} />
    <ColorField label="Line color" value={value.lineColor} onInput={lineColor => patch({ lineColor })} />
    {fill && <ColorField label="Fill color" value={value.fillColor} onInput={fillColor => patch({ fillColor })} />}
  </div>;
}

function AnchorFields({ value, onChange }: { value: { x: number; y: number }; onChange: (value: { x: number; y: number }) => void }) {
  return <div class="me-inline-fields">
    <NumberField label="Anchor X" value={value.x} step={1} onInput={x => onChange({ ...value, x: x ?? 0 })} />
    <NumberField label="Anchor Y" value={value.y} step={1} onInput={y => onChange({ ...value, y: y ?? 0 })} />
  </div>;
}

function splitClasses(value: string): string[] {
  return value.trim() ? value.trim().split(/\s+/) : [];
}
