import type { ConferenceOption, OptionCategory } from "../api/client";

export const CATEGORY_HEADINGS: Record<OptionCategory, string> = {
  WORKSHOP: "Workshops",
  EVENT: "Events",
  MEAL: "Meals",
  OTHER: "Other activities",
};

const CATEGORY_ORDER: OptionCategory[] = ["WORKSHOP", "EVENT", "MEAL", "OTHER"];

interface OptionGroupsProps {
  options: ConferenceOption[];
  selected: string[];
  onToggle: (id: string, checked: boolean) => void;
}

/** Active options grouped by set; empty sets are hidden (specification §12). */
export function OptionGroups({
  options,
  selected,
  onToggle,
}: OptionGroupsProps) {
  return (
    <>
      {CATEGORY_ORDER.map((category) => {
        const inCategory = options.filter((o) => o.category === category);
        if (inCategory.length === 0) return null;
        return (
          <fieldset key={category} data-option-group className="option-group">
            <legend>{CATEGORY_HEADINGS[category]}</legend>
            {inCategory.map((option) => (
              <label key={option.id} className="checkbox">
                <input
                  type="checkbox"
                  checked={selected.includes(option.id)}
                  onChange={(e) => onToggle(option.id, e.target.checked)}
                />
                {option.name}
              </label>
            ))}
          </fieldset>
        );
      })}
    </>
  );
}
