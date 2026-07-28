import type {
  ClientOperationalStatus,
  Gender,
  ProfessionalOperationalStatus,
} from './authTypes';

/**
 * Differential PATCH body for CLIENT profiles.
 * Presence of a key means "include in PATCH"; absence means unchanged.
 * Optional strings use "" to clear a persisted value.
 */
export interface ClientUpdateMyProfileRequest {
  readonly firstName?: string;
  readonly lastName?: string;
  readonly birthDate?: string;
  readonly heightCm?: number;
  readonly primaryGoal?: string;
  readonly gender?: Gender;
  readonly medicalNotes?: string;
  readonly injuryNotes?: string;
  readonly notes?: string;
}

/**
 * Differential PATCH body for PROFESSIONAL profiles.
 * Presence of a key means "include in PATCH"; absence means unchanged.
 * Optional strings use "" to clear a persisted value.
 */
export interface ProfessionalUpdateMyProfileRequest {
  readonly firstName?: string;
  readonly lastName?: string;
  readonly phoneNumber?: string;
  readonly bio?: string;
  readonly workplaceName?: string;
  readonly city?: string;
  readonly instagramUrl?: string;
  readonly websiteUrl?: string;
}

export type UpdateMyProfileRequest =
  ClientUpdateMyProfileRequest | ProfessionalUpdateMyProfileRequest;

export interface UpdateOperationalStatusRequest {
  readonly operationalStatus:
    ClientOperationalStatus | ProfessionalOperationalStatus;
}
