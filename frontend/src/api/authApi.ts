import type {
  LoginRequest,
  MyAccountResponse,
  MyProfileResponse,
} from './authTypes';
import { advanceEpoch, currentEpoch } from './authEpoch';
import { ensureCsrf } from './csrf';
import { performCsrfMutation, StaleAuthOperationError } from './csrfMutation';
import { request } from './httpClient';

export interface MeRequestOptions {
  readonly invalidateOn401?: boolean;
  readonly signal?: AbortSignal;
}

export class AuthTransitionInProgressError extends Error {
  constructor() {
    super('Another authentication transition is already in progress');
    this.name = 'AuthTransitionInProgressError';
  }
}

export class PostLoginCsrfRefreshError extends Error {
  readonly cause: unknown;

  constructor(cause: unknown) {
    super('Login succeeded but the post-login CSRF refresh failed');
    this.name = 'PostLoginCsrfRefreshError';
    this.cause = cause;
  }
}

export { StaleAuthOperationError };

let authTransitionInProgress = false;

function acquireAuthTransition(): () => void {
  if (authTransitionInProgress) {
    throw new AuthTransitionInProgressError();
  }

  authTransitionInProgress = true;
  return () => {
    authTransitionInProgress = false;
  };
}

function assertCurrentEpoch(expectedEpoch: number): void {
  const actualEpoch = currentEpoch();

  if (actualEpoch !== expectedEpoch) {
    throw new StaleAuthOperationError(expectedEpoch, actualEpoch);
  }
}

export async function login(credentials: LoginRequest): Promise<void> {
  const releaseTransition = acquireAuthTransition();

  try {
    const loginEpoch = advanceEpoch();

    await performCsrfMutation<void>('/auth/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(credentials),
      invalidateOn401: false,
      invalidateCsrfOnCommit: true,
    });
    assertCurrentEpoch(loginEpoch);

    try {
      await ensureCsrf();
    } catch (cause) {
      assertCurrentEpoch(loginEpoch);
      throw new PostLoginCsrfRefreshError(cause);
    }

    assertCurrentEpoch(loginEpoch);
  } finally {
    releaseTransition();
  }
}

export async function logout(): Promise<void> {
  const releaseTransition = acquireAuthTransition();

  try {
    advanceEpoch();

    await performCsrfMutation<void>('/auth/logout', {
      method: 'POST',
      invalidateOn401: false,
      invalidateCsrfOnCommit: true,
    });
  } finally {
    releaseTransition();
  }
}

export function getMyAccount(
  options: MeRequestOptions = {},
): Promise<MyAccountResponse> {
  return request<MyAccountResponse>('/me/account', {
    invalidateOn401: options.invalidateOn401 ?? true,
    signal: options.signal,
  });
}

export function getMyProfile(
  options: MeRequestOptions = {},
): Promise<MyProfileResponse> {
  return request<MyProfileResponse>('/me/profile', {
    invalidateOn401: options.invalidateOn401 ?? true,
    signal: options.signal,
  });
}
