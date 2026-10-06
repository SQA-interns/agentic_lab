# Decisions log

> Written in: every phase · Format: `skills/decisions` ("Decision record") · Agent: appends only

## D-01: Node.js 24.13.0 with npm 11.6.2 not installed
- Timestamp: 2026-10-06T07:43:39Z
- Phase: 0
- Type: blocking
- Trigger: `project/stack.md` platform `node` 24.13.0; `node` and `npm` do not resolve in a fresh shell (bash and zsh login). nvm holds 22.23.3, 24.11.1, 24.14.1 only. Confirmed twice.
- Options: 1 (default) human runs `nvm install 24.13.0`; the agent calls it by its nvm path. 2 use the installed 24.14.1 (replaces the stack entry for this run). 3 other.
- Human response: 2026-10-06T08:47:21Z "Installed now" (not present on re-check); 2026-10-06T09:02:04Z "can you install it?" (approval for the agent to install)
- Resolution: option 1, installed by the agent with the human's approval: `nvm install 24.13.0` (checksum matched), user-local under `~/.nvm`, not on the global PATH; the agent and `verify.sh` call it by `~/.nvm/versions/node/v24.13.0/bin`. Fresh-shell check 2026-10-06T09:02:30Z: node v24.13.0, npm 11.6.2.

## D-02: `.env` missing
- Timestamp: 2026-10-06T07:43:39Z
- Phase: 0
- Type: blocking
- Trigger: `secrets.sh check` reports `.env` missing (confirmed twice). Required keys per `project/secrets.env.example`: POSTGRES_PASSWORD, ORGANIZER_USERNAME, ORGANIZER_PASSWORD, ORGANIZER_EMAILS, NVD_API_KEY. Preflight checks 4 and 6 (Dependency-Check needs NVD_API_KEY) wait on it.
- Options: 1 (default) human copies `01_input/01_project/secrets.env.example` to `.env` and fills the five keys; RECAPTCHA_* and SMTP_* stay empty. 2 other.
- Human response: 2026-10-06T08:45:02Z "Done, .env is filled"
- Resolution: option 1; `secrets.sh check` 2026-10-06T08:45:02Z: all five required keys present, RECAPTCHA_* empty (allowed), SMTP_* present.

## D-03: Host JDK is Ubuntu OpenJDK 21.0.12.1, not Temurin 21.0.10+7
- Timestamp: 2026-10-06T07:43:39Z
- Phase: 0
- Type: non-blocking
- Trigger: `java -version` reports OpenJDK 21.0.12.1+1-Ubuntu; `project/stack.md` platform `java` lists Eclipse Temurin 21.0.10+7. Same major version; it runs.
- Options: 1 (default) build and test with the host JDK 21; the runtime image stays on the pinned `eclipse-temurin:21.0.10_7-jre-alpine`; the pin stays authoritative. 2 human installs Temurin 21.0.10+7.
- Human response: none
- Resolution: pending review (option 1)

## D-04: Docker Engine reports 29.3.1, not 29.8.0
- Timestamp: 2026-10-06T07:43:39Z
- Phase: 0
- Type: non-blocking
- Trigger: `docker version` reports server 29.3.1 (client 29.5.0); `project/stack.md` platform `docker` lists 29.8.0. It runs.
- Options: 1 (default) continue with the host engine; the pin stays authoritative. 2 human upgrades.
- Human response: none
- Resolution: pending review (option 1)

## D-05: Docker Compose reports v5.1.1, not 5.5.1
- Timestamp: 2026-10-06T07:43:39Z
- Phase: 0
- Type: non-blocking
- Trigger: `docker compose version` reports v5.1.1; `project/stack.md` platform `compose` lists 5.5.1. It runs.
- Options: 1 (default) continue with the host Compose; the pin stays authoritative. 2 human upgrades.
- Human response: none
- Resolution: pending review (option 1)

## D-06: Image `aldanial/cloc:2.10` reports cloc 1.98
- Timestamp: 2026-10-06T11:05:10Z
- Phase: 0
- Type: non-blocking
- Trigger: `docker run --rm aldanial/cloc:2.10 --version` prints `1.98` (confirmed twice); `project/stack.md` tooling `aldanial/cloc` lists "2.10". It runs.
- Options: 1 (default) use the pinned tag `aldanial/cloc:2.10`; the pin stays authoritative. 2 human changes the pin.
- Human response: none
- Resolution: pending review (option 1)

## D-07: npm audit Critical in tinypool via vitest 3.2.7
- Timestamp: 2026-10-06T11:38:04Z
- Phase: 0
- Type: blocking
- Trigger: preflight check 6, `verify.sh 00 fe-depscan` (`logs/00_fe-depscan.log`): critical=2 (tinypool <=2.1.1, GHSA-5gmw-xhrv-c9v3 and GHSA-85c8-ppgw-ccpr, prototype-pollution gadget to RCE; reported again under `vitest` as the direct dependency), plus moderate GHSA-82fw-gwwq-j7x9 in @vitest/mocker 3.2.7. Pulled by `vitest` 3.2.7 (tinypool ^1.1.1); test tooling only, not in the production image. Every fix changes the `vitest` and `@vitest/coverage-v8` pins of `project/stack.md`. Checked in a scratch copy: with 4.1.11 or 5.0.3 critical=0, high=0 (both accept vite 6.4.3 and Node 24; `@stryker-mutator/vitest-runner` 10.0.0 accepts vitest >=2.0.0).
- Options: 1 (default) `vitest` and `@vitest/coverage-v8` 3.2.7 → 4.1.11 (patched 4.x line, smallest change that fixes it). 2 → 5.0.3 (latest, what npm audit proposes). 3 keep 3.2.7 and accept the finding as test-only (downgrade of a Critical).
- Human response: 2026-10-06T11:40:16Z "4.1.11 (Recommended)"
- Resolution: option 1. `vitest` 4.1.11 and `@vitest/coverage-v8` 4.1.11 replace the 3.2.7 stack entries for this run; tinypool no longer in the tree. Re-run `verify.sh 00 all`: fe-depscan critical=0 high=0 medium=2 (D-10).

## D-08: Dependency-Check High CVE-2025-7962 on angus-activation 2.0.3 is a false positive
- Timestamp: 2026-10-06T11:38:04Z
- Phase: 0
- Type: blocking
- Trigger: preflight check 6, `verify.sh 00 be-depscan` (`logs/00_be-depscan.log`): high=1, CVE-2025-7962 (CVSS 7.5, SMTP injection in Jakarta Mail < 2.0.2 / angus_mail < 2.0.4). Evidence: the match is the CPE `eclipse:angus_mail:2.0.3` inferred from `org.eclipse.angus:angus-activation:2.0.3` (the activation framework, not the mail implementation); the mail implementation on the classpath is `org.eclipse.angus:angus-mail:2.0.5` (`dependency:tree`), which is >= the fixed 2.0.4. Lowering a High needs human approval (`standards/security.md`).
- Options: 1 (default) classify as false positive (Low) and suppress exactly this CVE for `pkg:maven/org.eclipse.angus/angus-activation@.*` in a Dependency-Check suppression file with this evidence. 2 keep it High and pin angus-activation explicitly (no fixed version exists for a CVE it does not have). 3 other.
- Human response: 2026-10-06T11:40:16Z "Yes, suppress (Recommended)"
- Resolution: option 1. Suppressed in `backend/dependency-check-suppressions.xml`; Dependency-Check now fails the build at CVSS >= 7. Re-run `verify.sh 00 all`: be-depscan critical=0 high=0 medium=0.

## D-09: Dependency-Check Medium CVE-2025-15104 on hibernate-validator 9.1.3 is a false positive
- Timestamp: 2026-10-06T11:38:04Z
- Phase: 0
- Type: non-blocking
- Trigger: `logs/00_be-depscan.log`: medium=1, CVE-2025-15104 (CVSS 5.3) concerns the Nu Html Checker (CPE `validator:validator`), matched to `org.hibernate.validator:hibernate-validator:9.1.3.Final` by name only.
- Options: 1 (default) classify as false positive (Low) and suppress exactly this CVE for hibernate-validator, with this evidence. 2 keep it as Medium.
- Human response: none
- Resolution: pending review (option 1)

## D-10: npm audit Medium in qs 6.15.1 via @stryker-mutator/core
- Timestamp: 2026-10-06T11:38:04Z
- Phase: 0
- Type: non-blocking
- Trigger: `logs/00_fe-depscan.log`: moderate GHSA-q8mj-m7cp-5q26, GHSA-x5fp-wj9c-mxmx, GHSA-4mjr-xmp4-gh2g in `qs` 6.15.1, reached via `@stryker-mutator/core` 10.0.0 → `typed-rest-client` 2.3.1 (dashboard reporter, not enabled in `stryker.config.json`). `npm audit fix` does not remove it; mutation tooling only, never in the production image.
- Options: 1 (default) keep, record as Medium, re-check in phase 6. 2 add an npm `overrides` entry for qs (unlisted pin).
- Human response: none
- Resolution: pending review (option 1)

## D-11: OQ-01 option availability per registration type
- Timestamp: 2026-10-06T11:46:08Z
- Phase: 1
- Type: non-blocking
- Trigger: `project/requirements.md` OQ-01 unanswered; US-002 speaks of "the activities available to students".
- Options: 1 (default, conservative) each configured option states which registration types may select it (both unless configured otherwise); an option not available to the submitted type is not offered on that form and is rejected. 2 every active option is available to both types.
- Human response: none
- Resolution: pending review (option 1)

## D-12: OQ-02 mandatory consents and wording
- Timestamp: 2026-10-06T11:46:08Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-02 unanswered; BR-05 requires mandatory consents, SB-12 and SB-14 apply.
- Options: 1 (default, conservative) one mandatory consent, to the processing of the submitted personal data for organising the conference; its wording comes from configuration (placeholder wording until the product owner supplies it); no optional consents; the consent and the time it was given are stored. 2 several consents (for example photography, newsletter).
- Human response: none
- Resolution: pending review (option 1)

## D-13: OQ-03 storage succeeds but an email fails
- Timestamp: 2026-10-06T11:46:08Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-03 unanswered; priority 1 ("a registration is never lost: storage comes before any notification") and AR-05.
- Options: 1 (default, conservative) the registration stays accepted and stored and the confirmation is shown; the failure is logged without personal data so organizers can follow up; no automatic resend in this release. 2 reject the registration when an email fails.
- Human response: none
- Resolution: pending review (option 1)

## D-14: OQ-04 number of options per category
- Timestamp: 2026-10-06T11:46:08Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-04 unanswered; no rule states a limit.
- Options: 1 (default) selecting options is optional (zero allowed); each option at most once; an optional maximum per category may be set in configuration and selections above it are rejected; no maximum is configured by default. 2 a fixed maximum of one per category.
- Human response: none
- Resolution: pending review (option 1)

## D-15: OQ-05 second registration with the same email
- Timestamp: 2026-10-06T11:46:08Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-05 unanswered; editing or cancelling is out of scope, so duplicates could not be corrected.
- Options: 1 (default, conservative) a second registration with an email already registered (ignoring letter case and surrounding whitespace, across both types) is rejected, with a message to contact the organizers. 2 allow duplicates.
- Human response: none
- Resolution: pending review (option 1)

## D-16: OQ-06 retention of registrations and JSON copies
- Timestamp: 2026-10-06T11:46:08Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-06 unanswered; SB-13 needs a retention period per personal-data item; deleting registrations is out of scope.
- Options: 1 (default) records and JSON copies are kept until organizers delete them after the conference; no automatic deletion in this release; the retention period is listed in the release notes ("Before production") for the product owner to set. 2 automatic deletion after a fixed period.
- Human response: none
- Resolution: pending review (option 1)

## D-17: Control characters in registration values are rejected
- Timestamp: 2026-10-06T11:49:25Z
- Phase: 2
- Type: non-blocking
- Trigger: AC-006-03 (phase 1) assumed an accepted registration may contain line breaks. SB-01 and SR-05 call for trusted-side validation; no field (names, email, institution, student ID) legitimately contains a line break or other control character, and BR-03 only requires Unicode text.
- Options: 1 (default, conservative) a value containing a control character (including CR, LF, TAB) is rejected as invalid for that field; AC-006-03 is amended before the phase 3 freeze to cover header text and markup in accepted values, and a rejection criterion AC-001-16 is added. 2 accept and strip control characters.
- Human response: none
- Resolution: pending review (option 1)

## D-18: User interface language
- Timestamp: 2026-10-06T11:49:25Z
- Phase: 2
- Type: non-blocking
- Trigger: no input states the language of the forms and emails; NFR-01 only requires Slovenian characters to survive.
- Options: 1 (default) English, the language of the requirements; every user-visible text lives in one module per component (`docs/02_contracts/ui-registration-form.json`, `email-messages.json`), so a translation changes no logic. 2 Slovenian.
- Human response: none
- Resolution: pending review (option 1)

## D-19: Semgrep High "use of basic authentication" on the export contract
- Timestamp: 2026-10-06T12:17:19Z
- Phase: 2
- Type: blocking
- Trigger: `verify.sh 02 semgrep` (`logs/02_semgrep.json`): ERROR (High) `yaml.openapi.security.use-of-basic-authentication` at `docs/02_contracts/openapi.yaml` securityScheme `organizerBasic`. Context: identity providers are out of scope (`project/constraints.md`); one organizer role, one read-only export (BR-08); ASVS Level 1. Mitigations in the design: refused over plain HTTP before credentials are read (SR-06), rate limited (SR-03), password kept only as a bcrypt hash in memory (SB-03), no session, constant-time comparison.
- Options: 1 (default) keep HTTP Basic with these mitigations and classify the finding as Low (rule match on a mitigated design). 2 replace with a login endpoint that issues a short-lived bearer token plus an organizer download page in the frontend (more code and a new UI; the password is still sent once per session).
- Human response: 2026-10-06T12:18:26Z "Keep Basic, mark Low (Recommended)"
- Resolution: option 1; classified Low; `nosemgrep` marker with D-19 on the `organizerBasic` scheme in `docs/02_contracts/openapi.yaml`. The mitigations are requirements in `docs/02_specification.md`.

## D-20: Semgrep Medium "npm missing minimum release age"
- Timestamp: 2026-10-06T12:17:19Z
- Phase: 2
- Type: non-blocking
- Trigger: `logs/02_semgrep.json`: WARNING (Medium) `npm-missing-minimum-release-age` at `frontend/.npmrc`. The setting exists from npm 11.10; the pinned npm is 11.6.2 (`project/stack.md`). Every dependency is an exact pin resolved from the committed lock file (ES-04), so no new release is picked up implicitly.
- Options: 1 (default) keep; record as Medium, mitigated by exact pins and the lock file. 2 upgrade npm (stack change).
- Human response: none
- Resolution: pending review (option 1)
