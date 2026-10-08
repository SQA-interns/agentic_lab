import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { FormConfig } from "./api";
import { RegistrationForm } from "./RegistrationForm";

const config: FormConfig = {
  conferenceName: "C",
  recaptcha: { siteKey: "", testMode: true },
  categories: [{ category: "WORKSHOP", maxSelections: 1 }],
  options: [
    { id: "a", name: "A", category: "WORKSHOP", registrationTypes: ["EXTERNAL", "STUDENT"] },
    { id: "b", name: "B", category: "WORKSHOP", registrationTypes: ["EXTERNAL"] },
  ],
  consents: [{ id: "data", text: "D", mandatory: true }],
};

function fillExternal() {
  for (const [name, value] of [
    ["firstName", "Ana"],
    ["lastName", "Novak"],
    ["email", "ana@example.si"],
    ["organization", "O"],
  ]) {
    fireEvent.change(screen.getByTestId(`field-${name}`), { target: { value } });
  }
  fireEvent.click(screen.getByTestId("consent-data"));
  fireEvent.click(screen.getByTestId("recaptcha-test"));
}

function stubFetch(response: () => Promise<Response>) {
  const fetchMock = vi.fn(response);
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("RegistrationForm", () => {
  it("drops options the new registration type cannot select (D-11)", async () => {
    const fetchMock = stubFetch(async () => new Response('{"id":"x"}', { status: 201 }));
    const onAccepted = vi.fn();
    render(<RegistrationForm config={config} onAccepted={onAccepted} />);
    fireEvent.click(screen.getByTestId("option-b"));
    fireEvent.click(screen.getByTestId("type-student"));
    fireEvent.click(screen.getByTestId("type-external"));
    expect(screen.getByTestId("option-b")).not.toBeChecked();
    fillExternal();
    fireEvent.click(screen.getByTestId("submit"));
    await waitFor(() => expect(onAccepted).toHaveBeenCalled());
    const body = JSON.parse(
      (fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1].body as string,
    );
    expect(body.optionIds).toEqual([]);
  });

  it("reports too many options before sending (D-14)", () => {
    const fetchMock = stubFetch(async () => new Response("{}", { status: 201 }));
    render(<RegistrationForm config={config} onAccepted={vi.fn()} />);
    fillExternal();
    fireEvent.click(screen.getByTestId("option-a"));
    fireEvent.click(screen.getByTestId("option-b"));
    fireEvent.click(screen.getByTestId("submit"));
    expect(screen.getByTestId("error-optionIds")).toBeVisible();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("disables the button while a submission is pending, so it is sent once", async () => {
    let release: (r: Response) => void = () => {};
    const fetchMock = stubFetch(() => new Promise<Response>((resolve) => (release = resolve)));
    render(<RegistrationForm config={config} onAccepted={vi.fn()} />);
    fillExternal();
    fireEvent.click(screen.getByTestId("submit"));
    await waitFor(() => expect(screen.getByTestId("submit")).toBeDisabled());
    fireEvent.click(screen.getByTestId("submit"));
    release(
      new Response('{"title":"Too many attempts, please try again in a minute.","status":429}', {
        status: 429,
      }),
    );
    expect(await screen.findByTestId("form-error")).toHaveTextContent("Too many attempts");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("submit")).toBeEnabled();
  });

  it("shows server errors for fields it does not display as a form error", async () => {
    stubFetch(
      async () =>
        new Response('{"status":400,"errors":[{"field":"type","code":"malformed"}]}', {
          status: 400,
        }),
    );
    render(<RegistrationForm config={config} onAccepted={vi.fn()} />);
    fillExternal();
    fireEvent.click(screen.getByTestId("submit"));
    expect(await screen.findByTestId("form-error")).toHaveTextContent("could not be read");
  });

  it("links field errors to their inputs for assistive technology", () => {
    stubFetch(async () => new Response("{}", { status: 201 }));
    render(<RegistrationForm config={config} onAccepted={vi.fn()} />);
    fireEvent.click(screen.getByTestId("submit"));
    const input = screen.getByTestId("field-email");
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAttribute("aria-describedby", "error-email");
  });
});
