import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';

import * as authApi from '../api/authApi';
import type { LoginRequest, MyProfileResponse } from '../api/authTypes';
import { advanceEpoch, currentEpoch } from '../api/authEpoch';
import { clearCsrf } from '../api/csrf';
import { subscribe } from '../api/sessionInvalidation';
import { HttpApiError } from '../api/types';
import {
  AuthOperationNotAllowedError,
  AuthContext,
  type AuthContextValue,
  type AuthOperation,
  type AuthState,
  type UnauthenticatedReason,
  type UnavailableReason,
} from './authState';
import { mapAuthenticatedUserToAccessProfile } from './mapAccessProfile';
import * as sessionReconciliation from './sessionReconciliation';
import type { SessionReconciliationOutcome } from './sessionReconciliation';

interface AuthProviderProps {
  readonly children: ReactNode;
}

interface OperationToken {
  readonly id: number;
  readonly mountGeneration: number;
  readonly operation: AuthOperation;
}

type ReconciliationSource = 'bootstrap' | 'post-login' | 'reconciliation';

const EMPTY_AUTH_DATA = {
  account: null,
  profile: null,
  accessProfile: null,
} as const;

const INITIAL_AUTH_STATE: AuthState = {
  status: 'initializing',
  operation: 'bootstrap',
  reason: null,
  ...EMPTY_AUTH_DATA,
};

function initializingState(operation: AuthOperation): AuthState {
  return {
    status: 'initializing',
    operation,
    reason: null,
    ...EMPTY_AUTH_DATA,
  };
}

function unauthenticatedState(reason: UnauthenticatedReason): AuthState {
  return {
    status: 'unauthenticated',
    operation: null,
    reason,
    ...EMPTY_AUTH_DATA,
  };
}

function unavailableState(reason: UnavailableReason): AuthState {
  return {
    status: 'unavailable',
    operation: null,
    reason,
    ...EMPTY_AUTH_DATA,
  };
}

function isAbortError(error: unknown): boolean {
  return (
    error !== null &&
    typeof error === 'object' &&
    'name' in error &&
    error.name === 'AbortError'
  );
}

function isDeterministicLoginRejection(error: unknown): boolean {
  return (
    error instanceof HttpApiError && error.status >= 400 && error.status < 500
  );
}

function isFinalCsrfValidationFailure(error: unknown): boolean {
  return (
    error instanceof HttpApiError &&
    error.status === 403 &&
    error.body?.code === 'CSRF_VALIDATION_FAILED'
  );
}

function unavailableReasonFor(
  source: ReconciliationSource,
  outcome: Extract<SessionReconciliationOutcome, { kind: 'unavailable' }>,
): UnavailableReason {
  if (outcome.reason === 'inconsistent-data') {
    return 'inconsistent-session-data';
  }

  switch (source) {
    case 'bootstrap':
      return 'bootstrap-failed';
    case 'post-login':
      return 'post-login-hydration-failed';
    case 'reconciliation':
      return 'reconciliation-failed';
  }
}

function unauthenticatedReasonFor(
  source: ReconciliationSource,
): UnauthenticatedReason {
  return source === 'post-login' ? 'post-login-session-missing' : 'no-session';
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [state, setState] = useState<AuthState>(INITIAL_AUTH_STATE);
  const stateRef = useRef<AuthState>(INITIAL_AUTH_STATE);
  const mountGenerationRef = useRef(0);
  const operationGenerationRef = useRef(0);
  const activeOperationRef = useRef<OperationToken | null>(null);
  const reconciliationControllerRef = useRef<AbortController | null>(null);

  const commitState = useCallback((nextState: AuthState): void => {
    stateRef.current = nextState;
    setState(nextState);
  }, []);

  const beginOperation = useCallback(
    (operation: AuthOperation, mountGeneration: number): OperationToken => {
      const token: OperationToken = {
        id: operationGenerationRef.current + 1,
        mountGeneration,
        operation,
      };

      operationGenerationRef.current = token.id;
      activeOperationRef.current = token;
      commitState(initializingState(operation));
      return token;
    },
    [commitState],
  );

  const finishOperation = useCallback((token: OperationToken): void => {
    if (activeOperationRef.current?.id === token.id) {
      activeOperationRef.current = null;
    }
  }, []);

  const isLifecycleCurrent = useCallback(
    (token: OperationToken, expectedEpoch: number): boolean =>
      mountGenerationRef.current === token.mountGeneration &&
      operationGenerationRef.current === token.id &&
      activeOperationRef.current?.id === token.id &&
      currentEpoch() === expectedEpoch,
    [],
  );

  const assertLifecycleCurrent = useCallback(
    (token: OperationToken, expectedEpoch: number): void => {
      if (!isLifecycleCurrent(token, expectedEpoch)) {
        throw new authApi.StaleAuthOperationError(
          expectedEpoch,
          currentEpoch(),
        );
      }
    },
    [isLifecycleCurrent],
  );

  const applyReconciliationOutcome = useCallback(
    (
      token: OperationToken,
      expectedEpoch: number,
      source: ReconciliationSource,
      outcome: SessionReconciliationOutcome,
    ): boolean => {
      if (!isLifecycleCurrent(token, expectedEpoch)) {
        return false;
      }

      switch (outcome.kind) {
        case 'authenticated':
          commitState({
            status: 'authenticated',
            operation: null,
            reason: null,
            account: outcome.account,
            profile: outcome.profile,
            accessProfile: outcome.accessProfile,
          });
          return true;
        case 'unauthenticated':
          clearCsrf();
          commitState(unauthenticatedState(unauthenticatedReasonFor(source)));
          return true;
        case 'unavailable':
          commitState(unavailableState(unavailableReasonFor(source, outcome)));
          return true;
        case 'cancelled':
          return false;
      }
    },
    [commitState, isLifecycleCurrent],
  );

  const runReconciliation = useCallback(
    async (
      token: OperationToken,
      expectedEpoch: number,
      source: ReconciliationSource,
      controller: AbortController,
    ): Promise<SessionReconciliationOutcome> => {
      reconciliationControllerRef.current = controller;

      try {
        const outcome = await sessionReconciliation.reconcileSessionSnapshot(
          controller.signal,
        );
        applyReconciliationOutcome(token, expectedEpoch, source, outcome);
        return outcome;
      } finally {
        if (reconciliationControllerRef.current === controller) {
          reconciliationControllerRef.current = null;
        }
      }
    },
    [applyReconciliationOutcome],
  );

  const assertNoActiveOperation = useCallback((): void => {
    if (
      activeOperationRef.current !== null ||
      stateRef.current.status === 'initializing'
    ) {
      throw new authApi.AuthTransitionInProgressError();
    }
  }, []);

  const login = useCallback(
    async (credentials: LoginRequest): Promise<void> => {
      assertNoActiveOperation();

      const previousState = stateRef.current;
      if (previousState.status !== 'unauthenticated') {
        throw new AuthOperationNotAllowedError('login', previousState.status);
      }

      const token = beginOperation('login', mountGenerationRef.current);
      const transitionPromise = authApi.login(credentials);
      const expectedEpoch = currentEpoch();

      try {
        try {
          await transitionPromise;
          assertLifecycleCurrent(token, expectedEpoch);
        } catch (error) {
          assertLifecycleCurrent(token, expectedEpoch);

          if (error instanceof authApi.AuthTransitionInProgressError) {
            commitState(previousState);
          } else if (error instanceof authApi.PostLoginCsrfRefreshError) {
            commitState(unavailableState('post-login-csrf-refresh-failed'));
          } else if (isDeterministicLoginRejection(error)) {
            commitState(unauthenticatedState('login-rejected'));
          } else if (
            error instanceof authApi.StaleAuthOperationError ||
            isAbortError(error)
          ) {
            throw error;
          } else {
            commitState(unavailableState('login-indeterminate'));
          }

          throw error;
        }

        assertLifecycleCurrent(token, expectedEpoch);
        commitState(initializingState('post-login-hydration'));

        const controller = new AbortController();
        const outcome = await runReconciliation(
          token,
          expectedEpoch,
          'post-login',
          controller,
        );
        assertLifecycleCurrent(token, expectedEpoch);

        if (outcome.kind === 'unauthenticated') {
          throw outcome.cause;
        }

        if (outcome.kind === 'unavailable') {
          throw outcome.cause;
        }

        if (outcome.kind === 'cancelled') {
          throw new DOMException(
            'Authentication lifecycle aborted',
            'AbortError',
          );
        }
      } finally {
        finishOperation(token);
      }
    },
    [
      assertLifecycleCurrent,
      assertNoActiveOperation,
      beginOperation,
      commitState,
      finishOperation,
      runReconciliation,
    ],
  );

  const logout = useCallback(async (): Promise<void> => {
    assertNoActiveOperation();

    const previousState = stateRef.current;
    if (previousState.status !== 'authenticated') {
      throw new AuthOperationNotAllowedError('logout', previousState.status);
    }

    const token = beginOperation('logout', mountGenerationRef.current);
    const transitionPromise = authApi.logout();
    const expectedEpoch = currentEpoch();

    try {
      try {
        await transitionPromise;
        assertLifecycleCurrent(token, expectedEpoch);
        commitState(unauthenticatedState('logout-completed'));
      } catch (error) {
        assertLifecycleCurrent(token, expectedEpoch);

        if (error instanceof authApi.AuthTransitionInProgressError) {
          commitState(previousState);
        } else if (isFinalCsrfValidationFailure(error)) {
          clearCsrf();
          commitState(previousState);
        } else if (
          error instanceof authApi.StaleAuthOperationError ||
          isAbortError(error)
        ) {
          throw error;
        } else {
          commitState(unavailableState('logout-indeterminate'));
        }

        throw error;
      }
    } finally {
      finishOperation(token);
    }
  }, [
    assertLifecycleCurrent,
    assertNoActiveOperation,
    beginOperation,
    commitState,
    finishOperation,
  ]);

  const reconcileSession = useCallback(async (): Promise<void> => {
    assertNoActiveOperation();

    const currentState = stateRef.current;
    if (currentState.status !== 'unavailable') {
      throw new AuthOperationNotAllowedError(
        'reconcileSession',
        currentState.status,
      );
    }

    const token = beginOperation('reconciliation', mountGenerationRef.current);
    const controller = new AbortController();
    const expectedEpoch = advanceEpoch();

    try {
      await runReconciliation(
        token,
        expectedEpoch,
        'reconciliation',
        controller,
      );
    } finally {
      finishOperation(token);
    }
  }, [
    assertNoActiveOperation,
    beginOperation,
    finishOperation,
    runReconciliation,
  ]);

  const applyProfileSnapshot = useCallback(
    (profile: MyProfileResponse, expectedEpoch: number): void => {
      const currentState = stateRef.current;

      if (currentState.status !== 'authenticated') {
        throw new AuthOperationNotAllowedError(
          'applyProfileSnapshot',
          currentState.status,
        );
      }

      const actualEpoch = currentEpoch();
      if (actualEpoch !== expectedEpoch) {
        throw new authApi.StaleAuthOperationError(expectedEpoch, actualEpoch);
      }

      const accessProfile = mapAuthenticatedUserToAccessProfile(
        currentState.account,
        profile,
      );

      commitState({
        status: 'authenticated',
        operation: null,
        reason: null,
        account: currentState.account,
        profile,
        accessProfile,
      });
    },
    [commitState],
  );

  useEffect(() => {
    const mountGeneration = mountGenerationRef.current + 1;
    mountGenerationRef.current = mountGeneration;

    const unsubscribe = subscribe(() => {
      if (mountGenerationRef.current !== mountGeneration) {
        return;
      }

      operationGenerationRef.current += 1;
      reconciliationControllerRef.current?.abort();
      reconciliationControllerRef.current = null;
      clearCsrf();
      commitState(unauthenticatedState('session-invalidated'));
    });

    const token = beginOperation('bootstrap', mountGeneration);
    const controller = new AbortController();
    const expectedEpoch = advanceEpoch();

    void runReconciliation(
      token,
      expectedEpoch,
      'bootstrap',
      controller,
    ).finally(() => {
      finishOperation(token);
    });

    return () => {
      if (mountGenerationRef.current === mountGeneration) {
        mountGenerationRef.current += 1;
      }
      operationGenerationRef.current += 1;
      controller.abort();
      reconciliationControllerRef.current?.abort();
      reconciliationControllerRef.current = null;
      unsubscribe();
    };
  }, [beginOperation, commitState, finishOperation, runReconciliation]);

  const value = useMemo<AuthContextValue>(
    () => ({
      state,
      login,
      logout,
      reconcileSession,
      applyProfileSnapshot,
    }),
    [applyProfileSnapshot, login, logout, reconcileSession, state],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
