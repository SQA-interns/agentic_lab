export type RegistrationType = 'EXTERNAL' | 'STUDENT';

export type OptionCategory = 'WORKSHOP' | 'EVENT' | 'MEAL' | 'ACTIVITY';

export interface ConferenceOption {
  id: string;
  category: OptionCategory;
  name: string;
}

export interface RegistrationResponse {
  id: string;
  type: RegistrationType;
  createdAt: string;
}

export type FieldName =
  | 'firstName'
  | 'lastName'
  | 'email'
  | 'organization'
  | 'studyInstitution'
  | 'studyProgramme'
  | 'studentId';

export type FieldKind = 'name' | 'email' | 'text' | 'studentId';

export interface FieldDef {
  name: FieldName;
  label: string;
  kind: FieldKind;
  autoComplete: string;
}
