import { CATEGORY_HEADINGS } from "./categories";
import type { ConferenceOption } from "./types";

// Active options grouped by category (US-003, docs/02_contracts/ui.md item 4).

type Props = {
  options: ConferenceOption[];
  selected: string[];
  onToggle: (id: string) => void;
  error?: string;
};

export function OptionGroups({ options, selected, onToggle, error }: Props) {
  return (
    <fieldset aria-describedby={error ? "optionIds-error" : undefined}>
      <legend>Options</legend>
      {CATEGORY_HEADINGS.map(({ category, heading }) => {
        const inCategory = options.filter((o) => o.category === category);
        if (inCategory.length === 0) return null;
        const headingId = `options-${category}`;
        return (
          <section key={category} aria-labelledby={headingId}>
            <h2 id={headingId}>{heading}</h2>
            {inCategory.map((o) => (
              <div key={o.id} className="choice">
                <input
                  type="checkbox"
                  id={`option-${o.id}`}
                  checked={selected.includes(o.id)}
                  onChange={() => onToggle(o.id)}
                />
                <label htmlFor={`option-${o.id}`}>{o.name}</label>
              </div>
            ))}
          </section>
        );
      })}
      {error && (
        <p id="optionIds-error" className="error">
          {error}
        </p>
      )}
    </fieldset>
  );
}
