import {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type FormEvent,
} from 'react';
import { flushSync } from 'react-dom';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import {
  confirmEmailVerification,
  resendEmailVerification,
} from '../../api/authOnboardingApi';
import { StaleAuthOperationError } from '../../api/csrfMutation';
import {
  getEmailVerificationErrorPresentation,
  getResendEmailFieldMessage,
  type EmailVerificationUiStatus,
} from '../../auth/emailVerificationError';
import { parseVerifyEmailTokenFromHash } from '../../auth/verifyEmailToken';
import styles from './VerifyEmailPage.module.css';

type VerifyPhase =
  | 'bootstrapping'
  | 'missing-token'
  | 'invalid-token'
  | EmailVerificationUiStatus
  | 'confirming';

const RESEND_NEUTRAL_COPY =
  'Se l’indirizzo è associato a un account da verificare, riceverai le istruzioni necessarie.';
const COOLDOWN_MS = 60_000;

interface ConfirmOperation {
  readonly generation: number;
  readonly attemptId: number;
  readonly token: string;
  readonly promise: Promise<void>;
}

function useCooldown(cooldownUntil: number | null): number {
  const [remainingMs, setRemainingMs] = useState(0);

  useEffect(() => {
    let cancelled = false;
    let intervalId: number | null = null;

    const syncRemaining = (value: number) => {
      queueMicrotask(() => {
        if (!cancelled) {
          setRemainingMs(value);
        }
      });
    };

    if (cooldownUntil === null) {
      syncRemaining(0);
      return () => {
        cancelled = true;
      };
    }

    const tick = () => {
      const remaining = Math.max(0, cooldownUntil - Date.now());
      syncRemaining(remaining);
      if (remaining === 0 && intervalId !== null) {
        window.clearInterval(intervalId);
        intervalId = null;
      }
    };

    tick();
    intervalId = window.setInterval(tick, 250);

    return () => {
      cancelled = true;
      if (intervalId !== null) {
        window.clearInterval(intervalId);
      }
    };
  }, [cooldownUntil]);

  return remainingMs;
}

function hasNonEmptyHash(hash: string): boolean {
  return hash !== '' && hash !== '#';
}

export function VerifyEmailPage() {
  const location = useLocation();
  const navigate = useNavigate();

  const [phase, setPhase] = useState<VerifyPhase>('bootstrapping');
  const [summary, setSummary] = useState<string | null>(null);
  const [resendEmail, setResendEmail] = useState('');
  const [resendEmailError, setResendEmailError] = useState<
    string | undefined
  >();
  const [resendSummary, setResendSummary] = useState<string | null>(null);
  const [isResending, setIsResending] = useState(false);
  const [cooldownUntil, setCooldownUntil] = useState<number | null>(null);

  const mountedRef = useRef(true);
  const flowGenerationRef = useRef(0);
  const tokenRef = useRef<string | null>(null);
  const confirmAttemptIdRef = useRef(0);
  const confirmInFlightRef = useRef<ConfirmOperation | null>(null);
  const sanitizeScheduledForGenerationRef = useRef<number | null>(null);
  const resendAttemptIdRef = useRef(0);
  const resendSubmittingRef = useRef(false);
  const resendGenerationRef = useRef(0);

  const cooldownRemainingMs = useCooldown(cooldownUntil);
  const cooldownActive = cooldownRemainingMs > 0;

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  function beginConfirmAttempt(
    generation: number,
    token: string,
  ): Promise<void> {
    const existing = confirmInFlightRef.current;
    if (
      existing !== null &&
      existing.generation === generation &&
      existing.token === token
    ) {
      return existing.promise;
    }

    const attemptId = confirmAttemptIdRef.current + 1;
    confirmAttemptIdRef.current = attemptId;

    let settle!: () => void;
    const promise = new Promise<void>((resolve) => {
      settle = resolve;
    });

    const operation: ConfirmOperation = {
      generation,
      attemptId,
      token,
      promise,
    };

    // Guardia registrata sincronicamente PRIMA di qualsiasi chiamata API.
    confirmInFlightRef.current = operation;

    void (async () => {
      let staleRetryUsed = false;

      try {
        while (true) {
          try {
            await confirmEmailVerification({ token });

            if (
              !mountedRef.current ||
              flowGenerationRef.current !== generation ||
              confirmAttemptIdRef.current !== attemptId ||
              tokenRef.current !== token ||
              confirmInFlightRef.current !== operation
            ) {
              return;
            }

            setPhase('success');
            setSummary('Email verificata correttamente.');
            return;
          } catch (error) {
            if (
              !mountedRef.current ||
              flowGenerationRef.current !== generation ||
              confirmAttemptIdRef.current !== attemptId ||
              tokenRef.current !== token ||
              confirmInFlightRef.current !== operation
            ) {
              return;
            }

            if (error instanceof StaleAuthOperationError && !staleRetryUsed) {
              staleRetryUsed = true;
              continue;
            }

            const presentation = getEmailVerificationErrorPresentation(error);
            setPhase(presentation.status);
            setSummary(presentation.summary);
            return;
          }
        }
      } finally {
        if (confirmInFlightRef.current === operation) {
          confirmInFlightRef.current = null;
        }
        settle();
      }
    })();

    return promise;
  }

  useLayoutEffect(() => {
    const hash = location.hash;
    const parsed = parseVerifyEmailTokenFromHash(hash);
    const needsSanitize = hasNonEmptyHash(hash);

    if (!needsSanitize && !parsed.ok) {
      let cancelled = false;
      queueMicrotask(() => {
        if (cancelled || !mountedRef.current) {
          return;
        }
        if (tokenRef.current === null) {
          setPhase('missing-token');
          setSummary('Link di verifica non disponibile.');
        }
      });
      return () => {
        cancelled = true;
      };
    }

    const generation = flowGenerationRef.current + 1;
    flowGenerationRef.current = generation;
    sanitizeScheduledForGenerationRef.current = generation;
    confirmInFlightRef.current = null;
    confirmAttemptIdRef.current += 1;

    if (parsed.ok) {
      tokenRef.current = parsed.token;
    } else {
      tokenRef.current = null;
    }

    resendGenerationRef.current = generation;
    resendAttemptIdRef.current += 1;
    resendSubmittingRef.current = false;

    let cancelled = false;

    queueMicrotask(() => {
      if (cancelled || !mountedRef.current) {
        return;
      }

      if (flowGenerationRef.current !== generation) {
        return;
      }

      if (sanitizeScheduledForGenerationRef.current !== generation) {
        return;
      }

      setIsResending(false);
      setResendEmail('');
      setResendEmailError(undefined);
      setResendSummary(null);
      setCooldownUntil(null);

      if (parsed.ok) {
        setPhase('confirming');
        setSummary(null);
      } else {
        setPhase(
          parsed.reason === 'missing' ? 'missing-token' : 'invalid-token',
        );
        setSummary(
          parsed.reason === 'missing'
            ? 'Link di verifica non disponibile.'
            : 'Il link di verifica non è valido.',
        );
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

  useLayoutEffect(() => {
    if (phase !== 'confirming') {
      return;
    }

    const token = tokenRef.current;
    const generation = flowGenerationRef.current;

    if (token === null) {
      return;
    }

    if (window.location.hash !== '' || location.hash !== '') {
      return;
    }

    void beginConfirmAttempt(generation, token);
  }, [phase, location.hash]);

  async function handleManualRetry(): Promise<void> {
    const token = tokenRef.current;
    const generation = flowGenerationRef.current;

    if (token === null || phase !== 'temporary-error') {
      return;
    }

    if (confirmInFlightRef.current !== null) {
      return;
    }

    setPhase('confirming');
    setSummary(null);
    void beginConfirmAttempt(generation, token);
  }

  async function handleResend(
    event: FormEvent<HTMLFormElement>,
  ): Promise<void> {
    event.preventDefault();

    if (resendSubmittingRef.current || cooldownActive) {
      return;
    }

    const email = resendEmail.trim();
    if (email === '') {
      setResendEmailError('Inserisci l’email.');
      setResendSummary(null);
      return;
    }

    if (email.length > 100 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setResendEmailError(
        email.length > 100
          ? 'L’email non può superare 100 caratteri.'
          : 'Inserisci un indirizzo email valido.',
      );
      setResendSummary(null);
      return;
    }

    resendSubmittingRef.current = true;
    const attemptId = resendAttemptIdRef.current + 1;
    resendAttemptIdRef.current = attemptId;
    const generation = flowGenerationRef.current;
    resendGenerationRef.current = generation;
    setIsResending(true);
    setResendEmailError(undefined);
    setResendSummary(null);

    try {
      await resendEmailVerification({ email });

      if (
        !mountedRef.current ||
        resendAttemptIdRef.current !== attemptId ||
        flowGenerationRef.current !== generation ||
        resendGenerationRef.current !== generation
      ) {
        return;
      }

      setResendSummary(RESEND_NEUTRAL_COPY);
      setCooldownUntil(Date.now() + COOLDOWN_MS);
    } catch (error) {
      if (
        !mountedRef.current ||
        resendAttemptIdRef.current !== attemptId ||
        flowGenerationRef.current !== generation ||
        resendGenerationRef.current !== generation
      ) {
        return;
      }

      const presentation = getResendEmailFieldMessage(error);
      setResendEmailError(presentation.email);
      setResendSummary(presentation.summary);
    } finally {
      if (
        resendAttemptIdRef.current === attemptId &&
        flowGenerationRef.current === generation &&
        resendGenerationRef.current === generation
      ) {
        resendSubmittingRef.current = false;
        if (mountedRef.current) {
          setIsResending(false);
        }
      }
    }
  }

  const showResend =
    phase === 'missing-token' ||
    phase === 'invalid-token' ||
    phase === 'expired' ||
    phase === 'not-found' ||
    phase === 'already-used' ||
    phase === 'application-error' ||
    phase === 'temporary-error';

  const cooldownSeconds = Math.ceil(cooldownRemainingMs / 1000);

  return (
    <article className={styles.page} aria-labelledby="verify-title">
      <header className={styles.introduction}>
        <p className={styles.eyebrow}>Verifica email</p>
        <h1 id="verify-title">Verifica dell’indirizzo email</h1>
        <p>Conferma il tuo account Support Trainer.</p>
      </header>

      <section
        className={styles.formPanel}
        aria-busy={phase === 'confirming' || isResending}
        aria-live="polite"
      >
        {phase === 'bootstrapping' || phase === 'confirming' ? (
          <p role="status">Verifica in corso</p>
        ) : null}

        {phase === 'success' ? (
          <>
            <p role="status">{summary ?? 'Email verificata correttamente.'}</p>
            <nav className={styles.secondaryLinks} aria-label="Prossimi passi">
              <Link to="/login">Vai al login</Link>
            </nav>
          </>
        ) : null}

        {phase !== 'bootstrapping' &&
        phase !== 'confirming' &&
        phase !== 'success' ? (
          <>
            {summary === null ? null : (
              <p className={styles.errorSummary} role="alert">
                {summary}
              </p>
            )}

            {phase === 'temporary-error' ? (
              <button
                className={styles.submit}
                onClick={() => void handleManualRetry()}
                type="button"
              >
                Riprova
              </button>
            ) : null}

            {showResend ? (
              <form
                className={styles.form}
                noValidate
                onSubmit={(event) => void handleResend(event)}
              >
                <p>Puoi richiedere un nuovo invio inserendo la tua email.</p>
                <div className={styles.field}>
                  <label htmlFor="verify-resend-email">Email</label>
                  <input
                    aria-describedby={
                      resendEmailError === undefined
                        ? undefined
                        : 'verify-resend-email-error'
                    }
                    aria-invalid={
                      resendEmailError === undefined ? undefined : true
                    }
                    autoCapitalize="none"
                    autoComplete="email"
                    disabled={isResending || cooldownActive}
                    id="verify-resend-email"
                    maxLength={100}
                    name="email"
                    onChange={(event) => {
                      setResendEmail(event.target.value);
                      setResendEmailError(undefined);
                      setResendSummary(null);
                    }}
                    spellCheck={false}
                    type="email"
                    value={resendEmail}
                  />
                  {resendEmailError === undefined ? null : (
                    <p
                      className={styles.fieldError}
                      id="verify-resend-email-error"
                    >
                      {resendEmailError}
                    </p>
                  )}
                </div>
                {resendSummary === null ? null : (
                  <p className={styles.infoMessage} role="status">
                    {resendSummary}
                  </p>
                )}
                <button
                  className={styles.submit}
                  disabled={isResending || cooldownActive}
                  type="submit"
                >
                  {isResending
                    ? 'Invio in corso'
                    : cooldownActive
                      ? `Invia di nuovo tra ${String(cooldownSeconds)} s`
                      : 'Invia di nuovo'}
                </button>
              </form>
            ) : null}

            <nav className={styles.secondaryLinks} aria-label="Altre opzioni">
              <Link to="/login">Vai al login</Link>
            </nav>
          </>
        ) : null}
      </section>
    </article>
  );
}
