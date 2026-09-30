# Surviving mutants: individual classification (phase 6)

PIT 1.19.1 after fix loop F-10: 472 mutants, 397 killed (84%), 31 survived, 44 no coverage. None indicates a defect.

| Class.method | Line | Mutator | Status | Classification | Reason |
|---|---|---|---|---|---|
| RegistrationValidator.groupSelection | 203 | EmptyObjectReturnValsMutator | SURVIVED | Equivalent | null/absent group returns the pre-filled empty list either way |
| RegistrationValidator.selections | 185 | EmptyObjectReturnValsMutator | SURVIVED | Equivalent | value returned only on the error path, which always throws |
| TextRules.isEmail | 63 | ConditionalsBoundaryMutator | SURVIVED | Equivalent | at==0 (leading @) is also rejected by the regex |
| TokenBucketLimiter$Bucket.isFull | 73 | ConditionalsBoundaryMutator | SURVIVED | Low: memory only | isFull only drives pruning of idle buckets; limits are unaffected (PlatformEdgeTest) |
| TokenBucketLimiter$Bucket.isFull | 72 | VoidMethodCallMutator | SURVIVED | Low: memory only | isFull only drives pruning of idle buckets; limits are unaffected (PlatformEdgeTest) |
| TokenBucketLimiter$Bucket.isFull | 75 | VoidMethodCallMutator | SURVIVED | Low: memory only | isFull only drives pruning of idle buckets; limits are unaffected (PlatformEdgeTest) |
| TokenBucketLimiter$Bucket.isFull | 73 | BooleanFalseReturnValsMutator | SURVIVED | Low: memory only | isFull only drives pruning of idle buckets; limits are unaffected (PlatformEdgeTest) |
| TokenBucketLimiter$Bucket.take | 65 | VoidMethodCallMutator | SURVIVED | Low: equivalent / concurrency | returning 0 on the success path is equivalent; lock release is unobservable single-threaded |
| TokenBucketLimiter$Bucket.take | 59 | PrimitiveReturnsMutator | SURVIVED | Low: equivalent / concurrency | returning 0 on the success path is equivalent; lock release is unobservable single-threaded |
| JsonStore.fsyncDirectory | 162 | NegateConditionalsMutator | NO_COVERAGE | Low: untestable durability | fsync effects need power-loss fault injection; verified by inspection (spec section 4) |
| JsonStore.fsyncDirectory | 159 | VoidMethodCallMutator | SURVIVED | Low: untestable durability | fsync effects need power-loss fault injection; verified by inspection (spec section 4) |
| JsonStore.publish | 66 | VoidMethodCallMutator | SURVIVED | Low: untestable durability | force()/directory fsync removal only matters on power loss; atomic rename behaviour is covered |
| JsonStore.publish | 73 | VoidMethodCallMutator | SURVIVED | Low: untestable durability | force()/directory fsync removal only matters on power loss; atomic rename behaviour is covered |
| NotificationDispatcher.backoff | 66 | ConditionalsBoundaryMutator | SURVIVED | Equivalent | boundary at exactly MAX_DELAY returns the same value |
| SecurityConfig.lambda$securityFilterChain$1 | 53 | VoidMethodCallMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| SecurityConfig.lambda$securityFilterChain$1 | 56 | VoidMethodCallMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| SecurityConfig.passwordEncoder | 77 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| SecurityConfig.requestGuardFilter | 113 | VoidMethodCallMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| SecurityConfig.requestGuardFilter | 114 | VoidMethodCallMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| SecurityConfig.requestGuardFilter | 115 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| SecurityConfig.securityFilterChain | 72 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| RegistrationConfig.jsonStore | 22 | NegateConditionalsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| RegistrationConfig.jsonStore | 22 | NegateConditionalsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| RegistrationConfig.jsonStore | 25 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| RegistrationConfig.organizerRecipients | 30 | NegateConditionalsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| RegistrationConfig.organizerRecipients | 30 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| RegistrationConfig.registrationValidator | 17 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| RequestGuardFilter$LimitedBodyRequest$1.isFinished | 109 | BooleanTrueReturnValsMutator | SURVIVED | Low | pure delegation; covered by the container at runtime |
| RequestGuardFilter$LimitedBodyRequest$1.isReady | 114 | BooleanTrueReturnValsMutator | SURVIVED | Low | pure delegation |
| RequestGuardFilter$LimitedBodyRequest$1.read | 98 | ConditionalsBoundaryMutator | SURVIVED | Equivalent | n>0 vs n>=0: a zero-length read adds nothing to the count |
| RequestGuardFilter$LimitedBodyRequest$1.setReadListener | 119 | VoidMethodCallMutator | NO_COVERAGE | Low | async IO not used |
| OutboxEntry.id | 104 | EmptyObjectReturnValsMutator | SURVIVED | Low | used only inside the Message-ID; duplicate-detection aid, not delivery |
| OutboxEntry.registrationId | 108 | NullReturnValsMutator | SURVIVED | Low | unit assertion is symmetric; covered by the acceptance attachment/ID checks |
| Reconciler.run | 59 | ConditionalsBoundaryMutator | SURVIVED | Low: log only | counter and summary-log arithmetic; quarantine behaviour is killed (ReconcilerTest, AC-005-04) |
| Reconciler.run | 54 | IncrementsMutator | SURVIVED | Low: log only | counter and summary-log arithmetic; quarantine behaviour is killed (ReconcilerTest, AC-005-04) |
| Reconciler.run | 59 | MathMutator | SURVIVED | Low: log only | counter and summary-log arithmetic; quarantine behaviour is killed (ReconcilerTest, AC-005-04) |
| Reconciler.run | 59 | NegateConditionalsMutator | SURVIVED | Low: log only | counter and summary-log arithmetic; quarantine behaviour is killed (ReconcilerTest, AC-005-04) |
| Reconciler.verifyAcceptedFiles | 83 | ConditionalsBoundaryMutator | SURVIVED | Low: log only | report-only path (counts and log lines) |
| Reconciler.verifyAcceptedFiles | 75 | IncrementsMutator | SURVIVED | Low: log only | report-only path (counts and log lines) |
| Reconciler.verifyAcceptedFiles | 79 | IncrementsMutator | SURVIVED | Low: log only | report-only path (counts and log lines) |
| Reconciler.verifyAcceptedFiles | 74 | NegateConditionalsMutator | SURVIVED | Low: log only | report-only path (counts and log lines) |
| Reconciler.verifyAcceptedFiles | 83 | NegateConditionalsMutator | SURVIVED | Low: log only | report-only path (counts and log lines) |
| RecaptchaVerifier.verify | 73 | VoidMethodCallMutator | NO_COVERAGE | Low | InterruptedException path; fails closed in all other tests (SR-01) |
| RecaptchaVerifier.verify | 74 | BooleanTrueReturnValsMutator | NO_COVERAGE | Low | InterruptedException path; fails closed in all other tests (SR-01) |
| TokenBucketLimiter.lambda$tryAcquire$0 | 33 | BooleanFalseReturnValsMutator | SURVIVED | Low: memory only | prune predicate; no effect on limiting |
| TokenBucketLimiter.nanos | 40 | MathMutator | SURVIVED | Low | clock arithmetic mutant equivalent for monotonic test sequences; rate limits verified by acceptance AC-001-10/AC-008-05 |
| TokenBucketLimiter.tryAcquire | 32 | ConditionalsBoundaryMutator | SURVIVED | Equivalent | prune threshold boundary only affects memory |
| ProblemHandler.api | 25 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| ProblemHandler.mediaType | 30 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| ProblemHandler.notFound | 55 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| ProblemHandler.respond | 66 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| ProblemHandler.tooLarge | 50 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| ProblemHandler.unexpected | 61 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| ProblemHandler.unreadable | 36 | NegateConditionalsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| ProblemHandler.unreadable | 37 | NegateConditionalsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| ProblemHandler.unreadable | 38 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| ProblemHandler.unreadable | 41 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| ApiException.title | 53 | EmptyObjectReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| ApiException.unavailable | 40 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| Problems.write | 36 | VoidMethodCallMutator | SURVIVED | Equivalent | problem bodies are ASCII; explicit UTF-8 has no observable effect |
| OptionsConfig.catalog | 14 | NegateConditionalsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| OptionsConfig.catalog | 14 | NegateConditionalsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| OptionsConfig.catalog | 17 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| CatalogController.catalog | 56 | EmptyObjectReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| CatalogController.lambda$new$0 | 35 | EmptyObjectReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| CatalogController.lambda$new$1 | 46 | EmptyObjectReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| RegistrationController.register | 57 | NegateConditionalsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| RegistrationController.register | 57 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| RegistrationQueries.allAccepted | 51 | EmptyObjectReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| RegistrationQueries.lambda$allAccepted$0 | 39 | EmptyObjectReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| PlatformConfig.appProfile | 22 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| PlatformConfig.clock | 17 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| ExportController.export | 33 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| NotificationService.lambda$enqueue$0 | 24 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |
| RegistrationAttachments.load | 20 | NullReturnValsMutator | NO_COVERAGE | Low: covered at higher level | Spring wiring/controller/handler executed and asserted by acceptance tests and ApiHardeningIT, which PIT excludes (they need containers) |

## Frontend (Stryker 9.0.1): 292 mutants, 79.6% score

| File | Line | Mutator | Status | Classification | Reason |
|---|---|---|---|---|---|
| src/components/RegistrationForm.tsx | 91 | BooleanLiteral | NoCoverage | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 106 | StringLiteral | NoCoverage | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 42 | ArrayDeclaration | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 43 | StringLiteral | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 47 | ConditionalExpression | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 50 | ConditionalExpression | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 50 | LogicalOperator | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 50 | ConditionalExpression | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 50 | EqualityOperator | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 50 | OptionalChaining | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 60 | MethodExpression | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 60 | ArrowFunction | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 55 | ArrayDeclaration | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 60 | ConditionalExpression | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 60 | ConditionalExpression | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 60 | EqualityOperator | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 66 | ArrayDeclaration | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 72 | ConditionalExpression | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 72 | LogicalOperator | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 72 | ConditionalExpression | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 72 | StringLiteral | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 72 | ConditionalExpression | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 72 | StringLiteral | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 74 | ConditionalExpression | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 74 | StringLiteral | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 86 | ConditionalExpression | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 91 | LogicalOperator | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 91 | OptionalChaining | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 107 | BooleanLiteral | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 116 | ArrowFunction | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 116 | ArithmeticOperator | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 141 | UnaryOperator | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 163 | LogicalOperator | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 163 | StringLiteral | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 164 | ConditionalExpression | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 164 | ConditionalExpression | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 164 | EqualityOperator | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 164 | StringLiteral | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 164 | StringLiteral | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 164 | StringLiteral | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 211 | BooleanLiteral | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 185 | ConditionalExpression | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 212 | StringLiteral | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 218 | ConditionalExpression | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 218 | ConditionalExpression | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 218 | LogicalOperator | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 240 | StringLiteral | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/components/RegistrationForm.tsx | 21 | StringLiteral | Survived | Low: UI | presentation/ARIA literals and state paths; behaviour covered by e2e AC-001-09/AC-002-08/AC-004-01/AC-004-02 |
| src/api.ts | 46 | ObjectLiteral | Survived | Low | header/option literals whose change is not observable with the mocked fetch; the real contract is covered by e2e api-contract tests |
| src/api.ts | 46 | ObjectLiteral | Survived | Low | header/option literals whose change is not observable with the mocked fetch; the real contract is covered by e2e api-contract tests |
| src/api.ts | 46 | StringLiteral | Survived | Low | header/option literals whose change is not observable with the mocked fetch; the real contract is covered by e2e api-contract tests |
| src/api.ts | 63 | ObjectLiteral | Survived | Low | header/option literals whose change is not observable with the mocked fetch; the real contract is covered by e2e api-contract tests |
| src/api.ts | 63 | StringLiteral | Survived | Low | header/option literals whose change is not observable with the mocked fetch; the real contract is covered by e2e api-contract tests |
| src/api.ts | 63 | StringLiteral | Survived | Low | header/option literals whose change is not observable with the mocked fetch; the real contract is covered by e2e api-contract tests |
| src/api.ts | 72 | BlockStatement | Survived | Low | header/option literals whose change is not observable with the mocked fetch; the real contract is covered by e2e api-contract tests |
| src/api.ts | 80 | BlockStatement | Survived | Low | header/option literals whose change is not observable with the mocked fetch; the real contract is covered by e2e api-contract tests |
| src/components/Captcha.tsx | 30 | ConditionalExpression | Survived | Low: UI | reCAPTCHA widget wiring; production captcha is a manual check (release notes) |
| src/components/Captcha.tsx | 30 | LogicalOperator | Survived | Low: UI | reCAPTCHA widget wiring; production captcha is a manual check (release notes) |
| src/components/Captcha.tsx | 30 | LogicalOperator | Survived | Low: UI | reCAPTCHA widget wiring; production captcha is a manual check (release notes) |
| src/components/Captcha.tsx | 30 | ConditionalExpression | Survived | Low: UI | reCAPTCHA widget wiring; production captcha is a manual check (release notes) |
| src/components/Captcha.tsx | 30 | ConditionalExpression | Survived | Low: UI | reCAPTCHA widget wiring; production captcha is a manual check (release notes) |
| src/components/Captcha.tsx | 33 | OptionalChaining | Survived | Low: UI | reCAPTCHA widget wiring; production captcha is a manual check (release notes) |
| src/components/Captcha.tsx | 50 | BooleanLiteral | Survived | Low: UI | reCAPTCHA widget wiring; production captcha is a manual check (release notes) |
| src/components/Captcha.tsx | 59 | LogicalOperator | Survived | Low: UI | reCAPTCHA widget wiring; production captcha is a manual check (release notes) |
| src/components/Captcha.tsx | 52 | ArrayDeclaration | Survived | Low: UI | reCAPTCHA widget wiring; production captcha is a manual check (release notes) |
| src/validation.ts | 35 | EqualityOperator | Survived | Low: usability only | client checks are a usability aid (BR-02); the backend rules are authoritative and mutation-tested; regex/label-literal mutants still reject/accept equivalently in the tested cases |
| src/validation.ts | 35 | ConditionalExpression | Survived | Low: usability only | client checks are a usability aid (BR-02); the backend rules are authoritative and mutation-tested; regex/label-literal mutants still reject/accept equivalently in the tested cases |
| src/validation.ts | 57 | EqualityOperator | Survived | Low: usability only | client checks are a usability aid (BR-02); the backend rules are authoritative and mutation-tested; regex/label-literal mutants still reject/accept equivalently in the tested cases |
| src/validation.ts | 64 | StringLiteral | Survived | Low: usability only | client checks are a usability aid (BR-02); the backend rules are authoritative and mutation-tested; regex/label-literal mutants still reject/accept equivalently in the tested cases |
| src/validation.ts | 17 | StringLiteral | Survived | Low: usability only | client checks are a usability aid (BR-02); the backend rules are authoritative and mutation-tested; regex/label-literal mutants still reject/accept equivalently in the tested cases |
| src/validation.ts | 18 | StringLiteral | Survived | Low: usability only | client checks are a usability aid (BR-02); the backend rules are authoritative and mutation-tested; regex/label-literal mutants still reject/accept equivalently in the tested cases |
| src/validation.ts | 18 | StringLiteral | Survived | Low: usability only | client checks are a usability aid (BR-02); the backend rules are authoritative and mutation-tested; regex/label-literal mutants still reject/accept equivalently in the tested cases |
| src/validation.ts | 26 | Regex | Survived | Low: usability only | client checks are a usability aid (BR-02); the backend rules are authoritative and mutation-tested; regex/label-literal mutants still reject/accept equivalently in the tested cases |
| src/validation.ts | 26 | Regex | Survived | Low: usability only | client checks are a usability aid (BR-02); the backend rules are authoritative and mutation-tested; regex/label-literal mutants still reject/accept equivalently in the tested cases |
| src/validation.ts | 26 | Regex | Survived | Low: usability only | client checks are a usability aid (BR-02); the backend rules are authoritative and mutation-tested; regex/label-literal mutants still reject/accept equivalently in the tested cases |
