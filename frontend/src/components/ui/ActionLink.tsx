import { Link, type LinkProps } from 'react-router-dom';

import styles from './ActionLink.module.css';

export type ActionLinkVariant = 'primary' | 'secondary';

export interface ActionLinkProps extends LinkProps {
  readonly variant?: ActionLinkVariant;
}

export function ActionLink({
  variant = 'secondary',
  className,
  ...props
}: ActionLinkProps) {
  const actionLinkClassName = [styles.link, styles[variant], className]
    .filter(Boolean)
    .join(' ');

  return <Link {...props} className={actionLinkClassName} />;
}
