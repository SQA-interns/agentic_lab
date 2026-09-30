import { codePointLength, isEmail, trimValue, validateClient } from './validation';

describe('validation', () => {
  it('trims NBSP and other Unicode spaces', () => {
    expect(trimValue('   Žiga  ')).toBe('Žiga');
    expect(trimValue(' ')).toBe('');
  });

  it.each(['a@example.test', 'first.last+x@sub.example.co', "o'reilly@example.test"])(
    'accepts %s',
    (email) => {
      expect(isEmail(email)).toBe(true);
    },
  );

  it.each([
    '',
    'plain',
    'a@localhost',
    'a..b@example.test',
    '.a@example.test',
    'a@-x.test',
    'a b@x.test',
  ])('rejects %s', (email) => {
    expect(isEmail(email)).toBe(false);
  });

  it('limits the local part to 64 characters', () => {
    expect(isEmail(`${'a'.repeat(64)}@example.test`)).toBe(true);
    expect(isEmail(`${'a'.repeat(65)}@example.test`)).toBe(false);
  });

  it('counts code points, not UTF-16 units', () => {
    expect(codePointLength('\u{1F600}Š')).toBe(2);
  });

  it('reports every missing field with its label, consent and captcha', () => {
    const errors = validateClient({
      form: 'student',
      values: { firstName: '  ' },
      consentRequired: true,
      consentGiven: false,
      captchaToken: '',
    });
    expect(Object.keys(errors).sort()).toEqual(
      [
        'captchaToken',
        'consentGiven',
        'email',
        'firstName',
        'lastName',
        'studentId',
        'studyInstitution',
        'studyProgramme',
      ].sort(),
    );
    expect(errors.firstName).toContain('First name');
    expect(errors.studentId).toContain('Student ID');
  });

  it('checks length and email format and passes valid input', () => {
    const base = {
      form: 'external' as const,
      consentRequired: false,
      consentGiven: false,
      captchaToken: 'tok',
    };
    const long = validateClient({
      ...base,
      values: { firstName: 'x'.repeat(101), lastName: 'L', email: 'bad', organization: 'O' },
    });
    expect(long.firstName).toContain('at most 100');
    expect(long.email).toContain('Email');
    expect(
      validateClient({
        ...base,
        values: { firstName: 'A', lastName: 'L', email: 'a@example.test', organization: 'O' },
      }),
    ).toEqual({});
  });
});
