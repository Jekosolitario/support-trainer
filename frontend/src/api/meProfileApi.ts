import type { MyProfileResponse } from './authTypes';
import { performCsrfMutation } from './csrfMutation';
import type {
  UpdateMyProfileRequest,
  UpdateOperationalStatusRequest,
} from './meProfileTypes';

export function updateMyProfile(
  body: UpdateMyProfileRequest,
): Promise<MyProfileResponse> {
  return performCsrfMutation<MyProfileResponse>('/me/profile', {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
    invalidateOn401: true,
    invalidateCsrfOnCommit: false,
  });
}

export function updateMyOperationalStatus(
  body: UpdateOperationalStatusRequest,
): Promise<MyProfileResponse> {
  return performCsrfMutation<MyProfileResponse>(
    '/me/profile/operational-status',
    {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
      invalidateOn401: true,
      invalidateCsrfOnCommit: false,
    },
  );
}
