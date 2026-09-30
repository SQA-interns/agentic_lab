# reCAPTCHA contract (frontend, backend ↔ Google reCAPTCHA v2)

> Written in: phase 2 · Source: SR-01, SR-02, DoD-P05, `environments.md` · Agent: writes

## Frontend (JavaScript widget)

- Outside test mode the frontend loads `https://www.google.com/recaptcha/api.js?render=explicit`, renders the checkbox widget with `recaptchaSiteKey` from `/api/config`, and sends the widget's response as `recaptchaToken`. The token is reset after every rejected submission.
- In test mode it loads nothing from Google, shows the notice "reCAPTCHA test mode", and sends `test-mode-pass`.

## Backend verification (HTTPS call)

- Request: `POST <app.recaptcha.verify-url>`, `Content-Type: application/x-www-form-urlencoded`, body `secret=<secret key>&response=<token>&remoteip=<client address>` (URL-encoded). Connect and read timeout 5 s each.
- Response (JSON): `{"success": true|false, "challenge_ts": "...", "hostname": "...", "error-codes": [...]}`. Only `success: true` accepts the token.
- Any other result (success false, non-200 status, invalid JSON, timeout, connection error) rejects the registration with 400 on `recaptchaToken`, nothing stored (fail closed). The secret and the token are never logged.
- The verification runs before any storage and before the duplicate check.
- Tests replace the endpoint with a local mock that records the request and answers `success: true` or `false` (AC-001-08, DoD-P05).
