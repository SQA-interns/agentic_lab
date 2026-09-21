/**
 * DOM helpers (specification § 9.4, § 11).
 *
 * Every dynamic value reaches the document through `textContent`; nothing in this module
 * assigns `innerHTML`, so a value stored verbatim from a form field can never execute as
 * markup (AC-G-09).
 */

export function element<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  attributes: Record<string, string> = {},
  text?: string,
): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);
  for (const [name, value] of Object.entries(attributes)) {
    node.setAttribute(name, value);
  }
  if (text !== undefined) {
    node.textContent = text;
  }
  return node;
}

export function clear(node: Element): void {
  while (node.firstChild) {
    node.removeChild(node.firstChild);
  }
}

export function requireElement<T extends Element>(selector: string): T {
  const node = document.querySelector<T>(selector);
  if (node === null) {
    throw new Error(`Required element not found: ${selector}`);
  }
  return node;
}
