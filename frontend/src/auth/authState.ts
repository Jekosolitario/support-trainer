import { createContext, useContext } from 'react';

import type {
  LoginRequest,
  MyAccountResponse,
  MyProfileResponse,
} from '../api/authTypes';
import type { UserAccessProfile } from '../app/config/access';

export type AuthStatus =
  'initializing' | 'unauthenticated' | 'authenticated' | 'unavailable';

export type AuthOperation =
  'bootstrap' | 'login' | 'post-login-hydration' | 'logout' | 'reconciliation';

export type AuthOperationName = 'login' | 'logout' | 'reconcileSession';

export type UnauthenticatedReason =
  | 'no-session'
  | 'session-invalidated'
  | 'login-rejected'
  | 'post-login-session-missing'
  | 'logout-completed';

export type UnavailableReason =
  | 'bootstrap-failed'
  | 'login-indeterminate'
  | 'post-login-csrf-refresh-failed'
  | 'post-login-hydration-failed'
  | 'logout-indeterminate'
  | 'reconciliation-failed'
  | 'inconsistent-session-data';

interface EmptyAuthData {
  readonly account: null;
  readonly profile: null;
  readonly accessProfile: null;
}

export interface InitializingAuthState extends EmptyAuthData {
  readonly status: 'initializing';
  readonly operation: AuthOperation;
  readonly reason: null;
}

export interface UnauthenticatedAuthState extends EmptyAuthData {
  readonly status: 'unauthenticated';
  readonly operation: null;
  readonly reason: UnauthenticatedReason;
}

export interface AuthenticatedAuthState {
  readonly status: 'authenticated';
  readonly operation: null;
  readonly reason: null;
  readonly account: MyAccountResponse;
  readonly profile: MyProfileResponse;
  readonly accessProfile: UserAccessProfile;
}

export interface UnavailableAuthState extends EmptyAuthData {
  readonly status: 'unavailable';
  readonly operation: null;
  readonly reason: UnavailableReason;
}

export type AuthState =
  | InitializingAuthState
  | UnauthenticatedAuthState
  | AuthenticatedAuthState
  | UnavailableAuthState;

export interface AuthContextValue {
  readonly state: AuthState;
  login(credentials: LoginRequest): Promise<void>;
  logout(): Promise<void>;
  reconcileSession(): Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);

  if (context === null) {
    throw new Error('useAuth must be used within an AuthProvider');
  }

  return context;
}

export class AuthOperationNotAllowedError extends Error {
  readonly operation: AuthOperationName;
  readonly status: AuthStatus;

  constructor(operation: AuthOperationName, status: AuthStatus) {
    super(
      `Authentication operation "${operation}" is not allowed from "${status}"`,
    );
    this.name = 'AuthOperationNotAllowedError';
    this.operation = operation;
    this.status = status;
  }
}
