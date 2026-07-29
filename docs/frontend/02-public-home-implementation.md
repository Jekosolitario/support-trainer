# Public Home Implementation — Support Trainer

## 1. Scopo e stato del documento

Questo documento descrive la home pubblica di Support Trainer e la parte di fondazione frontend necessaria a renderla disponibile sulla route `/`. Il perimetro comprende struttura narrativa, navigazione, componenti, copy, sistema visuale, tipografia, comportamento responsive, accessibilità e test automatici direttamente collegati alla pagina.

La home pubblica è implementata. Sono presenti anche il progetto React, il routing, il layout pubblico, i layout autenticati, le pagine di errore e le route applicative di base. L’autenticazione session-based frontend (login, logout, CSRF, guards, bootstrap) è implementata; vedi [Authentication Session Flow](./03-authentication-session-flow.md). La pagina Profilo autenticata (Account in sola lettura e Operational Status) è implementata e allineata al linguaggio visuale dark-tech; dettagli funzionali in [Frontend Functional Map](./01-frontend-functional-map-mvp.md). Registrazione PROFESSIONAL e verifica email sono implementate; validazione invito, registrazione CLIENT e le altre pagine business private restano prevalentemente placeholder.

Le fonti di verità implementative per questa pagina sono il codice e i test frontend collegati:

- [`AppRoutes.tsx`](../../frontend/src/app/router/AppRoutes.tsx);
- [`PublicLayout.tsx`](../../frontend/src/layouts/public/PublicLayout.tsx);
- [`HomePage.tsx`](../../frontend/src/pages/public/HomePage.tsx);
- [`homeContent.ts`](../../frontend/src/pages/public/homeContent.ts);
- [`FaqSection.tsx`](../../frontend/src/components/faq/FaqSection.tsx);
- [`HomeFooter.tsx`](../../frontend/src/components/home/HomeFooter.tsx);
- [`FutureFeature.tsx`](../../frontend/src/components/future/FutureFeature.tsx);
- [`Branding.tsx`](../../frontend/src/components/branding/Branding.tsx);
- [`global.css`](../../frontend/src/styles/global.css), i CSS Module collegati e i relativi test;
- [`package.json`](../../frontend/package.json).

Per ruoli, endpoint e regole business, sullo **stato corrente** prevalgono codice, test, configurazioni e la documentazione attiva ([Functional Scope](../01-functional-scope.md), [Endpoint Map](../08-endpoint-map.md), [Security Flow](../09-security-flow.md)). I documenti di sprint ([inviti e registrazione cliente](../13-sprint-02-verification-invite-client-link.md), [profili e relazioni](../14-sprint-03-profile-clients-professionals-read.md), [Availability](../16-sprint-04-availability.md), [Booking](../17-sprint-05-bookings.md)) e la [certificazione tecnica finale](../final-audit-mvp.md) restano riferimenti storici delle rispettive baseline, non source of truth prevalente sullo stato attuale.

## 2. Obiettivo e pubblico

La home svolge una funzione informativa e di orientamento. Presenta il problema affrontato dal prodotto, chiarisce le differenze tra i ruoli, separa le funzioni disponibili da quelle future e conduce verso le route pubbliche già registrate.

La progressione progettuale è:

1. **scoperta**, attraverso identità, promessa introduttiva e contesto;
2. **comprensione**, attraverso vantaggi, confronto e spiegazione dei percorsi;
3. **interesse**, attraverso esperienze differenziate per ruolo e funzioni disponibili;
4. **registrazione o accesso**, attraverso CTA coerenti con il tipo di visitatore.

Il pubblico è ordinato nel modo seguente:

1. professionista non registrato;
2. visitatore curioso;
3. cliente con invito;
4. utente già registrato.

Personal trainer e nutrizionisti possono raggiungere la registrazione professionale. Il cliente viene indirizzato alla validazione del codice invito, perché non esiste una registrazione cliente libera. L'utente già registrato può raggiungere la route di login.

La home non promette disponibilità immediata di funzioni prive di contratto operativo. Tutte le aree future sono separate da quelle disponibili e marcate “In arrivo”, senza date o priorità pubbliche.

## 3. Percorso narrativo

L'ordine reale della pagina è:

1. header pubblico;
2. hero;
3. introduzione al progetto;
4. vantaggi e confronto tra strumenti separati e piattaforma condivisa;
5. spiegazione di come iniziare;
6. esperienze per ruolo e riepilogo delle funzionalità disponibili;
7. moduli in arrivo;
8. FAQ;
9. CTA conclusiva;
10. footer.

La home espone ancore stabili per le sezioni principali:

- `#il-progetto` per l'introduzione al progetto;
- `#vantaggi` per il confronto e i benefici;
- `#come-funziona` per i percorsi dei tre ruoli;
- `#per-chi` per le esperienze differenziate;
- `#funzionalita` per il riepilogo di ciò che è disponibile;
- `#in-arrivo` per i moduli futuri;
- `#faq` per le domande frequenti.

Il testo completo non viene duplicato qui: [`homeContent.ts`](../../frontend/src/pages/public/homeContent.ts) resta la fonte aggiornata del copy.

## 4. CTA e navigazione

Le destinazioni principali sono:

| Intento                      | Destinazione             | Stato                                            |
| ---------------------------- | ------------------------ | ------------------------------------------------ |
| “Scopri come funziona”       | `#come-funziona`         | Ancora interna operativa                         |
| Registrazione professionista | `/register/professional` | **Implementata** (form, API, check-email/resend) |
| Verifica email               | `/verify-email`          | **Implementata** (confirm + resend)              |
| Codice invito cliente        | `/invite/validate`       | Route registrata, flusso ancora placeholder      |
| Registrazione cliente        | `/register/client`       | Route registrata, flusso ancora placeholder      |
| Accesso                      | `/login`                 | **Implementata** (LoginPage session-based)       |

La hero presenta l'ancora informativa, la validazione invito e la registrazione professionale. La sezione “Come funziona” aggiunge una CTA contestuale per il cliente con codice. La chiusura ripropone registrazione, invito e accesso secondo la gerarchia del pubblico.

Il footer separa le destinazioni applicative nel gruppo “Accesso” e le ancore della pagina nel gruppo “Esplora”. Il branding conduce alla route `/`.

Le CTA costituiscono navigazione disponibile verso route esistenti. Login, registrazione PROFESSIONAL e verifica email sono flussi reali; validazione invito e registrazione CLIENT restano placeholder finché form e chiamate API di dominio non saranno completati.

## 5. Ruoli e funzionalità

La home rappresenta tre esperienze:

- **personal trainer**: profilo professionale, inviti, clienti collegati, pubblicazione delle disponibilità e gestione delle richieste di prenotazione;
- **nutrizionista**: profilo professionale, inviti e clienti collegati; strumenti specifici per la nutrizione ancora futuri;
- **cliente**: ingresso tramite invito, professionisti collegati e, nel rapporto con un personal trainer, consultazione delle disponibilità e invio di richieste di prenotazione.

Profili, account, inviti e collegamenti costituiscono la base comune. Availability e Booking tramite slot riguardano soltanto il personal trainer e il cliente collegato. Il nutrizionista non dispone nell'MVP di Availability, Booking o di un modulo Nutrition operativo.

Le aree future presentate nella home riguardano:

- allenamento e nutrizione;
- progressi e misurazioni;
- feedback e strumenti di supporto al percorso.

I moduli `FutureFeature` non contengono link o pulsanti. Il badge “In arrivo” è testuale e non affida lo stato al solo colore. Il contratto completo delle funzioni reali e future resta nei documenti funzionali citati nella sezione 1.

## 6. Architettura frontend

### HomePage

[`HomePage.tsx`](../../frontend/src/pages/public/HomePage.tsx) compone le macro-sezioni nell'ordine approvato. Il componente contiene la struttura semantica e collega dati, route e componenti specializzati, senza incorporare l'intero copy.

### homeContent.ts

[`homeContent.ts`](../../frontend/src/pages/public/homeContent.ts) centralizza testi, elenchi, FAQ, CTA e gruppi del footer. Questa separazione rende il contenuto aggiornabile senza modificare la composizione JSX della pagina.

### PublicLayout

[`PublicLayout.tsx`](../../frontend/src/layouts/public/PublicLayout.tsx) fornisce skip link, header, branding, navigazione pubblica, `main` e `Outlet`. `HomeFooter` viene aggiunto soltanto quando il pathname è `/`.

### FaqSection

[`FaqSection.tsx`](../../frontend/src/components/faq/FaqSection.tsx) implementa l'accordion accessibile, mantiene aperta al massimo una risposta e usa i dati ricevuti da `homeContent.ts`.

### HomeFooter

[`HomeFooter.tsx`](../../frontend/src/components/home/HomeFooter.tsx) gestisce branding, navigazioni, stato del progetto, voci legali, supporto futuro e anno corrente. I collegamenti sono divisi esplicitamente tra `footerAccessLinks` e `footerExploreLinks`.

### FutureFeature

[`FutureFeature.tsx`](../../frontend/src/components/future/FutureFeature.tsx) espone una union presentazionale chiusa: `default` e `home`. La variante predefinita conserva l'aspetto legacy usato nelle dashboard; la variante `home` aggiunge esclusivamente la presentazione dark-tech pubblica.

### Branding e CSS Modules

[`Branding.tsx`](../../frontend/src/components/branding/Branding.tsx) è condiviso. Header e footer pubblici lo personalizzano tramite custom property. La shell autenticata può ancora usare fallback legacy per chrome e navigazione. Ogni area usa CSS Modules per limitare lo scope dei selettori; i token globali restano in [`global.css`](../../frontend/src/styles/global.css).

Il linguaggio dark-tech non è più confinato esclusivamente alle superfici pubbliche: Login e ProfilePage lo applicano localmente. Non è ancora il tema globale dell’intera area autenticata (vedi §8.1).

## 7. Gestione del contenuto

Il contenuto della home è centralizzato in [`homeContent.ts`](../../frontend/src/pages/public/homeContent.ts). Il file esporta gruppi coerenti con le sezioni della pagina e usa due tipi principali:

- `TextItem`, per coppie titolo-descrizione;
- `FaqItem`, per domanda, risposta ed eventuale collegamento contestuale.

La composizione JSX legge questi dati e decide soltanto struttura, semantica e collegamenti. Per aggiornare testi o voci si interviene prima su `homeContent.ts`; modifiche a destinazioni e comportamento devono essere confrontate anche con `AppRoutes.tsx` e con i test della home.

Il documento descrive organizzazione e responsabilità, ma non replica integralmente il copy per evitare una seconda fonte destinata a divergere.

## 8. Sistema visuale

“Dark-tech” è il nome interno della direzione visuale approvata per la home pubblica e ripresa sulle superfici autenticate già allineate. La composizione usa canvas scuri, pannelli glass, griglie di tipo bento (home), bordi sottili, indicatori tecnici e glow controllati. Questi elementi supportano la gerarchia senza sostituire testo, heading o label.

La palette approvata è definitiva e assegna ruoli distinti:

- **Deep Indigo** fornisce identità e profondità;
- **Teal** identifica accenti operativi, azioni, stati e focus;
- **Violet** introduce l'accento distintivo e atmosferico.

I colori di base e i token semantici per canvas, pannelli, testo, heading, superfici, bordi, glow, CTA, link, focus, callout e footer sono definiti in [`global.css`](../../frontend/src/styles/global.css). Il file resta la fonte dei valori aggiornati; questo documento non replica l'intero elenco degli HEX.

Il focus globale delle aree chiare usa il valore legacy. `PublicLayout` sovrascrive per ereditarietà la custom property del focus con il Pale Teal adatto alle superfici scure, senza duplicare la regola globale `:focus-visible`. Sulle superfici dark-tech (home, login, Profile) il focus resta ad alto contrasto e visibile.

`FutureFeature` e `Branding` conservano il comportamento legacy come default e ricevono la personalizzazione dark-tech nel contesto pubblico (e dove la pagina autenticata applica i token direttamente).

### 8.1 Estensione del linguaggio visuale

Alla baseline corrente:

| Superficie                 | Linguaggio visuale                                                                                                                      |
| -------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| Home pubblica `/`          | Dark-tech (canvas, glass, Indigo/Violet/Teal, glow moderato)                                                                            |
| LoginPage `/login`         | Coerente con lo stesso linguaggio (pannelli glass, accenti palette)                                                                     |
| ProfilePage autenticata    | Allineata al dark-tech: superfici glass/panel, accenti Indigo/Violet/Teal, glow moderato, tipografia e gerarchia coerenti, mobile-first |
| Shell / layout autenticato | Può conservare parti del linguaggio precedente (chrome, nav, sfondo di layout)                                                          |
| Altre pagine business      | Ancora placeholder o legacy; non ridisegnate                                                                                            |

**Non** risulta ridisegnata l’intera area autenticata: l’allineamento dark-tech riguarda le superfici implementate citate sopra, non un redesign globale delle dashboard o delle altre route private.

## 9. Tipografia e font

La home (e le superfici dark-tech allineate) usa:

- **Bruno Ace SC** per H1, H2 e numerazioni display, con moderazione;
- **Saira Condensed** come font prevalente per corpo, interfaccia, H3/H4, navigazione, CTA, FAQ, badge, micro-label, branding e footer.

Su ProfilePage i titoli di sezione usano il token display; corpo, form e controlli restano sul body/UI. Gli asset vengono distribuiti localmente tramite Fontsource. [`main.tsx`](../../frontend/src/main.tsx) importa Bruno Ace SC 400 e Saira Condensed nei pesi 400, 600, 700 e 800. I CSS forniti dai pacchetti dichiarano `font-display: swap`; non vengono richieste risorse font remote.

I token tipografici includono fallback `system-ui` e famiglie sans-serif. `font-synthesis: none` impedisce al browser di simulare stili non caricati.

## 10. Responsive mobile-first

Gli stili di base sono mobile-first. La pagina usa dimensioni fluide, `minmax(0, 1fr)`, wrapping e contenitori limitati per ridurre il rischio di overflow.

- sotto `23.5rem` il diagramma della hero viene adattato per viewport particolarmente strette;
- da `48rem` header, hero, griglie, confronto, percorsi, CTA conclusiva e footer acquistano composizioni a più colonne;
- da `64rem` alcune aree passano a griglie più articolate, inclusa la bento grid a dodici colonne.

Su mobile l'header mantiene branding e destinazioni pubbliche accessibili senza introdurre un menu simulato. La hero dispone testo, diagramma e azioni secondo lo spazio disponibile. Card e bento ritornano a una colonna quando necessario. FAQ, chiusura e footer mantengono target e reflow senza dipendere da larghezze desktop fisse.

La verifica manuale della home è stata svolta a 320, 390, 768 e 1440 CSS pixel, senza overflow orizzontale. Il controllo a larghezza ridotta copre anche il reflow equivalente allo zoom del 200% su una viewport desktop compatibile. Questo non costituisce una matrice ufficiale di browser o dispositivi.

ProfilePage è stata verificata a livello visuale/E2E su viewport mobile circa 375px e desktop: nessun overflow orizzontale osservato, CTA raggiungibili, layout leggibile. Non è una certificazione completa multi-device.

## 11. Accessibilità

L'obiettivo progettuale è WCAG 2.2 livello AA. Non viene dichiarata una certificazione: le verifiche tecniche e manuali descritte qui non sostituiscono un audit formale completo.

Le tecniche implementate comprendono:

- landmark `header`, `nav`, `main`, `section` e `footer`;
- skip link come primo collegamento della pagina;
- un solo H1 nella home e heading gerarchici per sezioni e card;
- focus chiaramente visibile e differenziato tra superfici pubbliche scure e superfici applicative chiare;
- contrasto del focus verificato oltre il minimo 3:1 nelle combinazioni controllate;
- target interattivi con altezza minima di 2.75 rem, salvo controlli nativi racchiusi in label cliccabili equivalenti;
- FAQ basata su button nativi e relazioni ARIA;
- decorazioni, numerazioni e indicatori tecnici marcati `aria-hidden` quando non aggiungono informazione;
- stati “Disponibile” e “In arrivo” espressi anche testualmente;
- regole `prefers-reduced-motion` nei componenti animati;
- navigazione e apertura delle FAQ da tastiera.

I controlli automatici coprono struttura e comportamento, mentre contrasto, reflow, resa visuale, zoom, screen reader e combinazioni browser-tecnologie assistive richiedono anche verifiche manuali. La conformità WCAG formale rimane una decisione futura.

ProfilePage mantiene le stesse priorità di accessibilità sulle superfici dark-tech: label reali, focus-visible, feedback testuale, informazioni non solo colore, avatar con fallback accessibile, badge di stato con testo, touch target adeguati, `prefers-reduced-motion` e layout responsive mobile-first. Non sostituisce un report WCAG dedicato.

## 12. FAQ

La sezione FAQ è un accordion a apertura singola. Ogni domanda è un `button` nativo con:

- `aria-expanded` coerente con lo stato;
- `aria-controls` verso l'identificatore della risposta;
- risposta resa come `region` con `aria-labelledby` verso la domanda;
- attivazione nativa con Invio e Spazio;
- possibilità di richiudere l'elemento aperto.

Il collegamento “Accedi” viene reso soltanto nella risposta che lo prevede. Domande, risposte e link sono definiti in `homeContent.ts`, mentre il componente conserva esclusivamente stato e semantica.

## 13. Footer

Il footer è presente soltanto nella home, dopo il `main`. Comprende:

- branding collegato a `/`;
- gruppo `footerAccessLinks` per login, registrazione professionista e validazione invito;
- gruppo `footerExploreLinks` per le ancore della home;
- stato del progetto;
- voci legali non interattive;
- Instagram e segnalazione di problemi o idee marcati “In arrivo” e non interattivi;
- barra inferiore con stato MVP, moduli in sviluppo e informazioni legali in preparazione;
- anno calcolato dinamicamente.

Privacy, Cookie, Termini e Accessibilità sono in preparazione e non vengono presentati come collegamenti attivi.

## 14. Test

I test collegati alla home hanno responsabilità distinte:

- [`HomePage.test.tsx`](../../frontend/src/pages/public/HomePage.test.tsx) verifica ordine delle macro-aree, unico H1, ancore, CTA, destinazioni approvate, separazione del nutrizionista dai booking e non interattività delle funzioni future;
- [`PublicLayout.test.tsx`](../../frontend/src/layouts/public/PublicLayout.test.tsx) verifica skip link, landmark, navigazione, `Outlet` e presenza del footer soltanto sulla home;
- [`FaqSection.test.tsx`](../../frontend/src/components/faq/FaqSection.test.tsx) verifica stato iniziale, apertura singola, relazioni ARIA, tastiera e link contestuale;
- [`HomeFooter.test.tsx`](../../frontend/src/components/home/HomeFooter.test.tsx) verifica gruppi dei link, elementi non interattivi e anno dinamico;
- [`FutureFeature.test.tsx`](../../frontend/src/components/future/FutureFeature.test.tsx) verifica variante predefinita, variante `home` e assenza di link o pulsanti.

Fotografia verificata il 22 luglio 2026: 9 file di test e 29 test superati. Il conteggio descrive lo stato corrente e non è una garanzia permanente.

I test automatici non certificano da soli responsive, contrasto o resa visuale. Questi aspetti richiedono controlli manuali proporzionati alle modifiche.

## 15. Dipendenze

Le sole dipendenze aggiunte specificamente per la tipografia della home sono:

- `@fontsource/bruno-ace-sc`;
- `@fontsource/saira-condensed`.

Versioni e risoluzione restano governate da `package.json` e `package-lock.json` e non vengono duplicate in questo documento.

## 16. Limiti e decisioni rimandate

Non sono ancora implementati o definiti in questo perimetro home:

- validazione invito e registrazione CLIENT (route pubbliche ancora placeholder);
- contenuti applicativi delle altre pagine business private (dashboard, clients, professionals, availability, bookings), ancora placeholder o legacy;
- redesign globale della shell autenticata;
- Workout, Nutrition, Feedback e Measurements;
- pagine Privacy, Cookie, Termini e Accessibilità;
- collegamenti operativi per Instagram e segnalazione di problemi o idee;
- analytics e monitoring;
- deploy e URL pubblico definitivo;
- matrice ufficiale di browser e dispositivi;
- audit formale WCAG.

API client e autenticazione session-based frontend sono implementati fuori dal perimetro home ([FE03](./03-authentication-session-flow.md)). Login non è più un gap strutturale della fondazione. Profilo/Account/Operational Status è documentato funzionalmente in [FE01](./01-frontend-functional-map-mvp.md); questo documento ne registra solo l’allineamento visuale dark-tech (§8.1).

Non sono definite date o priorità pubbliche per i moduli “In arrivo”.
