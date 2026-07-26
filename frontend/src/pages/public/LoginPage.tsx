import {
  useEffect,
  useRef,
  useState,
  type FormEvent,
  type RefObject,
} from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';

import { AuthUnavailableBoundary } from '../../auth/AuthUnavailableBoundary';
import {
  useAuth,
  type AuthContextValue,
  type AuthState,
  type InitializingAuthState,
  type UnauthenticatedAuthState,
  type UnauthenticatedReason,
} from '../../auth/authState';
import { getLoginErrorPresentation } from '../../auth/loginError';
import {
  getDashboardTarget,
  getSafeLoginTarget,
} from '../../auth/loginRedirect';
import styles from './LoginPage.module.css';

interface AttemptCandidate {
  readonly attemptId: number;
  readonly error: unknown;
}

interface AttemptUiState {
  readonly attemptId: number | null;
  readonly originReason: UnauthenticatedReason | null;
  readonly isSubmitting: boolean;
  readonly dismissed: boolean;
  readonly candidate: AttemptCandidate | null;
}

const EMPTY_ATTEMPT_UI: AttemptUiState = {
  attemptId: null,
  originReason: null,
  isSubmitting: false,
  dismissed: false,
  candidate: null,
};

interface LoginInteractionProps {
  readonly state: InitializingAuthState | UnauthenticatedAuthState;
  readonly login: AuthContextValue['login'];
}

function lifecycleMessage(state: AuthState): string | null {
  if (state.status !== 'unauthenticated') {
    return null;
  }

  switch (state.reason) {
    case 'post-login-session-missing':
      return 'Accesso non completato. Riprova.';
    case 'session-invalidated':
      return 'La sessione è terminata. Accedi di nuovo.';
    case 'login-rejected':
    case 'no-session':
    case 'logout-completed':
      return null;
  }
}

function LoginStatus({ operation }: { readonly operation: string }) {
  const isLoginOperation =
    operation === 'login' || operation === 'post-login-hydration';

  return (
    <article className={styles.statePage}>
      <section className={styles.statePanel} aria-labelledby="login-title">
        <p className={styles.eyebrow}>Accesso sicuro</p>
        <h1 id="login-title">Login</h1>
        <p aria-live="polite" role="status">
          {isLoginOperation
            ? 'Accesso in corso'
            : 'Verifica della sessione in corso'}
        </p>
      </section>
    </article>
  );
}

function focusLoginError(
  emailError: string | undefined,
  passwordError: string | undefined,
  emailRef: RefObject<HTMLInputElement | null>,
  passwordRef: RefObject<HTMLInputElement | null>,
  summaryRef: RefObject<HTMLParagraphElement | null>,
): void {
  if (emailError !== undefined) {
    emailRef.current?.focus();
  } else if (passwordError !== undefined) {
    passwordRef.current?.focus();
  } else {
    summaryRef.current?.focus();
  }
}

function LoginInteraction({ state, login }: LoginInteractionProps) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [attemptUi, setAttemptUi] = useState<AttemptUiState>(EMPTY_ATTEMPT_UI);
  const attemptIdRef = useRef(0);
  const activeAttemptIdRef = useRef<number | null>(null);
  const dismissedAttemptIdRef = useRef<number | null>(null);
  const submittingRef = useRef(false);
  const emailRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  const summaryRef = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    if (
      state.status === 'unauthenticated' &&
      state.reason !== 'login-rejected'
    ) {
      activeAttemptIdRef.current = null;
      submittingRef.current = false;
    }
  }, [state]);

  const currentVisibleError =
    state.status === 'unauthenticated' &&
    state.reason === 'login-rejected' &&
    !attemptUi.dismissed &&
    attemptUi.attemptId !== null &&
    attemptUi.candidate?.attemptId === attemptUi.attemptId
      ? getLoginErrorPresentation(attemptUi.candidate.error)
      : null;
  const globalLifecycleMessage = lifecycleMessage(state);
  const summary =
    globalLifecycleMessage ?? currentVisibleError?.summary ?? null;
  const emailError =
    globalLifecycleMessage === null
      ? currentVisibleError?.fieldErrors.email
      : undefined;
  const passwordError =
    globalLifecycleMessage === null
      ? currentVisibleError?.fieldErrors.password
      : undefined;
  const isSubmitting =
    attemptUi.isSubmitting &&
    (state.status !== 'unauthenticated' ||
      state.reason === attemptUi.originReason ||
      state.reason === 'login-rejected');

  useEffect(() => {
    if (
      state.status !== 'unauthenticated' ||
      (summary === null &&
        emailError === undefined &&
        passwordError === undefined)
    ) {
      return;
    }

    focusLoginError(
      emailError,
      passwordError,
      emailRef,
      passwordRef,
      summaryRef,
    );
  }, [emailError, passwordError, state.status, summary]);

  function dismissAttemptErrors(): void {
    if (activeAttemptIdRef.current !== null) {
      dismissedAttemptIdRef.current = activeAttemptIdRef.current;
    }

    setAttemptUi((current) => ({
      ...current,
      dismissed: true,
      candidate: null,
    }));
  }

  function handleEmailChange(value: string): void {
    setEmail(value);
    dismissAttemptErrors();
  }

  function handlePasswordChange(value: string): void {
    setPassword(value);
    dismissAttemptErrors();
  }

  async function handleSubmit(
    event: FormEvent<HTMLFormElement>,
  ): Promise<void> {
    event.preventDefault();

    if (submittingRef.current) {
      return;
    }

    submittingRef.current = true;
    const attemptId = attemptIdRef.current + 1;
    attemptIdRef.current = attemptId;
    activeAttemptIdRef.current = attemptId;
    dismissedAttemptIdRef.current = null;
    setAttemptUi({
      attemptId,
      originReason:
        state.status === 'unauthenticated' ? state.reason : 'no-session',
      isSubmitting: true,
      dismissed: false,
      candidate: null,
    });

    try {
      await login({ email, password });
    } catch (error) {
      if (
        attemptId === activeAttemptIdRef.current &&
        attemptId !== dismissedAttemptIdRef.current
      ) {
        setAttemptUi((current) =>
          current.attemptId === attemptId && !current.dismissed
            ? {
                ...current,
                candidate: { attemptId, error },
              }
            : current,
        );
      }
    } finally {
      if (attemptId === activeAttemptIdRef.current) {
        submittingRef.current = false;
        setAttemptUi((current) =>
          current.attemptId === attemptId
            ? { ...current, isSubmitting: false }
            : current,
        );
      }
    }
  }

  if (state.status === 'initializing') {
    return <LoginStatus operation={state.operation} />;
  }

  const hasErrors =
    summary !== null || emailError !== undefined || passwordError !== undefined;

  return (
    <article className={styles.page} aria-labelledby="login-title">
      <header className={styles.introduction}>
        <p className={styles.eyebrow}>Accesso sicuro</p>
        <h1 id="login-title">Login</h1>
        <p>
          Accedi alla tua area Support Trainer con le credenziali del tuo
          account.
        </p>
      </header>

      <section className={styles.formPanel} aria-label="Credenziali di accesso">
        <form
          className={styles.form}
          aria-busy={isSubmitting}
          onSubmit={(event) => void handleSubmit(event)}
        >
          {hasErrors ? (
            <div className={styles.errorRegion} role="alert">
              {summary === null ? null : (
                <p
                  className={styles.errorSummary}
                  ref={summaryRef}
                  tabIndex={-1}
                >
                  {summary}
                </p>
              )}
              {emailError === undefined ? null : (
                <p className={styles.fieldError} id="login-email-error">
                  {emailError}
                </p>
              )}
              {passwordError === undefined ? null : (
                <p className={styles.fieldError} id="login-password-error">
                  {passwordError}
                </p>
              )}
            </div>
          ) : null}

          <div className={styles.field}>
            <label htmlFor="login-email">Email</label>
            <input
              ref={emailRef}
              aria-describedby={
                emailError === undefined ? undefined : 'login-email-error'
              }
              aria-invalid={emailError === undefined ? undefined : true}
              autoCapitalize="none"
              autoComplete="username"
              disabled={isSubmitting}
              id="login-email"
              maxLength={100}
              name="email"
              onChange={(event) => handleEmailChange(event.target.value)}
              required
              spellCheck={false}
              type="email"
              value={email}
            />
          </div>

          <div className={styles.field}>
            <label htmlFor="login-password">Password</label>
            <input
              ref={passwordRef}
              aria-describedby={
                passwordError === undefined ? undefined : 'login-password-error'
              }
              aria-invalid={passwordError === undefined ? undefined : true}
              autoComplete="current-password"
              disabled={isSubmitting}
              id="login-password"
              minLength={8}
              name="password"
              onChange={(event) => handlePasswordChange(event.target.value)}
              required
              type="password"
              value={password}
            />
          </div>

          <button
            className={styles.submit}
            disabled={isSubmitting}
            type="submit"
          >
            {isSubmitting ? 'Accesso in corso' : 'Accedi'}
          </button>
        </form>
      </section>

      <nav className={styles.secondaryLinks} aria-label="Altre opzioni">
        <Link to="/register/professional">
          Sei un professionista? Registrati
        </Link>
        <Link to="/invite/validate">Hai un codice invito?</Link>
      </nav>
    </article>
  );
}

export function LoginPage() {
  const { state, login } = useAuth();
  const location = useLocation();

  if (state.status === 'unavailable') {
    return (
      <div className={styles.statePage}>
        <div className={styles.statePanel}>
          <AuthUnavailableBoundary />
        </div>
      </div>
    );
  }

  if (state.status === 'authenticated') {
    const target =
      getSafeLoginTarget(location.state) ??
      getDashboardTarget(state.accessProfile);

    return <Navigate replace to={target} />;
  }

  return <LoginInteraction login={login} state={state} />;
}
