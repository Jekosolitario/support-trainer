import { ProfileAvatar } from '../../components/profile/ProfileAvatar';
import styles from './ProfessionalClientsPage.module.css';

interface ProfessionalClientAvatarProps {
  readonly firstName: string;
  readonly lastName: string;
  readonly profileImageUrl: string | null;
  readonly size?: 'card' | 'detail';
}

export function ProfessionalClientAvatar({
  firstName,
  lastName,
  profileImageUrl,
  size = 'card',
}: ProfessionalClientAvatarProps) {
  const avatarClassName = `${styles.avatar} ${
    size === 'detail' ? styles.avatarDetail : ''
  }`;

  return (
    <ProfileAvatar
      className={avatarClassName}
      firstName={firstName}
      imageClassName={styles.avatarImage}
      lastName={lastName}
      profileImageUrl={profileImageUrl}
    />
  );
}
