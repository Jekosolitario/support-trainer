import {
  assertPositiveSafeIntegerInput,
  requestDecoded,
} from './apiResponseDecoders';
import {
  decodeClientDetail,
  decodeClientSummaryList,
  type ClientDetail,
  type ClientSummary,
} from './clientsTypes';

export interface ClientsApiOptions {
  readonly signal?: AbortSignal;
}

export function listMyClients(
  options: ClientsApiOptions = {},
): Promise<ClientSummary[]> {
  return requestDecoded(
    '/clients/my',
    options.signal,
    decodeClientSummaryList,
    'Client list response',
  );
}

export function getClientById(
  clientId: number,
  options: ClientsApiOptions = {},
): Promise<ClientDetail> {
  assertPositiveSafeIntegerInput(clientId, 'clientId');

  return requestDecoded(
    `/clients/${String(clientId)}`,
    options.signal,
    decodeClientDetail,
    'Client detail response',
  );
}
