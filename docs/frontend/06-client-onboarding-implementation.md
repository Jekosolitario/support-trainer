# Client Onboarding Implementation — Frontend

## 1. Scopo e maturity

Questo documento è la **source of truth tecnica frontend** del vertical slice pubblico **Validazione invito + Onboarding CLIENT**.

Il perimetro è implementato e testato nel working tree corrente. Comprende:

- validazione del codice invito;
- handoff memory-only verso la registrazione CLIENT;
- form e submit della registrazione pubblica;
- protocollo conservativo degli outcome;
- cleanup dei dati sensibili;
- schermata neutra di controllo email e reinvio;
- passaggio separato a verifica email e Login.

Non sostituisce:

- la mappa funzionale e la maturity complessiva → [`01-frontend-functional-map-mvp.md`](./01-frontend-functional-map-mvp.md);
- il lifecycle della sessione autenticata → [`03-authentication-session-flow.md`](./03-authentication-session-flow.md);
- l'onboarding pubblico PROFESSIONAL e la verifica email condivisa → [`04-professional-onboarding-implementation.md`](./04-professional-onboarding-implementation.md);
- la gestione autenticata degli inviti PROFESSIONAL → [`05-professional-invites-implementation.md`](./05-professional-invites-implementation.md);
- il catalogo endpoint → [`docs/08-endpoint-map.md`](../08-endpoint-map.md);
- le regole di validazione e sicurezza di piattaforma → [`docs/06-validation-rules.md`](../06-validation-rules.md) e [`docs/09-security-flow.md`](../09-security-flow.md).

## 2. Obiettivo e perimetro

Il CLIENT non dispone di una registrazione libera. Il percorso implementato è:

`/invite/validate` → validazione server → `/register/client` → `202 Accepted` o altro outcome → controllo email → `/verify-email` → `/login`.

Il vertical slice garantisce che:

- il codice invito sia validato prima di mostrare il form CLIENT;
- il backend ripeta comunque la validazione durante la registrazione;
- il codice non sia inserito nella URL né persistito nel browser;
- registrazione e verifica email non creino una sessione autenticata;
- Login resti l'unico passaggio che crea la sessione browser server-side.

## 3. Route e navigazione

| Route | Responsabilità | Stato |
| --- | --- | --- |
| `/invite/validate` | Inserimento e validazione del codice invito | Implementata |
| `/register/client` | Registrazione CLIENT con invito validato memory-only | Implementata |
| `/verify-email` | Conferma email mediante token nel fragment, condivisa con PROFESSIONAL | Implementata; dettaglio in [FE04](./04-professional-onboarding-implementation.md) |
| `/login` | Creazione separata della sessione autenticata | Implementata; dettaglio in [FE03](./03-authentication-session-flow.md) |

`/invite/validate` e `/register/client` condividono un layout pathless con provider memory-only e auth gate locale. Il passaggio Validate → Register usa una navigazione normale verso un path statico, senza secret nella URL o nello stato del router.

L'accesso diretto o il reload di `/register/client` senza invito nel provider è fail-closed: il form non viene mostrato e il router usa un redirect `replace` verso `/invite/validate`. Le schermate terminali di Register restano invece visibili dopo il clear del provider perché non dipendono più dall'invito.

Il Back verso Validate entra nuovamente nella pagina, revoca il successo precedente e richiede una nuova validazione.

## 4. Architettura memory-only

`ClientOnboardingProvider` è limitato al subtree delle due route CLIENT e conserva esclusivamente il codice canonico (`trim().toUpperCase()`). Non conserva:

- `professionalId`;
- `expiresAt`;
- dati del professionista;
- draft del form;
- outcome della registrazione.

Il codice invito è trattato come dato simile a una credenziale temporanea. Non viene trasportato o conservato in:

- query parameter;
- fragment;
- pathname dinamico;
- `location.state` o history state;
- `localStorage`;
- `sessionStorage`.

Reload, nuova scheda o uscita dal subtree distruggono naturalmente il provider e il codice. Il provider non tenta di ricostruirlo.

## 5. Auth gate locale

`ClientOnboardingAuthGate` usa gli stessi quattro stati dell'`AuthProvider`:

| Stato auth | Comportamento nel subtree CLIENT |
| --- | --- |
| `initializing` | Non monta le pagine; mostra uno stato di verifica/disconnessione |
| `unauthenticated` | Consente Validate e Register |
| `authenticated` | Pulisce l'invito e redirige con `replace` alla dashboard coerente col ruolo |
| `unavailable` | Resta fail-closed tramite `AuthUnavailableBoundary`, con possibilità di reconciliation |

Il gate è locale: non modifica le altre route pubbliche. Non crea una seconda source of truth auth e non sostituisce le autorizzazioni backend.

## 6. Validate Invite

### 6.1 Contratto API

`POST /api/v1/auth/register/client/validate-invite` è una mutation pubblica protetta da CSRF. Il client invia il codice e supporta un `AbortSignal` legato al lifecycle della pagina.

Il backend considera disponibile un invito soltanto se esiste, ha `active = true`, non è usato, non è scaduto e il professionista proprietario ha profilo attivo, email verificata e account `ACTIVE`. La validazione non consuma l'invito.

Il successo è riconosciuto esclusivamente quando sono vere tutte le condizioni seguenti:

1. status HTTP esatto `200`;
2. body leggibile come JSON;
3. decoder runtime valido;
4. `valid === true`;
5. codice restituito coerente con il codice canonico richiesto;
6. `professionalId` intero positivo e `expiresAt` Instant valido.

Solo il codice canonico viene salvato nel provider. `professionalId` non viene conservato; la scadenza è usata soltanto per il feedback locale della pagina Validate.

### 6.2 Failure e retry manuale

Il mapping UX riconosce semanticamente:

- `VALIDATION_ERROR`;
- `MALFORMED_REQUEST`;
- `INVITE_CODE_NOT_FOUND`;
- `INVITE_CODE_NOT_ACTIVE`;
- `INVITE_CODE_ALREADY_USED`;
- `INVITE_CODE_EXPIRED`.

Rete, `5xx`, body vuoto o illeggibile, JSON non valido, decoder failure, status anomalo e response inattesa falliscono in modo chiuso con errore temporaneo e consentono un nuovo tentativo manuale. `CSRF_VALIDATION_FAILED` residuo dopo il replay centralizzato è trattato come temporaneo.

La pagina non introduce retry applicativi automatici.

### 6.3 Lifecycle e concorrenza

Il componente applica, senza duplicare il codice riga per riga:

- snapshot canonico prima dell'`await`;
- fence sincrona contro double submit;
- attempt ID crescente;
- un `AbortController` per il tentativo corrente;
- abort e invalidazione quando l'input cambia;
- scarto delle response stale;
- protezione su unmount, uscita dal subtree e transizioni auth;
- clear del vecchio invito all'ingresso e prima di una nuova validazione;
- nessun clear nell'unmount Validate → Register;
- fence sincrona sulla CTA “Continua con la registrazione”.

## 7. Register CLIENT

### 7.1 Campi

Campi obbligatori, marcati anche con semantica HTML `required`:

- `firstName`;
- `lastName`;
- `email`;
- `password`;
- `birthDate`;
- `heightCm`;
- `primaryGoal`;
- `gender`.

Campi facoltativi:

- `medicalNotes`;
- `injuryNotes`;
- `notes`.

Copy privacy del form:

> Se vuoi, puoi aggiungere alcune informazioni al tuo profilo personale. Questi campi sono facoltativi e potrai modificarli o completarli in qualsiasi momento dalla tua area personale.

Il frontend non afferma che queste note siano mostrate o trasmesse al professionista.

### 7.2 Validazione client

Il server resta autoritativo; il frontend applica preventivamente le regole seguenti:

| Campo | Regole |
| --- | --- |
| Nome e cognome | Obbligatori, non blank, massimo 100 caratteri |
| Email | Obbligatoria, formato valido, massimo 100; `trim` e lowercase nel payload |
| Password | Minimo 8 unità UTF-16; almeno una maiuscola ASCII, una cifra ASCII e un carattere speciale; massimo 72 byte UTF-8; nessun trim, normalizzazione o troncamento |
| Data di nascita | Data civile valida `YYYY-MM-DD`, strettamente precedente a oggi; nessun requisito aggiuntivo di età |
| Altezza | Obbligatoria; da `0.01` a `999.99`; massimo tre cifre intere e due decimali; punto o virgola accettati; payload JSON numerico |
| Obiettivo principale | Obbligatorio, non blank, massimo 255 caratteri |
| Genere | `MALE`, `FEMALE`, `OTHER`, `NOT_SPECIFIED`; nessuna preselezione |
| Note | Facoltative, massimo 5000 caratteri ciascuna; `trim`; proprietà omessa se blank |

### 7.3 Payload

Il payload contiene esclusivamente proprietà del contratto backend:

```json
{
  "firstName": "Nome",
  "lastName": "Cognome",
  "email": "utente@example.invalid",
  "password": "<valore non mostrato>",
  "inviteCode": "INV-XXXXXXXXXX",
  "birthDate": "2000-01-01",
  "heightCm": 180.5,
  "primaryGoal": "Obiettivo sintetico",
  "gender": "NOT_SPECIFIED",
  "medicalNotes": "...",
  "injuryNotes": "...",
  "notes": "..."
}
```

Le proprietà facoltative sono omesse quando blank. La password è inviata invariata; l'invito proviene esclusivamente dal provider. Prima della request viene costruito uno snapshot immutabile, senza proprietà frontend-only.

La pagina acquisisce una fence sincrona prima dell'`await`: una submit locale produce una sola chiamata applicativa a `registerClient`.

## 8. Protocollo outcome Register

### 8.1 Successo confermato

Il solo successo confermato è **HTTP `202 Accepted`**. Dopo aver osservato `202`, il body è irrilevante, inclusi:

- body valido;
- body vuoto;
- JSON malformato;
- errore durante la lettura del body (`read_error`).

La UI passa alla schermata neutra “Controlla la tua email”. Non deduce che account, link o messaggio email siano stati certamente creati.

### 8.2 Failure deterministica

Una failure è deterministica soltanto quando status HTTP e `ErrorResponse.code` coincidono con una coppia nell'allowlist condivisa:

| Status | Code |
| --- | --- |
| `400` | `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `INVITE_CODE_NOT_ACTIVE`, `INVITE_CODE_ALREADY_USED`, `INVITE_CODE_EXPIRED` |
| `404` | `INVITE_CODE_NOT_FOUND` |
| `403` | `ACCOUNT_NOT_ACTIVE`, `EMAIL_NOT_VERIFIED`, `PROFESSIONAL_NOT_ACTIVE`, `CSRF_VALIDATION_FAILED` |

Anche `body.status` deve coincidere con lo status HTTP. Un code noto con uno status diverso non è considerato deterministico.

`VALIDATION_ERROR` mantiene il form e presenta gli errori mappabili; `MALFORMED_REQUEST` e il CSRF residuo usano un errore generale senza inventare dettagli. Le coppie invite/readiness allowlisted entrano nell'outcome unificato “Invito non disponibile”.

### 8.3 Invito non disponibile

La UX unifica invito non trovato, inattivo, già usato, scaduto e professionista/account non operativo o non verificato quando la coppia status/code è allowlisted:

> Questo invito non è più disponibile. Verifica un altro codice invito per continuare.

Non espone stato del professionista, verifica email, account status o motivo interno. `INVITE_CODE_ALREADY_USED` non è interpretato come prova che una precedente registrazione CLIENT sia riuscita.

### 8.4 Outcome ambiguo

Sono `ambiguous`:

- ogni `5xx`;
- `200`, `201`, `204` e ogni altro `2xx` diverso da `202`;
- status/code incoerenti;
- code sconosciuti;
- errori network/transport;
- operazioni auth stale;
- body/response inattesi o non classificabili con certezza.

La copy non afferma successo né fallimento certo. Non esistono retry automatici della registrazione, pulsanti per ripetere lo stesso POST o un secondo submit con lo stesso invito dalla schermata ambiguous. L'utente può richiedere un reinvio neutro, andare al Login oppure ricominciare con un altro codice.

## 9. Cleanup dei dati sensibili

Gli outcome terminali `confirmed`, `ambiguous` e `inviteUnavailable` eseguono:

- clear dell'invito;
- transizione atomica del reducer;
- sostituzione del draft con una nuova istanza vuota;
- rimozione di password, nomi, email del draft, data, altezza, obiettivo, genere e note;
- pulizia di errori e summary;
- smontaggio del form.

Quando serve per il reinvio, la sola email normalizzata viene conservata separatamente. Non rimangono retention esplicite del payload completo in state o ref terminali. Non vengono formulate garanzie sulla garbage collection del runtime.

## 10. Reinvio email

Le schermate `confirmed` e `ambiguous` riusano `POST /api/v1/auth/email-verification/resend` con la sola email normalizzata.

Invarianti:

- risposta e copy neutrali, senza enumerazione account;
- fence same-tick prima dell'`await`;
- cooldown UX di 60 secondi;
- guard autorevole locale basata su una deadline assoluta, indipendente dal solo stato `disabled` renderizzato;
- nessuna nuova chiamata `registerClient`;
- nessuna navigazione automatica a `/verify-email`;
- nessun ripristino dell'invito.

Il frontend non afferma che il backend abbia certamente generato un token o inviato una nuova email.

## 11. Sessione, sicurezza e CSRF

- Registrazione e verifica email non creano automaticamente una sessione browser.
- Login è il solo passaggio che crea la sessione autenticata server-side.
- Il cookie di sessione è gestito dal browser, è HttpOnly e segue i flag dell'ambiente descritti in [`docs/09-security-flow.md`](../09-security-flow.md).
- Non esistono JWT, Bearer token o token auth persistiti nel browser.
- CSRF, path relativi `/api/v1/...` e `credentials: 'same-origin'` sono riusati dalla foundation.
- La pagina esegue una sola chiamata applicativa a `registerClient` per submit e non implementa retry propri.
- La foundation può effettuare **un solo replay tecnico** del mutation POST dopo `403 CSRF_VALIDATION_FAILED`, con refresh del token; un secondo fallimento viene restituito senza ulteriori replay.

Non è quindi corretto affermare che venga sempre eseguito un solo HTTP POST: il replay tecnico CSRF è distinto dal retry applicativo, che non esiste.

## 12. Accessibilità e UX

- struttura mobile-first coerente con il frontend corrente;
- label reali associate ai controlli;
- `required` sui controlli obbligatori e nessun genere preselezionato;
- `aria-invalid` e `aria-describedby` per gli errori;
- error summary e focus sul primo errore utile;
- regioni `role="alert"`, `role="status"` e `aria-live` per feedback pertinenti;
- stati pending e cooldown comunicati testualmente, non soltanto tramite colore;
- codice invito non ripetuto nelle regioni live.

Queste misure non equivalgono a una certificazione formale WCAG.

## 13. Strategia test

La copertura certificata comprende:

- provider memory-only, scope del subtree e StrictMode;
- auth gate nei quattro stati e redirect per ruolo;
- route reali, direct access fail-closed e navigazione senza secret;
- decoder runtime e mapping degli errori Validate;
- double submit, abort, attempt ownership, response stale, unmount e auth transition;
- validazioni, payload, password invariata, altezza e note facoltative;
- classificazione `202`, `read_error`, allowlist status/code e outcome ambiguo;
- cleanup atomico del draft;
- resend same-tick, cooldown a deadline e cleanup timer;
- replay CSRF centralizzato e limite a un solo retry tecnico.

Snapshot certificato dei Lotti 1–3:

- suite esplicita: **14 file / 319 test**;
- suite frontend completa: **51 file / 786 test**.

Entrambe risultavano verdi nell'audit conclusivo del working tree. I conteggi sono uno snapshot, non un vincolo permanente.

## 14. Criteri di accettazione

- Validate riconosce successo solo su `200` con body coerente.
- Il provider conserva soltanto il codice canonico e non usa storage o URL.
- Register senza invito è fail-closed con redirect `replace`.
- Register riconosce successo soltanto su `202`, indipendentemente dal body.
- Ogni `5xx` e ogni `2xx` diverso da `202` è ambiguo.
- Le failure deterministiche richiedono una coppia status/code allowlisted.
- Non esiste retry applicativo di Register; il replay tecnico CSRF resta centralizzato e singolo.
- Gli outcome terminali puliscono invito e draft sensibile.
- Il reinvio è neutro, fenced e soggetto a cooldown UX di 60 secondi.
- Register e Verify non creano sessione; il passo successivo resta Login.

## 15. Fuori scope

- auto-login o ingresso diretto in dashboard dopo register/verify;
- persistenza o ripristino dell'invito dopo reload;
- retry automatico della registrazione;
- nuove API, DTO, enum o policy backend;
- esposizione delle note al professionista;
- modifica del flusso `/verify-email` condiviso;
- password reset, pagine business private, deploy e chiusura Git.

## 16. Stato di implementazione

I Lotti funzionali 1–3 del vertical slice sono implementati, testati e certificati tramite audit del working tree. Questo documento costituisce il Lotto 4 di allineamento documentale.

Il vertical slice è pronto per il successivo audit documentale e, se approvato, per la fase conclusiva Git. Commit, push, merge, import nel repository originale e deploy non sono dichiarati come eseguiti.
