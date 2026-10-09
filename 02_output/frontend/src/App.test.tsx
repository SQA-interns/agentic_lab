import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { App } from "./App";
import { externalForm, jsonResponse } from "./test/fixtures";

describe("App", () => {
  it("AC-004-01 replaces the form with the confirmation after acceptance", async () => {
    const accepted = {
      id: "1",
      type: "EXTERNAL",
      firstName: "Žan",
      lastName: "Čebašek",
      selectedOptions: [
        { id: "ev-a", name: "Gala večerja", category: "EVENT" },
      ],
    };
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(jsonResponse(200, externalForm))
        .mockResolvedValueOnce(jsonResponse(201, accepted)),
    );
    render(<App />);
    expect(screen.queryByTestId("field-firstName")).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId("type-external"));
    fireEvent.change(await screen.findByTestId("field-firstName"), {
      target: { value: "Žan" },
    });
    fireEvent.change(screen.getByTestId("field-lastName"), {
      target: { value: "Čebašek" },
    });
    fireEvent.change(screen.getByTestId("field-email"), {
      target: { value: "zan@example.si" },
    });
    fireEvent.change(screen.getByTestId("field-organization"), {
      target: { value: "IJS" },
    });
    fireEvent.click(screen.getByTestId("consent-privacy"));
    fireEvent.click(screen.getByTestId("captcha-test-checkbox"));
    fireEvent.click(screen.getByTestId("submit"));

    expect(await screen.findByTestId("confirmation")).toHaveTextContent(
      "Registration received",
    );
    expect(screen.getByTestId("confirmation-name")).toHaveTextContent(
      "Žan Čebašek",
    );
    expect(screen.getByTestId("confirmation-options")).toHaveTextContent(
      "Events: Gala večerja",
    );
    expect(screen.queryByTestId("submit")).not.toBeInTheDocument();
  });

  it("marks the chosen type", () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(jsonResponse(200, externalForm)),
    );
    render(<App />);

    fireEvent.click(screen.getByTestId("type-student"));

    expect(screen.getByTestId("type-student")).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.getByTestId("type-external")).toHaveAttribute(
      "aria-pressed",
      "false",
    );
  });
});
