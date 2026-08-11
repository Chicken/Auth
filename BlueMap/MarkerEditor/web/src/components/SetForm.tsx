import { MarkerSetModel, Scope } from "../types";
import { Checkbox, Field, NumberField, TextField } from "./Fields";
import { FormActions, FormHeading } from "./FormChrome";

interface SetFormProps {
  value: MarkerSetModel;
  onChange: (value: MarkerSetModel) => void;
  onSave: () => void;
  onBack: () => void;
  errors: Record<string, string>;
  isNew: boolean;
  saving: boolean;
  canCreateGlobal: boolean;
}

export function SetForm(props: SetFormProps) {
  const patch = (value: Partial<MarkerSetModel>) => props.onChange({ ...props.value, ...value });
  return (
    <form class="me-form" onSubmit={event => { event.preventDefault(); props.onSave(); }}>
      <FormHeading title={props.isNew ? "New marker set" : "Set properties"} onBack={props.onBack} />
      {props.isNew && <Field label="Visibility">
        <select class="me-input" value={props.value.scope} onChange={event => patch({ scope: event.currentTarget.value as Scope })}>
          <option value="private">Private to me</option>
          {props.canCreateGlobal && <option value="global">Global</option>}
        </select>
      </Field>}
      <TextField label="Key" required mono value={props.value.key} error={props.errors.key} hint="Stable ID within this map. Letters, numbers, dots, dashes, and underscores." onInput={key => patch({ key })} />
      <TextField label="Label" required value={props.value.label} error={props.errors.label} onInput={label => patch({ label })} />
      <NumberField label="Sorting" value={props.value.sorting} error={props.errors.sorting} onInput={sorting => patch({ sorting: sorting ?? 0 })} />
      <Checkbox label="Toggleable" checked={props.value.toggleable} onInput={toggleable => patch({ toggleable })} />
      <Checkbox label="Hidden by default" checked={props.value.defaultHidden} disabled={!props.value.toggleable} onInput={defaultHidden => patch({ defaultHidden })} />
      <FormActions saving={props.saving} isNew={props.isNew} onCancel={props.onBack} />
    </form>
  );
}
