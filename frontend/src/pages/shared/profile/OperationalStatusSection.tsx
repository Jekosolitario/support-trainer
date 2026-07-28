import type {
  ClientOperationalStatus,
  ProfessionalOperationalStatus,
  UserRole,
} from '../../../api/authTypes';

import styles from './ProfilePage.module.css';
import {
  CLIENT_OPERATIONAL_OPTIONS,
  clientOperationalStatusLabel,
  PROFESSIONAL_OPERATIONAL_OPTIONS,
  professionalOperationalStatusLabel,
} from './profileLabels';

interface OperationalStatusSectionProps {
  readonly role: UserRole;
  readonly currentStatus:
    ClientOperationalStatus | ProfessionalOperationalStatus;
  readonly selection: ClientOperationalStatus | ProfessionalOperationalStatus;
  readonly error: string | null;
  readonly saving: boolean;
  readonly disabled: boolean;
  readonly onSelectionChange: (
    value: ClientOperationalStatus | ProfessionalOperationalStatus,
  ) => void;
  readonly onSubmit: () => void;
}

function statusLabel(
  role: UserRole,
  status: ClientOperationalStatus | ProfessionalOperationalStatus,
): string {
  return role === 'CLIENT'
    ? clientOperationalStatusLabel(status as ClientOperationalStatus)
    : professionalOperationalStatusLabel(
        status as ProfessionalOperationalStatus,
      );
}

export function OperationalStatusSection({
  role,
  currentStatus,
  selection,
  error,
  saving,
  disabled,
  onSelectionChange,
  onSubmit,
}: OperationalStatusSectionProps) {
  const selectId = 'operational-status';
  const errorId = 'operational-status-error';
  const unchanged = selection === currentStatus;
  const submitDisabled = disabled || unchanged || saving;
  const currentLabel = statusLabel(role, currentStatus);

  return (
    <section
      className={`${styles.section} ${styles.sectionStatus}`}
      aria-labelledby="operational-status-heading"
    >
      <h2 id="operational-status-heading" className={styles.sectionTitle}>
        Stato operativo
      </h2>
      <p className={styles.sectionIntro}>
        Indica la tua disponibilità operativa corrente. Lo stato non modifica
        automaticamente prenotazioni o disponibilità.
      </p>

      <div className={styles.statusCurrent}>
        <span className={styles.statusCurrentLabel}>Stato corrente</span>
        <span className={styles.statusBadge} data-status={currentStatus}>
          <span className={styles.statusDot} aria-hidden="true" />
          <span>{currentLabel}</span>
        </span>
      </div>

      {error ? (
        <div
          className={styles.errorRegion}
          role="alert"
          id={errorId}
          tabIndex={-1}
        >
          <p className={styles.errorSummary}>{error}</p>
        </div>
      ) : null}

      <div className={`${styles.field} ${styles.statusSelect}`}>
        <label htmlFor={selectId}>Nuovo stato</label>
        <select
          id={selectId}
          className={styles.statusSelectControl}
          value={selection}
          disabled={disabled || saving}
          aria-invalid={error !== null}
          aria-describedby={error !== null ? errorId : undefined}
          onChange={(event) => {
            onSelectionChange(
              event.target.value as
                ClientOperationalStatus | ProfessionalOperationalStatus,
            );
          }}
        >
          {role === 'CLIENT'
            ? CLIENT_OPERATIONAL_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {clientOperationalStatusLabel(option)}
                </option>
              ))
            : PROFESSIONAL_OPERATIONAL_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {professionalOperationalStatusLabel(option)}
                </option>
              ))}
        </select>
      </div>

      <div className={styles.actions}>
        <button
          type="button"
          className={styles.button}
          disabled={submitDisabled}
          aria-busy={saving}
          onClick={onSubmit}
        >
          {saving ? 'Aggiornamento…' : 'Aggiorna stato'}
        </button>
      </div>
    </section>
  );
}
