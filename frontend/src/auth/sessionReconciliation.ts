import { getMyAccount, getMyProfile } from '../api/authApi';
import type { MyAccountResponse, MyProfileResponse } from '../api/authTypes';
import { HttpApiError } from '../api/types';
import type { UserAccessProfile } from '../app/config/access';
import {
  AuthConsistencyError,
  mapAuthenticatedUserToAccessProfile,
} from './mapAccessProfile';

export type SessionReconciliationOutcome =
  | {
      readonly kind: 'authenticated';
      readonly account: MyAccountResponse;
      readonly profile: MyProfileResponse;
      readonly accessProfile: UserAccessProfile;
    }
  | {
      readonly kind: 'unauthenticated';
      readonly cause: HttpApiError;
    }
  | {
      readonly kind: 'unavailable';
      readonly reason: 'request-failed' | 'inconsistent-data';
      readonly cause: unknown;
    }
  | {
      readonly kind: 'cancelled';
    };

function isAbortError(error: unknown): boolean {
  return (
    error !== null &&
    typeof error === 'object' &&
    'name' in error &&
    error.name === 'AbortError'
  );
}

function findUnauthorized(
  results: readonly PromiseSettledResult<unknown>[],
): HttpApiError | null {
  for (const result of results) {
    if (
      result.status === 'rejected' &&
      result.reason instanceof HttpApiError &&
      result.reason.status === 401
    ) {
      return result.reason;
    }
  }

  return null;
}

export async function reconcileSessionSnapshot(
  signal: AbortSignal,
): Promise<SessionReconciliationOutcome> {
  const accountPromise = getMyAccount({
    invalidateOn401: false,
    signal,
  });
  const profilePromise = getMyProfile({
    invalidateOn401: false,
    signal,
  });

  const [accountResult, profileResult] = await Promise.allSettled([
    accountPromise,
    profilePromise,
  ]);
  const results = [accountResult, profileResult] as const;
  const unauthorized = findUnauthorized(results);

  if (unauthorized !== null) {
    return {
      kind: 'unauthenticated',
      cause: unauthorized,
    };
  }

  if (
    (accountResult.status === 'rejected' &&
      isAbortError(accountResult.reason)) ||
    (profileResult.status === 'rejected' && isAbortError(profileResult.reason))
  ) {
    return { kind: 'cancelled' };
  }

  if (
    accountResult.status === 'fulfilled' &&
    profileResult.status === 'fulfilled'
  ) {
    try {
      return {
        kind: 'authenticated',
        account: accountResult.value,
        profile: profileResult.value,
        accessProfile: mapAuthenticatedUserToAccessProfile(
          accountResult.value,
          profileResult.value,
        ),
      };
    } catch (cause) {
      return {
        kind: 'unavailable',
        reason: 'inconsistent-data',
        cause:
          cause instanceof AuthConsistencyError
            ? cause
            : new AuthConsistencyError('INCOMPLETE_DATA'),
      };
    }
  }

  const cause =
    accountResult.status === 'rejected'
      ? accountResult.reason
      : profileResult.status === 'rejected'
        ? profileResult.reason
        : new Error('Session reconciliation returned an incomplete result');

  return {
    kind: 'unavailable',
    reason: 'request-failed',
    cause,
  };
}
