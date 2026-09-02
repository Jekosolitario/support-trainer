import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';

import { requestPasswordRecovery } from '../../api/authOnboardingApi';
import { getPasswordRecoveryRequestPresentation } from '../../auth/passwordRecoveryError';
import styles from './VerifyEmailPage.module.css';

type Phase = 'form' | 'submitting' | 'success';

const SUCCESS_COPY =
  'Se esiste un account associato a questa email, riceverai le istruzioni per reimpostare la password.';

function validateEmail(email: string): string | null {
  if (email === '') {
    return 'Inserisci l’email.';
  }
  if (email.length > 100) {
    return 'L’email non può superare 100 caratteri.';
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return 'Inserisci un indirizzo email valido.';
  }
  return null;
}

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [phase, setPhase] = useState<Phase>('form');
  const [emailError, setEmailError] = useState<string | undefined>();
  const [summary, setSummary] = useState<string | null>(null);

  const mountedRef = useRef(true);
  const submittingRef = useRef(false);
  const attemptIdRef = useRef(0);
  const emailRef = useRef<HTMLInputElement>(null);
  const summaryRef = useRef<HTMLParagraphElement>(null);

  const isSubmitting = phase === 'submitting';

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    if (emailError !== undefined) {
      emailRef.current?.focus();
      return;
    }
    if (summary !== null) {
      summaryRef.current?.focus();
    }
  }, [emailError, summary, phase]);

  function handleEmailChange(value: string): void {
    setEmail(value);
    setEmailError(undefined);
    if (phase !== 'success') {
      setSummary(null);
    }
  }

  async function handleSubmit(
    event: FormEvent<HTMLFormElement>,
  ): Promise<void> {
    event.preventDefault();

    if (submittingRef.current || phase === 'success') {
      return;
    }

    const trimmed = email.trim();
    const localError = validateEmail(trimmed);
    if (localError !== null) {
      setEmailError(localError);
      setSummary(null);
      return;
    }

    submittingRef.current = true;
    const attemptId = attemptIdRef.current + 1;
    attemptIdRef.current = attemptId;
    setPhase('submitting');
    setEmailError(undefined);
    setSummary(null);

    try {
      await requestPasswordRecovery({ email: trimmed });

      if (!mountedRef.current || attemptIdRef.current !== attemptId) {
        return;
      }

      setPhase('success');
      setSummary(SUCCESS_COPY);
    } catch (error) {
      if (!mountedRef.current || attemptIdRef.current !== attemptId) {
        return;
      }

      const presentation = getPasswordRecoveryRequestPresentation(error);
      setPhase('form');
      if (presentation.kind === 'email') {
        setEmailError(presentation.email);
        setSummary(null);
      } else {
        setEmailError(undefined);
        setSummary(presentation.summary);
      }
    } finally {
      if (attemptIdRef.current === attemptId) {
        submittingRef.current = false;
      }
    }
  }

  return (
    <article className={styles.page} aria-labelledby="forgot-password-title">
      <header className={styles.introduction}>
        <p className={styles.eyebrow}>Recupero password</p>
        <h1 id="forgot-password-title">Password dimenticata</h1>
        <p>
          Inserisci l’email del tuo account. Se è associata a un account,
          riceverai le istruzioni per reimpostare la password.
        </p>
      </header>

      <section
        className={styles.formPanel}
        aria-busy={isSubmitting}
        aria-live="polite"
      >
        {phase === 'success' ? (
          <p ref={summaryRef} role="status" tabIndex={-1}>
            {summary ?? SUCCESS_COPY}
          </p>
        ) : (
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
              <label htmlFor="forgot-password-email">Email</label>
              <input
                ref={emailRef}
                aria-describedby={
                  emailError === undefined
                    ? undefined
                    : 'forgot-password-email-error'
                }
                aria-invalid={emailError === undefined ? undefined : true}
                autoCapitalize="none"
                autoComplete="email"
                disabled={isSubmitting}
                id="forgot-password-email"
                maxLength={100}
                name="email"
                onChange={(event) => handleEmailChange(event.target.value)}
                spellCheck={false}
                type="email"
                value={email}
              />
              {emailError === undefined ? null : (
                <p
                  className={styles.fieldError}
                  id="forgot-password-email-error"
                >
                  {emailError}
                </p>
              )}
            </div>
            <button
              className={styles.submit}
              disabled={isSubmitting}
              type="submit"
            >
              {isSubmitting ? 'Invio in corso' : 'Invia istruzioni'}
            </button>
          </form>
        )}
      </section>

      <nav className={styles.secondaryLinks} aria-label="Altre opzioni">
        <Link to="/login">Torna al login</Link>
      </nav>
    </article>
  );
}
