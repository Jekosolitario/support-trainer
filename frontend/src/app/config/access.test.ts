import { describe, expectTypeOf, it } from 'vitest';

import type { UserAccessProfile } from './access';

describe('UserAccessProfile', () => {
  it('vincola ruolo e specializzazione tramite una discriminated union', () => {
    expectTypeOf<
      Extract<UserAccessProfile, { role: 'CLIENT' }>
    >().toEqualTypeOf<{
      role: 'CLIENT';
      specialization: null;
    }>();

    expectTypeOf<
      Extract<UserAccessProfile, { role: 'PROFESSIONAL' }>
    >().toEqualTypeOf<{
      role: 'PROFESSIONAL';
      specialization: 'PERSONAL_TRAINER' | 'NUTRITIONIST';
    }>();
  });
});
