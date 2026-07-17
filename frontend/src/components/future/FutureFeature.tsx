import styles from './FutureFeature.module.css';

interface FutureFeatureProps {
  title: string;
  description: string;
}

export function FutureFeature({ title, description }: FutureFeatureProps) {
  return (
    <article className={styles.feature} aria-label={`${title}: In arrivo`}>
      <div className={styles.heading}>
        <h3 className={styles.title}>{title}</h3>
        <span className={styles.badge}>In arrivo</span>
      </div>
      <p className={styles.description}>{description}</p>
    </article>
  );
}
