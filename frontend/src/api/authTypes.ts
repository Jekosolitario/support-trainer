export type UserRole = 'CLIENT' | 'PROFESSIONAL';

export type ProfessionalSpecialization = 'PERSONAL_TRAINER' | 'NUTRITIONIST';

export type AccountStatus = 'PENDING_VERIFICATION' | 'ACTIVE';

export type Gender = 'MALE' | 'FEMALE' | 'OTHER' | 'NOT_SPECIFIED';

export type ClientOperationalStatus = 'ATTIVO' | 'INFORTUNATO' | 'PAUSA';

export type ProfessionalOperationalStatus =
  'DISPONIBILE' | 'ASSENTE' | 'FERIE' | 'MALATTIA';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterProfessionalRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  specialization: ProfessionalSpecialization;
}

export interface ValidateInviteCodeRequest {
  code: string;
}

export interface ValidateInviteCodeResponse {
  valid: true;
  code: string;
  professionalId: number;
  expiresAt: string;
}

export interface RegisterClientRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  inviteCode: string;
  birthDate: string;
  heightCm: number;
  primaryGoal: string;
  gender: Gender;
  medicalNotes?: string;
  injuryNotes?: string;
  notes?: string;
}

export interface RegistrationAcceptedResponse {
  message: string;
}

export interface ConfirmEmailVerificationRequest {
  token: string;
}

export interface ResendEmailVerificationRequest {
  email: string;
}

export interface PasswordRecoveryRequest {
  email: string;
}

export interface PasswordRecoveryConfirmRequest {
  token: string;
  newPassword: string;
}

export interface PasswordRecoveryAcceptedResponse {
  message: string;
}

export interface MessageResponse {
  message: string;
}

export interface MyAccountResponse {
  id: number;
  email: string;
  role: UserRole;
  accountStatus: AccountStatus;
  emailVerified: boolean;
  createdAt: string;
  updatedAt: string;
}

interface MyProfileCommon {
  id: number;
  firstName: string;
  lastName: string;
  profileImageUrl: string | null;
  active: boolean;
}

export interface MyClientProfileResponse extends MyProfileCommon {
  role: 'CLIENT';
  operationalStatus: ClientOperationalStatus;
  specialization: null;
  phoneNumber: null;
  bio: null;
  workplaceName: null;
  city: null;
  instagramUrl: null;
  websiteUrl: null;
  birthDate: string;
  heightCm: number;
  primaryGoal: string;
  gender: Gender;
  medicalNotes: string | null;
  injuryNotes: string | null;
  notes: string | null;
}

export interface MyProfessionalProfileResponse extends MyProfileCommon {
  role: 'PROFESSIONAL';
  operationalStatus: ProfessionalOperationalStatus;
  specialization: ProfessionalSpecialization;
  phoneNumber: string | null;
  bio: string | null;
  workplaceName: string | null;
  city: string | null;
  instagramUrl: string | null;
  websiteUrl: string | null;
  birthDate: null;
  heightCm: null;
  primaryGoal: null;
  gender: null;
  medicalNotes: null;
  injuryNotes: null;
  notes: null;
}

export type MyProfileResponse =
  MyClientProfileResponse | MyProfessionalProfileResponse;
