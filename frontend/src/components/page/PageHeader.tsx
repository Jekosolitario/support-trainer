import styles from './PageHeader.module.css';

export type PageHeaderAppearance = 'legacy' | 'authenticated';

export interface PageHeaderProps {
  readonly title: string;
  readonly description?: string;
  readonly eyebrow?: string;
  readonly appearance?: PageHeaderAppearance;
}

export function PageHeader({
  title,
  description,
  eyebrow,
  appearance = 'authenticated',
}: PageHeaderProps) {
  return (
    <header className={`${styles.header} ${styles[appearance]}`}>
      {eyebrow ? <p className={styles.eyebrow}>{eyebrow}</p> : null}
      <h1 className={styles.title}>{title}</h1>
      {description !== undefined ? (
        <p className={styles.description}>{description}</p>
      ) : null}
    </header>
  );
}
