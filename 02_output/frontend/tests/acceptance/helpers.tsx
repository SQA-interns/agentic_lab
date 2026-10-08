import { fireEvent, render, screen } from "@testing-library/react";
import { App } from "../../src/App";

export async function openForm() {
  render(<App />);
  return screen.findByTestId("registration-page");
}

export async function chooseType(type: "EXTERNAL" | "STUDENT") {
  fireEvent.click(
    await screen.findByTestId(type === "EXTERNAL" ? "type-external" : "type-student"),
  );
}

export async function fill(name: string, value: string) {
  fireEvent.change(await screen.findByTestId(`field-${name}`), { target: { value } });
}

export async function fillValidExternal() {
  await chooseType("EXTERNAL");
  await fill("firstName", "Ana");
  await fill("lastName", "Novak");
  await fill("email", "ana.novak@example.si");
  await fill("organization", "Institut Primer");
  fireEvent.click(await screen.findByTestId("option-ws-testing"));
  fireEvent.click(await screen.findByTestId("consent-data-processing"));
  fireEvent.click(await screen.findByTestId("recaptcha-test"));
}

export async function fillValidStudent() {
  await chooseType("STUDENT");
  await fill("firstName", "Luka");
  await fill("lastName", "Horvat");
  await fill("email", "luka.horvat@student.example.si");
  await fill("studyInstitution", "Univerza v Mariboru");
  await fill("studyProgramme", "Informatika");
  await fill("studentId", "E1234567");
  fireEvent.click(await screen.findByTestId("option-ev-career-fair"));
  fireEvent.click(await screen.findByTestId("consent-data-processing"));
  fireEvent.click(await screen.findByTestId("recaptcha-test"));
}

export async function submit() {
  fireEvent.click(await screen.findByTestId("submit"));
}
