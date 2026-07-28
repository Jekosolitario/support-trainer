import type { FormEvent, ReactNode } from 'react';

import type {
  Gender,
  MyClientProfileResponse,
  MyProfessionalProfileResponse,
  MyProfileResponse,
} from '../../../api/authTypes';
import type {
  ClientProfileDraft,
  ProfessionalProfileDraft,
  ProfileFieldErrors,
} from './profileFormModel';
import styles from './ProfilePage.module.css';
import {
  GENDER_OPTIONS,
  genderLabel,
  profileImageFallback,
  roleLabel,
  specializationLabel,
} from './profileLabels';

type ProfileMode = 'view' | 'editing';

interface ProfileDetailsSectionBaseProps {
  readonly mode: ProfileMode;
  readonly profile: MyProfileResponse;
  readonly fieldErrors: ProfileFieldErrors;
  readonly globalError: string | null;
  readonly saving: boolean;
  readonly actionsDisabled: boolean;
  readonly saveDisabled: boolean;
  readonly onStartEdit: () => void;
  readonly onCancel: () => void;
  readonly onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}

interface ClientProfileDetailsProps extends ProfileDetailsSectionBaseProps {
  readonly profile: MyClientProfileResponse;
  readonly draft: ClientProfileDraft | null;
  readonly onClientDraftChange: (patch: Partial<ClientProfileDraft>) => void;
}

interface ProfessionalProfileDetailsProps extends ProfileDetailsSectionBaseProps {
  readonly profile: MyProfessionalProfileResponse;
  readonly draft: ProfessionalProfileDraft | null;
  readonly onProfessionalDraftChange: (
    patch: Partial<ProfessionalProfileDraft>,
  ) => void;
}

export type ProfileDetailsSectionProps =
  ClientProfileDetailsProps | ProfessionalProfileDetailsProps;

function FieldError({
  id,
  message,
}: {
  readonly id: string;
  readonly message: string | undefined;
}): ReactNode {
  if (message === undefined) {
    return null;
  }

  return (
    <p id={id} className={styles.fieldError} role="alert">
      {message}
    </p>
  );
}

function ProfileImage({
  profile,
}: {
  readonly profile: MyProfileResponse;
}): ReactNode {
  if (profile.profileImageUrl) {
    return (
      <img
        className={styles.avatarImage}
        src={profile.profileImageUrl}
        alt={`Foto profilo di ${profile.firstName} ${profile.lastName}`}
      />
    );
  }

  return (
    <span
      className={styles.avatar}
      role="img"
      aria-label="Nessuna foto profilo"
    >
      <span aria-hidden="true">
        {profileImageFallback(profile.firstName, profile.lastName)}
      </span>
    </span>
  );
}

function ClientView({
  profile,
}: {
  readonly profile: MyClientProfileResponse;
}): ReactNode {
  return (
    <dl className={styles.dl}>
      <div className={styles.row}>
        <dt>Immagine profilo</dt>
        <dd>
          <ProfileImage profile={profile} />
        </dd>
      </div>
      <div className={styles.row}>
        <dt>Ruolo</dt>
        <dd>{roleLabel(profile.role)}</dd>
      </div>
      <div className={styles.row}>
        <dt>Nome</dt>
        <dd>{profile.firstName}</dd>
      </div>
      <div className={styles.row}>
        <dt>Cognome</dt>
        <dd>{profile.lastName}</dd>
      </div>
      <div className={styles.row}>
        <dt>Data di nascita</dt>
        <dd>{profile.birthDate}</dd>
      </div>
      <div className={styles.row}>
        <dt>Altezza (cm)</dt>
        <dd>{profile.heightCm}</dd>
      </div>
      <div className={styles.row}>
        <dt>Obiettivo principale</dt>
        <dd>{profile.primaryGoal}</dd>
      </div>
      <div className={styles.row}>
        <dt>Genere</dt>
        <dd>{genderLabel(profile.gender)}</dd>
      </div>
      <div className={styles.row}>
        <dt>Note mediche</dt>
        <dd>{profile.medicalNotes ?? '—'}</dd>
      </div>
      <div className={styles.row}>
        <dt>Note infortuni</dt>
        <dd>{profile.injuryNotes ?? '—'}</dd>
      </div>
      <div className={styles.row}>
        <dt>Note</dt>
        <dd>{profile.notes ?? '—'}</dd>
      </div>
    </dl>
  );
}

function ProfessionalView({
  profile,
}: {
  readonly profile: MyProfessionalProfileResponse;
}): ReactNode {
  return (
    <dl className={styles.dl}>
      <div className={styles.row}>
        <dt>Immagine profilo</dt>
        <dd>
          <ProfileImage profile={profile} />
        </dd>
      </div>
      <div className={styles.row}>
        <dt>Ruolo</dt>
        <dd>{roleLabel(profile.role)}</dd>
      </div>
      <div className={styles.row}>
        <dt>Specializzazione</dt>
        <dd>{specializationLabel(profile.specialization)}</dd>
      </div>
      <div className={styles.row}>
        <dt>Nome</dt>
        <dd>{profile.firstName}</dd>
      </div>
      <div className={styles.row}>
        <dt>Cognome</dt>
        <dd>{profile.lastName}</dd>
      </div>
      <div className={styles.row}>
        <dt>Telefono</dt>
        <dd>{profile.phoneNumber ?? '—'}</dd>
      </div>
      <div className={styles.row}>
        <dt>Bio</dt>
        <dd>{profile.bio ?? '—'}</dd>
      </div>
      <div className={styles.row}>
        <dt>Luogo di lavoro</dt>
        <dd>{profile.workplaceName ?? '—'}</dd>
      </div>
      <div className={styles.row}>
        <dt>Città</dt>
        <dd>{profile.city ?? '—'}</dd>
      </div>
      <div className={styles.row}>
        <dt>Instagram</dt>
        <dd>{profile.instagramUrl ?? '—'}</dd>
      </div>
      <div className={styles.row}>
        <dt>Sito web</dt>
        <dd>{profile.websiteUrl ?? '—'}</dd>
      </div>
    </dl>
  );
}

function ClientEditForm({
  draft,
  fieldErrors,
  saving,
  saveDisabled,
  actionsDisabled,
  onClientDraftChange,
  onCancel,
  onSubmit,
}: {
  readonly draft: ClientProfileDraft;
  readonly fieldErrors: ProfileFieldErrors;
  readonly saving: boolean;
  readonly saveDisabled: boolean;
  readonly actionsDisabled: boolean;
  readonly onClientDraftChange: (patch: Partial<ClientProfileDraft>) => void;
  readonly onCancel: () => void;
  readonly onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}): ReactNode {
  return (
    <form className={styles.form} noValidate onSubmit={onSubmit}>
      <div className={styles.field}>
        <label htmlFor="profile-firstName">Nome</label>
        <input
          id="profile-firstName"
          name="firstName"
          type="text"
          autoComplete="given-name"
          value={draft.firstName}
          disabled={saving || actionsDisabled}
          aria-invalid={fieldErrors.firstName !== undefined}
          aria-describedby={
            fieldErrors.firstName !== undefined
              ? 'profile-firstName-error'
              : undefined
          }
          onChange={(event) => {
            onClientDraftChange({ firstName: event.target.value });
          }}
        />
        <FieldError
          id="profile-firstName-error"
          message={fieldErrors.firstName}
        />
      </div>

      <div className={styles.field}>
        <label htmlFor="profile-lastName">Cognome</label>
        <input
          id="profile-lastName"
          name="lastName"
          type="text"
          autoComplete="family-name"
          value={draft.lastName}
          disabled={saving || actionsDisabled}
          aria-invalid={fieldErrors.lastName !== undefined}
          aria-describedby={
            fieldErrors.lastName !== undefined
              ? 'profile-lastName-error'
              : undefined
          }
          onChange={(event) => {
            onClientDraftChange({ lastName: event.target.value });
          }}
        />
        <FieldError
          id="profile-lastName-error"
          message={fieldErrors.lastName}
        />
      </div>

      <div className={styles.field}>
        <label htmlFor="profile-birthDate">Data di nascita</label>
        <input
          id="profile-birthDate"
          name="birthDate"
          type="date"
          value={draft.birthDate}
          disabled={saving || actionsDisabled}
          aria-invalid={fieldErrors.birthDate !== undefined}
          aria-describedby={
            fieldErrors.birthDate !== undefined
              ? 'profile-birthDate-error'
              : undefined
          }
          onChange={(event) => {
            onClientDraftChange({ birthDate: event.target.value });
          }}
        />
        <FieldError
          id="profile-birthDate-error"
          message={fieldErrors.birthDate}
        />
      </div>

      <div className={styles.field}>
        <label htmlFor="profile-heightCm">Altezza (cm)</label>
        <input
          id="profile-heightCm"
          name="heightCm"
          type="text"
          inputMode="decimal"
          value={draft.heightCm}
          disabled={saving || actionsDisabled}
          aria-invalid={fieldErrors.heightCm !== undefined}
          aria-describedby={
            fieldErrors.heightCm !== undefined
              ? 'profile-heightCm-error'
              : undefined
          }
          onChange={(event) => {
            onClientDraftChange({ heightCm: event.target.value });
          }}
        />
        <FieldError
          id="profile-heightCm-error"
          message={fieldErrors.heightCm}
        />
      </div>

      <div className={`${styles.field} ${styles.fieldWide}`}>
        <label htmlFor="profile-primaryGoal">Obiettivo principale</label>
        <input
          id="profile-primaryGoal"
          name="primaryGoal"
          type="text"
          value={draft.primaryGoal}
          disabled={saving || actionsDisabled}
          aria-invalid={fieldErrors.primaryGoal !== undefined}
          aria-describedby={
            fieldErrors.primaryGoal !== undefined
              ? 'profile-primaryGoal-error'
              : undefined
          }
          onChange={(event) => {
            onClientDraftChange({ primaryGoal: event.target.value });
          }}
        />
        <FieldError
          id="profile-primaryGoal-error"
          message={fieldErrors.primaryGoal}
        />
      </div>

      <div className={styles.field}>
        <label htmlFor="profile-gender">Genere</label>
        <select
          id="profile-gender"
          name="gender"
          value={draft.gender}
          disabled={saving || actionsDisabled}
          aria-invalid={fieldErrors.gender !== undefined}
          aria-describedby={
            fieldErrors.gender !== undefined
              ? 'profile-gender-error'
              : undefined
          }
          onChange={(event) => {
            onClientDraftChange({
              gender: event.target.value as Gender | '',
            });
          }}
        >
          <option value="">Seleziona</option>
          {GENDER_OPTIONS.map((option) => (
            <option key={option} value={option}>
              {genderLabel(option)}
            </option>
          ))}
        </select>
        <FieldError id="profile-gender-error" message={fieldErrors.gender} />
      </div>

      <div className={`${styles.field} ${styles.fieldWide}`}>
        <label htmlFor="profile-medicalNotes">Note mediche</label>
        <textarea
          id="profile-medicalNotes"
          name="medicalNotes"
          value={draft.medicalNotes}
          disabled={saving || actionsDisabled}
          aria-invalid={fieldErrors.medicalNotes !== undefined}
          aria-describedby={
            fieldErrors.medicalNotes !== undefined
              ? 'profile-medicalNotes-error'
              : undefined
          }
          onChange={(event) => {
            onClientDraftChange({ medicalNotes: event.target.value });
          }}
        />
        <FieldError
          id="profile-medicalNotes-error"
          message={fieldErrors.medicalNotes}
        />
      </div>

      <div className={`${styles.field} ${styles.fieldWide}`}>
        <label htmlFor="profile-injuryNotes">Note infortuni</label>
        <textarea
          id="profile-injuryNotes"
          name="injuryNotes"
          value={draft.injuryNotes}
          disabled={saving || actionsDisabled}
          aria-invalid={fieldErrors.injuryNotes !== undefined}
          aria-describedby={
            fieldErrors.injuryNotes !== undefined
              ? 'profile-injuryNotes-error'
              : undefined
          }
          onChange={(event) => {
            onClientDraftChange({ injuryNotes: event.target.value });
          }}
        />
        <FieldError
          id="profile-injuryNotes-error"
          message={fieldErrors.injuryNotes}
        />
      </div>

      <div className={`${styles.field} ${styles.fieldWide}`}>
        <label htmlFor="profile-notes">Note</label>
        <textarea
          id="profile-notes"
          name="notes"
          value={draft.notes}
          disabled={saving || actionsDisabled}
          aria-invalid={fieldErrors.notes !== undefined}
          aria-describedby={
            fieldErrors.notes !== undefined ? 'profile-notes-error' : undefined
          }
          onChange={(event) => {
            onClientDraftChange({ notes: event.target.value });
          }}
        />
        <FieldError id="profile-notes-error" message={fieldErrors.notes} />
      </div>

      <div className={`${styles.actions} ${styles.fieldWide}`}>
        <button
          type="submit"
          className={styles.submit}
          disabled={saveDisabled || saving || actionsDisabled}
          aria-busy={saving}
        >
          {saving ? 'Salvataggio…' : 'Salva modifiche'}
        </button>
        <button
          type="button"
          className={styles.buttonSecondary}
          disabled={saving || actionsDisabled}
          onClick={onCancel}
        >
          Annulla
        </button>
      </div>
    </form>
  );
}

function ProfessionalEditForm({
  draft,
  fieldErrors,
  saving,
  saveDisabled,
  actionsDisabled,
  onProfessionalDraftChange,
  onCancel,
  onSubmit,
}: {
  readonly draft: ProfessionalProfileDraft;
  readonly fieldErrors: ProfileFieldErrors;
  readonly saving: boolean;
  readonly saveDisabled: boolean;
  readonly actionsDisabled: boolean;
  readonly onProfessionalDraftChange: (
    patch: Partial<ProfessionalProfileDraft>,
  ) => void;
  readonly onCancel: () => void;
  readonly onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}): ReactNode {
  return (
    <form className={styles.form} noValidate onSubmit={onSubmit}>
      <div className={styles.field}>
        <label htmlFor="profile-firstName">Nome</label>
        <input
          id="profile-firstName"
          name="firstName"
          type="text"
          autoComplete="given-name"
          value={draft.firstName}
          disabled={saving || actionsDisabled}
          aria-invalid={fieldErrors.firstName !== undefined}
          aria-describedby={
            fieldErrors.firstName !== undefined
              ? 'profile-firstName-error'
              : undefined
          }
          onChange={(event) => {
            onProfessionalDraftChange({ firstName: event.target.value });
          }}
        />
        <FieldError
          id="profile-firstName-error"
          message={fieldErrors.firstName}
        />
      </div>

      <div className={styles.field}>
        <label htmlFor="profile-lastName">Cognome</label>
        <input
          id="profile-lastName"
          name="lastName"
          type="text"
          autoComplete="family-name"
          value={draft.lastName}
          disabled={saving || actionsDisabled}
          aria-invalid={fieldErrors.lastName !== undefined}
          aria-describedby={
            fieldErrors.lastName !== undefined
              ? 'profile-lastName-error'
              : undefined
          }
          onChange={(event) => {
            onProfessionalDraftChange({ lastName: event.target.value });
          }}
        />
        <FieldError
          id="profile-lastName-error"
          message={fieldErrors.lastName}
        />
      </div>

      <div className={styles.field}>
        <label htmlFor="profile-phoneNumber">Telefono</label>
        <input
          id="profile-phoneNumber"
          name="phoneNumber"
          type="tel"
          autoComplete="tel"
          value={draft.phoneNumber}
          disabled={saving || actionsDisabled}
          aria-invalid={fieldErrors.phoneNumber !== undefined}
          aria-describedby={
            fieldErrors.phoneNumber !== undefined
              ? 'profile-phoneNumber-error'
              : undefined
          }
          onChange={(event) => {
            onProfessionalDraftChange({ phoneNumber: event.target.value });
          }}
        />
        <FieldError
          id="profile-phoneNumber-error"
          message={fieldErrors.phoneNumber}
        />
      </div>

      <div className={`${styles.field} ${styles.fieldWide}`}>
        <label htmlFor="profile-bio">Bio</label>
        <textarea
          id="profile-bio"
          name="bio"
          value={draft.bio}
          disabled={saving || actionsDisabled}
          aria-invalid={fieldErrors.bio !== undefined}
          aria-describedby={
            fieldErrors.bio !== undefined ? 'profile-bio-error' : undefined
          }
          onChange={(event) => {
            onProfessionalDraftChange({ bio: event.target.value });
          }}
        />
        <FieldError id="profile-bio-error" message={fieldErrors.bio} />
      </div>

      <div className={styles.field}>
        <label htmlFor="profile-workplaceName">Luogo di lavoro</label>
        <input
          id="profile-workplaceName"
          name="workplaceName"
          type="text"
          value={draft.workplaceName}
          disabled={saving || actionsDisabled}
          aria-invalid={fieldErrors.workplaceName !== undefined}
          aria-describedby={
            fieldErrors.workplaceName !== undefined
              ? 'profile-workplaceName-error'
              : undefined
          }
          onChange={(event) => {
            onProfessionalDraftChange({ workplaceName: event.target.value });
          }}
        />
        <FieldError
          id="profile-workplaceName-error"
          message={fieldErrors.workplaceName}
        />
      </div>

      <div className={styles.field}>
        <label htmlFor="profile-city">Città</label>
        <input
          id="profile-city"
          name="city"
          type="text"
          autoComplete="address-level2"
          value={draft.city}
          disabled={saving || actionsDisabled}
          aria-invalid={fieldErrors.city !== undefined}
          aria-describedby={
            fieldErrors.city !== undefined ? 'profile-city-error' : undefined
          }
          onChange={(event) => {
            onProfessionalDraftChange({ city: event.target.value });
          }}
        />
        <FieldError id="profile-city-error" message={fieldErrors.city} />
      </div>

      <div className={styles.field}>
        <label htmlFor="profile-instagramUrl">Instagram</label>
        <input
          id="profile-instagramUrl"
          name="instagramUrl"
          type="url"
          value={draft.instagramUrl}
          disabled={saving || actionsDisabled}
          aria-invalid={fieldErrors.instagramUrl !== undefined}
          aria-describedby={
            fieldErrors.instagramUrl !== undefined
              ? 'profile-instagramUrl-error'
              : undefined
          }
          onChange={(event) => {
            onProfessionalDraftChange({ instagramUrl: event.target.value });
          }}
        />
        <FieldError
          id="profile-instagramUrl-error"
          message={fieldErrors.instagramUrl}
        />
      </div>

      <div className={styles.field}>
        <label htmlFor="profile-websiteUrl">Sito web</label>
        <input
          id="profile-websiteUrl"
          name="websiteUrl"
          type="url"
          value={draft.websiteUrl}
          disabled={saving || actionsDisabled}
          aria-invalid={fieldErrors.websiteUrl !== undefined}
          aria-describedby={
            fieldErrors.websiteUrl !== undefined
              ? 'profile-websiteUrl-error'
              : undefined
          }
          onChange={(event) => {
            onProfessionalDraftChange({ websiteUrl: event.target.value });
          }}
        />
        <FieldError
          id="profile-websiteUrl-error"
          message={fieldErrors.websiteUrl}
        />
      </div>

      <div className={`${styles.actions} ${styles.fieldWide}`}>
        <button
          type="submit"
          className={styles.submit}
          disabled={saveDisabled || saving || actionsDisabled}
          aria-busy={saving}
        >
          {saving ? 'Salvataggio…' : 'Salva modifiche'}
        </button>
        <button
          type="button"
          className={styles.buttonSecondary}
          disabled={saving || actionsDisabled}
          onClick={onCancel}
        >
          Annulla
        </button>
      </div>
    </form>
  );
}

export function ProfileDetailsSection(
  props: ProfileDetailsSectionProps,
): ReactNode {
  if (isClientProfileDetails(props)) {
    return <ClientProfileDetailsBody {...props} />;
  }

  return <ProfessionalProfileDetailsBody {...props} />;
}

function isClientProfileDetails(
  props: ProfileDetailsSectionProps,
): props is ClientProfileDetailsProps {
  return props.profile.role === 'CLIENT';
}

function ClientProfileDetailsBody(props: ClientProfileDetailsProps): ReactNode {
  const {
    mode,
    profile,
    draft,
    fieldErrors,
    globalError,
    saving,
    actionsDisabled,
    saveDisabled,
    onStartEdit,
    onCancel,
    onSubmit,
    onClientDraftChange,
  } = props;

  return (
    <section className={styles.section} aria-labelledby="profile-heading">
      <h2 id="profile-heading" className={styles.sectionTitle}>
        Profilo
      </h2>
      <p className={styles.sectionIntro}>
        Dati personali e informazioni pertinenti al tipo di profilo.
      </p>

      {globalError ? (
        <div
          className={styles.errorRegion}
          role="alert"
          id="profile-global-error"
          tabIndex={-1}
        >
          <p className={styles.errorSummary}>{globalError}</p>
        </div>
      ) : null}

      {mode === 'view' ? (
        <>
          <ClientView profile={profile} />
          <div className={styles.actions}>
            <button
              type="button"
              className={styles.button}
              disabled={actionsDisabled}
              onClick={onStartEdit}
            >
              Modifica profilo
            </button>
          </div>
        </>
      ) : draft ? (
        <ClientEditForm
          draft={draft}
          fieldErrors={fieldErrors}
          saving={saving}
          saveDisabled={saveDisabled}
          actionsDisabled={actionsDisabled}
          onClientDraftChange={onClientDraftChange}
          onCancel={onCancel}
          onSubmit={onSubmit}
        />
      ) : null}
    </section>
  );
}

function ProfessionalProfileDetailsBody(
  props: ProfessionalProfileDetailsProps,
): ReactNode {
  const {
    mode,
    profile,
    draft,
    fieldErrors,
    globalError,
    saving,
    actionsDisabled,
    saveDisabled,
    onStartEdit,
    onCancel,
    onSubmit,
    onProfessionalDraftChange,
  } = props;

  return (
    <section className={styles.section} aria-labelledby="profile-heading">
      <h2 id="profile-heading" className={styles.sectionTitle}>
        Profilo
      </h2>
      <p className={styles.sectionIntro}>
        Dati personali e informazioni pertinenti al tipo di profilo.
      </p>

      {globalError ? (
        <div
          className={styles.errorRegion}
          role="alert"
          id="profile-global-error"
          tabIndex={-1}
        >
          <p className={styles.errorSummary}>{globalError}</p>
        </div>
      ) : null}

      {mode === 'view' ? (
        <>
          <ProfessionalView profile={profile} />
          <div className={styles.actions}>
            <button
              type="button"
              className={styles.button}
              disabled={actionsDisabled}
              onClick={onStartEdit}
            >
              Modifica profilo
            </button>
          </div>
        </>
      ) : draft ? (
        <ProfessionalEditForm
          draft={draft}
          fieldErrors={fieldErrors}
          saving={saving}
          saveDisabled={saveDisabled}
          actionsDisabled={actionsDisabled}
          onProfessionalDraftChange={onProfessionalDraftChange}
          onCancel={onCancel}
          onSubmit={onSubmit}
        />
      ) : null}
    </section>
  );
}
