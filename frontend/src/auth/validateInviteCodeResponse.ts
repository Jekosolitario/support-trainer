import type { ValidateInviteCodeResponse } from '../api/authTypes';
import { canonicalizeInviteCode } from './inviteCode';

/**
 * Instant-shaped ISO-8601 with required date, time and timezone (Z or numeric offset).
 * Aligns with Jackson Instant serialization used by ValidateInviteCodeResponse.
 */
const INSTANT_PATTERN =
  /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/;

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function isPositiveSafeInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
}

function daysInMonthUtc(year: number, month: number): number {
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

function isValidInstantString(value: unknown): value is string {
  if (typeof value !== 'string' || value.length === 0) {
    return false;
  }

  // Reject whitespace padding / ambiguous local forms early.
  if (value.trim() !== value) {
    return false;
  }

  const match = INSTANT_PATTERN.exec(value);
  if (match === null) {
    return false;
  }

  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const hour = Number(match[4]);
  const minute = Number(match[5]);
  const second = Number(match[6]);

  if (month < 1 || month > 12) {
    return false;
  }

  if (hour > 23 || minute > 59 || second > 59) {
    return false;
  }

  const maxDay = daysInMonthUtc(year, month);
  if (day < 1 || day > maxDay) {
    return false;
  }

  const parsedMs = Date.parse(value);
  if (Number.isNaN(parsedMs)) {
    return false;
  }

  return true;
}

/**
 * Fail-closed runtime decoder for validate-invite success body.
 * Requires `valid === true` and code coherence with the canonical request code.
 */
export function decodeValidateInviteCodeResponse(
  body: unknown,
  expectedCanonicalCode: string,
): ValidateInviteCodeResponse {
  if (!isRecord(body)) {
    throw new Error('Validate invite response body must be a JSON object');
  }

  if (body.valid !== true) {
    throw new Error('Validate invite response valid must be true');
  }

  if (typeof body.code !== 'string') {
    throw new Error('Validate invite response code must be a string');
  }

  const responseCode = canonicalizeInviteCode(body.code);
  if (responseCode !== expectedCanonicalCode) {
    throw new Error(
      'Validate invite response code does not match the request code',
    );
  }

  if (!isPositiveSafeInteger(body.professionalId)) {
    throw new Error('Validate invite response professionalId is invalid');
  }

  if (!isValidInstantString(body.expiresAt)) {
    throw new Error('Validate invite response expiresAt is invalid');
  }

  return {
    valid: true,
    code: responseCode,
    professionalId: body.professionalId,
    expiresAt: body.expiresAt,
  };
}
