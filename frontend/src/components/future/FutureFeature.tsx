import styles from './FutureFeature.module.css';

interface FutureFeatureProps {
  title: string;
  description: string;
  variant?: 'default' | 'home';
}

export function FutureFeature({
  title,
  description,
  variant = 'default',
}: FutureFeatureProps) {
  const className =
    variant === 'home' ? `${styles.feature} ${styles.home}` : styles.feature;

  return (
    <article className={className} aria-label={`${title}: In arrivo`}>
      {variant === 'home' ? (
        <div className={styles.moduleStatus} aria-hidden="true">
          <span>STATO MODULO</span>
          <span className={styles.statusTrack} />
        </div>
      ) : null}
      <div className={styles.heading}>
        <h3 className={styles.title}>{title}</h3>
        <span className={styles.badge}>In arrivo</span>
      </div>
      <p className={styles.description}>{description}</p>
    </article>
  );
}
