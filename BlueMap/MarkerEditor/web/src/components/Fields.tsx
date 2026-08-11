import { ComponentChildren } from "preact";
import { MarkerColor, Vec2, Vec3 } from "../types";

interface FieldProps {
  label: string;
  hint?: string;
  error?: string;
  className?: string;
  children: ComponentChildren;
}

export function Field({ label, hint, error, className = "", children }: FieldProps) {
  return (
    <label class={`me-field ${className} ${error ? "has-error" : ""}`}>
      <span class="me-field-label">{label}</span>
      {children}
      {hint && <span class="me-field-hint">{hint}</span>}
      {error && <span class="me-field-error" role="alert">{error}</span>}
    </label>
  );
}

interface TextFieldProps {
  label: string;
  value: string;
  onInput: (value: string) => void;
  hint?: string;
  error?: string;
  required?: boolean;
  multiline?: boolean;
  rows?: number;
  placeholder?: string;
  spellcheck?: boolean;
  mono?: boolean;
}

export function TextField(props: TextFieldProps) {
  const common = {
    value: props.value,
    required: props.required,
    placeholder: props.placeholder,
    spellcheck: props.spellcheck,
    class: `me-input ${props.mono ? "is-mono" : ""}`,
    onInput: (event: Event) => props.onInput((event.currentTarget as HTMLInputElement | HTMLTextAreaElement).value),
  };
  return (
    <Field label={props.label} hint={props.hint} error={props.error}>
      {props.multiline
        ? <textarea {...common} rows={props.rows ?? 4} />
        : <input {...common} type="text" />}
    </Field>
  );
}

interface NumberFieldProps {
  label: string;
  value: number | null;
  onInput: (value: number | null) => void;
  hint?: string;
  error?: string;
  min?: number;
  max?: number;
  step?: number;
  allowEmpty?: boolean;
}

export function NumberField({ label, value, onInput, hint, error, min, max, step = 1, allowEmpty }: NumberFieldProps) {
  return (
    <Field label={label} hint={hint} error={error}>
      <input
        class="me-input is-mono"
        type="number"
        value={value ?? ""}
        min={min}
        max={max}
        step={step}
        onInput={event => {
          const raw = event.currentTarget.value;
          if (allowEmpty && raw === "") onInput(null);
          else onInput(Number(raw));
        }}
      />
    </Field>
  );
}

interface CheckboxProps {
  label: string;
  checked: boolean;
  onInput: (checked: boolean) => void;
  hint?: string;
  disabled?: boolean;
}

export function Checkbox({ label, checked, onInput, hint, disabled }: CheckboxProps) {
  return (
    <label class={`me-check ${disabled ? "is-disabled" : ""}`}>
      <input type="checkbox" checked={checked} disabled={disabled} onInput={event => onInput(event.currentTarget.checked)} />
      <span class="me-check-box" aria-hidden="true" />
      <span>
        <span class="me-check-label">{label}</span>
        {hint && <span class="me-field-hint">{hint}</span>}
      </span>
    </label>
  );
}

export function Vec3Fields({ value, onInput, prefix = "" }: { value: Vec3; onInput: (value: Vec3) => void; prefix?: string }) {
  return (
    <div class="me-coordinate-grid">
      <NumberField label={`${prefix}X`} value={value.x} step={0.01} onInput={x => onInput({ ...value, x: x ?? 0 })} />
      <NumberField label={`${prefix}Y`} value={value.y} step={0.01} onInput={y => onInput({ ...value, y: y ?? 0 })} />
      <NumberField label={`${prefix}Z`} value={value.z} step={0.01} onInput={z => onInput({ ...value, z: z ?? 0 })} />
    </div>
  );
}

export function Vec3List({ value, onInput, minimum = 0 }: { value: Vec3[]; onInput: (value: Vec3[]) => void; minimum?: number }) {
  const update = (index: number, point: Vec3) => onInput(value.map((entry, i) => i === index ? point : entry));
  return (
    <div class="me-point-list">
      {value.map((point, index) => (
        <div class="me-point-row" key={index}>
          <span class="me-point-index">{index + 1}</span>
          <input aria-label={`Point ${index + 1} X`} type="number" step="0.01" value={point.x} onInput={e => update(index, { ...point, x: Number(e.currentTarget.value) })} />
          <input aria-label={`Point ${index + 1} Y`} type="number" step="0.01" value={point.y} onInput={e => update(index, { ...point, y: Number(e.currentTarget.value) })} />
          <input aria-label={`Point ${index + 1} Z`} type="number" step="0.01" value={point.z} onInput={e => update(index, { ...point, z: Number(e.currentTarget.value) })} />
          <button class="me-icon-button" type="button" title="Remove point" aria-label={`Remove point ${index + 1}`} disabled={value.length <= minimum} onClick={() => onInput(value.filter((_, i) => i !== index))}>×</button>
        </div>
      ))}
      <button class="me-button is-quiet" type="button" onClick={() => onInput([...value, { x: 0, y: 64, z: 0 }])}>Add point</button>
    </div>
  );
}

export function Vec2List({ value, onInput, minimum = 0, label = "Vertex" }: { value: Vec2[]; onInput: (value: Vec2[]) => void; minimum?: number; label?: string }) {
  const update = (index: number, point: Vec2) => onInput(value.map((entry, i) => i === index ? point : entry));
  return (
    <div class="me-point-list">
      {value.map((point, index) => (
        <div class="me-point-row is-2d" key={index}>
          <span class="me-point-index">{index + 1}</span>
          <input aria-label={`${label} ${index + 1} X`} type="number" step="0.01" value={point.x} onInput={e => update(index, { ...point, x: Number(e.currentTarget.value) })} />
          <input aria-label={`${label} ${index + 1} Z`} type="number" step="0.01" value={point.z} onInput={e => update(index, { ...point, z: Number(e.currentTarget.value) })} />
          <button class="me-icon-button" type="button" title={`Remove ${label.toLowerCase()}`} aria-label={`Remove ${label.toLowerCase()} ${index + 1}`} disabled={value.length <= minimum} onClick={() => onInput(value.filter((_, i) => i !== index))}>×</button>
        </div>
      ))}
      <button class="me-button is-quiet" type="button" onClick={() => onInput([...value, { x: 0, z: 0 }])}>Add {label.toLowerCase()}</button>
    </div>
  );
}

function hexPart(value: number): string {
  return Math.max(0, Math.min(255, Math.round(value))).toString(16).padStart(2, "0");
}

function toHex(color: MarkerColor): string {
  return `#${hexPart(color.r)}${hexPart(color.g)}${hexPart(color.b)}`;
}

function fromHex(hex: string, alpha: number): MarkerColor {
  return {
    r: Number.parseInt(hex.slice(1, 3), 16),
    g: Number.parseInt(hex.slice(3, 5), 16),
    b: Number.parseInt(hex.slice(5, 7), 16),
    a: alpha,
  };
}

export function ColorField({ label, value, onInput }: { label: string; value: MarkerColor; onInput: (color: MarkerColor) => void }) {
  return (
    <Field label={label}>
      <div class="me-color-field">
        <input type="color" value={toHex(value)} aria-label={`${label} RGB`} onInput={event => onInput(fromHex(event.currentTarget.value, value.a))} />
        <span class="me-color-value is-mono">{toHex(value)}</span>
        <label>
          <span>Opacity</span>
          <input type="number" min="0" max="1" step="0.05" value={value.a} onInput={event => onInput({ ...value, a: Number(event.currentTarget.value) })} />
        </label>
      </div>
    </Field>
  );
}
