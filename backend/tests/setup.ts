/**
 * Test environment defaults.
 *
 * Only values that must exist for *any* test to run are set here; each test builds its
 * own configuration explicitly so that nothing depends on hidden global state.
 */
process.env.NODE_ENV = 'test';
process.env.LOG_LEVEL = 'silent';
