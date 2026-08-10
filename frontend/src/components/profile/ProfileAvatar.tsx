import { useState } from 'react';

import { profileImageFallback } from '../../pages/shared/profile/profileLabels';

interface ProfileAvatarProps {
  readonly firstName: string;
  readonly lastName: string;
  readonly profileImageUrl: string | null;
  readonly className: string;
  readonly imageClassName: string;
}

export function ProfileAvatar({
  firstName,
  lastName,
  profileImageUrl,
  className,
  imageClassName,
}: ProfileAvatarProps) {
  const [failedImageUrl, setFailedImageUrl] = useState<string | null>(null);
  const fullName = `${firstName} ${lastName}`.trim();
  const showImage =
    profileImageUrl !== null && failedImageUrl !== profileImageUrl;

  if (!showImage) {
    return (
      <span
        className={className}
        role="img"
        aria-label={`Avatar di ${fullName}`}
      >
        {profileImageFallback(firstName, lastName)}
      </span>
    );
  }

  return (
    <span className={className}>
      <img
        className={imageClassName}
        src={profileImageUrl}
        alt={`Foto profilo di ${fullName}`}
        onError={() => {
          setFailedImageUrl(profileImageUrl);
        }}
      />
    </span>
  );
}
