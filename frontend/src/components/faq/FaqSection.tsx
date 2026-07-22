import { useState } from 'react';
import { Link } from 'react-router-dom';

import type { FaqItem } from '../../pages/public/homeContent';
import styles from './FaqSection.module.css';

interface FaqSectionProps {
  eyebrow: string;
  title: string;
  introduction: string;
  items: readonly FaqItem[];
}

export function FaqSection({
  eyebrow,
  title,
  introduction,
  items,
}: FaqSectionProps) {
  const [openItemId, setOpenItemId] = useState<string | null>(null);

  return (
    <section className={styles.section} id="faq" aria-labelledby="faq-title">
      <header className={styles.header}>
        <p className={styles.eyebrow}>{eyebrow}</p>
        <h2 id="faq-title">{title}</h2>
        <p>{introduction}</p>
      </header>

      <div className={styles.list}>
        {items.map((item, index) => {
          const isOpen = openItemId === item.id;
          const questionId = `faq-question-${item.id}`;
          const answerId = `faq-answer-${item.id}`;

          return (
            <article
              className={`${styles.item} ${isOpen ? styles.itemOpen : ''}`}
              key={item.id}
            >
              <h3 className={styles.question}>
                <button
                  type="button"
                  aria-expanded={isOpen}
                  aria-controls={answerId}
                  id={questionId}
                  onClick={() => setOpenItemId(isOpen ? null : item.id)}
                >
                  <span className={styles.questionIndex} aria-hidden="true">
                    {String(index + 1).padStart(2, '0')}
                  </span>
                  <span className={styles.questionText}>{item.question}</span>
                  <span className={styles.toggleIcon} aria-hidden="true">
                    {isOpen ? '−' : '+'}
                  </span>
                </button>
              </h3>

              {isOpen ? (
                <div
                  className={styles.answer}
                  id={answerId}
                  role="region"
                  aria-labelledby={questionId}
                >
                  <p>{item.answer}</p>
                  {item.link ? (
                    <Link to={item.link.to}>{item.link.label}</Link>
                  ) : null}
                </div>
              ) : null}
            </article>
          );
        })}
      </div>
    </section>
  );
}
