import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { RegistrationForm } from "./RegistrationForm";
import { externalForm, jsonResponse } from "./test/fixtures";

function setup(responses: Response[]) {
  const fetchMock = vi.fn();
  responses.forEach((r) => fetchMock.mockResolvedValueOnce(r));
  vi.stubGlobal("fetch", fetchMock);
  const onAccepted = vi.fn();
  render(<RegistrationForm type="EXTERNAL" onAccepted={onAccepted} />);
  return { fetchMock, onAccepted };
}

async function fillValid() {
  await screen.findByTestId("field-firstName");
  fireEvent.change(screen.getByTestId("field-firstName"), {
    target: { value: "Ana" },
  });
  fireEvent.change(screen.getByTestId("field-lastName"), {
    target: { value: "Novak" },
  });
  fireEvent.change(screen.getByTestId("field-email"), {
    target: { value: "ana@example.si" },
  });
  fireEvent.change(screen.getByTestId("field-organization"), {
    target: { value: "IJS" },
  });
  fireEvent.click(screen.getByTestId("consent-privacy"));
  fireEvent.click(screen.getByTestId("captcha-test-checkbox"));
}

describe("RegistrationForm (NFR-03)", () => {
  it("AC-001-01 AC-001-09 AC-001-13 renders the fields, grouped options and unticked consents", async () => {
    setup([jsonResponse(200, externalForm)]);

    expect(
      await screen.findByLabelText("Organization / institution"),
    ).toBeInTheDocument();
    expect(screen.getByTestId("category-EVENT")).toHaveTextContent(
      "Gala večerja",
    );
    expect(screen.getByTestId("category-MEAL")).toHaveTextContent(
      "No options available.",
    );
    expect(screen.getByTestId("consent-privacy")).not.toBeChecked();
    expect(screen.getByTestId("consent-photo")).not.toBeChecked();
    expect(
      screen.getByText("I am not a robot (test mode)"),
    ).toBeInTheDocument();
  });

  it("NFR-03 shows client errors next to the fields and sends nothing", async () => {
    const { fetchMock } = setup([jsonResponse(200, externalForm)]);
    await screen.findByTestId("submit");

    fireEvent.click(screen.getByTestId("submit"));

    expect(screen.getByTestId("error-firstName")).toHaveTextContent(
      "This field is required.",
    );
    expect(screen.getByTestId("field-firstName")).toHaveAttribute(
      "aria-describedby",
      "error-firstName",
    );
    expect(screen.getByTestId("error-consent-privacy")).toHaveTextContent(
      "This consent is required.",
    );
    expect(screen.getByTestId("error-recaptchaToken")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("AC-001-12 disables further options once a category is full", async () => {
    setup([jsonResponse(200, externalForm)]);
    await screen.findByTestId("option-ws-a");

    fireEvent.click(screen.getByTestId("option-ws-a"));

    expect(screen.getByTestId("option-ws-b")).toBeDisabled();
    fireEvent.click(screen.getByTestId("option-ws-a"));
    expect(screen.getByTestId("option-ws-b")).toBeEnabled();
  });

  it("AC-004-01 reports an accepted registration", async () => {
    const accepted = {
      id: "1",
      type: "EXTERNAL",
      firstName: "Ana",
      lastName: "Novak",
      selectedOptions: [],
    };
    const { onAccepted } = setup([
      jsonResponse(200, externalForm),
      jsonResponse(201, accepted),
    ]);
    await fillValid();

    fireEvent.click(screen.getByTestId("submit"));

    await waitFor(() => expect(onAccepted).toHaveBeenCalledWith(accepted));
  });

  it("AC-004-02 shows server field errors and resets the anti-automation check", async () => {
    setup([
      jsonResponse(200, externalForm),
      jsonResponse(409, {
        errors: [{ field: "email", code: "ALREADY_REGISTERED" }],
      }),
    ]);
    await fillValid();

    fireEvent.click(screen.getByTestId("submit"));

    expect(await screen.findByTestId("error-email")).toHaveTextContent(
      "A registration with this email already exists.",
    );
    expect(screen.getByTestId("captcha-test-checkbox")).not.toBeChecked();
  });

  it("AC-004-03 shows the general error when storage fails", async () => {
    setup([
      jsonResponse(200, externalForm),
      jsonResponse(503, { title: "Service Unavailable" }),
    ]);
    await fillValid();

    fireEvent.click(screen.getByTestId("submit"));

    expect(await screen.findByTestId("form-error")).toHaveTextContent(
      "Your registration could not be saved. Please try again later.",
    );
  });

  it("shows the rate-limit message on 429", async () => {
    setup([jsonResponse(200, externalForm), jsonResponse(429, {})]);
    await fillValid();

    fireEvent.click(screen.getByTestId("submit"));

    expect(await screen.findByTestId("form-error")).toHaveTextContent(
      "Too many attempts.",
    );
  });

  it("disables the submit button while the request is in flight", async () => {
    let release: (r: Response) => void = () => undefined;
    const pending = new Promise<Response>((resolve) => (release = resolve));
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, externalForm));
    fetchMock.mockReturnValueOnce(pending);
    vi.stubGlobal("fetch", fetchMock);
    render(<RegistrationForm type="EXTERNAL" onAccepted={vi.fn()} />);
    await fillValid();

    fireEvent.click(screen.getByTestId("submit"));

    expect(screen.getByTestId("submit")).toBeDisabled();
    await act(async () => release(jsonResponse(503, {})));
    expect(screen.getByTestId("submit")).toBeEnabled();
  });

  it("shows the general error when the form cannot be loaded", async () => {
    setup([jsonResponse(500, {})]);

    expect(await screen.findByTestId("form-error")).toBeInTheDocument();
  });

  it("KP-03 trims a field when it loses focus", async () => {
    setup([jsonResponse(200, externalForm)]);
    const field = await screen.findByTestId("field-firstName");

    fireEvent.change(field, { target: { value: " Ana " } });
    fireEvent.blur(field);

    expect(field).toHaveValue("Ana");
  });
});
