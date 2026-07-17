import type { ReactNode } from 'react';

import styles from './PageTemplate.module.css';

interface PageTemplateProps {
  title: string;
  description: string;
  eyebrow?: string;
  children?: ReactNode;
}

export function PageTemplate({
  title,
  description,
  eyebrow,
  children,
}: PageTemplateProps) {
  return (
    <article className={styles.page}>
      <header className={styles.header}>
        {eyebrow ? <p className={styles.eyebrow}>{eyebrow}</p> : null}
        <h1 className={styles.title}>{title}</h1>
        <p className={styles.description}>{description}</p>
      </header>
      {children ? <div className={styles.content}>{children}</div> : null}
    </article>
  );
}
