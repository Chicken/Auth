import { ComponentChildren } from "preact";

export function Notice({ title, children }: { title: string; children: ComponentChildren }) {
  return <div class="me-notice"><span class="me-notice-icon" aria-hidden="true">⌖</span><h2>{title}</h2><div>{children}</div></div>;
}

export function RawHtmlWarning({ children }: { children: ComponentChildren }) {
  return <div class="me-alert is-warning"><strong>Raw HTML</strong><span>{children} Only enter code you trust.</span></div>;
}

export function FormHeading({ title, onBack }: { title: string; onBack: () => void }) {
  return <div class="me-form-heading"><button class="me-icon-button" type="button" aria-label="Back" onClick={onBack}>‹</button><h2>{title}</h2></div>;
}

export function FormActions({ saving, isNew, onCancel }: { saving: boolean; isNew: boolean; onCancel: () => void }) {
  return <div class="me-form-actions"><button class="me-button" type="button" onClick={onCancel}>Cancel</button><button class="me-button is-primary" type="submit" disabled={saving}>{saving ? "Saving…" : isNew ? "Create" : "Save changes"}</button></div>;
}
