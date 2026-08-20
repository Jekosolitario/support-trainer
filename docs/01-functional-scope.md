# Functional Scope — Support Trainer

> Aggiornamento corrente: il Booking Workflow V1 Client ↔ Personal Trainer è implementato end-to-end. Il contratto normativo completo è in [19-booking-domain-contract-v1.md](19-booking-domain-contract-v1.md); eventuali indicazioni successive che descrivono Booking frontend o motivazioni come futuri sono superate.

## 1. Obiettivo del documento

Questo documento definisce:

- i tipi di utente del sistema;
- cosa può fare ogni utente;
- le regole funzionali principali;
- le funzionalità già implementate;
- le funzionalità ancora pianificate per completare la prima versione.

La verifica tecnica degli endpoint realmente presenti resta demandata a:

- `08-endpoint-map.md`

---

## 2. Tipi di utente

### 2.1 Ruoli di accesso implementati

Il sistema prevede i seguenti ruoli:

- `CLIENT`
- `PROFESSIONAL`

### 2.2 Specializzazioni del professionista implementate

Un utente con ruolo `PROFESSIONAL` può avere una delle seguenti specializzazioni:

- `PERSONAL_TRAINER`
- `NUTRITIONIST`

### 2.3 Nota progettuale

La separazione tra ruolo e specializzazione consente di:

- mantenere una gestione comune di autenticazione e account;
- applicare regole business diverse in base al tipo di professionista;
- estendere il progetto senza duplicare l’intero modello utenti.

---

## 3. Stato funzionale attuale del backend

### 3.1 Funzionalità implementate

Nel backend reale risultano implementate:

- registrazione professionista;
- verifica email uniforme per professionista e cliente;
- login session-based (Spring Session JDBC + CSRF);
- registrazione cliente tramite codice invito;
- validazione preventiva del codice invito;
- creazione automatica del collegamento professionista-cliente dopo registrazione cliente valida;
- lettura e aggiornamento del proprio profilo/account;
- aggiornamento stato operativo utente;
- lettura clienti collegati lato professionista;
- lettura professionisti collegati lato cliente;
- generazione e lettura codici invito lato professionista;
- gestione disponibilità del personal trainer;
- lettura availability lato cliente collegato;
- creazione e gestione richieste booking;
- conferma, rifiuto e cancellazione booking.

### 3.2 Funzionalità pianificate ma non ancora implementate

Non risultano ancora implementate:

- schede di allenamento;
- piani alimentari;
- feedback o segnalazioni sui contenuti;
- misurazioni e storico progressi;
- recupero e reset password;
- upload immagine profilo;
- editing account (email, password, cancellazione) e gestione dispositivi/sessioni;
- dashboard frontend con dati ancora placeholder; Availability e Booking Client ↔ Personal Trainer sono flussi operativi;
- preparazione completa al deploy.

---

## 4. Regole di registrazione

### 4.1 Registrazione professionista — Implementata

Il professionista può registrarsi liberamente al sistema selezionando la propria specializzazione.

Dopo la registrazione deve confermare il proprio indirizzo email tramite il POST pubblico dedicato.

Fino alla verifica email, il professionista non può utilizzare le funzionalità operative protette, tra cui:

- generare codici invito;
- gestire disponibilità;
- utilizzare i flussi professionali che richiedono account verificato.

### 4.2 Registrazione cliente — Implementata

Il cliente non può registrarsi liberamente.

Può completare la registrazione solo se possiede un codice invito:

- esistente;
- attivo;
- non utilizzato;
- non scaduto.

La validazione pubblica richiede inoltre che il professionista proprietario abbia profilo attivo, email verificata e account `ACTIVE`. Il backend ripete i controlli autoritativi durante la registrazione.

Nel ramo di una nuova registrazione, il backend crea atomicamente cliente pending, token email, link attivo e consumo dell'invito. Fino alla conferma il nuovo cliente resta `PENDING_VERIFICATION`, con `emailVerified=false`, non può fare login e non è leggibile dal professionista collegato. Dopo il POST di conferma passa a `ACTIVE` ed `emailVerified=true`.

Il contratto pubblico mantiene l'anti-enumeration: `202 Accepted` è una risposta neutra e non dimostra che il ramo di nuova registrazione o alcuno dei suoi effetti persistenti siano avvenuti. Se l'email esiste già, il backend può terminare anticipatamente senza creare account, link o token e senza consumare l'invito; il client non può distinguere questo caso dal solo `202`.

Professionista e cliente possono richiedere il reinvio con `POST /api/v1/auth/email-verification/resend`. Il backend restituisce sempre lo stesso `202 Accepted` per email sintatticamente valide, senza rivelare esistenza o stato dell'account. Solo un profilo attivo, pending e non verificato riceve logicamente un nuovo token; il cooldown è 60 secondi dal token più recente e termina al boundary esatto. Il reinvio invalida tramite `used/usedAt` tutti i precedenti token non usati, lascia un solo token utilizzabile da 24 ore e non modifica invito o link. Registrazioni e reinvii idonei pubblicano nella propria transazione una richiesta immutabile, consegnata sincronicamente soltanto `AFTER_COMMIT`; rollback o pubblicazioni senza transazione non inviano nulla. Il link usa il fragment `#token=...`. Il sender locale predefinito è disabilitato, quello di test è in-memory senza rete e la modalità `SMTP` invia email testuali UTF-8 con mittente configurato. Un fallimento SMTP è assorbito dopo il commit e non cambia le risposte `202`; garanzia durevole tramite outbox e retry restano assenti.

### 4.3 Collegamento cliente-professionista — Implementato

Il collegamento tra cliente e professionista viene creato automaticamente durante la registrazione cliente completata con successo tramite invito valido. L'invito è già consumato, ma il link non rende leggibile il cliente finché l'account è pending.

Nel backend attuale la relazione è gestita tramite:

- `ProfessionalClientLink`

Non esiste ancora un modulo API autonomo dedicato alla gestione manuale dei collegamenti.

### 4.4 Regole di sicurezza sul collegamento — Implementate

Il sistema impedisce:

- collegamenti attivi duplicati tra la stessa coppia cliente-professionista;
- collegamento di un utente a sé stesso;
- superamento del limite massimo di professionisti attivi collegati a un cliente.

---

## 5. Relazioni tra utenti

### 5.1 Limite professionisti per cliente

Un cliente può essere collegato fino a:

- `3` professionisti attivi.

### 5.2 Tipologie di professionista

I professionisti collegabili possono essere:

- personal trainer;
- nutrizionisti.

### 5.3 Utilizzo attuale della relazione

La relazione cliente-professionista è già utilizzata per:

- lettura clienti lato professionista;
- lettura professionisti lato cliente;
- lettura disponibilità del personal trainer;
- creazione booking.

### 5.4 Utilizzo futuro della relazione

Quando i relativi moduli saranno implementati, la relazione servirà anche per:

- assegnazione schede workout;
- assegnazione piani nutrizionali;
- accesso a feedback;
- accesso a misurazioni e progressi.

---

## 6. Ambito funzionale del personal trainer

### 6.1 Funzionalità implementate

Il personal trainer può:

- registrarsi al sistema;
- verificare il proprio account tramite email;
- accedere al sistema;
- generare codici invito;
- visualizzare i clienti collegati;
- visualizzare il dettaglio di un cliente collegato;
- aggiornare il proprio profilo;
- aggiornare il proprio stato operativo;
- creare slot di disponibilità;
- leggere i propri slot;
- modificare slot disponibili;
- bloccare e sbloccare slot;
- ricevere richieste booking;
- leggere il dettaglio delle richieste ricevute;
- confermare o rifiutare richieste pending;
- cancellare richieste confermate nei casi consentiti.

### 6.2 Funzionalità pianificate

Il personal trainer non può ancora:

- creare schede di allenamento;
- assegnare schede ai clienti;
- ricevere feedback sulle schede;
- gestire progressi o misurazioni del cliente.

---

## 7. Ambito funzionale del nutrizionista

### 7.1 Funzionalità implementate

Il nutrizionista, in quanto professionista, può:

- registrarsi al sistema;
- verificare il proprio account tramite email;
- accedere al sistema;
- generare codici invito;
- visualizzare i clienti collegati;
- visualizzare il dettaglio di un cliente collegato;
- aggiornare il proprio profilo;
- aggiornare il proprio stato operativo.

### 7.2 Funzionalità pianificate

Il nutrizionista non può ancora:

- creare piani alimentari;
- assegnare piani alimentari ai clienti;
- ricevere feedback sui piani.

### 7.3 Prenotazioni

Nell’ambito attuale del progetto, le prenotazioni tramite app riguardano solo il personal trainer.

Il nutrizionista non gestisce slot availability o booking tramite app.

---

## 8. Ambito funzionale del cliente

### 8.1 Funzionalità implementate

Il cliente può:

- registrarsi tramite codice invito valido;
- accedere al sistema;
- leggere e aggiornare il proprio profilo;
- aggiornare il proprio stato operativo;
- visualizzare i professionisti collegati;
- visualizzare il dettaglio di un professionista collegato;
- visualizzare le sole combinazioni future restituite in `bookableOptions` per un personal trainer collegato;
- creare una richiesta booking su uno slot disponibile;
- visualizzare le proprie richieste booking;
- visualizzare il dettaglio delle proprie richieste;
- cancellare richieste booking negli stati consentiti.

### 8.2 Funzionalità pianificate

Il cliente non può ancora:

- visualizzare schede di allenamento;
- visualizzare piani alimentari;
- inviare feedback o segnalazioni sui contenuti;
- inserire o consultare misurazioni fisiche.

---

## 9. Regole Availability — Implementate

### 9.1 Ambito

Gli slot availability riguardano solo professionisti con specializzazione:

- `PERSONAL_TRAINER`

### 9.2 Gestione lato professionista

Il personal trainer può gestire solo i propri slot.

Può:

- creare e gestire regole settimanali e relative occurrence;
- leggere i propri slot materializzati;
- ripianificare soltanto slot manuali legacy non bloccati e mai coinvolti in Booking;
- bloccare occurrence future non già bloccate, con reason quando esistono Booking impattati;
- sbloccare slot `BLOCKED`.

### 9.3 Regole temporali

Uno slot:

- deve avere un intervallo temporale valido;
- deve iniziare nel futuro in creazione o aggiornamento;
- non può sovrapporsi ad altri slot attivi dello stesso professionista.

### 9.4 Lettura lato cliente

Il cliente può visualizzare soltanto slot:

- appartenenti a un personal trainer collegato;
- attivi;
- non bloccati e non scaduti;
- con capacità residua per almeno una combinazione di inizio e durata.

Lo stato `AVAILABLE`/`BOOKED` è legacy e non rappresenta l'occupancy corrente. `PENDING` e `CONFIRMED` occupano capacità; la response cliente espone soltanto le combinazioni ancora prenotabili.

### 9.5 Stati slot implementati

- `AVAILABLE`
- `BLOCKED`
- `BOOKED`

---

## 10. Regole Bookings — Implementate

### 10.1 Ambito

Le prenotazioni tramite app riguardano solo slot availability del personal trainer.

### 10.2 Contratto attuale

Nel backend attuale una richiesta booking viene creata a partire da un singolo `availabilitySlotId`/`occurrenceId`, `startDateTime` e `durationMinutes` scelti fra le `bookableOptions` server-side.

Il modello dati mantiene la possibilità di una futura evoluzione multi-slot, ma questa non è parte delle API attuali.

### 10.3 Flusso implementato

Il flusso operativo è:

1. il personal trainer crea uno slot availability futuro;
2. il cliente collegato visualizza lo slot disponibile;
3. il cliente invia una richiesta booking;
4. la richiesta nasce in stato `PENDING`;
5. `PENDING` occupa capacità per il proprio intervallo, come `CONFIRMED`;
6. il personal trainer può confermare o rifiutare;
7. cliente o professionista possono cancellare secondo le regole previste.

### 10.4 Regole di prenotabilità

Il cliente può creare una richiesta solo se:

- è collegato al professionista proprietario dello slot;
- lo slot esiste;
- lo slot è attivo;
- lo slot è un'occurrence generata da regola settimanale, non bloccata e non scaduta;
- inizio e durata appartengono a `bookableOptions` e la capacità residua copre l'intero intervallo;
- il Client non ha un altro Booking `PENDING` o `CONFIRMED` sovrapposto con lo stesso professionista.

Quando una richiesta `PENDING` viene creata correttamente, occupa un posto nell'intervallo selezionato. La stessa occurrence può continuare a esporre altre combinazioni se hanno capacità residua.

### 10.5 Nota del booking

La richiesta può contenere una nota facoltativa.

La nota:

- viene normalizzata rimuovendo spazi iniziali e finali;
- viene trattata come assente se vuota dopo la normalizzazione;
- non può superare `1000` caratteri.

### 10.6 Stati booking implementati

- `PENDING`
- `CONFIRMED`
- `REJECTED`
- `CANCELLED`

### 10.7 Transizioni implementate

| Azione        | Attore autorizzato       | Stato iniziale | Stato finale | Effetto capacità                                      |
| ------------- | ------------------------ | -------------- | ------------ | ----------------------------------------------------- |
| Conferma      | professionista coinvolto | `PENDING`      | `CONFIRMED`  | invariata: entrambi gli stati occupano                |
| Rifiuto       | professionista coinvolto | `PENDING`      | `REJECTED`   | libera la capacità dell'intervallo                    |
| Cancellazione | cliente coinvolto        | `PENDING`      | `CANCELLED`  | libera la capacità; reason facoltativa                |
| Cancellazione | cliente coinvolto        | `CONFIRMED`    | `CANCELLED`  | libera la capacità; reason obbligatoria, actor Client |
| Cancellazione | professionista coinvolto | `CONFIRMED`    | `CANCELLED`  | libera la capacità; reason obbligatoria, actor PT     |

Il rifiuto PT richiede sempre una reason. `cancelledBy` è assegnato server-side come `CLIENT` o `PROFESSIONAL`; reason e actor restano nullable soltanto per lo storico legacy.

### 10.8 Protezioni temporali

Non è consentito:

- creare booking su uno slot ormai scaduto;
- confermare, rifiutare o cancellare quando `now >= MAX(BookingRequestItem.scheduledEnd)`.

Le mutation sono ammesse durante `IN_PROGRESS` quando il lifecycle lo consente. La fine snapshot del Booking è autorevole; lo stato o la fine dello slot live non definiscono questo limite.

---

## 11. Schede workout e piani alimentari — Pianificati, non implementati

### 11.1 Schede di allenamento

Le schede di allenamento saranno create dal personal trainer e assegnate al cliente collegato.

### 11.2 Piani alimentari

I piani alimentari saranno creati dal nutrizionista e assegnati al cliente collegato.

### 11.3 Visualizzazione lato cliente

Il cliente potrà visualizzare i contenuti assegnati e, se previsto, inviare feedback o richieste di modifica.

### 11.4 Stato attuale

Nessuna di queste funzionalità risulta ancora implementata nel backend reale.

---

## 12. Struttura funzionale futura dei contenuti

La struttura di workout plan e nutrition plan resta da confermare durante i relativi sprint.

L’ipotesi attuale prevede contenuti organizzati per:

- piano;
- settimane;
- giorni;
- dettaglio giornaliero.

Questa struttura rappresenta una proposta funzionale futura e non un contratto già implementato.

---

## 13. Stati operativi utente — Implementati

### 13.1 Stati del professionista

Il professionista può assumere uno dei seguenti stati operativi:

- `DISPONIBILE`
- `ASSENTE`
- `FERIE`
- `MALATTIA`

### 13.2 Stati del cliente

Il cliente può assumere uno dei seguenti stati operativi:

- `ATTIVO`
- `INFORTUNATO`
- `PAUSA`

### 13.3 Significato nell’MVP

Lo stato operativo è un’informazione di profilo modificabile in modo indipendente dall’editing degli altri campi profilo.

Nell’MVP corrente **non** implica automaticamente:

- blocco o sblocco di booking;
- modifica automatica degli slot availability;
- revoca dell’accesso applicativo;
- altre policy business non esplicitamente implementate.

---

## 13.A Frontend Profilo / Account / Operational Status — Implementato

### 13.A.1 Area comune autenticata

Esiste una pagina Profilo autenticata role-aware:

- CLIENT: `/app/client/profile`
- PROFESSIONAL: `/app/professional/profile`

La pagina comprende tre sezioni:

1. **Profilo** — editing dei campi consentiti dal contratto backend per il ruolo corrente;
2. **Account** — sola lettura;
3. **Operational Status** — aggiornamento indipendente dal form profilo.

### 13.A.2 CLIENT — implementato

Il cliente autenticato può:

- visualizzare e aggiornare i campi profilo consentiti (anagrafica, obiettivo, note pertinenti);
- consultare l’Account in sola lettura;
- aggiornare lo stato operativo tra `ATTIVO`, `INFORTUNATO`, `PAUSA`.

### 13.A.3 PROFESSIONAL — implementato

Il professionista autenticato può:

- visualizzare e aggiornare i campi profilo consentiti (anagrafica, contatti, bio, luogo di lavoro, città, link);
- consultare la specialization in sola lettura;
- consultare l’Account in sola lettura;
- aggiornare lo stato operativo tra `DISPONIBILE`, `ASSENTE`, `FERIE`, `MALATTIA`.

`PERSONAL_TRAINER` e `NUTRITIONIST` condividono la stessa struttura di pagina Profilo; si differenziano per la specialization e per le altre aree business ancora non integrate nel frontend.

### 13.A.4 Account — sola lettura

La sezione Account mostra soltanto:

- `email`;
- `role`;
- `accountStatus`;
- `emailVerified`;
- `createdAt`.

Non sono implementati:

- editing email;
- editing role;
- editing specialization;
- cambio password;
- cancellazione account;
- upload o cambio avatar;
- gestione sessioni o dispositivi.

### 13.A.5 Follow-up aperti (non bloccanti)

#### E2E-1 — FOLLOW-UP

Dopo il login, un target autenticato ricordato ma incompatibile con il ruolo della nuova sessione può terminare su `/forbidden`.

- preesistente nella baseline auth/routing;
- nessun bypass autorizzativo;
- follow-up separato;
- dettaglio tecnico: [`docs/frontend/03-authentication-session-flow.md`](frontend/03-authentication-session-flow.md).

#### M1-R — FOLLOW-UP

Dopo un aggiornamento riuscito dello stato operativo, eventuali errori di campo del form profilo possono restare visibili.

- UX;
- non bloccante;
- follow-up separato;
- dettaglio frontend/UX: [`docs/frontend/01-frontend-functional-map-mvp.md`](frontend/01-frontend-functional-map-mvp.md).

## 13.B Frontend Validazione invito + Onboarding CLIENT — Implementato

Il vertical slice pubblico CLIENT comprende:

1. inserimento e validazione del codice su `/invite/validate`;
2. conservazione memory-only del solo codice canonico nel subtree onboarding;
3. handoff senza secret nella URL verso `/register/client`;
4. registrazione CLIENT con campi obbligatori e note facoltative;
5. outcome confermato soltanto su `202 Accepted`, failure deterministiche per coppie status/code allowlisted e fallback ambiguo conservativo;
6. cleanup dell'invito e del draft sensibile negli outcome terminali;
7. schermata neutra di controllo email e reinvio con cooldown UX;
8. verifica email sulla route condivisa `/verify-email` e Login separato.

L'accesso diretto o il reload di `/register/client` senza invito nel provider non mostra il form e redirige con `replace` a `/invite/validate`. Il codice non viene conservato in query, fragment, router state, `localStorage` o `sessionStorage`.

Registrazione e verifica email non autenticano l'utente e non creano automaticamente una sessione. La documentazione tecnica completa è in [`docs/frontend/06-client-onboarding-implementation.md`](frontend/06-client-onboarding-implementation.md).

## 14. Perimetro della prima versione

### 14.1 Parte della v1 già implementata

Risultano già implementati:

- registrazione e autenticazione;
- verifica email obbligatoria per professionista e cliente;
- inviti e registrazione cliente;
- collegamento cliente-professionista;
- profilo/account;
- lettura relazioni cliente-professionista;
- availability del personal trainer;
- booking cliente-personal trainer;
- gestione stato operativo utente;
- foundation frontend con home pubblica e autenticazione session-based (login/logout/guards/bootstrap);
- frontend Profilo/Account/Operational Status autenticati (CLIENT e PROFESSIONAL);
- frontend registrazione pubblica PROFESSIONAL e verifica email (confirm + resend);
- frontend gestione inviti PROFESSIONAL (lista, genera, copia codice valido);
- frontend validazione invito e registrazione pubblica CLIENT con provider memory-only, outcome conservativi e reinvio neutro.

Restano fuori da questo vertical slice:

- dashboard frontend con dati ancora placeholder; relazioni, Availability e Booking sono operativi nel perimetro documentato.

### 14.2 Parte della v1 ancora da implementare

Per completare il perimetro funzionale originariamente previsto restano da sviluppare:

- schede workout;
- piani alimentari;
- feedback o segnalazioni cliente;
- eventuale gestione misurazioni/progressi;
- dashboard frontend con dati ancora placeholder; relazioni, Availability e Booking sono operativi nel perimetro documentato.

### 14.3 Funzionalità escluse dal perimetro attuale

Non fanno parte del perimetro attuale:

- chat real time;
- notifiche push;
- pagamenti;
- dashboard statistiche avanzate;
- gestione appuntamenti del nutrizionista tramite app.

---

## 15. Prossima decisione funzionale

Dopo home, auth, onboarding pubblico PROFESSIONAL e CLIENT, Profilo/Account/Operational Status e gestione inviti PROFESSIONAL, il prossimo passo dovrà essere scelto tra:

1. un ulteriore vertical slice frontend su un modulo business già disponibile nel backend (clients, professionals, availability o bookings);
2. introduzione del modulo Workout Plans.

La scelta definitiva del prossimo slice resta una decisione di prodotto/progetto.
