import {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type FormEvent,
} from 'react';
import { flushSync } from 'react-dom';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import { confirmPasswordRecovery } from '../../api/authOnboardingApi';
import { validateRegistrationPassword } from '../../auth/passwordPolicy';
import { getPasswordRecoveryConfirmPresentation } from '../../auth/passwordRecoveryError';
import { parsePasswordResetTokenFromHash } from '../../auth/passwordResetToken';
import styles from './VerifyEmailPage.module.css';

type Phase =
  | 'bootstrapping'
  | 'missing-token'
  | 'ready'
  | 'submitting'
  | 'invalid-or-expired'
  | 'success'
  | 'technical-error';

const MISSING_TOKEN_COPY = 'Questo link non è valido.';
const INVALID_OR_EXPIRED_COPY =
  'Questo link non è valido o non è più utilizzabile.';
const SUCCESS_COPY = 'Password aggiornata';
const PASSWORD_HELP =
  'Almeno 8 caratteri, una maiuscola, un numero e un carattere speciale; massimo 72 byte UTF-8.';

function hasNonEmptyHash(hash: string): boolean {
  return hash !== '' && hash !== '#';
}

export function ResetPasswordPage() {
  const location = useLocation();
  const navigate = useNavigate();

  const [phase, setPhase] = useState<Phase>('bootstrapping');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordError, setPasswordError] = useState<string | undefined>();
  const [confirmError, setConfirmError] = useState<string | undefined>();
  const [summary, setSummary] = useState<string | null>(null);

  const mountedRef = useRef(true);
  const tokenRef = useRef<string | null>(null);
  const flowGenerationRef = useRef(0);
  const submittingRef = useRef(false);
  const attemptIdRef = useRef(0);
  const passwordRef = useRef<HTMLInputElement>(null);
  const confirmRef = useRef<HTMLInputElement>(null);
  const statusRef = useRef<HTMLHeadingElement>(null);
  const summaryRef = useRef<HTMLParagraphElement>(null);

  const isSubmitting = phase === 'submitting';

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  useLayoutEffect(() => {
    const hash = location.hash;
    const parsed = parsePasswordResetTokenFromHash(hash);
    const needsSanitize = hasNonEmptyHash(hash);

    if (!needsSanitize && !parsed.ok) {
      let cancelled = false;
      queueMicrotask(() => {
        if (cancelled || !mountedRef.current) {
          return;
        }
        if (tokenRef.current === null) {
          setPhase('missing-token');
          setSummary(MISSING_TOKEN_COPY);
        }
      });
      return () => {
        cancelled = true;
      };
    }

    const generation = flowGenerationRef.current + 1;
    flowGenerationRef.current = generation;

    if (parsed.ok) {
      tokenRef.current = parsed.token;
    } else {
      tokenRef.current = null;
    }

    let cancelled = false;

    queueMicrotask(() => {
      if (cancelled || !mountedRef.current) {
        return;
      }
      if (flowGenerationRef.current !== generation) {
        return;
      }

      if (parsed.ok) {
        setPhase('ready');
        setSummary(null);
        setPasswordError(undefined);
        setConfirmError(undefined);
      } else {
        setPhase('missing-token');
        setSummary(MISSING_TOKEN_COPY);
      }

      if (!needsSanitize) {
        return;
      }

      if (window.location.hash === '' && location.hash === '') {
        return;
      }

      flushSync(() => {
        navigate(
          {
            pathname: location.pathname,
            search: location.search,
            hash: '',
          },
          {
            replace: true,
            state: location.state,
          },
        );
      });
    });

    return () => {
      cancelled = true;
    };
  }, [
    location.hash,
    location.pathname,
    location.search,
    location.state,
    navigate,
  ]);

  useEffect(() => {
    if (
      phase === 'success' ||
      phase === 'invalid-or-expired' ||
      phase === 'missing-token'
    ) {
      statusRef.current?.focus();
      return;
    }
    if (passwordError !== undefined) {
      passwordRef.current?.focus();
      return;
    }
    if (confirmError !== undefined) {
      confirmRef.current?.focus();
      return;
    }
    if (summary !== null && phase === 'technical-error') {
      summaryRef.current?.focus();
    }
  }, [phase, passwordError, confirmError, summary]);

  async function handleSubmit(
    event: FormEvent<HTMLFormElement>,
  ): Promise<void> {
    event.preventDefault();

    if (submittingRef.current || phase === 'success') {
      return;
    }

    const token = tokenRef.current;
    if (token === null) {
      setPhase('missing-token');
      setSummary(MISSING_TOKEN_COPY);
      return;
    }

    const localPasswordError = validateRegistrationPassword(newPassword);
    const localConfirmError =
      newPassword !== confirmPassword
        ? 'Le password non coincidono.'
        : undefined;

    if (localPasswordError !== null || localConfirmError !== undefined) {
      setPasswordError(
        localPasswordError === null ? undefined : localPasswordError,
      );
      setConfirmError(localConfirmError);
      setSummary(null);
      setPhase('ready');
      return;
    }

    submittingRef.current = true;
    const attemptId = attemptIdRef.current + 1;
    attemptIdRef.current = attemptId;
    const generation = flowGenerationRef.current;
    setPhase('submitting');
    setPasswordError(undefined);
    setConfirmError(undefined);
    setSummary(null);

    try {
      await confirmPasswordRecovery({ token, newPassword });

      if (
        !mountedRef.current ||
        attemptIdRef.current !== attemptId ||
        flowGenerationRef.current !== generation
      ) {
        return;
      }

      tokenRef.current = null;
      setPhase('success');
      setSummary(SUCCESS_COPY);
      setNewPassword('');
      setConfirmPassword('');
    } catch (error) {
      if (
        !mountedRef.current ||
        attemptIdRef.current !== attemptId ||
        flowGenerationRef.current !== generation
      ) {
        return;
      }

      const presentation = getPasswordRecoveryConfirmPresentation(error);
      if (presentation.kind === 'invalid-or-expired') {
        tokenRef.current = null;
        setPhase('invalid-or-expired');
        setSummary(presentation.summary);
        return;
      }

      if (presentation.kind === 'validation') {
        setPhase('ready');
        setPasswordError(presentation.password);
        setSummary(presentation.summary);
        return;
      }

      setPhase('technical-error');
      setSummary(presentation.summary);
    } finally {
      if (attemptIdRef.current === attemptId) {
        submittingRef.current = false;
      }
    }
  }

  const showForm =
    phase === 'ready' || phase === 'submitting' || phase === 'technical-error';

  return (
    <article className={styles.page} aria-labelledby="reset-password-title">
      <header className={styles.introduction}>
        <p className={styles.eyebrow}>Recupero password</p>
        <h1 id="reset-password-title" ref={statusRef} tabIndex={-1}>
          {phase === 'success' ? SUCCESS_COPY : 'Reimposta la password'}
        </h1>
        {phase === 'success' ? (
          <p>Ora puoi accedere con la nuova password.</p>
        ) : (
          <p>Scegli una nuova password per il tuo account Support Trainer.</p>
        )}
      </header>

      <section
        className={styles.formPanel}
        aria-busy={isSubmitting}
        aria-live="polite"
      >
        {phase === 'bootstrapping' ? <p role="status">Caricamento</p> : null}

        {phase === 'missing-token' ? (
          <>
            <p className={styles.errorSummary} role="alert">
              {summary ?? MISSING_TOKEN_COPY}
            </p>
            <nav className={styles.secondaryLinks} aria-label="Prossimi passi">
              <Link to="/forgot-password">Richiedi un nuovo link</Link>
            </nav>
          </>
        ) : null}

        {phase === 'invalid-or-expired' ? (
          <>
            <p className={styles.errorSummary} role="alert">
              {summary ?? INVALID_OR_EXPIRED_COPY}
            </p>
            <nav className={styles.secondaryLinks} aria-label="Prossimi passi">
              <Link to="/forgot-password">Richiedi un nuovo link</Link>
            </nav>
          </>
        ) : null}

        {phase === 'success' ? (
          <>
            <p role="status">{summary ?? SUCCESS_COPY}</p>
            <nav className={styles.secondaryLinks} aria-label="Prossimi passi">
              <Link to="/login">Accedi</Link>
            </nav>
          </>
        ) : null}

        {showForm ? (
          <form
            className={styles.form}
            noValidate
            onSubmit={(event) => void handleSubmit(event)}
          >
            {summary === null ? null : (
              <p
                className={styles.errorSummary}
                ref={summaryRef}
                role="alert"
                tabIndex={-1}
              >
                {summary}
              </p>
            )}

            <div className={styles.field}>
              <label htmlFor="reset-password-new">Nuova password</label>
              <p className={styles.helperText} id="reset-password-help">
                {PASSWORD_HELP}
              </p>
              <input
                ref={passwordRef}
                aria-describedby={
                  passwordError === undefined
                    ? 'reset-password-help'
                    : 'reset-password-help reset-password-error'
                }
                aria-invalid={passwordError === undefined ? undefined : true}
                autoComplete="new-password"
                disabled={isSubmitting}
                id="reset-password-new"
                name="newPassword"
                onChange={(event) => {
                  setNewPassword(event.target.value);
                  setPasswordError(undefined);
                  if (phase === 'technical-error') {
                    setSummary(null);
                    setPhase('ready');
                  }
                }}
                type="password"
                value={newPassword}
              />
              {passwordError === undefined ? null : (
                <p className={styles.fieldError} id="reset-password-error">
                  {passwordError}
                </p>
              )}
            </div>

            <div className={styles.field}>
              <label htmlFor="reset-password-confirm">
                Conferma nuova password
              </label>
              <input
                ref={confirmRef}
                aria-describedby={
                  confirmError === undefined
                    ? undefined
                    : 'reset-password-confirm-error'
                }
                aria-invalid={confirmError === undefined ? undefined : true}
                autoComplete="new-password"
                disabled={isSubmitting}
                id="reset-password-confirm"
                name="confirmPassword"
                onChange={(event) => {
                  setConfirmPassword(event.target.value);
                  setConfirmError(undefined);
                  if (phase === 'technical-error') {
                    setSummary(null);
                    setPhase('ready');
                  }
                }}
                type="password"
                value={confirmPassword}
              />
              {confirmError === undefined ? null : (
                <p
                  className={styles.fieldError}
                  id="reset-password-confirm-error"
                >
                  {confirmError}
                </p>
              )}
            </div>

            <button
              className={styles.submit}
              disabled={isSubmitting}
              type="submit"
            >
              {isSubmitting ? 'Aggiornamento in corso' : 'Aggiorna password'}
            </button>
          </form>
        ) : null}
      </section>
    </article>
  );
}
