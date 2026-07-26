/**
 * Process-lifetime monotonic auth epoch.
 * Independent of React remounts and StrictMode.
 */

let epoch = 0;

export function currentEpoch(): number {
  return epoch;
}

export function advanceEpoch(): number {
  epoch += 1;
  return epoch;
}

/**
 * Synchronously advances the epoch once when `expectedEpoch` is still current.
 * Returns true only for the first valid invalidation of that epoch.
 */
export function invalidateIfCurrent(expectedEpoch: number): boolean {
  if (expectedEpoch !== epoch) {
    return false;
  }

  epoch += 1;
  return true;
}
