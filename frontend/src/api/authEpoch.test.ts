import { describe, expect, it } from 'vitest';

import { advanceEpoch, currentEpoch, invalidateIfCurrent } from './authEpoch';

describe('authEpoch', () => {
  it('avanza in modo monotono rispetto alla baseline corrente', () => {
    const baseline = currentEpoch();

    const first = advanceEpoch();
    const second = advanceEpoch();

    expect(first).toBe(baseline + 1);
    expect(second).toBe(baseline + 2);
    expect(currentEpoch()).toBe(second);
    expect(second).toBeGreaterThan(first);
  });

  it('invalidateIfCurrent con epoch corrente restituisce true e avanza', () => {
    const expected = currentEpoch();

    expect(invalidateIfCurrent(expected)).toBe(true);
    expect(currentEpoch()).toBe(expected + 1);
  });

  it('invalidateIfCurrent con epoch stale restituisce false senza modifiche', () => {
    const before = currentEpoch();

    expect(invalidateIfCurrent(before - 1)).toBe(false);
    expect(currentEpoch()).toBe(before);
  });

  it('più invalidazioni stale non cambiano l’epoch', () => {
    const baseline = advanceEpoch();

    expect(invalidateIfCurrent(baseline - 1)).toBe(false);
    expect(invalidateIfCurrent(baseline - 5)).toBe(false);
    expect(invalidateIfCurrent(baseline)).toBe(true);

    const afterValid = currentEpoch();
    expect(afterValid).toBe(baseline + 1);

    expect(invalidateIfCurrent(baseline)).toBe(false);
    expect(invalidateIfCurrent(baseline)).toBe(false);
    expect(currentEpoch()).toBe(afterValid);
  });
});
