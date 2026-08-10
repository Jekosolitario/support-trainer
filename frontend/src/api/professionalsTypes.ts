import type {
  ProfessionalOperationalStatus,
  ProfessionalSpecialization,
} from './authTypes';
import {
  requireArray,
  requireBoolean,
  requireEnum,
  requireJsonObject,
  requireNullableString,
  requirePositiveSafeInteger,
  requireString,
  type JsonObject,
} from './apiResponseDecoders';

const PROFESSIONAL_SPECIALIZATIONS: readonly ProfessionalSpecialization[] = [
  'PERSONAL_TRAINER',
  'NUTRITIONIST',
];

const PROFESSIONAL_OPERATIONAL_STATUSES: readonly ProfessionalOperationalStatus[] =
  ['DISPONIBILE', 'ASSENTE', 'FERIE', 'MALATTIA'];

export interface ProfessionalSummary {
  id: number;
  firstName: string;
  lastName: string;
  profileImageUrl: string | null;
  specialization: ProfessionalSpecialization;
  operationalStatus: ProfessionalOperationalStatus;
  active: boolean;
}

export interface ProfessionalDetail extends ProfessionalSummary {
  phoneNumber: string | null;
  bio: string | null;
  workplaceName: string | null;
  city: string | null;
  instagramUrl: string | null;
  websiteUrl: string | null;
}

function decodeProfessionalSummaryRecord(
  record: JsonObject,
): ProfessionalSummary {
  return {
    id: requirePositiveSafeInteger(record, 'id'),
    firstName: requireString(record, 'firstName'),
    lastName: requireString(record, 'lastName'),
    profileImageUrl: requireNullableString(record, 'profileImageUrl'),
    specialization: requireEnum(
      record,
      'specialization',
      PROFESSIONAL_SPECIALIZATIONS,
    ),
    operationalStatus: requireEnum(
      record,
      'operationalStatus',
      PROFESSIONAL_OPERATIONAL_STATUSES,
    ),
    active: requireBoolean(record, 'active'),
  };
}

export function decodeProfessionalSummary(value: unknown): ProfessionalSummary {
  return decodeProfessionalSummaryRecord(
    requireJsonObject(value, 'Professional summary'),
  );
}

export function decodeProfessionalDetail(value: unknown): ProfessionalDetail {
  const record = requireJsonObject(value, 'Professional detail');
  const summary = decodeProfessionalSummaryRecord(record);

  return {
    ...summary,
    phoneNumber: requireNullableString(record, 'phoneNumber'),
    bio: requireNullableString(record, 'bio'),
    workplaceName: requireNullableString(record, 'workplaceName'),
    city: requireNullableString(record, 'city'),
    instagramUrl: requireNullableString(record, 'instagramUrl'),
    websiteUrl: requireNullableString(record, 'websiteUrl'),
  };
}

export function decodeProfessionalSummaryList(
  value: unknown,
): ProfessionalSummary[] {
  return requireArray(value, 'Professional summary response').map(
    decodeProfessionalSummary,
  );
}
