/**
 * Architecture conformance rules (specification § 2.2).
 * Dependencies point strictly downwards: api -> application -> domain/infrastructure.
 */
module.exports = {
  forbidden: [
    {
      name: 'no-circular',
      severity: 'error',
      comment: 'A dependency cycle makes the layering unenforceable.',
      from: {},
      to: { circular: true },
    },
    {
      name: 'domain-is-pure',
      severity: 'error',
      comment: 'The domain layer must not depend on any other layer.',
      from: { path: '^src/domain' },
      to: { path: '^src/(api|application|infrastructure|config)' },
    },
    {
      name: 'infrastructure-not-inward',
      severity: 'error',
      comment: 'Infrastructure must not depend on the api or application layers.',
      from: { path: '^src/infrastructure' },
      to: { path: '^src/(api|application)' },
    },
    {
      name: 'application-not-on-api',
      severity: 'error',
      comment: 'The application layer must not depend on the delivery layer.',
      from: { path: '^src/application' },
      to: { path: '^src/api' },
    },
    {
      name: 'sql-only-in-repository',
      severity: 'error',
      comment: 'Only the repository may speak to the database driver.',
      from: { path: '^src', pathNot: '^src/infrastructure/db' },
      to: { path: 'better-sqlite3' },
    },
    {
      name: 'no-orphans',
      severity: 'warn',
      from: { orphan: true, pathNot: '^src/server\.ts$' },
      to: {},
    },
  ],
  options: {
    doNotFollow: { path: 'node_modules' },
    tsConfig: { fileName: 'tsconfig.build.json' },
    tsPreCompilationDeps: true,
    exclude: { path: 'node_modules' },
  },
};
