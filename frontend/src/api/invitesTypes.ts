/**
 * Invite list/create contracts aligned with backend InviteCodeResponse.
 * Temporal fields are Instant serialized as UTC ISO-8601 with `Z`.
 */

export interface InviteCodeResponse {
  id: number;
  code: string;
  professionalId: number;
  expiresAt: string;
  used: boolean;
  usedAt: string | null;
  active: boolean;
  createdAt: string;
}
