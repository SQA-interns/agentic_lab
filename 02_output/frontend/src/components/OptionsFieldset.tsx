import { messages } from "../messages";
import type { Category, FormOption } from "../types";

interface OptionsFieldsetProps {
  category: Category;
  options: FormOption[];
  selected: string[];
  invalid: boolean;
  onToggle: (optionId: string) => void;
}

/** The options of one category available to the chosen registration type (AC-003-01). */
export function OptionsFieldset({
  category,
  options,
  selected,
  invalid,
  onToggle,
}: OptionsFieldsetProps) {
  return (
    <fieldset data-testid={`options-${category}`} className="options">
      <legend>{messages.categories[category]}</legend>
      {options.map((option) => (
        <label key={option.id} className="choice">
          <input
            type="checkbox"
            data-testid={`option-${option.id}`}
            checked={selected.includes(option.id)}
            aria-describedby={invalid ? "optionIds-error" : undefined}
            onChange={() => onToggle(option.id)}
          />
          {option.name}
        </label>
      ))}
    </fieldset>
  );
}
