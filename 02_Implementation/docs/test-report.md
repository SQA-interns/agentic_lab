# Test Report (Phase 4)

Tests were written only after implementation (commit `0ea12e9`). Sources per level:

| Level | Source | Location |
|---|---|---|
| Behavioural / API contract + integration | AC + specification §4–§11 | `backend/src/test/java/.../it/*IT.java` (Testcontainers PostgreSQL 16 + GreenMail SMTP) |
| Backend unit | implementation | `backend/src/test/java/.../service|config|api/*Test.java` |
| Architecture | specification §1 | `backend/src/test/java/.../ArchitectureTest.java` (ArchUnit) |
| Frontend component/unit | AC (US-001…004, AC-PC-*) + spec §10 | `frontend/src/**/*.test.ts(x)` (Vitest + RTL + jsdom) |
| Container execution | DoD §4 | `scripts/smoke-test.mjs` (Phase 5) |

## First complete test run (before any fix) — 2026-09-24 21:14–21:16 +02:00

The plain `mvnw verify` at 21:13:52 stopped after Surefire (unit failures), so integration tests
did not run. To obtain a *complete* first run it was repeated immediately (21:15) with
`-Dmaven.test.failure.ignore=true`, still without any code change.

| Suite | Run | Passed | Failed |
|---|---|---|---|
| Backend unit (Surefire) | 55 | 53 | 2 |
| Backend integration (Failsafe) | 38 | 32 | 6 |
| Frontend (Vitest) | 32 | 32 | 0 |

Failures and root causes:

| # | Test | Root cause | Classification | Fix |
|---|---|---|---|---|
| T1 | `BackupWriterTest.writingExistingFileFailsWithBackupFailedException` | `Files.move(..., ATOMIC_MOVE)` silently replaced an existing backup file | **Implementation defect** | `BackupWriter.write` refuses to overwrite an existing backup |
| T2 | `NotificationServiceTest.sendsParticipant…` | JavaMail only sets `Content-Type` headers on `saveChanges()` (done by `send`); the mock never calls it | Test defect | Test calls `saveChanges()` before reading headers |
| T3–T5 | `ExportIT.*` (3) | `server.servlet.encoding.force=true` appended `;charset=UTF-8` to the `.xlsx` Content-Type | **Implementation defect** | Force encoding for requests only (`force-request=true`, `force-response=false`) |
| T6–T8 | `RegistrationApiIT.external…Mailed`, `slovenianCharacters…`, `invalidRegistrationSendsNoEmail` | Emails are async after commit; mails from earlier tests arrived during later tests and GreenMail's `waitForIncomingEmail` counts only new arrivals | Test isolation defect | Mail assertions select messages by registration id / unique marker and poll |

No test was weakened to make an incorrect implementation pass: T1 and T3–T5 were fixed in the
implementation; T2 and T6–T8 were incorrect test assumptions (the implementation behaviour was
verified to be correct in the failure output: attachment present, UTF-8 encoding correct).

## Final result — 2026-09-24 21:23 +02:00

| Suite | Run | Passed | Failed |
|---|---|---|---|
| Backend unit | 55 | 55 | 0 |
| Backend integration | 38 | 38 | 0 |
| Frontend | 32 | 32 | 0 |

## Coverage (reported separately, DoD §2)

| Scope | Line | Branch | Instruction/Statement |
|---|---|---|---|
| Backend **unit only** (JaCoCo `jacoco.exec`) | 71.9 % (442/615) | 69.7 % | 71.2 % |
| Backend **integration only** (JaCoCo `jacoco-it.exec`) | 90.9 % (559/615) | 66.3 % | 90.2 % |
| Frontend unit/component (Vitest v8) | 96.5 % (165/171) | 85.1 % | 96.6 % statements |

The frontend has no separate integration-test level (TECH_STACK mandates no browser E2E
framework); its behaviour against the real backend is exercised by the container smoke test in
Phase 5.

## AC → test traceability

| AC | Test(s) |
|---|---|
| AC-001-01 | `RegistrationApiIT.externalRegistrationIsAccepted…`, `RegistrationForm.test` "submits trimmed data…" |
| AC-001-02 | `RegistrationForm.test` "shows exactly the external fixed fields" |
| AC-001-03 | `RegistrationForm.test` "blocks submission of an empty required field" |
| AC-001-04 | `RegistrationApiIT.malformedEmailIsRejectedAndNothingStored`, `emailWithoutTopLevelDomainIsRejected` |
| AC-001-05 | `RegistrationApiIT.missingConsentIsRejected`, `RegistrationForm.test` "blocks submission without consent", `RegistrationServiceTest.missingConsentIsRejected` |
| AC-001-06 | `RegistrationForm.test` "does not preselect the mandatory consent" |
| AC-001-07 | `RegistrationApiIT.slovenianCharactersArePreserved`, `rules.test` |
| AC-001-08 | `RegistrationApiIT.surroundingWhitespaceIsTrimmed`, `TrimmingStringDeserializerTest` |
| AC-001-09 | `RegistrationApiIT.whitespaceOnlyRequiredFieldIsRejected` |
| AC-002-01 | `RegistrationApiIT.studentRegistrationIsAccepted`, `RegistrationForm.test` "posts to the student endpoint" |
| AC-002-02 | `RegistrationForm.test` "shows exactly the student fixed fields" |
| AC-002-03 | `RegistrationApiIT.missingStudentIdIsRejected` |
| AC-002-04 | `RegistrationForm.test` "blocks a malformed email with a message" |
| AC-002-05 | `RegistrationApiIT.studentEndpointRejectsExternalFieldSet` |
| AC-002-06 | `RegistrationApiIT.optionNotAvailableToStudentsIsRejected`, `OptionCatalogTest.resolveRejectsOptionNotAvailableToAudience` |
| AC-002-07 | `RegistrationApiIT.optionsEndpointReturnsOnlyActiveOptionsForVariant` |
| AC-003-01 | `RegistrationApiIT.optionsEndpoint…`, `RegistrationForm.test` "displays active options grouped by category" |
| AC-003-02 | `OptionsConfigurationIT` |
| AC-003-03 | `RegistrationApiIT.unknownOptionIsRejected` |
| AC-003-04 | `RegistrationApiIT.inactiveOptionIsRejected`, `OptionCatalogTest.resolveRejectsInactiveOption` |
| AC-003-05 | `RegistrationApiIT.optionsEndpoint…`, `OptionCatalogTest.activeFor…` |
| AC-003-06 | `RegistrationApiIT.registrationWithoutOptionsIsAccepted` |
| AC-003-07 | `OptionCatalogTest.*FailsStartup` |
| AC-004-01 | `RegistrationForm.test` "…shows confirmation only after 201" |
| AC-004-02 | `RegistrationForm.test` "shows backend field errors and no confirmation on 400" |
| AC-004-03 | `RegistrationForm.test` "general error… server error", "network error" |
| AC-004-04 | `RegistrationForm.test` "disables submit while a request is in flight" |
| AC-005-01/02/06 | `RegistrationApiIT.externalRegistrationIsAccepted…` |
| AC-005-03 | `RegistrationApiIT.malformedEmailIsRejectedAndNothingStored` |
| AC-005-04 | `FailureHandlingIT`, `RegistrationServiceTest.backupFailure…` |
| AC-005-05 | container restart check in Phase 5 (verification report) |
| AC-006-01 | `RegistrationApiIT.externalRegistrationIsAccepted…`, `NotificationServiceTest` |
| AC-006-02 / AC-007-03 | `RegistrationApiIT.invalidRegistrationSendsNoEmail`, `FailureHandlingIT` |
| AC-006-03 | `MailOutageIT`, `NotificationServiceTest.mailFailureIsSwallowed…` |
| AC-006-04 | `RegistrationApiIT.slovenianCharactersArePreserved`, `NotificationServiceTest` |
| AC-007-01/02 | `RegistrationApiIT.externalRegistrationIsAccepted…`, `NotificationServiceTest` |
| AC-007-04 | `NotificationServiceTest.organizerBodyIsPlainText…` (plain text, markup inert) |
| AC-008-01/04 | `ExportIT.exportContainsCurrentRegistrations` |
| AC-008-02 | `ExportIT.exportRequiresCredentials` |
| AC-008-03 | `ExportIT.emptyExportHasHeaderOnly`, `ExcelExportServiceTest` |
| AC-008-05 | `ExportIT.formulaLikeInputIsExportedAsInertText`, `ExcelExportServiceTest` |
| AC-PC-01 | `RegistrationApiIT.markupInFreeTextIsStoredLiterally`; React escaping (no `dangerouslySetInnerHTML`) |
| AC-PC-02 | `RegistrationApiIT.overlongFieldIsRejected`, `oversizedBodyIsRejected` |
| AC-PC-03 | `RegistrationApiIT.filledHoneypotIsRejectedAndNotStored` |
| AC-PC-04 | `AntiAutomationIT.submissionFasterThanMinimumFillTimeIsRejected`, `FormTokenServiceTest` |
| AC-PC-05 | `AntiAutomationIT.clientExceedingRateLimitGets429…`, `RateLimiterTest.concurrentRequestsNeverExceedLimit` |
| AC-PC-06 | `RegistrationApiIT.responsesCarrySecurityHeaders`, `AntiAutomationIT` (headers on 429); frontend headers in Phase 5 |
| AC-PC-07 | `RegistrationApiIT.malformedJsonGivesGenericErrorWithoutInternals` |
| AC-PC-08 | manual/automated viewport check in Phase 5 |
| AC-PC-09 | `RegistrationApiIT.healthEndpointsAreUp`; container check in Phase 5 |
| AC-PC-10 | `RegistrationApiIT.unknownEndpointsDoNotExposeData` |
