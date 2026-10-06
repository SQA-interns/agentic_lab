import type { ConferenceOption, OptionCategory } from "../api/types";
import { LABELS, MESSAGES } from "../messages";

const CATEGORIES: OptionCategory[] = ["WORKSHOP", "EVENT", "MEAL", "OTHER"];

interface Props {
  options: ConferenceOption[];
  selected: string[];
  error: boolean;
  onToggle: (id: string) => void;
}

/** Offered options grouped by category (AC-001-11). */
export function OptionGroups({ options, selected, error, onToggle }: Props) {
  return (
    <div className="options">
      {error && (
        <p data-testid="error-optionIds" className="error" role="alert">
          {MESSAGES.OPTION}
        </p>
      )}
      {CATEGORIES.map((category) => {
        const inCategory = options.filter((o) => o.category === category);
        return (
          <fieldset key={category} data-testid={`options-${category}`}>
            <legend>{LABELS[category]}</legend>
            {inCategory.length === 0 && <p className="hint">No options offered.</p>}
            {inCategory.map((o) => (
              <div className="check" key={o.id}>
                <input
                  type="checkbox"
                  id={`option-${o.id}`}
                  data-testid={`option-${o.id}`}
                  checked={selected.includes(o.id)}
                  onChange={() => onToggle(o.id)}
                />
                <label htmlFor={`option-${o.id}`}>{o.name}</label>
              </div>
            ))}
          </fieldset>
        );
      })}
    </div>
  );
}
