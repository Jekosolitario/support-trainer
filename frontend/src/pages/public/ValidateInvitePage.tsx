import {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type FormEvent,
  type RefObject,
} from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { validateInviteCode } from '../../api/authOnboardingApi';
import { useClientOnboarding } from '../../auth/clientOnboardingState';
import { canonicalizeInviteCode } from '../../auth/inviteCode';
import { getValidateInviteErrorPresentation } from '../../auth/validateInviteError';
import styles from './ValidateInvitePage.module.css';

type PageStatus =
  'idle' | 'validating' | 'invalid' | 'temporaryError' | 'valid';

interface FieldFocusRefs {
  readonly code: RefObject<HTMLInputElement | null>;
  readonly summary: RefObject<HTMLParagraphElement | null>;
}

function isAbortError(error: unknown): boolean {
  return (
    (error instanceof DOMException || error instanceof Error) &&
    error.name === 'AbortError'
  );
}

function localCodeError(rawCode: string): string | null {
  if (rawCode.length > 100) {
    return 'Il codice invito non può superare 100 caratteri.';
  }

  if (canonicalizeInviteCode(rawCode) === '') {
    return 'Inserisci il codice invito.';
  }

  return null;
}

function formatExpiresAt(iso: string): string | null {
  const parsed = new Date(iso);
  if (Number.isNaN(parsed.getTime())) {
    return null;
  }

  return new Intl.DateTimeFormat('it-IT', {
    dateStyle: 'long',
    timeStyle: 'short',
  }).format(parsed);
}

function focusValidateError(
  fieldError: string | null,
  refs: FieldFocusRefs,
): void {
  if (fieldError !== null) {
    refs.code.current?.focus();
    return;
  }

  refs.summary.current?.focus();
}

export function ValidateInvitePage() {
  const navigate = useNavigate();
  const { inviteCode, setValidatedInvite, clearInvite } = useClientOnboarding();

  const [code, setCode] = useState('');
  const [status, setStatus] = useState<PageStatus>('idle');
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [summary, setSummary] = useState<string | null>(null);
  const [expiresAtLabel, setExpiresAtLabel] = useState<string | null>(null);

  const mountedRef = useRef(true);
  const submittingRef = useRef(false);
  const continueLockRef = useRef(false);
  /**
   * Authorizes Continua independently from a captured handler closure.
   * Updated synchronously on local save/clear and revoked in the layout phase
   * when the provider invite disappears — never via a passive mirror effect.
   */
  const continueInviteRef = useRef<string | null>(null);
  const attemptIdRef = useRef(0);
  const abortControllerRef = useRef<AbortController | null>(null);
  const codeRef = useRef<HTMLInputElement>(null);
  const summaryRef = useRef<HTMLParagraphElement>(null);

  const hasUsableValidatedInvite = status === 'valid' && inviteCode !== null;
  const visibleExpiresAtLabel = hasUsableValidatedInvite
    ? expiresAtLabel
    : null;

  // Clear any previous invite on entry / re-entry. Never clear on unmount:
  // that would wipe the code while navigating validate → register.
  useEffect(() => {
    continueInviteRef.current = null;
    clearInvite();
  }, [clearInvite]);

  // Revoke the authorization token during the same commit that loses the
  // provider invite, before the browser can dispatch an event to stale UI.
  useLayoutEffect(() => {
    if (inviteCode === null) {
      continueInviteRef.current = null;
    }
  }, [inviteCode]);

  // Fail-closed UI is already derived above; this only realigns local status
  // after commit when the provider invite was cleared externally.
  useEffect(() => {
    if (status !== 'valid' || inviteCode !== null) {
      return;
    }

    let cancelled = false;
    queueMicrotask(() => {
      if (cancelled) {
        return;
      }
      setStatus('idle');
      setExpiresAtLabel(null);
      setFieldError(null);
      setSummary(null);
    });

    return () => {
      cancelled = true;
    };
  }, [status, inviteCode]);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      abortControllerRef.current?.abort();
      abortControllerRef.current = null;
      submittingRef.current = false;
    };
  }, []);

  useEffect(() => {
    if (status !== 'invalid' && status !== 'temporaryError') {
      return;
    }

    if (summary === null && fieldError === null) {
      return;
    }

    focusValidateError(fieldError, {
      code: codeRef,
      summary: summaryRef,
    });
  }, [fieldError, status, summary]);

  function invalidateInFlightAttempt(): void {
    attemptIdRef.current += 1;
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    submittingRef.current = false;
  }

  function revokeContinueAuthorization(): void {
    continueInviteRef.current = null;
  }

  function handleCodeChange(value: string): void {
    setCode(value);

    if (status === 'validating') {
      invalidateInFlightAttempt();
      setStatus('idle');
      setFieldError(null);
      setSummary(null);
      setExpiresAtLabel(null);
      return;
    }

    if (status === 'valid') {
      revokeContinueAuthorization();
      clearInvite();
      setStatus('idle');
      setExpiresAtLabel(null);
    }

    if (fieldError !== null || summary !== null) {
      setFieldError(null);
      setSummary(null);
      if (status === 'invalid' || status === 'temporaryError') {
        setStatus('idle');
      }
    }
  }

  async function handleSubmit(
    event: FormEvent<HTMLFormElement>,
  ): Promise<void> {
    event.preventDefault();

    if (submittingRef.current) {
      return;
    }

    const localError = localCodeError(code);
    if (localError !== null) {
      setStatus('invalid');
      setFieldError(localError);
      setSummary(null);
      setExpiresAtLabel(null);
      return;
    }

    const snapshot = canonicalizeInviteCode(code);

    // Ownership fence: sync clear + lock + attempt + abort previous, before await.
    revokeContinueAuthorization();
    clearInvite();
    continueLockRef.current = false;
    submittingRef.current = true;
    const attemptId = attemptIdRef.current + 1;
    attemptIdRef.current = attemptId;
    abortControllerRef.current?.abort();
    const controller = new AbortController();
    abortControllerRef.current = controller;

    setStatus('validating');
    setFieldError(null);
    setSummary(null);
    setExpiresAtLabel(null);

    try {
      const result = await validateInviteCode(
        { code: snapshot },
        { signal: controller.signal },
      );

      if (
        !mountedRef.current ||
        attemptIdRef.current !== attemptId ||
        controller.signal.aborted
      ) {
        return;
      }

      continueInviteRef.current = result.code;
      setValidatedInvite(result.code);
      setExpiresAtLabel(formatExpiresAt(result.expiresAt));
      setStatus('valid');
      setFieldError(null);
      setSummary(null);
    } catch (error) {
      if (
        !mountedRef.current ||
        attemptIdRef.current !== attemptId ||
        controller.signal.aborted ||
        isAbortError(error)
      ) {
        return;
      }

      const presentation = getValidateInviteErrorPresentation(error);
      setExpiresAtLabel(null);
      setStatus(
        presentation.kind === 'temporary' ? 'temporaryError' : 'invalid',
      );
      setFieldError(presentation.fieldError ?? null);
      setSummary(presentation.summary);
    } finally {
      if (attemptIdRef.current === attemptId) {
        submittingRef.current = false;
        if (abortControllerRef.current === controller) {
          abortControllerRef.current = null;
        }
      }
    }
  }

  function handleContinue(): void {
    if (continueLockRef.current) {
      return;
    }

    // Synced authorization token — not a passive inviteCode mirror.
    // Protects captured handlers after an external provider clear.
    if (continueInviteRef.current === null) {
      return;
    }

    continueLockRef.current = true;
    navigate('/register/client');
  }

  const isValidating = status === 'validating';
  const globalError =
    status === 'invalid' || status === 'temporaryError' ? summary : null;
  const codeFieldError = status === 'invalid' ? fieldError : null;
  const describedBy = [
    'invite-code-help',
    codeFieldError !== null ? 'invite-code-error' : null,
  ]
    .filter((value): value is string => value !== null)
    .join(' ');

  return (
    <article className={styles.page} aria-labelledby="validate-invite-title">
      <header className={styles.introduction}>
        <p className={styles.eyebrow}>Invito cliente</p>
        <h1 id="validate-invite-title">Validazione invito</h1>
        <p>
          Inserisci il codice ricevuto dal professionista per accedere alla
          registrazione cliente.
        </p>
      </header>

      {hasUsableValidatedInvite ? (
        <section
          className={styles.successPanel}
          aria-label="Codice invito verificato"
        >
          <div className={styles.successContent}>
            <h2 className={styles.successTitle}>Codice verificato</h2>
            <p role="status" aria-live="polite">
              Puoi continuare con la registrazione.
            </p>
            {visibleExpiresAtLabel !== null ? (
              <p>Codice valido fino al {visibleExpiresAtLabel}</p>
            ) : null}
            <button
              type="button"
              className={styles.continue}
              onClick={handleContinue}
            >
              Continua con la registrazione
            </button>
          </div>
        </section>
      ) : (
        <section
          className={styles.formPanel}
          aria-label="Verifica codice invito"
        >
          <form
            className={styles.form}
            noValidate
            aria-busy={isValidating}
            onSubmit={(event) => {
              void handleSubmit(event);
            }}
          >
            {globalError !== null ? (
              <div className={styles.errorRegion} role="alert">
                <p
                  ref={summaryRef}
                  tabIndex={-1}
                  className={styles.errorSummary}
                >
                  {globalError}
                </p>
              </div>
            ) : null}

            <div className={styles.field}>
              <label htmlFor="invite-code">Codice invito</label>
              <p id="invite-code-help" className={styles.helperText}>
                Usa il codice fornito dal professionista. Massimo 100 caratteri.
              </p>
              <input
                ref={codeRef}
                id="invite-code"
                name="code"
                type="text"
                autoComplete="off"
                spellCheck={false}
                maxLength={100}
                required
                value={code}
                aria-invalid={codeFieldError !== null}
                aria-describedby={describedBy}
                onChange={(event) => {
                  handleCodeChange(event.target.value);
                }}
              />
              {codeFieldError !== null ? (
                <p id="invite-code-error" className={styles.fieldError}>
                  {codeFieldError}
                </p>
              ) : null}
            </div>

            <button
              type="submit"
              className={styles.submit}
              disabled={isValidating}
            >
              {isValidating ? 'Verifica in corso…' : 'Verifica codice'}
            </button>
          </form>
        </section>
      )}

      <nav className={styles.secondaryLinks} aria-label="Altre opzioni">
        <Link to="/login">Hai già un account? Accedi</Link>
        <Link to="/register/professional">Sei un professionista?</Link>
      </nav>
    </article>
  );
}
