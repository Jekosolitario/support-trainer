import {
  useEffect,
  useMemo,
  useReducer,
  useRef,
  useState,
  type FormEvent,
  type RefObject,
} from 'react';
import { Link, Navigate } from 'react-router-dom';

import {
  registerClient,
  resendEmailVerification,
} from '../../api/authOnboardingApi';
import type { Gender } from '../../api/authTypes';
import type { HttpApiError } from '../../api/types';
import type { RegisterClientOutcome } from '../../auth/clientOnboardingOutcome';
import { useClientOnboarding } from '../../auth/clientOnboardingState';
import {
  CLIENT_GENDERS,
  buildRegisterClientPayload,
  createInitialRegisterClientFlowState,
  localCivilDate,
  mapRegisterClientValidationFailure,
  registerClientFlowReducer,
  validateRegisterClientDraft,
  type RegisterClientDraft,
  type RegisterClientField,
  type RegisterClientFieldErrors,
  type RegisterClientTerminalPhase,
} from '../../auth/registerClientForm';
import styles from './RegisterClientPage.module.css';

type FocusTarget = HTMLInputElement | HTMLTextAreaElement | HTMLFieldSetElement;

const COOLDOWN_MS = 60_000;
const REGISTER_GENERIC_FAILURE =
  'Non è stato possibile inviare la richiesta. Controlla i dati e riprova.';
const RESEND_NEUTRAL_COPY =
  'Se l’indirizzo è associato a un account da verificare, riceverai le istruzioni necessarie.';
const RESEND_FAILURE_COPY =
  'Non è stato possibile completare l’invio. Potrai riprovare.';

const INVITE_UNAVAILABLE_CODES = new Set([
  'INVITE_CODE_NOT_FOUND',
  'INVITE_CODE_NOT_ACTIVE',
  'INVITE_CODE_ALREADY_USED',
  'INVITE_CODE_EXPIRED',
  'ACCOUNT_NOT_ACTIVE',
  'EMAIL_NOT_VERIFIED',
  'PROFESSIONAL_NOT_ACTIVE',
]);

const GENDER_LABELS: Record<Gender, string> = {
  MALE: 'Uomo',
  FEMALE: 'Donna',
  OTHER: 'Altro',
  NOT_SPECIFIED: 'Preferisco non specificarlo',
};

const FIELD_ORDER: readonly RegisterClientField[] = [
  'firstName',
  'lastName',
  'email',
  'password',
  'birthDate',
  'heightCm',
  'primaryGoal',
  'gender',
  'medicalNotes',
  'injuryNotes',
  'notes',
];

interface CooldownSnapshot {
  readonly deadline: number | null;
  readonly remainingMs: number;
}

function useCooldown(cooldownUntil: number | null): CooldownSnapshot {
  const [snapshot, setSnapshot] = useState<CooldownSnapshot>({
    deadline: null,
    remainingMs: 0,
  });

  useEffect(() => {
    let cancelled = false;
    let intervalId: number | null = null;

    const syncRemaining = () => {
      const remaining =
        cooldownUntil === null ? 0 : Math.max(0, cooldownUntil - Date.now());
      queueMicrotask(() => {
        if (!cancelled) {
          setSnapshot((current) =>
            current.deadline === cooldownUntil &&
            current.remainingMs === remaining
              ? current
              : { deadline: cooldownUntil, remainingMs: remaining },
          );
        }
      });
      return remaining;
    };

    if (cooldownUntil === null) {
      syncRemaining();
      return () => {
        cancelled = true;
      };
    }

    syncRemaining();

    intervalId = window.setInterval(() => {
      if (syncRemaining() === 0 && intervalId !== null) {
        window.clearInterval(intervalId);
        intervalId = null;
      }
    }, 250);

    return () => {
      cancelled = true;
      if (intervalId !== null) {
        window.clearInterval(intervalId);
      }
    };
  }, [cooldownUntil]);

  return snapshot;
}

function errorId(field: RegisterClientField): string {
  return `register-client-${field}-error`;
}

function describedBy(
  field: RegisterClientField,
  errors: RegisterClientFieldErrors,
  helperId?: string,
): string | undefined {
  const ids = [helperId, errors[field] === undefined ? null : errorId(field)]
    .filter((value): value is string => value !== undefined && value !== null)
    .join(' ');
  return ids === '' ? undefined : ids;
}

function FieldErrorList({
  field,
  errors,
}: {
  readonly field: RegisterClientField;
  readonly errors: RegisterClientFieldErrors;
}) {
  const messages = errors[field];
  if (messages === undefined) {
    return null;
  }

  return (
    <ul className={styles.fieldErrors} id={errorId(field)}>
      {messages.map((message) => (
        <li key={message}>{message}</li>
      ))}
    </ul>
  );
}

function focusFirstError(
  errors: RegisterClientFieldErrors,
  refs: Record<RegisterClientField, RefObject<FocusTarget | null>>,
  summaryRef: RefObject<HTMLParagraphElement | null>,
): void {
  for (const field of FIELD_ORDER) {
    if (errors[field] !== undefined) {
      refs[field].current?.focus();
      return;
    }
  }

  summaryRef.current?.focus();
}

export function RegisterClientPage() {
  const { inviteCode, clearInvite } = useClientOnboarding();
  const [{ phase, draft }, dispatchFlow] = useReducer(
    registerClientFlowReducer,
    undefined,
    createInitialRegisterClientFlowState,
  );
  const [fieldErrors, setFieldErrors] = useState<RegisterClientFieldErrors>({});
  const [summary, setSummary] = useState<string | null>(null);
  const [registeredEmail, setRegisteredEmail] = useState('');
  const [resendSummary, setResendSummary] = useState<string | null>(null);
  const [isResending, setIsResending] = useState(false);
  const [cooldownUntil, setCooldownUntil] = useState<number | null>(null);

  const mountedRef = useRef(true);
  const submittingRef = useRef(false);
  const registerAttemptIdRef = useRef(0);
  const resendSubmittingRef = useRef(false);
  const resendAttemptIdRef = useRef(0);
  const cooldownDeadlineRef = useRef<number | null>(null);
  const shouldFocusErrorsRef = useRef(false);

  const firstNameRef = useRef<HTMLInputElement>(null);
  const lastNameRef = useRef<HTMLInputElement>(null);
  const emailRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  const birthDateRef = useRef<HTMLInputElement>(null);
  const heightCmRef = useRef<HTMLInputElement>(null);
  const primaryGoalRef = useRef<HTMLTextAreaElement>(null);
  const genderRef = useRef<HTMLFieldSetElement>(null);
  const medicalNotesRef = useRef<HTMLTextAreaElement>(null);
  const injuryNotesRef = useRef<HTMLTextAreaElement>(null);
  const notesRef = useRef<HTMLTextAreaElement>(null);
  const summaryRef = useRef<HTMLParagraphElement>(null);

  const focusRefs = useMemo<
    Record<RegisterClientField, RefObject<FocusTarget | null>>
  >(
    () => ({
      firstName: firstNameRef,
      lastName: lastNameRef,
      email: emailRef,
      password: passwordRef,
      birthDate: birthDateRef,
      heightCm: heightCmRef,
      primaryGoal: primaryGoalRef,
      gender: genderRef,
      medicalNotes: medicalNotesRef,
      injuryNotes: injuryNotesRef,
      notes: notesRef,
    }),
    [],
  );

  const cooldownSnapshot = useCooldown(cooldownUntil);
  const cooldownHasSynced = cooldownSnapshot.deadline === cooldownUntil;
  const cooldownRemainingMs = cooldownHasSynced
    ? cooldownSnapshot.remainingMs
    : cooldownUntil === null
      ? 0
      : COOLDOWN_MS;
  const cooldownActive =
    cooldownUntil !== null &&
    (!cooldownHasSynced || cooldownSnapshot.remainingMs > 0);
  const isSubmitting = phase === 'submitting';

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    if (phase !== 'form' || !shouldFocusErrorsRef.current) {
      return;
    }

    shouldFocusErrorsRef.current = false;
    focusFirstError(fieldErrors, focusRefs, summaryRef);
  }, [fieldErrors, focusRefs, phase, summary]);

  function updateField<K extends RegisterClientField>(
    field: K,
    value: RegisterClientDraft[K],
  ): void {
    dispatchFlow({ type: 'updateField', field, value });
    setFieldErrors((current) => {
      if (current[field] === undefined) {
        return current;
      }

      const next = { ...current };
      delete next[field];
      return next;
    });
    setSummary(null);
  }

  function scheduleErrorFocus(): void {
    shouldFocusErrorsRef.current = true;
  }

  function enterTerminal(
    nextPhase: RegisterClientTerminalPhase,
    email = '',
  ): void {
    cooldownDeadlineRef.current = null;
    clearInvite();
    dispatchFlow({ type: 'enterTerminal', phase: nextPhase });
    setFieldErrors({});
    setSummary(null);
    setRegisteredEmail(email);
    setResendSummary(null);
    setCooldownUntil(null);
  }

  function showKnownFailure(error: HttpApiError): void {
    const code = error.body?.code;
    if (code !== undefined && INVITE_UNAVAILABLE_CODES.has(code)) {
      enterTerminal('inviteUnavailable');
      return;
    }

    if (code === 'VALIDATION_ERROR') {
      const presentation = mapRegisterClientValidationFailure(error);
      setFieldErrors(presentation.fieldErrors);
      setSummary(presentation.summary);
      dispatchFlow({ type: 'setInteractivePhase', phase: 'form' });
      scheduleErrorFocus();
      return;
    }

    setFieldErrors({});
    setSummary(
      code === 'MALFORMED_REQUEST'
        ? 'La richiesta non è valida. Controlla i dati e riprova.'
        : REGISTER_GENERIC_FAILURE,
    );
    dispatchFlow({ type: 'setInteractivePhase', phase: 'form' });
    scheduleErrorFocus();
  }

  function applyOutcome(outcome: RegisterClientOutcome, email: string): void {
    switch (outcome.kind) {
      case 'accepted':
        enterTerminal('confirmed', email);
        return;
      case 'known_failure':
        showKnownFailure(outcome.error);
        return;
      case 'ambiguous':
        enterTerminal('ambiguous', email);
        return;
    }
  }

  async function handleSubmit(
    event: FormEvent<HTMLFormElement>,
  ): Promise<void> {
    event.preventDefault();

    if (submittingRef.current || inviteCode === null) {
      return;
    }

    submittingRef.current = true;

    const nextErrors = validateRegisterClientDraft(draft);
    if (Object.keys(nextErrors).length > 0) {
      submittingRef.current = false;
      setFieldErrors(nextErrors);
      setSummary(null);
      scheduleErrorFocus();
      return;
    }

    const payload = buildRegisterClientPayload(draft, inviteCode);
    if (payload === null) {
      submittingRef.current = false;
      setSummary(REGISTER_GENERIC_FAILURE);
      scheduleErrorFocus();
      return;
    }

    const snapshot = Object.freeze(payload);
    const emailForOutcome = snapshot.email;
    const attemptId = registerAttemptIdRef.current + 1;
    registerAttemptIdRef.current = attemptId;
    setFieldErrors({});
    setSummary(null);
    dispatchFlow({ type: 'setInteractivePhase', phase: 'submitting' });

    try {
      const outcome = await registerClient(snapshot);
      if (!mountedRef.current || registerAttemptIdRef.current !== attemptId) {
        return;
      }

      applyOutcome(outcome, emailForOutcome);
    } catch (cause) {
      if (!mountedRef.current || registerAttemptIdRef.current !== attemptId) {
        return;
      }

      applyOutcome({ kind: 'ambiguous', cause }, emailForOutcome);
    } finally {
      if (registerAttemptIdRef.current === attemptId) {
        submittingRef.current = false;
      }
    }
  }

  async function handleResend(): Promise<void> {
    const cooldownDeadline = cooldownDeadlineRef.current;
    if (
      resendSubmittingRef.current ||
      (cooldownDeadline !== null && cooldownDeadline > Date.now()) ||
      registeredEmail === '' ||
      (phase !== 'confirmed' && phase !== 'ambiguous')
    ) {
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

      const nextCooldownDeadline = Date.now() + COOLDOWN_MS;
      cooldownDeadlineRef.current = nextCooldownDeadline;
      setCooldownUntil(nextCooldownDeadline);
      setResendSummary(RESEND_NEUTRAL_COPY);
    } catch {
      if (!mountedRef.current || resendAttemptIdRef.current !== attemptId) {
        return;
      }

      setResendSummary(RESEND_FAILURE_COPY);
    } finally {
      if (resendAttemptIdRef.current === attemptId) {
        resendSubmittingRef.current = false;
        if (mountedRef.current) {
          setIsResending(false);
        }
      }
    }
  }

  if ((phase === 'form' || phase === 'submitting') && inviteCode === null) {
    return <Navigate replace to="/invite/validate" />;
  }

  if (phase === 'confirmed' || phase === 'ambiguous') {
    const cooldownSeconds = Math.ceil(cooldownRemainingMs / 1000);
    const isConfirmed = phase === 'confirmed';

    return (
      <article className={styles.page} aria-labelledby="register-client-title">
        <header className={styles.introduction}>
          <p className={styles.eyebrow}>Registrazione cliente</p>
          <h1 id="register-client-title">
            {isConfirmed
              ? 'Controlla la tua email'
              : 'Esito della registrazione non confermato'}
          </h1>
        </header>

        <section className={styles.outcomePanel} aria-live="polite">
          <p>
            {isConfirmed
              ? 'Richiesta ricevuta. Controlla la tua email per completare la verifica dell’account. Se non trovi il messaggio, puoi richiederne un nuovo invio.'
              : 'Non possiamo confermare l’esito della registrazione. Se la richiesta è stata completata, riceverai un’email di verifica. Per evitare operazioni duplicate, non inviare nuovamente la registrazione con lo stesso codice.'}
          </p>

          {resendSummary === null ? null : (
            <p className={styles.infoMessage} role="status">
              {resendSummary}
            </p>
          )}

          <button
            className={styles.primaryAction}
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

        <nav className={styles.secondaryLinks} aria-label="Azioni successive">
          <Link to="/login">Vai al login</Link>
          {isConfirmed ? null : (
            <Link to="/invite/validate">Verifica un altro codice</Link>
          )}
        </nav>
      </article>
    );
  }

  if (phase === 'inviteUnavailable') {
    return (
      <article className={styles.page} aria-labelledby="register-client-title">
        <header className={styles.introduction}>
          <p className={styles.eyebrow}>Registrazione cliente</p>
          <h1 id="register-client-title">Invito non disponibile</h1>
        </header>
        <section className={styles.outcomePanel} aria-live="polite">
          <p>
            Questo invito non è più disponibile. Verifica un altro codice invito
            per continuare.
          </p>
        </section>
        <nav className={styles.secondaryLinks} aria-label="Azioni successive">
          <Link to="/invite/validate">Verifica un altro codice</Link>
        </nav>
      </article>
    );
  }

  const today = localCivilDate(new Date());

  return (
    <article className={styles.page} aria-labelledby="register-client-title">
      <header className={styles.introduction}>
        <p className={styles.eyebrow}>Registrazione cliente</p>
        <h1 id="register-client-title">Registrazione cliente</h1>
        <p>
          Completa i dati del tuo profilo. Al termine riceverai le istruzioni
          per verificare l’indirizzo email.
        </p>
      </header>

      <section
        className={styles.formPanel}
        aria-label="Dati di registrazione cliente"
      >
        <form
          className={styles.form}
          aria-busy={isSubmitting}
          noValidate
          onSubmit={(event) => void handleSubmit(event)}
        >
          {summary === null ? null : (
            <div className={styles.errorRegion} role="alert">
              <p ref={summaryRef} tabIndex={-1}>
                {summary}
              </p>
            </div>
          )}

          <div className={styles.requiredGrid}>
            <div className={styles.field}>
              <label htmlFor="register-client-first-name">Nome</label>
              <input
                ref={firstNameRef}
                aria-describedby={describedBy('firstName', fieldErrors)}
                aria-invalid={
                  fieldErrors.firstName === undefined ? undefined : true
                }
                autoComplete="given-name"
                disabled={isSubmitting}
                id="register-client-first-name"
                maxLength={100}
                name="firstName"
                onChange={(event) =>
                  updateField('firstName', event.target.value)
                }
                required
                value={draft.firstName}
              />
              <FieldErrorList field="firstName" errors={fieldErrors} />
            </div>

            <div className={styles.field}>
              <label htmlFor="register-client-last-name">Cognome</label>
              <input
                ref={lastNameRef}
                aria-describedby={describedBy('lastName', fieldErrors)}
                aria-invalid={
                  fieldErrors.lastName === undefined ? undefined : true
                }
                autoComplete="family-name"
                disabled={isSubmitting}
                id="register-client-last-name"
                maxLength={100}
                name="lastName"
                onChange={(event) =>
                  updateField('lastName', event.target.value)
                }
                required
                value={draft.lastName}
              />
              <FieldErrorList field="lastName" errors={fieldErrors} />
            </div>

            <div className={styles.field}>
              <label htmlFor="register-client-email">Email</label>
              <input
                ref={emailRef}
                aria-describedby={describedBy('email', fieldErrors)}
                aria-invalid={
                  fieldErrors.email === undefined ? undefined : true
                }
                autoCapitalize="none"
                autoComplete="email"
                disabled={isSubmitting}
                id="register-client-email"
                maxLength={100}
                name="email"
                onChange={(event) => updateField('email', event.target.value)}
                required
                spellCheck={false}
                type="email"
                value={draft.email}
              />
              <FieldErrorList field="email" errors={fieldErrors} />
            </div>

            <div className={styles.field}>
              <label htmlFor="register-client-password">Password</label>
              <p
                className={styles.helperText}
                id="register-client-password-help"
              >
                Almeno 8 caratteri, una maiuscola, un numero e un carattere
                speciale; massimo 72 byte UTF-8.
              </p>
              <input
                ref={passwordRef}
                aria-describedby={describedBy(
                  'password',
                  fieldErrors,
                  'register-client-password-help',
                )}
                aria-invalid={
                  fieldErrors.password === undefined ? undefined : true
                }
                autoComplete="new-password"
                disabled={isSubmitting}
                id="register-client-password"
                name="password"
                onChange={(event) =>
                  updateField('password', event.target.value)
                }
                required
                type="password"
                value={draft.password}
              />
              <FieldErrorList field="password" errors={fieldErrors} />
            </div>

            <div className={styles.field}>
              <label htmlFor="register-client-birth-date">
                Data di nascita
              </label>
              <input
                ref={birthDateRef}
                aria-describedby={describedBy('birthDate', fieldErrors)}
                aria-invalid={
                  fieldErrors.birthDate === undefined ? undefined : true
                }
                disabled={isSubmitting}
                id="register-client-birth-date"
                max={today}
                name="birthDate"
                onChange={(event) =>
                  updateField('birthDate', event.target.value)
                }
                required
                type="date"
                value={draft.birthDate}
              />
              <FieldErrorList field="birthDate" errors={fieldErrors} />
            </div>

            <div className={styles.field}>
              <label htmlFor="register-client-height">Altezza (cm)</label>
              <p className={styles.helperText} id="register-client-height-help">
                Da 0,01 a 999,99, con massimo due cifre decimali.
              </p>
              <input
                ref={heightCmRef}
                aria-describedby={describedBy(
                  'heightCm',
                  fieldErrors,
                  'register-client-height-help',
                )}
                aria-invalid={
                  fieldErrors.heightCm === undefined ? undefined : true
                }
                disabled={isSubmitting}
                id="register-client-height"
                inputMode="decimal"
                name="heightCm"
                onChange={(event) =>
                  updateField('heightCm', event.target.value)
                }
                placeholder="170,00"
                required
                value={draft.heightCm}
              />
              <FieldErrorList field="heightCm" errors={fieldErrors} />
            </div>
          </div>

          <div className={styles.field}>
            <label htmlFor="register-client-primary-goal">
              Obiettivo principale
            </label>
            <p
              className={styles.helperText}
              id="register-client-primary-goal-help"
            >
              Descrivi il tuo obiettivo in massimo 255 caratteri.
            </p>
            <textarea
              ref={primaryGoalRef}
              aria-describedby={describedBy(
                'primaryGoal',
                fieldErrors,
                'register-client-primary-goal-help',
              )}
              aria-invalid={
                fieldErrors.primaryGoal === undefined ? undefined : true
              }
              disabled={isSubmitting}
              id="register-client-primary-goal"
              maxLength={255}
              name="primaryGoal"
              onChange={(event) =>
                updateField('primaryGoal', event.target.value)
              }
              required
              rows={3}
              value={draft.primaryGoal}
            />
            <FieldErrorList field="primaryGoal" errors={fieldErrors} />
          </div>

          <fieldset
            ref={genderRef}
            className={styles.fieldset}
            aria-describedby={describedBy('gender', fieldErrors)}
            aria-invalid={fieldErrors.gender === undefined ? undefined : true}
            disabled={isSubmitting}
            tabIndex={-1}
          >
            <legend>Genere</legend>
            <div className={styles.radioGrid}>
              {CLIENT_GENDERS.map((gender) => (
                <label className={styles.radioOption} key={gender}>
                  <input
                    checked={draft.gender === gender}
                    name="gender"
                    onChange={() => updateField('gender', gender)}
                    required
                    type="radio"
                    value={gender}
                  />
                  {GENDER_LABELS[gender]}
                </label>
              ))}
            </div>
            <FieldErrorList field="gender" errors={fieldErrors} />
          </fieldset>

          <fieldset className={styles.optionalPanel} disabled={isSubmitting}>
            <legend>
              Informazioni personali <span>(facoltative)</span>
            </legend>
            <p className={styles.optionalCopy}>
              Se vuoi, puoi aggiungere alcune informazioni al tuo profilo
              personale. Questi campi sono facoltativi e potrai modificarli o
              completarli in qualsiasi momento dalla tua area personale.
            </p>

            <div className={styles.field}>
              <label htmlFor="register-client-medical-notes">
                Note mediche <span>(facoltative)</span>
              </label>
              <p
                className={styles.helperText}
                id="register-client-medical-help"
              >
                Massimo 5000 caratteri.
              </p>
              <textarea
                ref={medicalNotesRef}
                aria-describedby={describedBy(
                  'medicalNotes',
                  fieldErrors,
                  'register-client-medical-help',
                )}
                aria-invalid={
                  fieldErrors.medicalNotes === undefined ? undefined : true
                }
                id="register-client-medical-notes"
                maxLength={5000}
                name="medicalNotes"
                onChange={(event) =>
                  updateField('medicalNotes', event.target.value)
                }
                rows={4}
                value={draft.medicalNotes}
              />
              <FieldErrorList field="medicalNotes" errors={fieldErrors} />
            </div>

            <div className={styles.field}>
              <label htmlFor="register-client-injury-notes">
                Note sugli infortuni <span>(facoltative)</span>
              </label>
              <p className={styles.helperText} id="register-client-injury-help">
                Massimo 5000 caratteri.
              </p>
              <textarea
                ref={injuryNotesRef}
                aria-describedby={describedBy(
                  'injuryNotes',
                  fieldErrors,
                  'register-client-injury-help',
                )}
                aria-invalid={
                  fieldErrors.injuryNotes === undefined ? undefined : true
                }
                id="register-client-injury-notes"
                maxLength={5000}
                name="injuryNotes"
                onChange={(event) =>
                  updateField('injuryNotes', event.target.value)
                }
                rows={4}
                value={draft.injuryNotes}
              />
              <FieldErrorList field="injuryNotes" errors={fieldErrors} />
            </div>

            <div className={styles.field}>
              <label htmlFor="register-client-notes">
                Altre note <span>(facoltative)</span>
              </label>
              <p className={styles.helperText} id="register-client-notes-help">
                Massimo 5000 caratteri.
              </p>
              <textarea
                ref={notesRef}
                aria-describedby={describedBy(
                  'notes',
                  fieldErrors,
                  'register-client-notes-help',
                )}
                aria-invalid={
                  fieldErrors.notes === undefined ? undefined : true
                }
                id="register-client-notes"
                maxLength={5000}
                name="notes"
                onChange={(event) => updateField('notes', event.target.value)}
                rows={4}
                value={draft.notes}
              />
              <FieldErrorList field="notes" errors={fieldErrors} />
            </div>
          </fieldset>

          <button
            className={styles.primaryAction}
            disabled={isSubmitting}
            type="submit"
          >
            {isSubmitting ? 'Registrazione in corso' : 'Crea account cliente'}
          </button>
        </form>
      </section>

      <nav className={styles.secondaryLinks} aria-label="Altre opzioni">
        <Link to="/login">Hai già un account? Accedi</Link>
        <Link to="/invite/validate">Verifica un altro codice</Link>
      </nav>
    </article>
  );
}
