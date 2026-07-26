import { screen, within } from '@testing-library/react';

import { AppRoutes } from '../../app/router/AppRoutes';
import { renderApp } from '../../test/renderApp';
import {
  createAuthContextValue,
  createUnauthenticatedAuthState,
  renderWithAuthContext,
} from '../../test/renderWithAuthContext';
import {
  closingContent,
  futureContent,
  heroContent,
  processContent,
  rolesContent,
} from './homeContent';

const approvedIds = [
  'il-progetto',
  'vantaggi',
  'come-funziona',
  'per-chi',
  'funzionalita',
  'in-arrivo',
  'faq',
];

describe('HomePage', () => {
  it("rende le macro-aree nell'ordine approvato con un solo h1", () => {
    renderApp('/');

    const hero = screen
      .getByRole('heading', { level: 1, name: heroContent.title })
      .closest('section');

    const closing = screen
      .getByRole('heading', { level: 2, name: closingContent.title })
      .closest('section');

    const publicHeader = screen
      .getByRole('navigation', { name: 'Navigazione pubblica' })
      .closest('header');

    if (!publicHeader) {
      throw new Error('Header pubblico non trovato');
    }

    const areas = [
      publicHeader,
      hero,
      document.getElementById('il-progetto'),
      document.getElementById('vantaggi'),
      document.getElementById('come-funziona'),
      document.getElementById('per-chi'),
      document.getElementById('in-arrivo'),
      document.getElementById('faq'),
      closing,
      document.querySelector('footer'),
    ];

    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(areas.every(Boolean)).toBe(true);
    expect(new Set(areas).size).toBe(10);

    areas.slice(0, -1).forEach((area, index) => {
      expect(area?.compareDocumentPosition(areas[index + 1] as Node) ?? 0).toBe(
        Node.DOCUMENT_POSITION_FOLLOWING,
      );
    });
  });

  it('rende una sola istanza di ogni ancora e annida funzionalita in per-chi', () => {
    renderApp('/');

    approvedIds.forEach((id) => {
      expect(document.querySelectorAll(`[id="${id}"]`)).toHaveLength(1);
    });

    expect(document.getElementById('per-chi')).toContainElement(
      document.getElementById('funzionalita'),
    );
  });

  it('limita tutti i collegamenti applicativi alle rotte approvate', () => {
    renderApp('/');

    const approvedApplicationRoutes = [
      '/',
      '/login',
      '/register/professional',
      '/invite/validate',
    ];
    const applicationHrefs = screen
      .getAllByRole('link')
      .map((link) => link.getAttribute('href'))
      .filter((href): href is string => href?.startsWith('/') ?? false);

    approvedApplicationRoutes.forEach((route) => {
      expect(applicationHrefs).toContain(route);
    });
    applicationHrefs.forEach((href) => {
      expect(approvedApplicationRoutes).toContain(href);
    });

    expect(applicationHrefs).not.toContain('/register/client');
  });

  it('associa ogni CTA alla destinazione approvata nella propria area', () => {
    renderApp('/');

    const hero = screen
      .getByRole('heading', { level: 1, name: heroContent.title })
      .closest('section');
    const process = screen
      .getByRole('heading', { level: 2, name: processContent.title })
      .closest('section');
    const closing = screen
      .getByRole('heading', { level: 2, name: closingContent.title })
      .closest('section');
    const footer = screen.getByRole('contentinfo');

    expect(hero).not.toBeNull();
    expect(process).not.toBeNull();
    expect(closing).not.toBeNull();

    const expectedLinks = [
      {
        container: hero as HTMLElement,
        links: [
          ['Scopri come funziona', '#come-funziona'],
          ['Ho un codice invito', '/invite/validate'],
          ['Sei un professionista? Registrati', '/register/professional'],
        ],
      },
      {
        container: process as HTMLElement,
        links: [
          [
            'Hai ricevuto un codice? Inizia dalla validazione',
            '/invite/validate',
          ],
        ],
      },
      {
        container: closing as HTMLElement,
        links: [
          ['Registrati come professionista', '/register/professional'],
          ['Ho un codice invito', '/invite/validate'],
          ['Hai già un account? Accedi', '/login'],
        ],
      },
      {
        container: footer,
        links: [
          ['Accedi', '/login'],
          ['Registrati come professionista', '/register/professional'],
          ['Ho un codice invito', '/invite/validate'],
          ['Come funziona', '#come-funziona'],
          ['FAQ', '#faq'],
          ['Informazioni sul progetto', '#il-progetto'],
        ],
      },
    ] as const;

    expectedLinks.forEach(({ container, links }) => {
      links.forEach(([name, href]) => {
        expect(within(container).getByRole('link', { name })).toHaveAttribute(
          'href',
          href,
        );
      });
    });
  });

  it('non rende marcatori interni e mantiene il nutrizionista separato dalle prenotazioni', () => {
    renderApp('/');

    expect(document.body).not.toHaveTextContent(
      /\[CAMPO INTERNO|Testo visibile:|Macro-area|Sottosezione|Gruppo di confronto|Esperienza \d|Funzione \d/,
    );

    const nutritionist = screen
      .getByRole('heading', {
        level: 3,
        name: rolesContent.experiences[1].title,
      })
      .closest('article');

    expect(nutritionist).not.toBeNull();
    expect(
      within(nutritionist as HTMLElement).queryByText(/disponibilità/i),
    ).not.toBeInTheDocument();
    expect(
      within(nutritionist as HTMLElement).queryByText(/prenotazion/i),
    ).not.toBeInTheDocument();
  });

  it('mantiene le aree future non interattive', () => {
    renderApp('/');

    futureContent.areas.forEach((area) => {
      const futureArea = screen.getByRole('article', {
        name: `${area.title}: In arrivo`,
      });

      expect(within(futureArea).queryByRole('link')).not.toBeInTheDocument();
      expect(within(futureArea).queryByRole('button')).not.toBeInTheDocument();
    });

    const nutritionFuture = screen.getByText(
      rolesContent.experiences[1].future,
    );
    expect(nutritionFuture.closest('a, button')).toBeNull();
  });

  it('rende il footer soltanto nella home', () => {
    const { unmount } = renderApp('/');

    expect(screen.getByRole('contentinfo')).toBeInTheDocument();
    unmount();

    renderWithAuthContext(
      <AppRoutes isDevelopment={false} />,
      createAuthContextValue(createUnauthenticatedAuthState()),
      { initialEntries: ['/login'] },
    );
    expect(screen.queryByRole('contentinfo')).not.toBeInTheDocument();
  });
});
