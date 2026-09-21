const { test, describe } = require('node:test');
const assert = require('node:assert/strict');
const optionsService = require('../../src/services/optionsService');

describe('Unit Tests: Configurable Conference Options Service', () => {
  test('getActiveOptions returns only items with active: true', () => {
    const active = optionsService.getActiveOptions();
    assert.ok(active.workshops, 'Should contain workshops');
    assert.ok(active.events, 'Should contain events');
    assert.ok(active.meals, 'Should contain meals');
    assert.ok(active.other, 'Should contain other activities');

    // Inactive option 'ws-legacy-migration' must be excluded
    const hasInactive = active.workshops.some(w => w.id === 'ws-legacy-migration');
    assert.equal(hasInactive, false, 'Inactive option should not be in active list');

    // Active option 'ws-cloud-native' must be included
    const hasActive = active.workshops.some(w => w.id === 'ws-cloud-native');
    assert.equal(hasActive, true, 'Active option must be present');
  });

  test('validateSelectedOptions accepts valid active option IDs', () => {
    const selected = ['ws-cloud-native', 'ev-keynote', 'meal-vegetarian'];
    const res = optionsService.validateSelectedOptions(selected);
    assert.equal(res.valid, true);
    assert.equal(res.errors.length, 0);
    assert.equal(res.resolvedOptions.length, 3);
    assert.equal(res.resolvedOptions[0].name, 'Cloud Native Architecture Masterclass');
  });

  test('validateSelectedOptions rejects unknown option IDs', () => {
    const selected = ['ws-unknown-12345'];
    const res = optionsService.validateSelectedOptions(selected);
    assert.equal(res.valid, false);
    assert.ok(res.errors.some(e => e.includes("does not exist")));
  });

  test('validateSelectedOptions rejects inactive option IDs', () => {
    const selected = ['ws-legacy-migration'];
    const res = optionsService.validateSelectedOptions(selected);
    assert.equal(res.valid, false);
    assert.ok(res.errors.some(e => e.includes("inactive or no longer available")));
  });
});
