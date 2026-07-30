import { performCsrfMutation } from './csrfMutation';
import { request } from './httpClient';
import type { InviteCodeResponse } from './invitesTypes';

export interface ListMyInvitesOptions {
  readonly signal?: AbortSignal;
  readonly invalidateOn401?: boolean;
}

export function listMyInvites(
  options: ListMyInvitesOptions = {},
): Promise<InviteCodeResponse[]> {
  return request<InviteCodeResponse[]>('/invites', {
    invalidateOn401: options.invalidateOn401 ?? true,
    signal: options.signal,
  });
}

export function createInvite(): Promise<InviteCodeResponse> {
  return performCsrfMutation<InviteCodeResponse>('/invites', {
    method: 'POST',
    invalidateOn401: true,
    invalidateCsrfOnCommit: false,
  });
}
