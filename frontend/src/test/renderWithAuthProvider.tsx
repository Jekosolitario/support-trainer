import { StrictMode, type ReactElement } from 'react';
import { render } from '@testing-library/react';

import { AuthProvider } from '../auth/AuthContext';

interface RenderWithAuthProviderOptions {
  readonly strictMode?: boolean;
}

export function renderWithAuthProvider(
  element: ReactElement,
  { strictMode = false }: RenderWithAuthProviderOptions = {},
) {
  const provider = <AuthProvider>{element}</AuthProvider>;

  return render(strictMode ? <StrictMode>{provider}</StrictMode> : provider);
}
