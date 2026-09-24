import { CATEGORY_LABELS, CATEGORY_ORDER } from '../formConfig';
import type { ConferenceOption } from '../types';

interface OptionGroupsProps {
  options: readonly ConferenceOption[];
  selected: ReadonlySet<string>;
  disabled: boolean;
  error: string | undefined;
  onToggle: (id: string) => void;
}

/** Configurable options grouped by category; empty categories are not shown (US-003). */
export function OptionGroups({ options, selected, disabled, error, onToggle }: OptionGroupsProps) {
  const groups = CATEGORY_ORDER.map((category) => ({
    category,
    items: options.filter((o) => o.category === category),
  })).filter((g) => g.items.length > 0);

  if (groups.length === 0) {
    return null;
  }
  return (
    <div className="option-groups">
      {groups.map((group) => (
        <fieldset key={group.category} className="option-group">
          <legend>{CATEGORY_LABELS[group.category]}</legend>
          {group.items.map((option) => (
            <label key={option.id} className="checkbox">
              <input
                type="checkbox"
                name="optionIds"
                value={option.id}
                checked={selected.has(option.id)}
                disabled={disabled}
                onChange={() => {
                  onToggle(option.id);
                }}
              />
              <span>{option.name}</span>
            </label>
          ))}
        </fieldset>
      ))}
      {error !== undefined && (
        <p className="field-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
