import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

import { faqContent } from '../../pages/public/homeContent';
import { FaqSection } from './FaqSection';

function renderFaq() {
  return render(
    <MemoryRouter>
      <FaqSection {...faqContent} />
    </MemoryRouter>,
  );
}

describe('FaqSection', () => {
  it('rende nove domande tutte chiuse con associazioni ARIA stabili', () => {
    renderFaq();

    const questions = screen.getAllByRole('button');

    expect(questions).toHaveLength(9);
    questions.forEach((question) => {
      const answerId = question.getAttribute('aria-controls');

      expect(question).toHaveAttribute('aria-expanded', 'false');
      expect(answerId).toBeTruthy();
      expect(document.getElementById(answerId ?? '')).not.toBeInTheDocument();
    });
  });

  it('mantiene aperta una sola risposta alla volta e permette di richiuderla', async () => {
    const user = userEvent.setup();
    renderFaq();

    const firstQuestion = screen.getByRole('button', {
      name: faqContent.items[0].question,
    });
    const secondQuestion = screen.getByRole('button', {
      name: faqContent.items[1].question,
    });

    await user.click(firstQuestion);
    expect(firstQuestion).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText(faqContent.items[0].answer)).toBeVisible();
    const firstAnswer = screen.getByRole('region', {
      name: faqContent.items[0].question,
    });
    expect(firstAnswer).toHaveAttribute(
      'id',
      firstQuestion.getAttribute('aria-controls'),
    );
    expect(firstAnswer).toHaveAttribute(
      'aria-labelledby',
      firstQuestion.getAttribute('id'),
    );

    await user.click(secondQuestion);
    expect(firstQuestion).toHaveAttribute('aria-expanded', 'false');
    expect(secondQuestion).toHaveAttribute('aria-expanded', 'true');
    expect(
      screen.queryByText(faqContent.items[0].answer),
    ).not.toBeInTheDocument();
    expect(screen.getByText(faqContent.items[1].answer)).toBeVisible();

    await user.click(secondQuestion);
    expect(secondQuestion).toHaveAttribute('aria-expanded', 'false');
    expect(
      screen.queryByText(faqContent.items[1].answer),
    ).not.toBeInTheDocument();
  });

  it('usa la semantica nativa del button con Invio e Spazio', async () => {
    const user = userEvent.setup();
    renderFaq();

    const firstQuestion = screen.getByRole('button', {
      name: faqContent.items[0].question,
    });

    await user.tab();
    expect(firstQuestion).toHaveFocus();

    await user.keyboard('{Enter}');
    expect(firstQuestion).toHaveAttribute('aria-expanded', 'true');

    await user.keyboard(' ');
    expect(firstQuestion).toHaveAttribute('aria-expanded', 'false');
  });

  it('rende il collegamento Accedi soltanto quando la FAQ 8 è aperta', async () => {
    const user = userEvent.setup();
    renderFaq();

    const accountQuestion = screen.getByRole('button', {
      name: faqContent.items[7].question,
    });

    expect(
      screen.queryByRole('link', { name: 'Accedi' }),
    ).not.toBeInTheDocument();

    await user.click(accountQuestion);
    expect(screen.getByRole('link', { name: 'Accedi' })).toHaveAttribute(
      'href',
      '/login',
    );

    await user.click(accountQuestion);
    expect(
      screen.queryByRole('link', { name: 'Accedi' }),
    ).not.toBeInTheDocument();
  });
});
