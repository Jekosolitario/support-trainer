import {
  assertPositiveSafeIntegerInput,
  requestDecoded,
} from './apiResponseDecoders';
import {
  decodeProfessionalDetail,
  decodeProfessionalSummaryList,
  type ProfessionalDetail,
  type ProfessionalSummary,
} from './professionalsTypes';

export interface ProfessionalsApiOptions {
  readonly signal?: AbortSignal;
}

export function listMyProfessionals(
  options: ProfessionalsApiOptions = {},
): Promise<ProfessionalSummary[]> {
  return requestDecoded(
    '/professionals/my',
    options.signal,
    decodeProfessionalSummaryList,
    'Professional list response',
  );
}

export function getProfessionalById(
  professionalId: number,
  options: ProfessionalsApiOptions = {},
): Promise<ProfessionalDetail> {
  assertPositiveSafeIntegerInput(professionalId, 'professionalId');

  return requestDecoded(
    `/professionals/${String(professionalId)}`,
    options.signal,
    decodeProfessionalDetail,
    'Professional detail response',
  );
}
