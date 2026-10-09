import { fireEvent, screen } from "@testing-library/react";

/** Interactions with the registration page through the elements of ui-form.json. */

export async function chooseType(type: "EXTERNAL" | "STUDENT"): Promise<void> {
  const radio = await screen.findByTestId(type === "EXTERNAL" ? "type-external" : "type-student");
  fireEvent.click(radio);
}

export function fill(field: string, value: string): void {
  fireEvent.change(screen.getByTestId(`field-${field}`), { target: { value } });
}

export function check(testId: string): void {
  fireEvent.click(screen.getByTestId(testId));
}

export function submit(): void {
  fireEvent.click(screen.getByTestId("submit"));
}

export function fillValidExternal(): void {
  fill("firstName", "Ana");
  fill("lastName", "Novak");
  fill("email", "ana.novak@example.si");
  fill("organization", "Institut Jožef Stefan");
  check("consent-data-processing");
  check("captcha-test");
}

export function fillValidStudent(): void {
  fill("firstName", "Luka");
  fill("lastName", "Kranjc");
  fill("email", "luka.kranjc@example.si");
  fill("studyInstitution", "Fakulteta za računalništvo in informatiko");
  fill("studyProgramme", "Računalništvo in informatika");
  fill("studentId", "63200001");
  check("consent-data-processing");
  check("captcha-test");
}

/** The text and e-mail inputs of the form, i.e. the participant fields shown. */
export function participantInputs(): HTMLInputElement[] {
  const form = screen.getByTestId("registration-form");
  return Array.from(form.querySelectorAll<HTMLInputElement>("input[type=text], input[type=email]"));
}
