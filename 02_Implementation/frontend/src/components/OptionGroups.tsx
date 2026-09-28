import type { ConferenceOption, OptionCategory } from "../api";

const CATEGORY_LABELS: Record<OptionCategory, string> = {
  WORKSHOP: "Workshops",
  EVENT: "Events",
  MEAL: "Meals",
  OTHER: "Other activities",
};

const CATEGORY_ORDER: OptionCategory[] = ["WORKSHOP", "EVENT", "MEAL", "OTHER"];

interface OptionGroupsProps {
  options: ConferenceOption[];
  selected: string[];
  onToggle: (id: string) => void;
  error?: string;
}

/** Active conference options grouped by category; empty categories are hidden. */
export function OptionGroups({
  options,
  selected,
  onToggle,
  error,
}: OptionGroupsProps) {
  return (
    <div className="option-groups">
      {CATEGORY_ORDER.map((category) => {
        const inCategory = options.filter((o) => o.category === category);
        if (inCategory.length === 0) {
          return null;
        }
        return (
          <fieldset key={category}>
            <legend>{CATEGORY_LABELS[category]}</legend>
            {inCategory.map((option) => (
              <label key={option.id} className="checkbox">
                <input
                  type="checkbox"
                  name="optionIds"
                  value={option.id}
                  checked={selected.includes(option.id)}
                  onChange={() => onToggle(option.id)}
                />
                {option.name}
              </label>
            ))}
          </fieldset>
        );
      })}
      {error && (
        <p className="field-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
