import type { ClientOperationalStatus, Gender } from './authTypes';
import {
  requireArray,
  requireEnum,
  requireFiniteNumber,
  requireIsoLocalDate,
  requireJsonObject,
  requireNullableString,
  requirePositiveSafeInteger,
  requireString,
  type JsonObject,
} from './apiResponseDecoders';

const CLIENT_OPERATIONAL_STATUSES: readonly ClientOperationalStatus[] = [
  'ATTIVO',
  'INFORTUNATO',
  'PAUSA',
];

const GENDERS: readonly Gender[] = ['MALE', 'FEMALE', 'OTHER', 'NOT_SPECIFIED'];

export interface ClientSummary {
  id: number;
  firstName: string;
  lastName: string;
  profileImageUrl: string | null;
}

export interface ClientDetail extends ClientSummary {
  primaryGoal: string;
  operationalStatus: ClientOperationalStatus;
  birthDate: string;
  heightCm: number;
  gender: Gender;
}

function decodeClientSummaryRecord(record: JsonObject): ClientSummary {
  return {
    id: requirePositiveSafeInteger(record, 'id'),
    firstName: requireString(record, 'firstName'),
    lastName: requireString(record, 'lastName'),
    profileImageUrl: requireNullableString(record, 'profileImageUrl'),
  };
}

export function decodeClientSummary(value: unknown): ClientSummary {
  return decodeClientSummaryRecord(requireJsonObject(value, 'Client summary'));
}

export function decodeClientDetail(value: unknown): ClientDetail {
  const record = requireJsonObject(value, 'Client detail');
  const summary = decodeClientSummaryRecord(record);

  return {
    ...summary,
    primaryGoal: requireString(record, 'primaryGoal'),
    operationalStatus: requireEnum(
      record,
      'operationalStatus',
      CLIENT_OPERATIONAL_STATUSES,
    ),
    birthDate: requireIsoLocalDate(record, 'birthDate'),
    heightCm: requireFiniteNumber(record, 'heightCm'),
    gender: requireEnum(record, 'gender', GENDERS),
  };
}

export function decodeClientSummaryList(value: unknown): ClientSummary[] {
  return requireArray(value, 'Client summary response').map(
    decodeClientSummary,
  );
}
