import { useRef, useState, type FormEvent } from 'react';

import {
  updateMyOperationalStatus,
  updateMyProfile,
} from '../../api/meProfileApi';
import type {
  ClientOperationalStatus,
  ProfessionalOperationalStatus,
} from '../../api/authTypes';
import { currentEpoch } from '../../api/authEpoch';
import { StaleAuthOperationError } from '../../api/csrfMutation';
import { PageTemplate } from '../../components/page/PageTemplate';
import { AuthOperationNotAllowedError, useAuth } from '../../auth/authState';
import { AccountSection } from './profile/AccountSection';
import { OperationalStatusSection } from './profile/OperationalStatusSection';
import { ProfileDetailsSection } from './profile/ProfileDetailsSection';
import { getProfileErrorPresentation } from './profile/profileError';
import {
  buildClientProfilePatch,
  buildProfessionalProfilePatch,
  isClientProfileDirty,
  isProfessionalProfileDirty,
  mapClientProfileToDraft,
  mapProfessionalProfileToDraft,
  validateClientProfileDraft,
  validateProfessionalProfileDraft,
  type ClientProfileDraft,
  type ProfessionalProfileDraft,
  type ProfileFieldErrors,
} from './profile/profileFormModel';
import styles from './profile/ProfilePage.module.css';

interface ProfilePageProps {
  area: 'cliente' | 'professionista';
}

type ProfileMode = 'view' | 'editing';

export function ProfilePage({ area }: ProfilePageProps) {
  const { state, applyProfileSnapshot } = useAuth();

  const [mode, setMode] = useState<ProfileMode>('view');
  const [clientDraft, setClientDraft] = useState<ClientProfileDraft | null>(
    null,
  );
  const [professionalDraft, setProfessionalDraft] =
    useState<ProfessionalProfileDraft | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ProfileFieldErrors>({});
  const [profileGlobalError, setProfileGlobalError] = useState<string | null>(
    null,
  );
  const [statusError, setStatusError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [profileSaving, setProfileSaving] = useState(false);
  const [statusSaving, setStatusSaving] = useState(false);
  const [statusSelection, setStatusSelection] = useState<
    ClientOperationalStatus | ProfessionalOperationalStatus | null
  >(null);
  const profileSavingRef = useRef(false);
  const statusSavingRef = useRef(false);

  if (state.status !== 'authenticated') {
    return null;
  }

  const { account, profile } = state;
  const busy = profileSaving || statusSaving;
  const currentStatusSelection = statusSelection ?? profile.operationalStatus;

  const isDirty =
    mode === 'editing' && profile.role === 'CLIENT' && clientDraft !== null
      ? isClientProfileDirty(profile, clientDraft)
      : mode === 'editing' &&
          profile.role === 'PROFESSIONAL' &&
          professionalDraft !== null
        ? isProfessionalProfileDirty(profile, professionalDraft)
        : false;

  function clearProfileEditingState(): void {
    setMode('view');
    setClientDraft(null);
    setProfessionalDraft(null);
    setFieldErrors({});
    setProfileGlobalError(null);
  }

  function handleStartEdit(): void {
    if (busy) {
      return;
    }

    setSuccessMessage(null);
    setProfileGlobalError(null);
    setFieldErrors({});

    if (profile.role === 'CLIENT') {
      setClientDraft(mapClientProfileToDraft(profile));
      setProfessionalDraft(null);
    } else {
      setProfessionalDraft(mapProfessionalProfileToDraft(profile));
      setClientDraft(null);
    }

    setMode('editing');
  }

  function handleCancel(): void {
    if (profileSaving) {
      return;
    }

    clearProfileEditingState();
  }

  async function handleProfileSubmit(
    event: FormEvent<HTMLFormElement>,
  ): Promise<void> {
    event.preventDefault();

    if (
      profileSavingRef.current ||
      statusSavingRef.current ||
      mode !== 'editing'
    ) {
      return;
    }

    setSuccessMessage(null);
    setProfileGlobalError(null);

    if (profile.role === 'CLIENT') {
      if (clientDraft === null) {
        return;
      }

      const validationErrors = validateClientProfileDraft(clientDraft);
      if (Object.keys(validationErrors).length > 0) {
        setFieldErrors(validationErrors);
        return;
      }

      if (!isClientProfileDirty(profile, clientDraft)) {
        return;
      }

      const patch = buildClientProfilePatch(profile, clientDraft);
      if (patch === null) {
        setProfileGlobalError('Operazione non completata. Riprova.');
        return;
      }

      profileSavingRef.current = true;
      setProfileSaving(true);
      setFieldErrors({});
      setStatusError(null);

      try {
        const expectedEpoch = currentEpoch();
        const response = await updateMyProfile(patch);
        applyProfileSnapshot(response, expectedEpoch);
        clearProfileEditingState();
        setStatusError(null);
        setSuccessMessage('Profilo aggiornato');
      } catch (error) {
        if (
          error instanceof StaleAuthOperationError ||
          error instanceof AuthOperationNotAllowedError
        ) {
          const presentation = getProfileErrorPresentation(error, 'profile');
          setProfileGlobalError(presentation.summary);
          return;
        }

        const presentation = getProfileErrorPresentation(error, 'profile');
        setFieldErrors(presentation.fieldErrors);
        setProfileGlobalError(presentation.summary);
      } finally {
        profileSavingRef.current = false;
        setProfileSaving(false);
      }

      return;
    }

    if (professionalDraft === null) {
      return;
    }

    const validationErrors =
      validateProfessionalProfileDraft(professionalDraft);
    if (Object.keys(validationErrors).length > 0) {
      setFieldErrors(validationErrors);
      return;
    }

    if (!isProfessionalProfileDirty(profile, professionalDraft)) {
      return;
    }

    const patch = buildProfessionalProfilePatch(profile, professionalDraft);
    if (patch === null) {
      setProfileGlobalError('Operazione non completata. Riprova.');
      return;
    }

    profileSavingRef.current = true;
    setProfileSaving(true);
    setFieldErrors({});
    setStatusError(null);

    try {
      const expectedEpoch = currentEpoch();
      const response = await updateMyProfile(patch);
      applyProfileSnapshot(response, expectedEpoch);
      clearProfileEditingState();
      setStatusError(null);
      setSuccessMessage('Profilo aggiornato');
    } catch (error) {
      if (
        error instanceof StaleAuthOperationError ||
        error instanceof AuthOperationNotAllowedError
      ) {
        const presentation = getProfileErrorPresentation(error, 'profile');
        setProfileGlobalError(presentation.summary);
        return;
      }

      const presentation = getProfileErrorPresentation(error, 'profile');
      setFieldErrors(presentation.fieldErrors);
      setProfileGlobalError(presentation.summary);
    } finally {
      profileSavingRef.current = false;
      setProfileSaving(false);
    }
  }

  async function handleStatusSubmit(): Promise<void> {
    if (profileSavingRef.current || statusSavingRef.current) {
      return;
    }

    if (currentStatusSelection === profile.operationalStatus) {
      return;
    }

    setSuccessMessage(null);
    setStatusError(null);
    setProfileGlobalError(null);
    statusSavingRef.current = true;
    setStatusSaving(true);

    try {
      const expectedEpoch = currentEpoch();
      const response = await updateMyOperationalStatus({
        operationalStatus: currentStatusSelection,
      });
      applyProfileSnapshot(response, expectedEpoch);
      setStatusSelection(null);
      setProfileGlobalError(null);
      setSuccessMessage('Stato aggiornato');
    } catch (error) {
      if (
        error instanceof StaleAuthOperationError ||
        error instanceof AuthOperationNotAllowedError
      ) {
        const presentation = getProfileErrorPresentation(
          error,
          'operational-status',
        );
        setStatusError(presentation.summary);
        return;
      }

      const presentation = getProfileErrorPresentation(
        error,
        'operational-status',
      );
      setStatusError(presentation.summary);
    } finally {
      statusSavingRef.current = false;
      setStatusSaving(false);
    }
  }

  return (
    <div className={styles.page}>
      <PageTemplate
        eyebrow={`Area ${area}`}
        title="Profilo"
        description="Gestisci i dati del profilo, consulta l’account e aggiorna lo stato operativo."
      >
        {successMessage ? (
          <p className={styles.feedback} role="status" aria-live="polite">
            {successMessage}
          </p>
        ) : null}

        {profile.role === 'CLIENT' ? (
          <ProfileDetailsSection
            mode={mode}
            profile={profile}
            draft={clientDraft}
            fieldErrors={fieldErrors}
            globalError={profileGlobalError}
            saving={profileSaving}
            actionsDisabled={busy}
            saveDisabled={!isDirty || busy}
            onStartEdit={handleStartEdit}
            onCancel={handleCancel}
            onSubmit={(event) => {
              void handleProfileSubmit(event);
            }}
            onClientDraftChange={(patch) => {
              setClientDraft((previous) =>
                previous === null ? previous : { ...previous, ...patch },
              );
            }}
          />
        ) : (
          <ProfileDetailsSection
            mode={mode}
            profile={profile}
            draft={professionalDraft}
            fieldErrors={fieldErrors}
            globalError={profileGlobalError}
            saving={profileSaving}
            actionsDisabled={busy}
            saveDisabled={!isDirty || busy}
            onStartEdit={handleStartEdit}
            onCancel={handleCancel}
            onSubmit={(event) => {
              void handleProfileSubmit(event);
            }}
            onProfessionalDraftChange={(patch) => {
              setProfessionalDraft((previous) =>
                previous === null ? previous : { ...previous, ...patch },
              );
            }}
          />
        )}

        <AccountSection account={account} />

        <OperationalStatusSection
          role={profile.role}
          currentStatus={profile.operationalStatus}
          selection={currentStatusSelection}
          error={statusError}
          saving={statusSaving}
          disabled={busy}
          onSelectionChange={(value) => {
            setStatusError(null);
            setStatusSelection(value);
          }}
          onSubmit={() => {
            void handleStatusSubmit();
          }}
        />
      </PageTemplate>
    </div>
  );
}
