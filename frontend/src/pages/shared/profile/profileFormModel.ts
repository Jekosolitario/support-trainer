import type {
  Gender,
  MyClientProfileResponse,
  MyProfessionalProfileResponse,
} from '../../../api/authTypes';
import type {
  ClientUpdateMyProfileRequest,
  ProfessionalUpdateMyProfileRequest,
} from '../../../api/meProfileTypes';

export interface ClientProfileDraft {
  firstName: string;
  lastName: string;
  birthDate: string;
  heightCm: string;
  primaryGoal: string;
  gender: Gender | '';
  medicalNotes: string;
  injuryNotes: string;
  notes: string;
}

export interface ProfessionalProfileDraft {
  firstName: string;
  lastName: string;
  phoneNumber: string;
  bio: string;
  workplaceName: string;
  city: string;
  instagramUrl: string;
  websiteUrl: string;
}

export type ProfileFieldErrors = Readonly<Record<string, string>>;

const URL_PATTERN = /^(?:\s*|https?:\/\/.+)$/;
const HEIGHT_DIGITS_PATTERN = /^(?:0|[1-9]\d{0,2})(?:\.\d{1,2})?$/;
const GENDERS: readonly Gender[] = ['MALE', 'FEMALE', 'OTHER', 'NOT_SPECIFIED'];

function optionalBaseline(value: string | null): string {
  return value ?? '';
}

function trimRequired(value: string): string {
  return value.trim();
}

function optionalStringsEqual(baseline: string | null, draft: string): boolean {
  return optionalBaseline(baseline) === draft.trim();
}

function isPastDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return false;
  }

  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) {
    return false;
  }

  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return date < today;
}

function parseHeightCm(value: string): number | null {
  const trimmed = value.trim();
  if (trimmed === '' || !HEIGHT_DIGITS_PATTERN.test(trimmed)) {
    return null;
  }

  const parsed = Number(trimmed);
  if (!Number.isFinite(parsed) || parsed < 0.01) {
    return null;
  }

  return parsed;
}

function isGender(value: string): value is Gender {
  return (GENDERS as readonly string[]).includes(value);
}

function isProfileUrl(value: string): boolean {
  return URL_PATTERN.test(value);
}

export function mapClientProfileToDraft(
  profile: MyClientProfileResponse,
): ClientProfileDraft {
  return {
    firstName: profile.firstName,
    lastName: profile.lastName,
    birthDate: profile.birthDate,
    heightCm: String(profile.heightCm),
    primaryGoal: profile.primaryGoal,
    gender: profile.gender,
    medicalNotes: optionalBaseline(profile.medicalNotes),
    injuryNotes: optionalBaseline(profile.injuryNotes),
    notes: optionalBaseline(profile.notes),
  };
}

export function mapProfessionalProfileToDraft(
  profile: MyProfessionalProfileResponse,
): ProfessionalProfileDraft {
  return {
    firstName: profile.firstName,
    lastName: profile.lastName,
    phoneNumber: optionalBaseline(profile.phoneNumber),
    bio: optionalBaseline(profile.bio),
    workplaceName: optionalBaseline(profile.workplaceName),
    city: optionalBaseline(profile.city),
    instagramUrl: optionalBaseline(profile.instagramUrl),
    websiteUrl: optionalBaseline(profile.websiteUrl),
  };
}

export function isClientProfileDirty(
  baseline: MyClientProfileResponse,
  draft: ClientProfileDraft,
): boolean {
  if (trimRequired(draft.firstName) !== baseline.firstName) {
    return true;
  }
  if (trimRequired(draft.lastName) !== baseline.lastName) {
    return true;
  }
  if (draft.birthDate.trim() !== baseline.birthDate) {
    return true;
  }

  const heightCm = parseHeightCm(draft.heightCm);
  if (heightCm === null || heightCm !== baseline.heightCm) {
    return true;
  }

  if (trimRequired(draft.primaryGoal) !== baseline.primaryGoal) {
    return true;
  }
  if (draft.gender !== baseline.gender) {
    return true;
  }
  if (!optionalStringsEqual(baseline.medicalNotes, draft.medicalNotes)) {
    return true;
  }
  if (!optionalStringsEqual(baseline.injuryNotes, draft.injuryNotes)) {
    return true;
  }
  if (!optionalStringsEqual(baseline.notes, draft.notes)) {
    return true;
  }

  return false;
}

export function isProfessionalProfileDirty(
  baseline: MyProfessionalProfileResponse,
  draft: ProfessionalProfileDraft,
): boolean {
  return buildProfessionalProfilePatch(baseline, draft) !== null;
}

export function buildClientProfilePatch(
  baseline: MyClientProfileResponse,
  draft: ClientProfileDraft,
): ClientUpdateMyProfileRequest | null {
  const patch: {
    -readonly [
      K in keyof ClientUpdateMyProfileRequest
    ]?: ClientUpdateMyProfileRequest[K];
  } = {};

  const firstName = trimRequired(draft.firstName);
  if (firstName !== baseline.firstName) {
    patch.firstName = firstName;
  }

  const lastName = trimRequired(draft.lastName);
  if (lastName !== baseline.lastName) {
    patch.lastName = lastName;
  }

  const birthDate = draft.birthDate.trim();
  if (birthDate !== '' && birthDate !== baseline.birthDate) {
    patch.birthDate = birthDate;
  }

  const heightCm = parseHeightCm(draft.heightCm);
  if (heightCm !== null && heightCm !== baseline.heightCm) {
    patch.heightCm = heightCm;
  }

  const primaryGoal = trimRequired(draft.primaryGoal);
  if (primaryGoal !== baseline.primaryGoal) {
    patch.primaryGoal = primaryGoal;
  }

  if (draft.gender !== '' && draft.gender !== baseline.gender) {
    patch.gender = draft.gender;
  }

  if (!optionalStringsEqual(baseline.medicalNotes, draft.medicalNotes)) {
    patch.medicalNotes = draft.medicalNotes.trim();
  }

  if (!optionalStringsEqual(baseline.injuryNotes, draft.injuryNotes)) {
    patch.injuryNotes = draft.injuryNotes.trim();
  }

  if (!optionalStringsEqual(baseline.notes, draft.notes)) {
    patch.notes = draft.notes.trim();
  }

  return Object.keys(patch).length === 0 ? null : patch;
}

export function buildProfessionalProfilePatch(
  baseline: MyProfessionalProfileResponse,
  draft: ProfessionalProfileDraft,
): ProfessionalUpdateMyProfileRequest | null {
  const patch: {
    -readonly [
      K in keyof ProfessionalUpdateMyProfileRequest
    ]?: ProfessionalUpdateMyProfileRequest[K];
  } = {};

  const firstName = trimRequired(draft.firstName);
  if (firstName !== baseline.firstName) {
    patch.firstName = firstName;
  }

  const lastName = trimRequired(draft.lastName);
  if (lastName !== baseline.lastName) {
    patch.lastName = lastName;
  }

  if (!optionalStringsEqual(baseline.phoneNumber, draft.phoneNumber)) {
    patch.phoneNumber = draft.phoneNumber.trim();
  }

  if (!optionalStringsEqual(baseline.bio, draft.bio)) {
    patch.bio = draft.bio.trim();
  }

  if (!optionalStringsEqual(baseline.workplaceName, draft.workplaceName)) {
    patch.workplaceName = draft.workplaceName.trim();
  }

  if (!optionalStringsEqual(baseline.city, draft.city)) {
    patch.city = draft.city.trim();
  }

  if (!optionalStringsEqual(baseline.instagramUrl, draft.instagramUrl)) {
    patch.instagramUrl = draft.instagramUrl.trim();
  }

  if (!optionalStringsEqual(baseline.websiteUrl, draft.websiteUrl)) {
    patch.websiteUrl = draft.websiteUrl.trim();
  }

  return Object.keys(patch).length === 0 ? null : patch;
}

/**
 * Pure client-side mirror of backend constraints for future form UX.
 * Does not invent stricter rules than Bean Validation + MeService.
 */
export function validateClientProfileDraft(
  draft: ClientProfileDraft,
): ProfileFieldErrors {
  const errors: Record<string, string> = {};

  const firstName = trimRequired(draft.firstName);
  if (firstName === '') {
    errors.firstName = 'Il nome non può essere vuoto.';
  } else if (firstName.length > 100) {
    errors.firstName = 'Il nome non può superare 100 caratteri.';
  }

  const lastName = trimRequired(draft.lastName);
  if (lastName === '') {
    errors.lastName = 'Il cognome non può essere vuoto.';
  } else if (lastName.length > 100) {
    errors.lastName = 'Il cognome non può superare 100 caratteri.';
  }

  const birthDate = draft.birthDate.trim();
  if (birthDate === '') {
    errors.birthDate = 'La data di nascita è obbligatoria.';
  } else if (!isPastDate(birthDate)) {
    errors.birthDate = 'La data di nascita deve essere nel passato.';
  }

  if (parseHeightCm(draft.heightCm) === null) {
    errors.heightCm =
      'L’altezza deve essere maggiore di 0 e avere al massimo 3 cifre intere e 2 decimali.';
  }

  const primaryGoal = trimRequired(draft.primaryGoal);
  if (primaryGoal === '') {
    errors.primaryGoal = 'L’obiettivo principale non può essere vuoto.';
  } else if (primaryGoal.length > 255) {
    errors.primaryGoal =
      'L’obiettivo principale non può superare 255 caratteri.';
  }

  if (draft.gender === '' || !isGender(draft.gender)) {
    errors.gender = 'Seleziona un genere valido.';
  }

  if (draft.medicalNotes.trim().length > 5000) {
    errors.medicalNotes =
      'Le note mediche non possono superare 5000 caratteri.';
  }

  if (draft.injuryNotes.trim().length > 5000) {
    errors.injuryNotes =
      'Le note sugli infortuni non possono superare 5000 caratteri.';
  }

  if (draft.notes.trim().length > 5000) {
    errors.notes = 'Le note non possono superare 5000 caratteri.';
  }

  return errors;
}

export function validateProfessionalProfileDraft(
  draft: ProfessionalProfileDraft,
): ProfileFieldErrors {
  const errors: Record<string, string> = {};

  const firstName = trimRequired(draft.firstName);
  if (firstName === '') {
    errors.firstName = 'Il nome non può essere vuoto.';
  } else if (firstName.length > 100) {
    errors.firstName = 'Il nome non può superare 100 caratteri.';
  }

  const lastName = trimRequired(draft.lastName);
  if (lastName === '') {
    errors.lastName = 'Il cognome non può essere vuoto.';
  } else if (lastName.length > 100) {
    errors.lastName = 'Il cognome non può superare 100 caratteri.';
  }

  if (draft.phoneNumber.trim().length > 30) {
    errors.phoneNumber = 'Il numero di telefono non può superare 30 caratteri.';
  }

  if (draft.bio.trim().length > 5000) {
    errors.bio = 'La bio non può superare 5000 caratteri.';
  }

  if (draft.workplaceName.trim().length > 150) {
    errors.workplaceName =
      'Il nome del luogo di lavoro non può superare 150 caratteri.';
  }

  if (draft.city.trim().length > 100) {
    errors.city = 'La città non può superare 100 caratteri.';
  }

  const instagramUrl = draft.instagramUrl.trim();
  if (instagramUrl.length > 255) {
    errors.instagramUrl = 'L’URL Instagram non può superare 255 caratteri.';
  } else if (!isProfileUrl(draft.instagramUrl)) {
    errors.instagramUrl =
      'L’URL Instagram deve iniziare con http:// o https://.';
  }

  const websiteUrl = draft.websiteUrl.trim();
  if (websiteUrl.length > 255) {
    errors.websiteUrl = 'L’URL del sito web non può superare 255 caratteri.';
  } else if (!isProfileUrl(draft.websiteUrl)) {
    errors.websiteUrl =
      'L’URL del sito web deve iniziare con http:// o https://.';
  }

  return errors;
}
