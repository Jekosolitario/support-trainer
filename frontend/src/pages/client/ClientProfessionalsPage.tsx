import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

import { listMyProfessionals } from '../../api/professionalsApi';
import type { ProfessionalSummary } from '../../api/professionalsTypes';
import { ProfileAvatar } from '../../components/profile/ProfileAvatar';
import { PageTemplate } from '../../components/page/PageTemplate';
import {
  professionalOperationalStatusLabel,
  specializationLabel,
} from '../shared/profile/profileLabels';
import { isAbortError } from './clientProfessionalsPresentation';
import styles from './ClientProfessionalsPage.module.css';

type ProfessionalsLoadState =
  | { readonly status: 'loading' }
  | {
      readonly status: 'success';
      readonly professionals: ProfessionalSummary[];
    }
  | { readonly status: 'error' };

export function ClientProfessionalsPage() {
  const [loadState, setLoadState] = useState<ProfessionalsLoadState>({
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

  const loadProfessionals = useCallback(async (): Promise<void> => {
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
      const professionals = await listMyProfessionals({
        signal: controller.signal,
      });
      if (
        !mountedRef.current ||
        generation !== loadGenerationRef.current ||
        controller.signal.aborted
      ) {
        return;
      }

      setLoadState({ status: 'success', professionals });
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
        void loadProfessionals();
      }
    });

    return () => {
      cancelled = true;
      mountedRef.current = false;
      abortInFlight();
      loadGenerationRef.current += 1;
      loadLockRef.current = false;
    };
  }, [abortInFlight, loadProfessionals]);

  return (
    <PageTemplate
      eyebrow="Area cliente"
      title="Professionisti"
      description="Consulta i professionisti collegati al tuo profilo cliente."
    >
      {loadState.status === 'loading' ? (
        <p className={styles.statusRegion} role="status">
          Caricamento professionisti…
        </p>
      ) : null}

      {loadState.status === 'error' ? (
        <div className={styles.feedbackError} role="alert">
          <p>Non è stato possibile caricare i professionisti. Riprova.</p>
          <button
            type="button"
            className={styles.buttonSecondary}
            onClick={() => {
              void loadProfessionals();
            }}
          >
            Riprova
          </button>
        </div>
      ) : null}

      {loadState.status === 'success' &&
      loadState.professionals.length === 0 ? (
        <div className={styles.emptyState}>
          <p>Nessun professionista collegato al momento.</p>
        </div>
      ) : null}

      {loadState.status === 'success' && loadState.professionals.length > 0 ? (
        <ul className={styles.professionalList}>
          {loadState.professionals.map((professional) => {
            const fullName = `${professional.firstName} ${professional.lastName}`;
            return (
              <li key={professional.id} className={styles.professionalCard}>
                <Link
                  className={styles.professionalLink}
                  to={`/app/client/professionals/${String(professional.id)}`}
                  aria-label={`Apri il profilo professionista ${fullName}`}
                >
                  <ProfileAvatar
                    className={styles.avatar}
                    firstName={professional.firstName}
                    imageClassName={styles.avatarImage}
                    lastName={professional.lastName}
                    profileImageUrl={professional.profileImageUrl}
                  />
                  <span className={styles.professionalSummary}>
                    <span className={styles.professionalName}>{fullName}</span>
                    <span className={styles.professionalMetadata}>
                      {specializationLabel(professional.specialization)}
                    </span>
                    <span className={styles.professionalMetadata}>
                      {professionalOperationalStatusLabel(
                        professional.operationalStatus,
                      )}
                    </span>
                  </span>
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
