/**
 * Frontend mirror of backend invite normalization:
 * `InviteCodeService.normalizeInviteCode` → `trim().toUpperCase()`.
 * Backend remains authoritative.
 */
export function canonicalizeInviteCode(code: string): string {
  return code.trim().toUpperCase();
}
