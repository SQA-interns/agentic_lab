// Runtime demonstration against the local stack (phase 6, DoD-06, DoD-P01, DoD-P03).
// Runs in the node image inside the compose network; needs no secret.
//   node runtime-demo.mjs register   -> two registrations, prints their ids, emails and mails
const BASE = process.env.DEMO_BASE_URL ?? 'http://frontend:8080';
const MAILPIT = process.env.MAILPIT_URL ?? 'http://mailpit:8025';
const stamp = Date.now().toString(36);

async function post(path, body, headers = {}) {
  const response = await fetch(BASE + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body),
  });
  return { status: response.status, body: await response.json().catch(() => null) };
}

async function register() {
  const config = await (await fetch(`${BASE}/api/form-config`)).json();
  console.log(`form-config: ${config.options.length} active options, test mode ${config.captcha.testMode}`);
  const external = {
    type: 'EXTERNAL',
    firstName: 'Špela',
    lastName: 'Žagar',
    email: `demo.external.${stamp}@example.si`,
    organization: 'Inštitut Jožef Stefan',
    optionIds: ['ws-ai', 'meal-lunch-day1'],
    consentGiven: true,
    captchaToken: 'test-pass',
  };
  const student = {
    type: 'STUDENT',
    firstName: 'Žiga',
    lastName: 'Čebašek',
    email: `demo.student.${stamp}@example.si`,
    studyInstitution: 'Univerza v Ljubljani',
    studyProgramme: 'Računalništvo in informatika',
    studentId: '63210001',
    optionIds: ['ev-reception'],
    consentGiven: true,
    captchaToken: 'test-pass',
  };
  for (const registration of [external, student]) {
    const result = await post('/api/registrations', registration);
    console.log(
      `POST /api/registrations ${registration.type}: ${result.status} id=${result.body?.id} email=${registration.email}`,
    );
  }
  const rejected = await post('/api/registrations', { ...external, captchaToken: 'wrong' });
  console.log(`POST /api/registrations with a failed captcha: ${rejected.status} ${rejected.body?.code}`);
  for (const registration of [external, student]) {
    const query = encodeURIComponent(`to:"${registration.email}"`);
    const list = await (await fetch(`${MAILPIT}/api/v1/search?query=${query}`)).json();
    console.log(`Mailpit participant mail to ${registration.email}: ${list.messages.length} (${list.messages[0]?.Subject})`);
    const byText = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`"${registration.email}"`)}`)).json();
    for (const summary of byText.messages) {
      const detail = await (await fetch(`${MAILPIT}/api/v1/message/${summary.ID}`)).json();
      if (detail.Attachments.length > 0) {
        console.log(
          `Mailpit organizer mail: "${detail.Subject}", attachment ${detail.Attachments[0].FileName} (${detail.Attachments[0].ContentType})`,
        );
      }
    }
  }
}

const [command] = process.argv.slice(2);
if (command === 'register') {
  await register();
} else {
  console.log('usage: runtime-demo.mjs register');
  process.exit(2);
}
