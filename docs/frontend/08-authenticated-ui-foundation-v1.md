# Authenticated UI Foundation V1

Documento normativo della foundation visiva e strutturale dell’area autenticata. Descrive decisioni e contratti; non sostituisce il CSS come source of truth implementativa.

## 1. Purpose

Authenticated UI Foundation V1 fornisce un linguaggio visivo e un insieme di primitive riutilizzabili per l’area privata:

- shell dark-tech (sidebar desktop, topbar e drawer mobile);
- token meccanici e namespace `--st-auth-*`;
- primitive UI (`Button`, `Card`, `PageHeader`, `ActionLink`);
- Dashboard come **reference integration** statica.

La Dashboard resta la reference UI. Availability PT, Booking condiviso CLIENT/PT, Client Professional Availability e Invites PT/NUT consumano già la stessa foundation. La foundation non introduce dati aggregati, API nuove, StatusBadge, Skeleton o un design system di form.

## 2. Visual identity

L’area autenticata appartiene allo stesso prodotto di Home e Login, che restano la source of truth visiva.

Principi:

- palette indigo / teal / violet;
- superfici scure profonde, border sottili, glow controllato;
- Energy Line come firma discreta, non decorazione continua;
- interfaccia applicativa premium, non landing e non “cyberpunk demo”.

Non copiare nella privata diagrammi, network animation o motion ambientale della Home.

## 3. Typography

| Font | Ruolo |
| --- | --- |
| Bruno Ace SC (`--font-display`) | Titoli di pagina autenticati e identità di sezione. |
| Saira Condensed (`--font-body`) | Interfaccia, copy, navigation, CTA. |

I pesi e i tracking usano i token meccanici (`--font-weight-*`, `--tracking-*`). Non introdurre font aggiuntivi per la privata.

## 4. Token architecture

### Token globali meccanici

Definiti in `frontend/src/styles/global.css`. Descrivono misure e motori condivisi (control size, radius, durata motion, Energy Line, ombre), non un tema.

### Namespace `--st-auth-*`

Lo shell autenticato e le pagine con `appearance="authenticated"` mappano i token di brand su un namespace locale `--st-auth-*`. Le primitive UI leggono prima `--st-auth-*` e fanno fallback ai token di brand.

I token `--st-auth-*` sono dichiarati in due scope CSS Module, da tenere allineati:

- `AuthenticatedLayout.module.css` su `.layout` (vale per tutto lo shell autenticato);
- `PageTemplate.module.css` su `.authenticated` (vale per le pagine opt-in, anche nei test senza layout).

Oltre a canvas, testo, bordo, focus e danger, lo scope include una semantica warning autenticata:

| Token | Ruolo |
| --- | --- |
| `--st-auth-warning` | Accento/testo warning su dark |
| `--st-auth-warning-border` | Bordo warning |
| `--st-auth-warning-background` | Superficie warning |

Availability (feedback warning) e Invites (feedback warning, stato Scaduto) consumano questi token. I valori non sono duplicati nei CSS di feature. Non esistono nuovi token generici `--color-*` e i valori legacy light (`--color-warning`, `--color-warning-surface`) restano invariati.

### Perché i legacy `--color-*` non vengono ridefiniti

Le pagine private non ancora migrate e alcuni componenti condivisi (incluso `FutureFeature` default) usano ancora i token legacy chiari (`--color-primary`, `--color-background`, ecc.). Ridefinirli globalmente romperebbe Home/Login e le feature legacy. La foundation convive con quel layer; la migrazione è pagina per pagina.

### CSS Modules

Stili scoped per layout, primitive e pagine. Nessuna dipendenza CSS-in-JS.

## 5. UI primitives

### Button

- Responsabilità: azione in-page su `<button>`.
- Variant: `primary`, `secondary`, `ghost`, `danger`.
- Limiti: non è polymorphic; non wrappa un `Link`; non sostituisce la navigazione.

### Card

- Responsabilità: superficie presentational.
- Variant: `static`, `interactive`, `highlighted`.
- Limiti: non è clickable da sola. `interactive` richiede un controllo reale al suo interno (Link, Button, CTA). `highlighted` può portare un’Energy Line statica.

### PageHeader

- Responsabilità: identità di pagina (eyebrow, title, description).
- Appearance: `legacy` (default di `PageTemplate`) e `authenticated` (Energy Line, font display, colori `--st-auth-*`).
- Limiti: non contiene azioni; le CTA stanno nel contenuto.

### ActionLink

- Responsabilità: navigazione con aspetto CTA, basata su React Router `Link`.
- Variant: `primary`, `secondary`.
- Limiti: semantica da link, non da button; non è un `Button` con `as`. Condivide il linguaggio visivo di Button tramite token, non tramite un’astrazione unica.

### PageTemplate

- Compone `PageHeader` e lo slot contenuto.
- Default `appearance="legacy"`: le pagine non migrate restano invariate.
- `appearance="authenticated"` è opt-in. Consumer attuali: Dashboard (reference), Availability PT, Booking CLIENT/PT (lista e dettaglio), Client Professional Availability, Invites PT/NUT.

## 6. Layout

`AuthenticatedLayout` è lo shell delle route private.

| Superficie | Comportamento |
| --- | --- |
| Mobile topbar | Sticky, branding + trigger drawer. Blur su strato visuale, non sull’ancestor del modal. |
| Desktop sidebar | Branding, navigation role-aware, profilo e logout. |
| Main | Contenuto pagina, skip-link verso `#main-content`. |
| Breakpoint strutturale | `48rem`. |

Sotto `48rem` la sidebar è nascosta e vale il drawer. Da `48rem` in su vale la sidebar; il drawer non è modalmente attivo.

Altezza topbar mobile: `--st-auth-mobile-header-height` (`4rem`). Il main e lo shell usano `100vh` come fallback e `100dvh` come override progressivo.

### Canvas root autenticato

`:root` conserva il background legacy light (`--color-background`). Lo shell dipinge il canvas dark solo internamente; la scrollbar o il gutter del viewport può quindi esporre il root chiaro.

Per coprire html/body senza tematizzare le pagine pubbliche, `AuthenticatedLayout` applica `data-st-authenticated` su `document.documentElement` al mount e lo rimuove al cleanup (StrictMode-safe: add → remove → add, nessun residuo dopo unmount). `global.css` tematizza **solo** quello stato:

```css
html[data-st-authenticated] {
  background: var(--color-canvas);
  color-scheme: dark;
}
```

`--color-background` globale non viene modificato. Home, Login, Register, Verify email e `PublicLayout` non ricevono l’attributo; all’uscita dall’area autenticata il canvas pubblico torna quello originale.

## 7. Navigation contract

La navigation è derivata da `getNavigationItems(profile)`.

### CLIENT

- Dashboard
- Professionisti
- Prenotazioni
- Profilo

### PERSONAL_TRAINER

- Dashboard
- Clienti
- Disponibilità
- Prenotazioni
- Inviti
- Profilo

### NUTRITIONIST

- Dashboard
- Clienti
- Inviti
- Profilo

NUTRITIONIST non riceve Availability né Booking.

## 8. Mobile drawer

Implementazione custom React + CSS Modules. Nessun `<dialog>` nativo e nessuna libreria esterna.

Contratto:

- trigger `aria-expanded` / `aria-controls`;
- layer `role="dialog"` con `aria-modal` solo mentre il drawer è modalmente attivo;
- overlay click, Escape e NavLink chiudono il drawer;
- focus iniziale sul close; focus trap su `keydown` Tab; restore sul trigger per close utente;
- body scroll lock con restore del valore precedente;
- `isModalActive = isOpen && !isDesktop`;
- sync breakpoint con `window.matchMedia('(min-width: 48rem)')`;
- passaggio a desktop: chiusura immediata, niente restore focus sul trigger nascosto;
- ritorno a mobile: il drawer resta chiuso.

Il layer è `position: fixed; inset: 0` a livello viewport. `backdrop-filter` non sta sull’ancestor del layer.

## 9. Accessibility

- HTML semantico (landmark, heading, link/button nativi);
- `focus-visible` visibile, non solo colore;
- tastiera: Tab, Shift+Tab, Escape;
- reduced motion senza perdita di gerarchia o feedback;
- target touch da `--control-min-size` (2.75rem);
- stati “In arrivo” comunicati da copy/badge, non solo dal colore;
- nessuna Card o `div` finta-clickable.

## 10. Motion

- durata di riferimento: `--motion-duration-short` (~160ms);
- micro-translate 1–2px su hover desktop;
- Energy Line statica;
- niente loop, bounce, stagger aggressivo, zoom o parallax;
- `@media (prefers-reduced-motion: reduce)` toglie transition/transform non essenziali.

## 11. Dashboard reference implementation

`DashboardPage` è il primo consumer completo della foundation:

- `PageTemplate appearance="authenticated"`;
- card operative role-aware con `ActionLink`;
- moduli futuri tramite `FutureFeature` (file protetto; la Dashboard adatta solo il contenitore).

Resta **static/reference**: stessi link e sezioni di prima, nessuna API, nessun KPI, nessun dato inventato. La Dashboard dati reale è un lavoro successivo (Role-aware Dashboard MVP).

## 12. Primitive vs feature components

| Primitive foundation | Feature / domain |
| --- | --- |
| Button, Card, PageHeader, ActionLink, PageTemplate | Booking, Availability, Invites, Clients, Professionals, Profile, FutureFeature |
| AuthenticatedLayout, navigation, drawer | Pagine di dominio e form operativi |

Le primitive non conoscono il dominio. I feature component non devono duplicare Button/Card se già coprono il caso. I controlli form nativi (`select`, `input`, `textarea`, checkbox) restano feature-local, con `color-scheme: dark` dove serve su canvas autenticato. Non è stata introdotta una `StatusBadge` condivisa: gli stati (booking, inviti) restano markup locale.

## 13. Migration strategy

Le pagine private non ancora migrate restano navigabili nello shell dark. Non vanno uniformate in un mega-restyling.

Superfici già migrate a `appearance="authenticated"`:

- Dashboard (reference statica);
- Availability PT (`/app/professional/availability`);
- Booking condiviso CLIENT/PT (liste e dettaglio);
- Client Professional Availability (`/app/client/professionals/:id/availability`);
- Invites PT/NUT (`/app/professional/invites`).

Migrazione visiva restante, deferred:

- Clients / Professionals (liste e dettaglio);
- Profile / Account.

Ogni migrazione deve restare backward-compatible sul contract funzionale e usare `appearance="authenticated"` solo quando la pagina è pronta.

## 14. Known follow-ups

- **L2-03** — il selettore focusable del drawer è permissivo (`[tabindex]:not([tabindex="-1"])` e analoghi). Non è stata introdotta una focus-management utility. Follow-up se un contenuto reale nel drawer espone il problema.
- Feature private visivamente legacy nello shell dark (Clients, Professionals, Profile): atteso, non un bug della foundation.
- Dashboard dati, analytics, Workout/Nutrition operativi: fuori da questa foundation.

## 15. Out of scope

- Dashboard data / KPI / activity feed
- analytics e notification center
- Workout / Nutrition operativi
- migrazione visiva restante (Clients / Professionals, Profile e altre private ancora legacy), form design system, StatusBadge e feature future fuori da questo slice. Availability PT, Booking CLIENT/PT, Client Professional Availability e Invites PT/NUT sono già migrate (sezione 13) e non sono out of scope
- backend / domain / API nuove
- light theme autenticato
- dipendenze UI esterne per dialog/drawer
