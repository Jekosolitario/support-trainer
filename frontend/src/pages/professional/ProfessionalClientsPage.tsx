import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

import { listMyClients } from '../../api/clientsApi';
import type { ClientSummary } from '../../api/clientsTypes';
import { PageTemplate } from '../../components/page/PageTemplate';
import { ProfessionalClientAvatar } from './ProfessionalClientAvatar';
import { isAbortError } from './professionalClientsPresentation';
import styles from './ProfessionalClientsPage.module.css';

type ClientsLoadState =
  | { readonly status: 'loading' }
  | { readonly status: 'success'; readonly clients: ClientSummary[] }
  | { readonly status: 'error' };

export function ProfessionalClientsPage() {
  const [loadState, setLoadState] = useState<ClientsLoadState>({
    status: 'loading',
  });
  const mountedRef = useRef(true);
  const loadLockRef = useRef(false);
  const loadGenerationRef = useRef(0);
  const abortRef = useRef<AbortController | null>(null);

  const abortInFlight = useCallback((): void => {
    abortRef.current?.abort();
    abortRef.current = null;
  }, []);

  const loadClients = useCallback(async (): Promise<void> => {
    if (loadLockRef.current) {
      return;
    }

    loadLockRef.current = true;
    abortInFlight();
    const controller = new AbortController();
    abortRef.current = controller;
    const generation = loadGenerationRef.current + 1;
    loadGenerationRef.current = generation;
    setLoadState({ status: 'loading' });

    try {
      const clients = await listMyClients({ signal: controller.signal });
      if (
        !mountedRef.current ||
        generation !== loadGenerationRef.current ||
        controller.signal.aborted
      ) {
        return;
      }

      setLoadState({ status: 'success', clients });
    } catch (error) {
      if (isAbortError(error)) {
        return;
      }

      if (!mountedRef.current || generation !== loadGenerationRef.current) {
        return;
      }

      setLoadState({ status: 'error' });
    } finally {
      if (abortRef.current === controller) {
        abortRef.current = null;
      }
      if (generation === loadGenerationRef.current) {
        loadLockRef.current = false;
      }
    }
  }, [abortInFlight]);

  useEffect(() => {
    mountedRef.current = true;
    let cancelled = false;

    queueMicrotask(() => {
      if (!cancelled) {
        void loadClients();
      }
    });

    return () => {
      cancelled = true;
      mountedRef.current = false;
      abortInFlight();
      loadGenerationRef.current += 1;
      loadLockRef.current = false;
    };
  }, [abortInFlight, loadClients]);

  return (
    <PageTemplate
      eyebrow="Area professionista"
      title="Clienti"
      description="Consulta i clienti collegati al tuo profilo professionale."
    >
      {loadState.status === 'loading' ? (
        <p className={styles.statusRegion} role="status">
          Caricamento clienti…
        </p>
      ) : null}

      {loadState.status === 'error' ? (
        <div className={styles.feedbackError} role="alert">
          <p>Non è stato possibile caricare i clienti. Riprova.</p>
          <button
            type="button"
            className={styles.buttonSecondary}
            onClick={() => {
              void loadClients();
            }}
          >
            Riprova
          </button>
        </div>
      ) : null}

      {loadState.status === 'success' && loadState.clients.length === 0 ? (
        <div className={styles.emptyState}>
          <p>Nessun cliente collegato al momento.</p>
        </div>
      ) : null}

      {loadState.status === 'success' && loadState.clients.length > 0 ? (
        <ul className={styles.clientList}>
          {loadState.clients.map((client) => {
            const fullName = `${client.firstName} ${client.lastName}`;
            return (
              <li key={client.id} className={styles.clientCard}>
                <Link
                  className={styles.clientLink}
                  to={`/app/professional/clients/${String(client.id)}`}
                  aria-label={`Apri il profilo cliente ${fullName}`}
                >
                  <ProfessionalClientAvatar {...client} />
                  <span className={styles.clientName}>{fullName}</span>
                  <span className={styles.openHint} aria-hidden="true">
                    Apri
                  </span>
                </Link>
              </li>
            );
          })}
        </ul>
      ) : null}
    </PageTemplate>
  );
}
