import { BrowserRouter } from 'react-router-dom';

import { AuthProvider } from '../auth/AuthContext';
import { AppRoutes } from './router/AppRoutes';

export function App() {
  return (
    <BrowserRouter useTransitions={false}>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  );
}
