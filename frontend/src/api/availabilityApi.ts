import {
  assertPositiveSafeIntegerInput,
  requestDecoded,
} from './apiResponseDecoders';
import { performCsrfMutation } from './csrfMutation';
import { UnexpectedResponseError } from './types';
import {
  decodeAvailabilitySlot,
  decodeAvailabilitySlotList,
  decodeClientAvailabilityWindowList,
  decodeWeeklyAvailabilityRule,
  decodeWeeklyAvailabilityRuleImpact,
  decodeWeeklyAvailabilityRuleList,
  type AvailabilitySlot,
  type ClientAvailabilityWindow,
  type CreateWeeklyAvailabilityRuleInput,
  type UpdateWeeklyAvailabilityRuleInput,
  type WeeklyAvailabilityRule,
  type WeeklyAvailabilityRuleImpact,
} from './availabilityTypes';

export interface AvailabilityApiOptions {
  readonly signal?: AbortSignal;
}

export function listMyWeeklyAvailabilityRules(
  options: AvailabilityApiOptions = {},
): Promise<WeeklyAvailabilityRule[]> {
  return requestDecoded(
    '/availability/weekly-rules/my',
    options.signal,
    decodeWeeklyAvailabilityRuleList,
    'Weekly availability rule list response',
  );
}

export function listMyAvailabilitySlots(
  options: AvailabilityApiOptions = {},
): Promise<AvailabilitySlot[]> {
  return requestDecoded(
    '/availability/my',
    options.signal,
    decodeAvailabilitySlotList,
    'Availability slot list response',
  );
}

export function listProfessionalAvailability(
  professionalId: number,
  options: AvailabilityApiOptions = {},
): Promise<ClientAvailabilityWindow[]> {
  assertPositiveSafeIntegerInput(professionalId, 'professionalId');
  return requestDecoded(
    `/professionals/${String(professionalId)}/availability`,
    options.signal,
    decodeClientAvailabilityWindowList,
    'Client availability window list response',
  );
}

export async function createWeeklyAvailabilityRule(
  input: CreateWeeklyAvailabilityRuleInput,
): Promise<WeeklyAvailabilityRule> {
  const payload = await performCsrfMutation<unknown>(
    '/availability/weekly-rules',
    jsonMutation('POST', input),
  );
  return decodeMutation(payload, decodeWeeklyAvailabilityRule, 201);
}

export async function updateWeeklyAvailabilityRule(
  ruleId: number,
  input: UpdateWeeklyAvailabilityRuleInput,
): Promise<WeeklyAvailabilityRule> {
  assertPositiveSafeIntegerInput(ruleId, 'ruleId');
  const payload = await performCsrfMutation<unknown>(
    `/availability/weekly-rules/${String(ruleId)}`,
    jsonMutation('PUT', input),
  );
  return decodeMutation(payload, decodeWeeklyAvailabilityRule, 200);
}

export async function deactivateWeeklyAvailabilityRule(
  ruleId: number,
  changeReason: string | null,
): Promise<void> {
  assertPositiveSafeIntegerInput(ruleId, 'ruleId');
  await performCsrfMutation<void>(
    `/availability/weekly-rules/${String(ruleId)}/deactivate`,
    jsonMutation('PATCH', { changeReason }),
  );
}

export function previewWeeklyAvailabilityRuleImpact(
  ruleId: number,
  options: AvailabilityApiOptions = {},
): Promise<WeeklyAvailabilityRuleImpact> {
  assertPositiveSafeIntegerInput(ruleId, 'ruleId');
  return requestDecoded(
    `/availability/weekly-rules/${String(ruleId)}/impact`,
    options.signal,
    decodeWeeklyAvailabilityRuleImpact,
    'Weekly availability impact response',
  );
}

export async function setAvailabilitySlotBlocked(
  slotId: number,
  blocked: boolean,
  changeReason: string | null = null,
): Promise<AvailabilitySlot> {
  assertPositiveSafeIntegerInput(slotId, 'slotId');
  const payload = await performCsrfMutation<unknown>(
    `/availability/${String(slotId)}/${blocked ? 'block' : 'unblock'}`,
    jsonMutation('PATCH', { changeReason }),
  );
  return decodeMutation(payload, decodeAvailabilitySlot, 200);
}

function jsonMutation(
  method: 'POST' | 'PUT' | 'PATCH',
  body: object,
): {
  readonly method: 'POST' | 'PUT' | 'PATCH';
  readonly headers: { readonly 'Content-Type': 'application/json' };
  readonly body: string;
  readonly invalidateOn401: true;
  readonly invalidateCsrfOnCommit: false;
} {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    invalidateOn401: true,
    invalidateCsrfOnCommit: false,
  };
}

function decodeMutation<T>(
  payload: unknown,
  decoder: (value: unknown) => T,
  status: number,
): T {
  try {
    return decoder(payload);
  } catch (cause) {
    throw new UnexpectedResponseError(
      status,
      new Response(null, { status }),
      cause,
      'Weekly availability mutation response failed runtime decoding',
    );
  }
}
