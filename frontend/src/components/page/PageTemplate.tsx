import type { ReactNode } from 'react';

import { PageHeader, type PageHeaderAppearance } from './PageHeader';
import styles from './PageTemplate.module.css';

export interface PageTemplateProps {
  readonly title: string;
  readonly description: string;
  readonly eyebrow?: string;
  readonly children?: ReactNode;
  readonly appearance?: PageHeaderAppearance;
}

export function PageTemplate({
  title,
  description,
  eyebrow,
  children,
  appearance = 'legacy',
}: PageTemplateProps) {
  const pageClassName =
    appearance === 'authenticated'
      ? `${styles.page} ${styles.authenticated}`
      : styles.page;

  return (
    <article className={pageClassName}>
      <PageHeader
        appearance={appearance}
        description={description}
        eyebrow={eyebrow}
        title={title}
      />
      {children ? <div className={styles.content}>{children}</div> : null}
    </article>
  );
}
