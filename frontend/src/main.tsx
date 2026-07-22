import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import '@fontsource/bruno-ace-sc/latin-400.css';
import '@fontsource/saira-condensed/latin-400.css';
import '@fontsource/saira-condensed/latin-600.css';
import '@fontsource/saira-condensed/latin-700.css';
import '@fontsource/saira-condensed/latin-800.css';

import { App } from './app/App';
import './styles/global.css';

const rootElement = document.getElementById('root');

if (!rootElement) {
  throw new Error('Elemento root non trovato.');
}

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
