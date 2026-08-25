import { render, screen, within } from '@testing-library/react';

import pageHeaderStyles from './PageHeader.module.css';
import pageTemplateStyles from './PageTemplate.module.css';
import { PageTemplate } from './PageTemplate';

describe('PageTemplate', () => {
  it('preserva le props correnti e compone PageHeader', () => {
    render(
      <PageTemplate
        eyebrow="Area applicativa"
        title="Dashboard"
        description="Riepilogo delle aree disponibili."
      >
        <section aria-label="Contenuto pagina">Contenuto</section>
      </PageTemplate>,
    );

    const heading = screen.getByRole('heading', {
      level: 1,
      name: 'Dashboard',
    });
    const header = heading.closest('header');

    expect(header).not.toBeNull();
    expect(
      within(header as HTMLElement).getByText('Area applicativa'),
    ).toBeVisible();
    expect(
      within(header as HTMLElement).getByText(
        'Riepilogo delle aree disponibili.',
      ),
    ).toBeVisible();
    expect(
      screen.getByRole('region', { name: 'Contenuto pagina' }),
    ).toHaveTextContent('Contenuto');
    expect(header).toHaveClass(pageHeaderStyles.legacy);
  });

  it('mantiene legacy il default e rende opt-in il namespace autenticato', () => {
    const { rerender } = render(
      <PageTemplate title="Profilo" description="Dati del profilo." />,
    );

    const legacyPage = screen.getByRole('article');
    expect(legacyPage).not.toHaveClass(pageTemplateStyles.authenticated);

    rerender(
      <PageTemplate
        appearance="authenticated"
        title="Profilo"
        description="Dati del profilo."
      />,
    );

    const authenticatedPage = screen.getByRole('article');
    const header = screen
      .getByRole('heading', { level: 1, name: 'Profilo' })
      .closest('header');

    expect(authenticatedPage).toHaveClass(pageTemplateStyles.authenticated);
    expect(header).toHaveClass(pageHeaderStyles.authenticated);
  });
});
