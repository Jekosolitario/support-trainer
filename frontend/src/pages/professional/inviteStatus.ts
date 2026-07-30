export type InviteDisplayStatus =
  'Non attivo' | 'Usato' | 'Scaduto' | 'Non disponibile' | 'Valido';

export interface InviteStatusFields {
  readonly active: boolean;
  readonly used: boolean;
  readonly expiresAt: string;
}

/**
 * Derives invite UX status with backend-aligned precedence:
 * Non attivo → Usato → Scaduto → Non disponibile (invalid expiresAt) → Valido
 */
export function deriveInviteDisplayStatus(
  invite: InviteStatusFields,
  nowMs: number,
): InviteDisplayStatus {
  if (invite.active === false) {
    return 'Non attivo';
  }

  if (invite.used === true) {
    return 'Usato';
  }

  const expiresAtMs = Date.parse(invite.expiresAt);
  if (Number.isNaN(expiresAtMs)) {
    return 'Non disponibile';
  }

  if (expiresAtMs <= nowMs) {
    return 'Scaduto';
  }

  return 'Valido';
}

/** Next future expiry among currently Valido invites, or null. */
export function getNextValidInviteExpiryMs(
  invites: readonly InviteStatusFields[],
  nowMs: number,
): number | null {
  let nextExpiryMs: number | null = null;

  for (const invite of invites) {
    if (deriveInviteDisplayStatus(invite, nowMs) !== 'Valido') {
      continue;
    }

    const expiresAtMs = Date.parse(invite.expiresAt);
    if (Number.isNaN(expiresAtMs) || expiresAtMs <= nowMs) {
      continue;
    }

    if (nextExpiryMs === null || expiresAtMs < nextExpiryMs) {
      nextExpiryMs = expiresAtMs;
    }
  }

  return nextExpiryMs;
}
