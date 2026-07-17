import { Link } from 'react-router-dom';

import styles from './Branding.module.css';

interface BrandingProps {
  linkTo?: string;
}

function BrandContent() {
  return (
    <>
      <span className={styles.mark} aria-hidden="true" />
      <span className={styles.name}>Support Trainer</span>
    </>
  );
}

export function Branding({ linkTo }: BrandingProps) {
  if (linkTo) {
    return (
      <Link
        className={styles.brand}
        to={linkTo}
        aria-label="Support Trainer, home"
      >
        <BrandContent />
      </Link>
    );
  }

  return (
    <div className={styles.brand} aria-label="Support Trainer">
      <BrandContent />
    </div>
  );
}
