import type { ConferenceOption } from "../types";

interface OptionGroupProps {
  legend: string;
  options: ConferenceOption[];
  selected: ReadonlySet<string>;
  onToggle: (id: string, checked: boolean) => void;
}

export function OptionGroup({
  legend,
  options,
  selected,
  onToggle,
}: OptionGroupProps) {
  return (
    <fieldset className="option-group">
      <legend>{legend}</legend>
      {options.map((option) => (
        <label key={option.id} className="checkbox">
          <input
            type="checkbox"
            name="optionIds"
            value={option.id}
            checked={selected.has(option.id)}
            onChange={(event) => onToggle(option.id, event.target.checked)}
          />{" "}
          {option.name}
        </label>
      ))}
    </fieldset>
  );
}
