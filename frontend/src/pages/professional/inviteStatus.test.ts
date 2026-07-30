import { describe, expect, it } from 'vitest';

import {
  deriveInviteDisplayStatus,
  getNextValidInviteExpiryMs,
} from './inviteStatus';

const NOW = Date.parse('2026-07-30T12:00:00.000Z');

function invite(overrides: {
  active?: boolean;
  used?: boolean;
  expiresAt?: string;
}) {
  return {
    active: overrides.active ?? true,
    used: overrides.used ?? false,
    expiresAt: overrides.expiresAt ?? '2026-08-06T12:00:00.000Z',
  };
}

describe('deriveInviteDisplayStatus', () => {
  it('restituisce Valido quando attivo, non usato e non scaduto', () => {
    expect(deriveInviteDisplayStatus(invite({}), NOW)).toBe('Valido');
  });

  it('restituisce Usato quando used=true e active=true', () => {
    expect(deriveInviteDisplayStatus(invite({ used: true }), NOW)).toBe(
      'Usato',
    );
  });

  it('restituisce Scaduto quando expiresAt < now', () => {
    expect(
      deriveInviteDisplayStatus(
        invite({ expiresAt: '2026-07-30T11:59:59.999Z' }),
        NOW,
      ),
    ).toBe('Scaduto');
  });

  it('restituisce Scaduto al boundary expiresAt === now', () => {
    expect(
      deriveInviteDisplayStatus(
        invite({ expiresAt: '2026-07-30T12:00:00.000Z' }),
        NOW,
      ),
    ).toBe('Scaduto');
  });

  it('restituisce Non attivo quando active=false', () => {
    expect(deriveInviteDisplayStatus(invite({ active: false }), NOW)).toBe(
      'Non attivo',
    );
  });

  it('precede: inactive + used → Non attivo', () => {
    expect(
      deriveInviteDisplayStatus(invite({ active: false, used: true }), NOW),
    ).toBe('Non attivo');
  });

  it('precede: inactive + expired → Non attivo', () => {
    expect(
      deriveInviteDisplayStatus(
        invite({
          active: false,
          expiresAt: '2026-07-01T00:00:00.000Z',
        }),
        NOW,
      ),
    ).toBe('Non attivo');
  });

  it('precede: used + expired → Usato', () => {
    expect(
      deriveInviteDisplayStatus(
        invite({
          used: true,
          expiresAt: '2026-07-01T00:00:00.000Z',
        }),
        NOW,
      ),
    ).toBe('Usato');
  });

  it('expiresAt invalido → Non disponibile (fail-closed, mai Valido)', () => {
    expect(
      deriveInviteDisplayStatus(invite({ expiresAt: 'not-a-date' }), NOW),
    ).toBe('Non disponibile');
  });
});

describe('getNextValidInviteExpiryMs', () => {
  it('sceglie la scadenza futura più vicina tra gli inviti Valido', () => {
    const next = getNextValidInviteExpiryMs(
      [
        invite({ expiresAt: '2026-08-10T12:00:00.000Z' }),
        invite({ expiresAt: '2026-08-01T12:00:00.000Z' }),
        invite({ used: true, expiresAt: '2026-07-31T12:00:00.000Z' }),
        invite({ expiresAt: '2026-07-29T12:00:00.000Z' }),
      ],
      NOW,
    );

    expect(next).toBe(Date.parse('2026-08-01T12:00:00.000Z'));
  });

  it('ignora expiresAt invalido', () => {
    expect(
      getNextValidInviteExpiryMs([invite({ expiresAt: 'bogus' })], NOW),
    ).toBeNull();
  });
});
