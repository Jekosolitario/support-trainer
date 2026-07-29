import {
  useEffect,
  useRef,
  useState,
  type FormEvent,
  type RefObject,
} from 'react';
import { Link } from 'react-router-dom';

import {
  registerProfessional,
  resendEmailVerification,
} from '../../api/authOnboardingApi';
import type { ProfessionalSpecialization } from '../../api/authTypes';
import { isApiClientError } from '../../api/types';
import { validateRegistrationPassword } from '../../auth/passwordPolicy';
import {
  getRegisterGenericFailureMessage,
  getRegisterProfessionalErrorPresentation,
  type RegisterProfessionalFieldErrors,
} from '../../auth/registerProfessionalError';
import { specializationLabel } from '../shared/profile/profileLabels';
import styles from './RegisterProfessionalPage.module.css';

type Phase = 'form' | 'check-email';

const CHECK_EMAIL_COPY =
  'Se la registrazione può essere completata, riceverai le istruzioni per verificare l’indirizzo email.';
const RESEND_NEUTRAL_COPY =
  'Se l’indirizzo è associato a un account da verificare, riceverai le istruzioni necessarie.';
const COOLDOWN_MS = 60_000;
const SPECIALIZATIONS: ProfessionalSpecialization[] = [
  'PERSONAL_TRAINER',
  'NUTRITIONIST',
];

interface FormDraft {
  readonly firstName: string;
  readonly lastName: string;
  readonly email: string;
  readonly password: string;
  readonly specialization: ProfessionalSpecialization | '';
}

const EMPTY_DRAFT: FormDraft = {
  firstName: '',
  lastName: '',
  email: '',
  password: '',
  specialization: '',
};

function validateDraft(draft: FormDraft): RegisterProfessionalFieldErrors {
  const fieldErrors: {
    firstName?: string;
    lastName?: string;
    email?: string;
    password?: string;
    specialization?: string;
  } = {};

  const firstName = draft.firstName.trim();
  const lastName = draft.lastName.trim();
  const email = draft.email.trim();

  if (firstName === '') {
    fieldErrors.firstName = 'Inserisci il nome.';
  } else if (firstName.length > 100) {
    fieldErrors.firstName = 'Il nome non può superare 100 caratteri.';
  }

  if (lastName === '') {
    fieldErrors.lastName = 'Inserisci il cognome.';
  } else if (lastName.length > 100) {
    fieldErrors.lastName = 'Il cognome non può superare 100 caratteri.';
  }

  if (email === '') {
    fieldErrors.email = 'Inserisci l’email.';
  } else if (email.length > 100) {
    fieldErrors.email = 'L’email non può superare 100 caratteri.';
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    fieldErrors.email = 'Inserisci un indirizzo email valido.';
  }

  const passwordError = validateRegistrationPassword(draft.password);
  if (passwordError !== null) {
    fieldErrors.password = passwordError;
  }

  if (draft.specialization === '') {
    fieldErrors.specialization = 'Seleziona la specializzazione.';
  }

  return fieldErrors;
}

function focusFirstRegisterError(
  fieldErrors: RegisterProfessionalFieldErrors,
  refs: {
    readonly firstName: RefObject<HTMLInputElement | null>;
    readonly lastName: RefObject<HTMLInputElement | null>;
    readonly email: RefObject<HTMLInputElement | null>;
    readonly password: RefObject<HTMLInputElement | null>;
    readonly specialization: RefObject<HTMLFieldSetElement | null>;
    readonly summary: RefObject<HTMLParagraphElement | null>;
  },
): void {
  if (fieldErrors.firstName !== undefined) {
    refs.firstName.current?.focus();
  } else if (fieldErrors.lastName !== undefined) {
    refs.lastName.current?.focus();
  } else if (fieldErrors.email !== undefined) {
    refs.email.current?.focus();
  } else if (fieldErrors.password !== undefined) {
    refs.password.current?.focus();
  } else if (fieldErrors.specialization !== undefined) {
    refs.specialization.current?.focus();
  } else {
    refs.summary.current?.focus();
  }
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

export function RegisterProfessionalPage() {
  const [phase, setPhase] = useState<Phase>('form');
  const [draft, setDraft] = useState<FormDraft>(EMPTY_DRAFT);
  const [fieldErrors, setFieldErrors] =
    useState<RegisterProfessionalFieldErrors>({});
  const [summary, setSummary] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [registeredEmail, setRegisteredEmail] = useState('');
  const [resendSummary, setResendSummary] = useState<string | null>(null);
  const [isResending, setIsResending] = useState(false);
  const [cooldownUntil, setCooldownUntil] = useState<number | null>(null);

  const mountedRef = useRef(true);
  const submittingRef = useRef(false);
  const registerAttemptIdRef = useRef(0);
  const resendSubmittingRef = useRef(false);
  const resendAttemptIdRef = useRef(0);

  const firstNameRef = useRef<HTMLInputElement>(null);
  const lastNameRef = useRef<HTMLInputElement>(null);
  const emailRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  const specializationRef = useRef<HTMLFieldSetElement>(null);
  const summaryRef = useRef<HTMLParagraphElement>(null);

  const cooldownRemainingMs = useCooldown(cooldownUntil);
  const cooldownActive = cooldownRemainingMs > 0;

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  function updateField<K extends keyof FormDraft>(
    key: K,
    value: FormDraft[K],
  ): void {
    setDraft((current) => ({ ...current, [key]: value }));
    setFieldErrors((current) => {
      if (current[key] === undefined && summary === null) {
        return current;
      }

      const next = { ...current };
      delete next[key];
      return next;
    });
    setSummary(null);
  }

  async function handleSubmit(
    event: FormEvent<HTMLFormElement>,
  ): Promise<void> {
    event.preventDefault();

    if (submittingRef.current) {
      return;
    }

    const nextErrors = validateDraft(draft);
    if (Object.keys(nextErrors).length > 0) {
      setFieldErrors(nextErrors);
      setSummary(null);
      queueMicrotask(() => {
        focusFirstRegisterError(nextErrors, {
          firstName: firstNameRef,
          lastName: lastNameRef,
          email: emailRef,
          password: passwordRef,
          specialization: specializationRef,
          summary: summaryRef,
        });
      });
      return;
    }

    if (draft.specialization === '') {
      return;
    }

    submittingRef.current = true;
    const attemptId = registerAttemptIdRef.current + 1;
    registerAttemptIdRef.current = attemptId;
    setIsSubmitting(true);
    setFieldErrors({});
    setSummary(null);

    try {
      await registerProfessional({
        firstName: draft.firstName.trim(),
        lastName: draft.lastName.trim(),
        email: draft.email.trim(),
        password: draft.password,
        specialization: draft.specialization,
      });

      if (!mountedRef.current || registerAttemptIdRef.current !== attemptId) {
        return;
      }

      const emailForResend = draft.email.trim();
      setRegisteredEmail(emailForResend);
      setDraft(EMPTY_DRAFT);
      setFieldErrors({});
      setSummary(null);
      setPhase('check-email');
      setResendSummary(null);
      setCooldownUntil(null);
    } catch (error) {
      if (!mountedRef.current || registerAttemptIdRef.current !== attemptId) {
        return;
      }

      const presentation = getRegisterProfessionalErrorPresentation(error);
      if (presentation === null) {
        setSummary(
          isApiClientError(error)
            ? getRegisterGenericFailureMessage()
            : getRegisterGenericFailureMessage(),
        );
        setFieldErrors({});
      } else {
        setSummary(presentation.summary);
        setFieldErrors(presentation.fieldErrors);
      }

      queueMicrotask(() => {
        focusFirstRegisterError(presentation?.fieldErrors ?? {}, {
          firstName: firstNameRef,
          lastName: lastNameRef,
          email: emailRef,
          password: passwordRef,
          specialization: specializationRef,
          summary: summaryRef,
        });
      });
    } finally {
      if (registerAttemptIdRef.current === attemptId) {
        submittingRef.current = false;
        if (mountedRef.current) {
          setIsSubmitting(false);
        }
      }
    }
  }

  async function handleResend(): Promise<void> {
    if (resendSubmittingRef.current || cooldownActive) {
      return;
    }

    resendSubmittingRef.current = true;
    const attemptId = resendAttemptIdRef.current + 1;
    resendAttemptIdRef.current = attemptId;
    setIsResending(true);
    setResendSummary(null);

    try {
      await resendEmailVerification({ email: registeredEmail });

      if (!mountedRef.current || resendAttemptIdRef.current !== attemptId) {
        return;
      }

      setResendSummary(RESEND_NEUTRAL_COPY);
      setCooldownUntil(Date.now() + COOLDOWN_MS);
    } catch {
      if (!mountedRef.current || resendAttemptIdRef.current !== attemptId) {
        return;
      }

      setResendSummary('Invio non completato. Riprova.');
    } finally {
      if (resendAttemptIdRef.current === attemptId) {
        resendSubmittingRef.current = false;
        if (mountedRef.current) {
          setIsResending(false);
        }
      }
    }
  }

  if (phase === 'check-email') {
    const cooldownSeconds = Math.ceil(cooldownRemainingMs / 1000);

    return (
      <article className={styles.page} aria-labelledby="register-title">
        <header className={styles.introduction}>
          <p className={styles.eyebrow}>Registrazione</p>
          <h1 id="register-title">Registrazione professionista</h1>
          <p>Controlla la tua email</p>
        </header>

        <section className={styles.formPanel} aria-live="polite">
          <p>{CHECK_EMAIL_COPY}</p>
          <p>
            Abbiamo usato l’indirizzo <strong>{registeredEmail}</strong>.
          </p>
          {resendSummary === null ? null : (
            <p className={styles.infoMessage} role="status">
              {resendSummary}
            </p>
          )}
          <button
            className={styles.submit}
            disabled={isResending || cooldownActive}
            onClick={() => void handleResend()}
            type="button"
          >
            {isResending
              ? 'Invio in corso'
              : cooldownActive
                ? `Invia di nuovo tra ${String(cooldownSeconds)} s`
                : 'Invia di nuovo'}
          </button>
          {cooldownActive ? (
            <p className={styles.cooldownHint} aria-live="polite">
              Potrai richiedere un nuovo invio tra {String(cooldownSeconds)}{' '}
              secondi.
            </p>
          ) : null}
        </section>

        <nav className={styles.secondaryLinks} aria-label="Altre opzioni">
          <Link to="/login">Vai al login</Link>
        </nav>
      </article>
    );
  }

  const hasErrors =
    summary !== null ||
    fieldErrors.firstName !== undefined ||
    fieldErrors.lastName !== undefined ||
    fieldErrors.email !== undefined ||
    fieldErrors.password !== undefined ||
    fieldErrors.specialization !== undefined;

  return (
    <article className={styles.page} aria-labelledby="register-title">
      <header className={styles.introduction}>
        <p className={styles.eyebrow}>Registrazione</p>
        <h1 id="register-title">Registrazione professionista</h1>
        <p>
          Crea il tuo account professionale. Dopo l’invio riceverai le
          istruzioni per verificare l’email.
        </p>
      </header>

      <section
        className={styles.formPanel}
        aria-label="Dati di registrazione professionista"
      >
        <form
          className={styles.form}
          aria-busy={isSubmitting}
          noValidate
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
              {fieldErrors.firstName === undefined ? null : (
                <p className={styles.fieldError} id="register-first-name-error">
                  {fieldErrors.firstName}
                </p>
              )}
              {fieldErrors.lastName === undefined ? null : (
                <p className={styles.fieldError} id="register-last-name-error">
                  {fieldErrors.lastName}
                </p>
              )}
              {fieldErrors.email === undefined ? null : (
                <p className={styles.fieldError} id="register-email-error">
                  {fieldErrors.email}
                </p>
              )}
              {fieldErrors.password === undefined ? null : (
                <p className={styles.fieldError} id="register-password-error">
                  {fieldErrors.password}
                </p>
              )}
              {fieldErrors.specialization === undefined ? null : (
                <p
                  className={styles.fieldError}
                  id="register-specialization-error"
                >
                  {fieldErrors.specialization}
                </p>
              )}
            </div>
          ) : null}

          <div className={styles.field}>
            <label htmlFor="register-first-name">Nome</label>
            <input
              ref={firstNameRef}
              aria-describedby={
                fieldErrors.firstName === undefined
                  ? undefined
                  : 'register-first-name-error'
              }
              aria-invalid={
                fieldErrors.firstName === undefined ? undefined : true
              }
              autoComplete="given-name"
              disabled={isSubmitting}
              id="register-first-name"
              maxLength={100}
              name="firstName"
              onChange={(event) => updateField('firstName', event.target.value)}
              value={draft.firstName}
            />
          </div>

          <div className={styles.field}>
            <label htmlFor="register-last-name">Cognome</label>
            <input
              ref={lastNameRef}
              aria-describedby={
                fieldErrors.lastName === undefined
                  ? undefined
                  : 'register-last-name-error'
              }
              aria-invalid={
                fieldErrors.lastName === undefined ? undefined : true
              }
              autoComplete="family-name"
              disabled={isSubmitting}
              id="register-last-name"
              maxLength={100}
              name="lastName"
              onChange={(event) => updateField('lastName', event.target.value)}
              value={draft.lastName}
            />
          </div>

          <div className={styles.field}>
            <label htmlFor="register-email">Email</label>
            <input
              ref={emailRef}
              aria-describedby={
                fieldErrors.email === undefined
                  ? undefined
                  : 'register-email-error'
              }
              aria-invalid={fieldErrors.email === undefined ? undefined : true}
              autoCapitalize="none"
              autoComplete="email"
              disabled={isSubmitting}
              id="register-email"
              maxLength={100}
              name="email"
              onChange={(event) => updateField('email', event.target.value)}
              spellCheck={false}
              type="email"
              value={draft.email}
            />
          </div>

          <div className={styles.field}>
            <label htmlFor="register-password">Password</label>
            <input
              ref={passwordRef}
              aria-describedby={
                fieldErrors.password === undefined
                  ? undefined
                  : 'register-password-error'
              }
              aria-invalid={
                fieldErrors.password === undefined ? undefined : true
              }
              autoComplete="new-password"
              disabled={isSubmitting}
              id="register-password"
              name="password"
              onChange={(event) => updateField('password', event.target.value)}
              type="password"
              value={draft.password}
            />
          </div>

          <fieldset
            ref={specializationRef}
            className={styles.fieldset}
            aria-describedby={
              fieldErrors.specialization === undefined
                ? undefined
                : 'register-specialization-error'
            }
            aria-invalid={
              fieldErrors.specialization === undefined ? undefined : true
            }
            disabled={isSubmitting}
            tabIndex={-1}
          >
            <legend>Specializzazione</legend>
            {SPECIALIZATIONS.map((value) => (
              <label key={value} className={styles.radioOption}>
                <input
                  checked={draft.specialization === value}
                  name="specialization"
                  onChange={() => updateField('specialization', value)}
                  type="radio"
                  value={value}
                />
                {specializationLabel(value)}
              </label>
            ))}
          </fieldset>

          <button
            className={styles.submit}
            disabled={isSubmitting}
            type="submit"
          >
            {isSubmitting ? 'Registrazione in corso' : 'Registrati'}
          </button>
        </form>
      </section>

      <nav className={styles.secondaryLinks} aria-label="Altre opzioni">
        <Link to="/login">Hai già un account? Accedi</Link>
      </nav>
    </article>
  );
}
