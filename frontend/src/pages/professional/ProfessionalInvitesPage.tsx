import { useEffect, useRef, useState } from 'react';

import { createInvite, listMyInvites } from '../../api/invitesApi';
import type { InviteCodeResponse } from '../../api/invitesTypes';
import { PageTemplate } from '../../components/page/PageTemplate';
import { formatAccountDate } from '../shared/profile/profileLabels';
import {
  CREATE_OUTCOME_UNCONFIRMED_MESSAGE,
  getInviteCreateKnownErrorMessage,
  getInviteListErrorMessage,
  isAbortError,
  isAmbiguousCreateOutcome,
  isStaleAuthCreateOutcome,
} from './inviteErrors';
import {
  deriveInviteDisplayStatus,
  getNextValidInviteExpiryMs,
  type InviteDisplayStatus,
} from './inviteStatus';
import styles from './ProfessionalInvitesPage.module.css';

type ListStatus = 'loading' | 'success' | 'error';

const MAX_TIMEOUT_MS = 2_147_483_647;

function prependInvite(
  invites: readonly InviteCodeResponse[],
  created: InviteCodeResponse,
): InviteCodeResponse[] {
  return [created, ...invites.filter((invite) => invite.id !== created.id)];
}

async function copyInviteCode(code: string): Promise<void> {
  if (
    typeof navigator === 'undefined' ||
    !navigator.clipboard ||
    typeof navigator.clipboard.writeText !== 'function'
  ) {
    throw new Error('Clipboard API unavailable');
  }

  await navigator.clipboard.writeText(code);
}

export function ProfessionalInvitesPage() {
  const [invites, setInvites] = useState<InviteCodeResponse[]>([]);
  const [listStatus, setListStatus] = useState<ListStatus>('loading');
  const [listError, setListError] = useState<string | null>(null);
  const [createPending, setCreatePending] = useState(false);
  const [createKnownError, setCreateKnownError] = useState<string | null>(null);
  const [outcomeUnconfirmed, setOutcomeUnconfirmed] = useState(false);
  const [createGateClosed, setCreateGateClosed] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [copyFeedback, setCopyFeedback] = useState<string | null>(null);
  const [copyError, setCopyError] = useState<string | null>(null);
  const [nowMs, setNowMs] = useState(() => Date.now());
  const [listBusy, setListBusy] = useState(false);

  const createLockRef = useRef(false);
  const listLockRef = useRef(false);
  const loadGenerationRef = useRef(0);
  const reconcileAttemptRef = useRef(0);
  const listAbortRef = useRef<AbortController | null>(null);
  const mountedRef = useRef(true);

  const hasKnownList = listStatus === 'success';
  const generateDisabled =
    !hasKnownList ||
    listBusy ||
    createPending ||
    outcomeUnconfirmed ||
    createGateClosed;
  const listRetryDisabled =
    createPending || outcomeUnconfirmed || createGateClosed || listBusy;

  function abortInFlightList(): void {
    listAbortRef.current?.abort();
    listAbortRef.current = null;
  }

  function releaseCreateGate(): void {
    createLockRef.current = false;
    setCreateGateClosed(false);
    setCreatePending(false);
  }

  function acquireCreateGate(): boolean {
    if (createLockRef.current || listLockRef.current) {
      return false;
    }
    createLockRef.current = true;
    setCreateGateClosed(true);
    return true;
  }

  function commitInviteDataset(
    next:
      | InviteCodeResponse[]
      | ((current: InviteCodeResponse[]) => InviteCodeResponse[]),
  ): void {
    const freshNow = Date.now();
    setNowMs(freshNow);
    setInvites(next);
  }

  async function loadInvites(options: {
    mode: 'initial' | 'retry' | 'reconcile';
    reconcileAttempt?: number;
  }): Promise<boolean> {
    if (options.mode !== 'reconcile' && createLockRef.current) {
      return false;
    }

    if (
      options.mode === 'retry' &&
      (createLockRef.current || outcomeUnconfirmed || createGateClosed)
    ) {
      return false;
    }

    if (listLockRef.current && options.mode !== 'reconcile') {
      // Another list op already owns the lock (same-tick race).
      // Reconcile may replace an in-flight list after abort below.
    }

    if (options.mode !== 'reconcile' && listLockRef.current) {
      return false;
    }

    abortInFlightList();
    const controller = new AbortController();
    listAbortRef.current = controller;
    const generation = loadGenerationRef.current + 1;
    loadGenerationRef.current = generation;

    listLockRef.current = true;
    setListBusy(true);
    setListStatus('loading');
    setListError(null);
    if (options.mode !== 'reconcile') {
      setSuccessMessage(null);
      setCopyFeedback(null);
      setCopyError(null);
    }

    try {
      const response = await listMyInvites({ signal: controller.signal });

      if (
        !mountedRef.current ||
        generation !== loadGenerationRef.current ||
        controller.signal.aborted
      ) {
        return false;
      }

      if (
        options.mode === 'reconcile' &&
        options.reconcileAttempt !== undefined &&
        options.reconcileAttempt !== reconcileAttemptRef.current
      ) {
        return false;
      }

      commitInviteDataset(response);
      setListStatus('success');
      setListError(null);
      return true;
    } catch (error) {
      if (isAbortError(error)) {
        return false;
      }

      if (!mountedRef.current || generation !== loadGenerationRef.current) {
        return false;
      }

      if (
        options.mode === 'reconcile' &&
        options.reconcileAttempt !== undefined &&
        options.reconcileAttempt !== reconcileAttemptRef.current
      ) {
        return false;
      }

      setListStatus('error');
      setListError(getInviteListErrorMessage(error));
      return false;
    } finally {
      if (listAbortRef.current === controller) {
        listAbortRef.current = null;
      }
      if (generation === loadGenerationRef.current) {
        listLockRef.current = false;
        setListBusy(false);
      }
    }
  }

  async function reconcileAfterAmbiguousCreate(): Promise<void> {
    const attempt = reconcileAttemptRef.current + 1;
    reconcileAttemptRef.current = attempt;

    const ok = await loadInvites({
      mode: 'reconcile',
      reconcileAttempt: attempt,
    });

    if (!mountedRef.current || attempt !== reconcileAttemptRef.current) {
      return;
    }

    if (ok) {
      setOutcomeUnconfirmed(false);
      setCreateKnownError(null);
      releaseCreateGate();
      return;
    }

    setOutcomeUnconfirmed(true);
    createLockRef.current = true;
    setCreateGateClosed(true);
    setCreatePending(false);
  }

  async function handleGenerate(): Promise<void> {
    if (
      createLockRef.current ||
      listLockRef.current ||
      listStatus !== 'success' ||
      outcomeUnconfirmed ||
      createGateClosed
    ) {
      return;
    }

    if (!acquireCreateGate()) {
      return;
    }

    setCreatePending(true);
    setCreateKnownError(null);
    setSuccessMessage(null);
    setCopyFeedback(null);
    setCopyError(null);
    setOutcomeUnconfirmed(false);
    abortInFlightList();
    loadGenerationRef.current += 1;
    listLockRef.current = false;
    setListBusy(false);

    try {
      const created = await createInvite();

      if (!mountedRef.current) {
        return;
      }

      loadGenerationRef.current += 1;
      commitInviteDataset((current) => prependInvite(current, created));
      setListStatus('success');
      setListError(null);
      setSuccessMessage('Invito generato');
      releaseCreateGate();
    } catch (error) {
      if (!mountedRef.current) {
        return;
      }

      if (isAbortError(error)) {
        releaseCreateGate();
        return;
      }

      if (isStaleAuthCreateOutcome(error)) {
        // Auth lifecycle owns the transition — no false success/failure, no retry.
        setCreatePending(false);
        setCreateKnownError(null);
        setSuccessMessage(null);
        createLockRef.current = true;
        setCreateGateClosed(true);
        return;
      }

      if (isAmbiguousCreateOutcome(error)) {
        setCreatePending(false);
        setCreateKnownError(null);
        setSuccessMessage(null);
        setOutcomeUnconfirmed(true);
        createLockRef.current = true;
        setCreateGateClosed(true);
        void reconcileAfterAmbiguousCreate();
        return;
      }

      const knownMessage = getInviteCreateKnownErrorMessage(error);
      setCreateKnownError(
        knownMessage ?? 'Non è stato possibile generare l’invito. Riprova.',
      );
      releaseCreateGate();
    }
  }

  async function handleCopy(code: string): Promise<void> {
    setCopyFeedback(null);
    setCopyError(null);

    try {
      await copyInviteCode(code);
      if (!mountedRef.current) {
        return;
      }
      setCopyFeedback('Codice copiato');
    } catch {
      if (!mountedRef.current) {
        return;
      }
      setCopyError(
        'Copia non riuscita. Seleziona e copia il codice manualmente.',
      );
    }
  }

  useEffect(() => {
    mountedRef.current = true;
    let cancelled = false;

    queueMicrotask(() => {
      if (!cancelled) {
        void loadInvites({ mode: 'initial' });
      }
    });

    return () => {
      cancelled = true;
      mountedRef.current = false;
      abortInFlightList();
      loadGenerationRef.current += 1;
      reconcileAttemptRef.current += 1;
      listLockRef.current = false;
    };
    // Initial load only.
    // eslint-disable-next-line react-hooks/exhaustive-deps -- mount lifecycle
  }, []);

  useEffect(() => {
    function onVisibilityChange(): void {
      if (document.visibilityState === 'visible') {
        setNowMs(Date.now());
      }
    }

    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => {
      document.removeEventListener('visibilitychange', onVisibilityChange);
    };
  }, []);

  useEffect(() => {
    const nextExpiryMs = getNextValidInviteExpiryMs(invites, nowMs);
    if (nextExpiryMs === null) {
      return;
    }

    const delay = Math.min(Math.max(0, nextExpiryMs - nowMs), MAX_TIMEOUT_MS);
    const timerId = window.setTimeout(() => {
      setNowMs(Date.now());
    }, delay);

    return () => {
      window.clearTimeout(timerId);
    };
  }, [invites, nowMs]);

  return (
    <PageTemplate
      eyebrow="Area professionista"
      title="Inviti"
      description="Genera e consulta i codici invito da condividere con i clienti fuori dall’app. La validità definitiva del codice è sempre determinata dal server."
    >
      <div className={styles.toolbar}>
        <button
          type="button"
          className={styles.button}
          disabled={generateDisabled}
          onClick={() => {
            void handleGenerate();
          }}
        >
          {createPending ? 'Generazione…' : 'Genera invito'}
        </button>
      </div>

      {successMessage ? (
        <p
          className={`${styles.feedback} ${styles.feedbackSuccess}`}
          role="status"
        >
          {successMessage}
        </p>
      ) : null}

      {copyFeedback ? (
        <p
          className={`${styles.feedback} ${styles.feedbackSuccess}`}
          role="status"
        >
          {copyFeedback}
        </p>
      ) : null}

      {copyError ? (
        <p
          className={`${styles.feedback} ${styles.feedbackError}`}
          role="alert"
        >
          {copyError}
        </p>
      ) : null}

      {createKnownError ? (
        <p
          className={`${styles.feedback} ${styles.feedbackError}`}
          role="alert"
        >
          {createKnownError}
        </p>
      ) : null}

      {outcomeUnconfirmed ? (
        <div
          className={`${styles.feedback} ${styles.feedbackWarning}`}
          role="alert"
        >
          <p>{CREATE_OUTCOME_UNCONFIRMED_MESSAGE}</p>
          {listStatus === 'error' ? (
            <div className={styles.toolbar}>
              <button
                type="button"
                className={styles.buttonSecondary}
                disabled={listBusy}
                onClick={() => {
                  void reconcileAfterAmbiguousCreate();
                }}
              >
                Aggiorna elenco
              </button>
            </div>
          ) : null}
          {listBusy || listStatus === 'loading' ? (
            <p className={styles.statusRegion}>Aggiornamento elenco…</p>
          ) : null}
        </div>
      ) : null}

      {(listBusy || listStatus === 'loading') && !outcomeUnconfirmed ? (
        <p className={styles.statusRegion} role="status">
          Caricamento inviti…
        </p>
      ) : null}

      {!outcomeUnconfirmed && listStatus === 'error' ? (
        <div
          className={`${styles.feedback} ${styles.feedbackError}`}
          role="alert"
        >
          <p>{listError ?? getInviteListErrorMessage(null)}</p>
          <div className={styles.toolbar}>
            <button
              type="button"
              className={styles.buttonSecondary}
              disabled={listRetryDisabled}
              onClick={() => {
                void loadInvites({ mode: 'retry' });
              }}
            >
              Riprova
            </button>
          </div>
        </div>
      ) : null}

      {listStatus === 'success' &&
      invites.length === 0 &&
      !outcomeUnconfirmed ? (
        <div className={styles.card}>
          <p className={styles.statusRegion}>
            Non hai ancora inviti. Genera un codice da condividere con un
            cliente.
          </p>
        </div>
      ) : null}

      {listStatus === 'success' && invites.length > 0 ? (
        <ul className={styles.list}>
          {invites.map((invite) => {
            const status = deriveInviteDisplayStatus(invite, nowMs);
            return (
              <InviteCard
                key={invite.id}
                invite={invite}
                status={status}
                onCopy={() => {
                  void handleCopy(invite.code);
                }}
              />
            );
          })}
        </ul>
      ) : null}
    </PageTemplate>
  );
}

interface InviteCardProps {
  readonly invite: InviteCodeResponse;
  readonly status: InviteDisplayStatus;
  readonly onCopy: () => void;
}

function InviteCard({ invite, status, onCopy }: InviteCardProps) {
  const canCopy = status === 'Valido';
  const expiresLabel = Number.isNaN(Date.parse(invite.expiresAt))
    ? 'Non disponibile'
    : formatAccountDate(invite.expiresAt);

  return (
    <li className={styles.card}>
      <div className={styles.cardHeader}>
        <p className={styles.code}>{invite.code}</p>
        <p className={styles.status} aria-label={`Stato ${status}`}>
          {status}
        </p>
      </div>

      <dl className={styles.meta}>
        <div className={styles.metaRow}>
          <dt>Creato</dt>
          <dd>{formatAccountDate(invite.createdAt)}</dd>
        </div>
        <div className={styles.metaRow}>
          <dt>Scade</dt>
          <dd>{expiresLabel}</dd>
        </div>
        {invite.used && invite.usedAt ? (
          <div className={styles.metaRow}>
            <dt>Usato il</dt>
            <dd>{formatAccountDate(invite.usedAt)}</dd>
          </div>
        ) : null}
      </dl>

      {canCopy ? (
        <div className={styles.cardActions}>
          <button
            type="button"
            className={styles.buttonSecondary}
            onClick={onCopy}
            aria-label={`Copia codice ${invite.code}`}
          >
            Copia codice
          </button>
        </div>
      ) : null}
    </li>
  );
}
