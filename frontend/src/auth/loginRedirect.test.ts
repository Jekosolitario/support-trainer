import { describe, expect, it } from 'vitest';

import type { UserAccessProfile } from '../app/config/access';
import { getDashboardTarget, getSafeLoginTarget } from './loginRedirect';

function redirectState(
  pathname: unknown,
  search: unknown = '',
  hash: unknown = '',
): unknown {
  return {
    from: {
      pathname,
      search,
      hash,
    },
  };
}

describe('getSafeLoginTarget', () => {
  it.each([
    ['/app/client/dashboard', '', '', '/app/client/dashboard'],
    ['/app/client/bookings', '', '', '/app/client/bookings'],
    ['/app/client/bookings/123', '', '', '/app/client/bookings/123'],
    ['/app/professional/dashboard', '', '', '/app/professional/dashboard'],
    [
      '/app/professional/bookings',
      '?filter=pending',
      '',
      '/app/professional/bookings?filter=pending',
    ],
    [
      '/app/professional/bookings/123',
      '',
      '',
      '/app/professional/bookings/123',
    ],
    [
      '/app/client/bookings/11',
      '',
      '#details',
      '/app/client/bookings/11#details',
    ],
    [
      '/app/professional/clients/7',
      '?tab=profile',
      '#notes',
      '/app/professional/clients/7?tab=profile#notes',
    ],
  ])(
    'accetta una destinazione protetta interna %s%s%s',
    (pathname, search, hash, expected) => {
      expect(getSafeLoginTarget(redirectState(pathname, search, hash))).toBe(
        expected,
      );
    },
  );

  it.each([
    ['undefined', undefined],
    ['null', null],
    ['stringa', '/app/client/bookings'],
    ['array', []],
    ['from assente', {}],
    ['from null', { from: null }],
    ['from array', { from: [] }],
    ['shape incompleta', { from: { pathname: '/app/client/bookings' } }],
    ['pathname non stringa', redirectState(42)],
    ['search non stringa', redirectState('/app/client/bookings', null)],
    ['hash non stringa', redirectState('/app/client/bookings', '', false)],
    ['URL assoluta', redirectState('https://evil.example/app/client')],
    ['protocol-relative', redirectState('//evil.example/app/client')],
    ['login', redirectState('/login')],
    ['home', redirectState('/')],
    ['radice app', redirectState('/app')],
    ['namespace esterno', redirectState('/admin/dashboard')],
    ['namespace app estraneo', redirectState('/app/admin/dashboard')],
    ['prefix clientfragile', redirectState('/app/clientevil/dashboard')],
    [
      'prefix professionalfragile',
      redirectState('/app/professionalevil/dashboard'),
    ],
    ['doppio slash interno', redirectState('/app/client//evil.example')],
    ['backslash raw', redirectState('/app/client\\bookings')],
    ['backslash encoded', redirectState('/app/client/%5Cbookings')],
    ['query nel pathname', redirectState('/app/client/bookings?tab=active')],
    ['hash nel pathname', redirectState('/app/client/bookings#details')],
    ['controllo nel pathname', redirectState('/app/client/bookings\n')],
    ['search senza ?', redirectState('/app/client/bookings', 'tab=active')],
    ['search con hash', redirectState('/app/client/bookings', '?tab=#bad')],
    ['search con backslash', redirectState('/app/client/bookings', '?tab=\\x')],
    ['search con controllo', redirectState('/app/client/bookings', '?\ntab')],
    ['hash senza #', redirectState('/app/client/bookings', '', 'details')],
    ['hash con backslash', redirectState('/app/client/bookings', '', '#\\x')],
    [
      'hash con controllo',
      redirectState('/app/client/bookings', '', '#\ndetails'),
    ],
  ])('rifiuta %s', (_label, value) => {
    expect(getSafeLoginTarget(value)).toBeNull();
  });

  it.each([
    '/app/client/./dashboard',
    '/app/client/../professional/dashboard',
    '/app/client/../../login',
    '/app/client/foo/../dashboard',
    '/app/professional/../client/dashboard',
    '/app/professional/../../login',
  ])('rifiuta traversal raw %s', (pathname) => {
    expect(getSafeLoginTarget(redirectState(pathname))).toBeNull();
  });

  it.each([
    '/app/client/%2e/dashboard',
    '/app/client/%2E/dashboard',
    '/app/client/%2e%2e/professional/dashboard',
    '/app/client/%2E%2E/professional/dashboard',
    '/app/client/%2e%2E/professional/dashboard',
    '/app/client/.%2e/professional/dashboard',
    '/app/client/%2e./professional/dashboard',
    '/app/client/%2e%2e/%2e%2e/login',
    '/app/client/%2e%2e%2fprofessional/dashboard',
    '/app/client/%GZ/dashboard',
  ])('rifiuta traversal encoded o percent malformato %s', (pathname) => {
    expect(getSafeLoginTarget(redirectState(pathname))).toBeNull();
  });
});

describe('getDashboardTarget', () => {
  it.each<readonly [string, UserAccessProfile, string]>([
    [
      'client',
      { role: 'CLIENT', specialization: null },
      '/app/client/dashboard',
    ],
    [
      'personal trainer',
      { role: 'PROFESSIONAL', specialization: 'PERSONAL_TRAINER' },
      '/app/professional/dashboard',
    ],
    [
      'nutrizionista',
      { role: 'PROFESSIONAL', specialization: 'NUTRITIONIST' },
      '/app/professional/dashboard',
    ],
  ])('usa il fallback per %s', (_label, profile, expected) => {
    expect(getDashboardTarget(profile)).toBe(expected);
  });
});
