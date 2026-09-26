import "@testing-library/jest-dom/vitest";
import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { fireEvent } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ConferenceConfig } from "../types";
import { RegistrationPage } from "./RegistrationPage";

const config: ConferenceConfig = {
  options: [
    { id: "ws-a", name: "Workshop A", category: "WORKSHOP" },
    { id: "ev-dinner", name: "Conference dinner", category: "EVENT" },
    { id: "meal-lunch", name: "Lunch", category: "MEAL" },
    { id: "other-tour", name: "City tour", category: "OTHER" },
  ],
  consents: [
    { id: "privacy", label: "I agree to data processing", required: true },
    { id: "photos", label: "Photos may be taken", required: false },
  ],
  recaptcha: { siteKey: "", testMode: true },
};

type Handler = (
  url: string,
  init?: RequestInit,
) => Response | Promise<Response>;

let submitHandler: Handler;
let fetchMock: ReturnType<typeof vi.fn>;

function json(body: unknown, status: number) {
  return new Response(JSON.stringify(body), { status });
}

beforeEach(() => {
  submitHandler = () =>
    json({ registrationId: "abc-123", type: "EXTERNAL" }, 201);
  fetchMock = vi.fn((url: string, init?: RequestInit) =>
    Promise.resolve(
      url === "/api/conference" ? json(config, 200) : submitHandler(url, init),
    ),
  );
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

async function renderForm() {
  render(<RegistrationPage />);
  await screen.findByRole("form", { name: "Conference registration" });
}

function fill(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(new RegExp(`^${label}`)), {
    target: { value },
  });
}

function fillExternal() {
  fill("First name", "  Žiga ");
  fill("Last name", "Šušteršič");
  fill("Email", "ziga@example.si");
  fill("Organization / institution", "Univerza v Ljubljani");
}

function acceptConsentAndCaptcha() {
  fireEvent.click(screen.getByLabelText(/I agree to data processing/));
  fireEvent.click(screen.getByLabelText(/I am not a robot/));
}

function submittedBody() {
  const call = fetchMock.mock.calls.find(
    ([url]) => url === "/api/registrations",
  );
  return JSON.parse((call?.[1] as RequestInit).body as string);
}

describe("RegistrationPage", () => {
  it("shows the external participant fields by default", async () => {
    await renderForm();
    for (const label of [
      "First name",
      "Last name",
      "Email",
      "Organization / institution",
    ]) {
      expect(
        screen.getByLabelText(new RegExp(`^${label}`)),
      ).toBeInTheDocument();
    }
    expect(screen.queryByLabelText(/^Student ID/)).not.toBeInTheDocument();
  });

  it("shows the student fields after switching type", async () => {
    await renderForm();
    fireEvent.click(screen.getByLabelText("Student"));
    for (const label of [
      "First name",
      "Last name",
      "Email",
      "Study institution",
      "Study programme",
      "Student ID",
    ]) {
      expect(
        screen.getByLabelText(new RegExp(`^${label}`)),
      ).toBeInTheDocument();
    }
    expect(screen.queryByLabelText(/^Organization/)).not.toBeInTheDocument();
  });

  it("offers the configured options grouped by category", async () => {
    await renderForm();
    for (const [group, option] of [
      ["Workshops", "Workshop A"],
      ["Events", "Conference dinner"],
      ["Meals", "Lunch"],
      ["Other activities", "City tour"],
    ]) {
      const fieldset = screen.getByRole("group", { name: group });
      expect(within(fieldset).getByLabelText(option)).toBeInTheDocument();
    }
  });

  it("does not preselect consents", async () => {
    await renderForm();
    expect(
      screen.getByLabelText(/I agree to data processing/),
    ).not.toBeChecked();
    expect(screen.getByLabelText(/Photos may be taken/)).not.toBeChecked();
  });

  it("blocks submission with client-side errors and shows no confirmation", async () => {
    await renderForm();
    fill("Email", "invalid");
    fireEvent.click(screen.getByRole("button", { name: "Register" }));
    expect(
      await screen.findByText("Enter a valid email address."),
    ).toBeInTheDocument();
    expect(screen.getAllByText("This field is required.")).toHaveLength(3);
    expect(screen.getByText("This consent is required.")).toBeInTheDocument();
    expect(
      screen.getByText("Please confirm that you are not a robot."),
    ).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalledWith(
      "/api/registrations",
      expect.anything(),
    );
    expect(screen.queryByText("Registration received")).not.toBeInTheDocument();
  });

  it("submits only the fields of the selected type and shows the confirmation", async () => {
    await renderForm();
    fireEvent.click(screen.getByLabelText("Student"));
    fill("First name", "Ana");
    fill("Last name", "Čeč");
    fill("Email", "ana@example.si");
    fill("Study institution", "FRI");
    fill("Study programme", "Informatika");
    fill("Student ID", "63200001");
    fireEvent.click(screen.getByLabelText("Conference dinner"));
    acceptConsentAndCaptcha();
    submitHandler = () =>
      json({ registrationId: "st-1", type: "STUDENT" }, 201);
    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(
      await screen.findByText("Registration received"),
    ).toBeInTheDocument();
    expect(screen.getByText("st-1")).toBeInTheDocument();
    const body = submittedBody();
    expect(body).toMatchObject({
      type: "STUDENT",
      studentId: "63200001",
      optionIds: ["ev-dinner"],
      consentIds: ["privacy"],
      recaptchaToken: "test-mode-token",
    });
    expect(body).not.toHaveProperty("organization");
  });

  it("confirms an external registration only after a successful response", async () => {
    let resolve: (r: Response) => void = () => undefined;
    submitHandler = () => new Promise<Response>((r) => (resolve = r));
    await renderForm();
    fillExternal();
    fireEvent.click(screen.getByLabelText("Workshop A"));
    acceptConsentAndCaptcha();
    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "Submitting…" }),
      ).toBeDisabled(),
    );
    expect(screen.queryByText("Registration received")).not.toBeInTheDocument();
    resolve(json({ registrationId: "abc-123", type: "EXTERNAL" }, 201));
    expect(
      await screen.findByText("Registration received"),
    ).toBeInTheDocument();
    expect(submittedBody()).toMatchObject({
      firstName: "  Žiga ",
      lastName: "Šušteršič",
      optionIds: ["ws-a"],
    });
  });

  it("shows server validation errors and no confirmation", async () => {
    submitHandler = () =>
      json(
        {
          code: "VALIDATION_FAILED",
          message: "Please correct the highlighted fields.",
          fieldErrors: [
            {
              field: "optionIds",
              message: "Contains an unknown or unavailable option.",
            },
          ],
        },
        400,
      );
    await renderForm();
    fillExternal();
    acceptConsentAndCaptcha();
    fireEvent.click(screen.getByRole("button", { name: "Register" }));
    expect(
      await screen.findByText("Contains an unknown or unavailable option."),
    ).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Please correct the highlighted fields.",
    );
    expect(screen.queryByText("Registration received")).not.toBeInTheDocument();
    expect(screen.getByLabelText(/I am not a robot/)).not.toBeChecked();
  });

  it("shows a general error and keeps the data when processing fails", async () => {
    submitHandler = () =>
      json(
        {
          code: "REGISTRATION_FAILED",
          message:
            "Your registration could not be completed. Please try again later.",
          fieldErrors: [],
        },
        500,
      );
    await renderForm();
    fillExternal();
    acceptConsentAndCaptcha();
    fireEvent.click(screen.getByRole("button", { name: "Register" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "could not be completed",
    );
    expect(screen.queryByText("Registration received")).not.toBeInTheDocument();
    expect(screen.getByLabelText(/^Last name/)).toHaveValue("Šušteršič");
  });

  it("renders user-provided text as text, not markup", async () => {
    submitHandler = () =>
      json(
        {
          code: "VALIDATION_FAILED",
          message: "x",
          fieldErrors: [
            { field: "firstName", message: "<img src=x onerror=alert(1)>" },
          ],
        },
        400,
      );
    await renderForm();
    fillExternal();
    acceptConsentAndCaptcha();
    fireEvent.click(screen.getByRole("button", { name: "Register" }));
    expect(
      await screen.findByText("<img src=x onerror=alert(1)>"),
    ).toBeInTheDocument();
    expect(document.querySelector("img")).toBeNull();
  });

  it("offers a retry when the configuration cannot be loaded", async () => {
    fetchMock.mockImplementationOnce(() =>
      Promise.resolve(new Response("", { status: 500 })),
    );
    render(<RegistrationPage />);
    const retry = await screen.findByRole("button", { name: "Retry" });
    fireEvent.click(retry);
    expect(
      await screen.findByRole("form", { name: "Conference registration" }),
    ).toBeInTheDocument();
  });

  it("offers no account, login, payment or edit functions", async () => {
    await renderForm();
    expect(
      screen.queryByText(/log ?in|sign ?in|password|payment|pay now|edit/i),
    ).toBeNull();
  });
});
