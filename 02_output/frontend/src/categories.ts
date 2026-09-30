import type { Category } from "./types";

// Category headings in display order (docs/02_contracts/ui.md item 4).
export const CATEGORY_HEADINGS: { category: Category; heading: string }[] = [
  { category: "workshop", heading: "Workshops" },
  { category: "event", heading: "Events" },
  { category: "meal", heading: "Meals" },
  { category: "other", heading: "Other activities" },
];
