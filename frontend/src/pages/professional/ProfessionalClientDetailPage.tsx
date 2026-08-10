import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { getClientById } from '../../api/clientsApi';
import type { ClientDetail } from '../../api/clientsTypes';
import { HttpApiError } from '../../api/types';
import { PageTemplate } from '../../components/page/PageTemplate';
import {
  clientOperationalStatusLabel,
  genderLabel,
} from '../shared/profile/profileLabels';
import { ProfessionalClientAvatar } from './ProfessionalClientAvatar';
import {
  CLIENT_PROFILE_UNAVAILABLE_MESSAGE,
  formatClientBirthDate,
  formatClientHeight,
  isAbortError,
  parseCanonicalClientId,
} from './professionalClientsPresentation';
import styles from './ProfessionalClientsPage.module.css';

type ClientDetailLoadState =
  | { readonly clientId: number; readonly status: 'loading' }
  | {
      readonly clientId: number;
      readonly status: 'success';
      readonly client: ClientDetail;
    }
  | { readonly clientId: number; readonly status: 'unavailable' }
  | { readonly clientId: number; readonly status: 'error' };

function ClientUnavailableState() {
  return (
    <div className={styles.unavailableState}>
      <p>{CLIENT_PROFILE_UNAVAILABLE_MESSAGE}</p>
      <Link className={styles.backLink} to="/app/professional/clients">
        Torna ai clienti
      </Link>
    </div>
  );
}

export function ProfessionalClientDetailPage() {
  const { clientId: rawClientId } = useParams();
  const clientId = parseCanonicalClientId(rawClientId);
  const currentClientIdRef = useRef<number | null>(clientId);

  const [loadState, setLoadState] = useState<ClientDetailLoadState | null>(
    null,
  );
  const mountedRef = useRef(true);
  const loadLockRef = useRef(false);
  const loadGenerationRef = useRef(0);
  const abortRef = useRef<AbortController | null>(null);

  const abortInFlight = useCallback((): void => {
    abortRef.current?.abort();
    abortRef.current = null;
  }, []);

  const loadClient = useCallback(
    async (targetClientId: number): Promise<void> => {
      if (loadLockRef.current) {
        return;
      }

      loadLockRef.current = true;
      abortInFlight();
      const controller = new AbortController();
      abortRef.current = controller;
      const generation = loadGenerationRef.current + 1;
      loadGenerationRef.current = generation;
      setLoadState({ clientId: targetClientId, status: 'loading' });

      try {
        const client = await getClientById(targetClientId, {
          signal: controller.signal,
        });
        if (
          !mountedRef.current ||
          generation !== loadGenerationRef.current ||
          controller.signal.aborted ||
          currentClientIdRef.current !== targetClientId
        ) {
          return;
        }

        setLoadState({
          clientId: targetClientId,
          status: 'success',
          client,
        });
      } catch (error) {
        if (isAbortError(error)) {
          return;
        }

        if (
          !mountedRef.current ||
          generation !== loadGenerationRef.current ||
          currentClientIdRef.current !== targetClientId
        ) {
          return;
        }

        setLoadState({
          clientId: targetClientId,
          status:
            error instanceof HttpApiError && error.status === 404
              ? 'unavailable'
              : 'error',
        });
      } finally {
        if (abortRef.current === controller) {
          abortRef.current = null;
        }
        if (generation === loadGenerationRef.current) {
          loadLockRef.current = false;
        }
      }
    },
    [abortInFlight],
  );

  useEffect(() => {
    mountedRef.current = true;
    currentClientIdRef.current = clientId;
    let cancelled = false;

    if (clientId !== null) {
      queueMicrotask(() => {
        if (!cancelled) {
          void loadClient(clientId);
        }
      });
    } else {
      abortInFlight();
      loadGenerationRef.current += 1;
      loadLockRef.current = false;
    }

    return () => {
      cancelled = true;
      mountedRef.current = false;
      abortInFlight();
      loadGenerationRef.current += 1;
      loadLockRef.current = false;
    };
  }, [abortInFlight, clientId, loadClient]);

  const currentLoadState =
    clientId !== null && loadState?.clientId === clientId ? loadState : null;

  return (
    <PageTemplate
      eyebrow="Area professionista"
      title="Dettaglio cliente"
      description="Consulta le informazioni condivise dal cliente collegato."
    >
      {clientId === null ? <ClientUnavailableState /> : null}

      {clientId !== null &&
      (currentLoadState === null || currentLoadState.status === 'loading') ? (
        <p className={styles.statusRegion} role="status">
          Caricamento profilo cliente…
        </p>
      ) : null}

      {currentLoadState?.status === 'unavailable' ? (
        <ClientUnavailableState />
      ) : null}

      {currentLoadState?.status === 'error' ? (
        <div className={styles.feedbackError} role="alert">
          <p>Non è stato possibile caricare il profilo cliente. Riprova.</p>
          <button
            type="button"
            className={styles.buttonSecondary}
            onClick={() => {
              void loadClient(currentLoadState.clientId);
            }}
          >
            Riprova
          </button>
        </div>
      ) : null}

      {currentLoadState?.status === 'success' ? (
        <section
          className={styles.detailCard}
          aria-labelledby="client-profile-name"
        >
          <div className={styles.detailIdentity}>
            <ProfessionalClientAvatar
              {...currentLoadState.client}
              size="detail"
            />
            <div>
              <p className={styles.identityLabel}>Cliente</p>
              <h2 id="client-profile-name" className={styles.detailName}>
                {currentLoadState.client.firstName}{' '}
                {currentLoadState.client.lastName}
              </h2>
            </div>
          </div>

          <dl className={styles.detailList}>
            <div className={styles.detailRow}>
              <dt>Obiettivo principale</dt>
              <dd>{currentLoadState.client.primaryGoal}</dd>
            </div>
            <div className={styles.detailRow}>
              <dt>Data di nascita</dt>
              <dd>
                {formatClientBirthDate(currentLoadState.client.birthDate)}
              </dd>
            </div>
            <div className={styles.detailRow}>
              <dt>Altezza</dt>
              <dd>{formatClientHeight(currentLoadState.client.heightCm)}</dd>
            </div>
            <div className={styles.detailRow}>
              <dt>Genere dichiarato</dt>
              <dd>{genderLabel(currentLoadState.client.gender)}</dd>
            </div>
            <div className={styles.detailRow}>
              <dt>Stato operativo</dt>
              <dd>
                <span className={styles.statusBadge}>
                  {clientOperationalStatusLabel(
                    currentLoadState.client.operationalStatus,
                  )}
                </span>
              </dd>
            </div>
          </dl>

          <Link className={styles.backLink} to="/app/professional/clients">
            Torna ai clienti
          </Link>
        </section>
      ) : null}
    </PageTemplate>
  );
}
