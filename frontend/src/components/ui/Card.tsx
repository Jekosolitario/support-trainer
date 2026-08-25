import type { HTMLAttributes } from 'react';

import styles from './Card.module.css';

export type CardVariant = 'static' | 'interactive' | 'highlighted';

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  readonly variant?: CardVariant;
}

export function Card({ variant = 'static', className, ...props }: CardProps) {
  const cardClassName = [styles.card, styles[variant], className]
    .filter(Boolean)
    .join(' ');

  return <div {...props} className={cardClassName} />;
}
