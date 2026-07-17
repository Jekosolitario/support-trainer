import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import { AppRoutes } from '../app/router/AppRoutes';

export function renderApp(path: string, isDevelopment = false) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes isDevelopment={isDevelopment} />
    </MemoryRouter>,
  );
}
