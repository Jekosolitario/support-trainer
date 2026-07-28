import type { MyAccountResponse } from '../../../api/authTypes';

import styles from './ProfilePage.module.css';
import {
  accountStatusLabel,
  formatAccountDate,
  roleLabel,
} from './profileLabels';

interface AccountSectionProps {
  readonly account: MyAccountResponse;
}

export function AccountSection({ account }: AccountSectionProps) {
  return (
    <section
      className={`${styles.section} ${styles.sectionSecondary}`}
      aria-labelledby="account-heading"
    >
      <h2 id="account-heading" className={styles.sectionTitle}>
        Account
      </h2>
      <p className={styles.sectionIntro}>
        Informazioni dell&apos;account in sola lettura.
      </p>
      <dl className={styles.dl}>
        <div className={styles.row}>
          <dt>Email</dt>
          <dd>{account.email}</dd>
        </div>
        <div className={styles.row}>
          <dt>Ruolo</dt>
          <dd>{roleLabel(account.role)}</dd>
        </div>
        <div className={styles.row}>
          <dt>Stato account</dt>
          <dd>
            <span
              className={`${styles.badge} ${
                account.accountStatus === 'ACTIVE'
                  ? styles.badgePositive
                  : styles.badgeNeutral
              }`}
            >
              {accountStatusLabel(account.accountStatus)}
            </span>
          </dd>
        </div>
        <div className={styles.row}>
          <dt>Email verificata</dt>
          <dd>
            <span
              className={`${styles.badge} ${
                account.emailVerified
                  ? styles.badgePositive
                  : styles.badgeNeutral
              }`}
            >
              {account.emailVerified ? 'Verificata' : 'Non verificata'}
            </span>
          </dd>
        </div>
        <div className={styles.row}>
          <dt>Creato il</dt>
          <dd>{formatAccountDate(account.createdAt)}</dd>
        </div>
      </dl>
    </section>
  );
}
