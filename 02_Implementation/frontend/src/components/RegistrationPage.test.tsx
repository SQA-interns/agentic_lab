import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  CONSENT_TEXT,
  GENERAL_FAILURE,
  RegistrationPage,
} from "./RegistrationPage";

const OPTIONS = [
  { id: "ws-1", category: "WORKSHOP", name: "Delavnica" },
  { id: "meal-1", category: "MEAL", name: "Kosilo" },
];

type Handler = (url: string, init?: RequestInit) => Response;
let postHandler: Handler;
let fetchMock: ReturnType<typeof vi.fn>;

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

beforeEach(() => {
  postHandler = () =>
    json(
      {
        registrationId: "11111111-2222-3333-4444-555555555555",
        submittedAt: "2026-09-29T10:00:00Z",
        type: "EXTERNAL",
        firstName: "Živa",
        lastName: "Čepič",
        email: "ziva@x.si",
        options: [OPTIONS[0]],
      },
      201,
    );
  fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (url === "/api/options") return Promise.resolve(json(OPTIONS));
    if (url === "/api/config")
      return Promise.resolve(
        json({ recaptchaSiteKey: "", recaptchaTestMode: true }),
      );
    return Promise.resolve(postHandler(url, init));
  });
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

async function renderLoaded() {
  render(<RegistrationPage />);
  await screen.findByRole("checkbox", { name: "I am not a robot" });
}

function fillExternal() {
  fireEvent.change(screen.getByLabelText("First name"), {
    target: { value: " Živa " },
  });
  fireEvent.change(screen.getByLabelText("Last name"), {
    target: { value: "Čepič" },
  });
  fireEvent.change(screen.getByLabelText("Email"), {
    target: { value: "ziva@x.si" },
  });
  fireEvent.change(screen.getByLabelText("Organization / institution"), {
    target: { value: "IJS" },
  });
  fireEvent.click(screen.getByRole("checkbox", { name: CONSENT_TEXT }));
  fireEvent.click(screen.getByRole("checkbox", { name: "I am not a robot" }));
}

function postedBody(): Record<string, unknown> {
  const call = fetchMock.mock.calls.find(
    ([url]) => url === "/api/registrations",
  );
  return JSON.parse((call?.[1] as RequestInit).body as string);
}

describe("RegistrationPage", () => {
  it("shows options grouped by set and an unchecked consent", async () => {
    await renderLoaded();

    const workshops = screen.getByRole("group", { name: "Workshops" });
    expect(
      within(workshops).getByRole("checkbox", { name: "Delavnica" }),
    ).not.toBeChecked();
    expect(screen.getByRole("group", { name: "Meals" })).toBeInTheDocument();
    expect(
      screen.queryByRole("group", { name: "Events" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("checkbox", { name: CONSENT_TEXT }),
    ).not.toBeChecked();
  });

  it("switches fields with the registration type", async () => {
    await renderLoaded();
    fireEvent.click(screen.getByRole("radio", { name: "Student" }));

    expect(screen.getByLabelText("Student ID")).toBeInTheDocument();
    expect(
      screen.queryByLabelText("Organization / institution"),
    ).not.toBeInTheDocument();
  });

  it("does not submit when client validation fails", async () => {
    await renderLoaded();
    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(await screen.findAllByText("This field is required.")).toHaveLength(
      4,
    );
    expect(screen.getByLabelText("First name")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(
      screen.getByText("Please confirm you are not a robot."),
    ).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalledWith(
      "/api/registrations",
      expect.anything(),
    );
  });

  it("submits trimmed values with only this type's fields and shows the confirmation", async () => {
    await renderLoaded();
    fillExternal();
    fireEvent.click(screen.getByRole("checkbox", { name: "Delavnica" }));
    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(
      await screen.findByRole("heading", { name: "Registration received" }),
    ).toBeVisible();
    expect(screen.getByText("Živa Čepič")).toBeInTheDocument();
    expect(postedBody()).toEqual({
      type: "EXTERNAL",
      firstName: "Živa",
      lastName: "Čepič",
      email: "ziva@x.si",
      organization: "IJS",
      optionIds: ["ws-1"],
      personalDataConsent: true,
      recaptchaToken: "test-pass",
    });
  });

  it("maps server field errors and resets the robot check after a reCAPTCHA failure", async () => {
    postHandler = () =>
      json(
        {
          code: "RECAPTCHA_FAILED",
          errors: [
            { field: "recaptchaToken", code: "RECAPTCHA_FAILED", message: "x" },
          ],
        },
        400,
      );
    await renderLoaded();
    fillExternal();
    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(
      await screen.findByText("Please confirm you are not a robot."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("checkbox", { name: "I am not a robot" }),
    ).not.toBeChecked();
    expect(
      screen.queryByRole("heading", { name: "Registration received" }),
    ).toBeNull();
  });

  it("shows option errors as a general message and reloads the options", async () => {
    postHandler = () =>
      json(
        {
          errors: [
            {
              field: "optionIds[0]",
              code: "INACTIVE_OPTION",
              message: "This option is no longer available.",
            },
          ],
        },
        400,
      );
    await renderLoaded();
    fillExternal();
    fireEvent.click(screen.getByRole("checkbox", { name: "Delavnica" }));
    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "This option is no longer available.",
    );
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.filter(([url]) => url === "/api/options"),
      ).toHaveLength(2),
    );
  });

  it("shows the general failure without confirmation on a server error", async () => {
    postHandler = () => json({ code: "REGISTRATION_NOT_SAVED" }, 500);
    await renderLoaded();
    fillExternal();
    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(GENERAL_FAILURE);
    expect(
      screen.queryByRole("heading", { name: "Registration received" }),
    ).toBeNull();
  });

  it("reports a form that could not be loaded", async () => {
    fetchMock.mockImplementation(() => Promise.resolve(json({}, 503)));
    render(<RegistrationPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "could not be loaded",
    );
  });
});
