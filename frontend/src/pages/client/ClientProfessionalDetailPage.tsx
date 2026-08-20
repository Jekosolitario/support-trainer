import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { getProfessionalById } from '../../api/professionalsApi';
import type { ProfessionalDetail } from '../../api/professionalsTypes';
import { HttpApiError } from '../../api/types';
import { ProfileAvatar } from '../../components/profile/ProfileAvatar';
import { PageTemplate } from '../../components/page/PageTemplate';
import {
  professionalOperationalStatusLabel,
  specializationLabel,
} from '../shared/profile/profileLabels';
import {
  hasDisplayText,
  isAbortError,
  parseCanonicalProfessionalId,
  PROFESSIONAL_PROFILE_UNAVAILABLE_MESSAGE,
  safeExternalHttpUrl,
} from './clientProfessionalsPresentation';
import styles from './ClientProfessionalsPage.module.css';

type ProfessionalDetailLoadState =
  | { readonly professionalId: number; readonly status: 'loading' }
  | {
      readonly professionalId: number;
      readonly status: 'success';
      readonly professional: ProfessionalDetail;
    }
  | { readonly professionalId: number; readonly status: 'unavailable' }
  | { readonly professionalId: number; readonly status: 'error' };

function ProfessionalUnavailableState() {
  return (
    <div className={styles.unavailableState}>
      <p>{PROFESSIONAL_PROFILE_UNAVAILABLE_MESSAGE}</p>
      <Link className={styles.backLink} to="/app/client/professionals">
        Torna ai professionisti
      </Link>
    </div>
  );
}

function ProfessionalProfile({
  professional,
}: {
  readonly professional: ProfessionalDetail;
}) {
  const fullName = `${professional.firstName} ${professional.lastName}`;
  const instagramUrl = safeExternalHttpUrl(professional.instagramUrl);
  const websiteUrl = safeExternalHttpUrl(professional.websiteUrl);
  const hasContacts =
    hasDisplayText(professional.phoneNumber) ||
    instagramUrl !== null ||
    websiteUrl !== null;

  return (
    <section
      className={styles.detailCard}
      aria-labelledby="professional-profile-name"
    >
      <div className={styles.detailIdentity}>
        <ProfileAvatar
          className={`${styles.avatar} ${styles.avatarDetail}`}
          firstName={professional.firstName}
          imageClassName={styles.avatarImage}
          lastName={professional.lastName}
          profileImageUrl={professional.profileImageUrl}
        />
        <div>
          <p className={styles.identityLabel}>Professionista</p>
          <h2 id="professional-profile-name" className={styles.detailName}>
            {fullName}
          </h2>
        </div>
      </div>

      <dl className={styles.detailList}>
        <div className={styles.detailRow}>
          <dt>Specializzazione</dt>
          <dd>{specializationLabel(professional.specialization)}</dd>
        </div>
        <div className={styles.detailRow}>
          <dt>Stato operativo</dt>
          <dd>
            <span className={styles.statusBadge}>
              {professionalOperationalStatusLabel(
                professional.operationalStatus,
              )}
            </span>
          </dd>
        </div>
        {hasDisplayText(professional.bio) ? (
          <div className={`${styles.detailRow} ${styles.detailRowWide}`}>
            <dt>Biografia</dt>
            <dd>{professional.bio}</dd>
          </div>
        ) : null}
        {hasDisplayText(professional.workplaceName) ? (
          <div className={styles.detailRow}>
            <dt>Luogo di lavoro</dt>
            <dd>{professional.workplaceName}</dd>
          </div>
        ) : null}
        {hasDisplayText(professional.city) ? (
          <div className={styles.detailRow}>
            <dt>Città</dt>
            <dd>{professional.city}</dd>
          </div>
        ) : null}
      </dl>

      {hasContacts ? (
        <section
          className={styles.contactsSection}
          aria-labelledby="professional-contacts-title"
        >
          <h3 id="professional-contacts-title">Contatti</h3>
          <dl className={styles.contactList}>
            {hasDisplayText(professional.phoneNumber) ? (
              <div className={styles.detailRow}>
                <dt>Telefono</dt>
                <dd>{professional.phoneNumber}</dd>
              </div>
            ) : null}
            {instagramUrl !== null ? (
              <div className={styles.detailRow}>
                <dt>Instagram</dt>
                <dd>
                  <a
                    className={styles.externalLink}
                    href={instagramUrl}
                    rel="noopener noreferrer"
                    target="_blank"
                  >
                    Instagram di {fullName}
                  </a>
                </dd>
              </div>
            ) : null}
            {websiteUrl !== null ? (
              <div className={styles.detailRow}>
                <dt>Sito web</dt>
                <dd>
                  <a
                    className={styles.externalLink}
                    href={websiteUrl}
                    rel="noopener noreferrer"
                    target="_blank"
                  >
                    Sito web di {fullName}
                  </a>
                </dd>
              </div>
            ) : null}
          </dl>
        </section>
      ) : null}

      {professional.specialization === 'PERSONAL_TRAINER' ? (
        <Link
          className={styles.backLink}
          to={`/app/client/professionals/${String(professional.id)}/availability`}
        >
          Visualizza disponibilità
        </Link>
      ) : null}

      <Link className={styles.backLink} to="/app/client/professionals">
        Torna ai professionisti
      </Link>
    </section>
  );
}

export function ClientProfessionalDetailPage() {
  const { professionalId: rawProfessionalId } = useParams();
  const professionalId = parseCanonicalProfessionalId(rawProfessionalId);
  const currentProfessionalIdRef = useRef<number | null>(professionalId);
  const [loadState, setLoadState] =
    useState<ProfessionalDetailLoadState | null>(null);
  const mountedRef = useRef(true);
  const loadLockRef = useRef(false);
  const loadGenerationRef = useRef(0);
  const abortRef = useRef<AbortController | null>(null);

  const abortInFlight = useCallback((): void => {
    abortRef.current?.abort();
    abortRef.current = null;
  }, []);

  const loadProfessional = useCallback(
    async (targetProfessionalId: number): Promise<void> => {
      if (loadLockRef.current) {
        return;
      }

      loadLockRef.current = true;
      abortInFlight();
      const controller = new AbortController();
      abortRef.current = controller;
      const generation = loadGenerationRef.current + 1;
      loadGenerationRef.current = generation;
      setLoadState({
        professionalId: targetProfessionalId,
        status: 'loading',
      });

      try {
        const professional = await getProfessionalById(targetProfessionalId, {
          signal: controller.signal,
        });
        if (
          !mountedRef.current ||
          generation !== loadGenerationRef.current ||
          controller.signal.aborted ||
          currentProfessionalIdRef.current !== targetProfessionalId
        ) {
          return;
        }

        setLoadState({
          professionalId: targetProfessionalId,
          status: 'success',
          professional,
        });
      } catch (error) {
        if (isAbortError(error)) {
          return;
        }

        if (
          !mountedRef.current ||
          generation !== loadGenerationRef.current ||
          currentProfessionalIdRef.current !== targetProfessionalId
        ) {
          return;
        }

        setLoadState({
          professionalId: targetProfessionalId,
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
    currentProfessionalIdRef.current = professionalId;
    let cancelled = false;

    if (professionalId !== null) {
      queueMicrotask(() => {
        if (!cancelled) {
          void loadProfessional(professionalId);
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
  }, [abortInFlight, loadProfessional, professionalId]);

  const currentLoadState =
    professionalId !== null && loadState?.professionalId === professionalId
      ? loadState
      : null;

  return (
    <PageTemplate
      eyebrow="Area cliente"
      title="Dettaglio professionista"
      description="Consulta le informazioni condivise dal professionista collegato."
    >
      {professionalId === null ? <ProfessionalUnavailableState /> : null}

      {professionalId !== null &&
      (currentLoadState === null || currentLoadState.status === 'loading') ? (
        <p className={styles.statusRegion} role="status">
          Caricamento profilo professionista…
        </p>
      ) : null}

      {currentLoadState?.status === 'unavailable' ? (
        <ProfessionalUnavailableState />
      ) : null}

      {currentLoadState?.status === 'error' ? (
        <div className={styles.feedbackError} role="alert">
          <p>
            Non è stato possibile caricare il profilo professionista. Riprova.
          </p>
          <button
            type="button"
            className={styles.buttonSecondary}
            onClick={() => {
              void loadProfessional(currentLoadState.professionalId);
            }}
          >
            Riprova
          </button>
        </div>
      ) : null}

      {currentLoadState?.status === 'success' ? (
        <ProfessionalProfile professional={currentLoadState.professional} />
      ) : null}
    </PageTemplate>
  );
}
