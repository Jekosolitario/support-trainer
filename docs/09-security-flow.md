# Security Flow — Support Trainer

## 1. Obiettivo del documento
Questo documento definisce il flusso di sicurezza **attualmente implementato** nella v1 di Support Trainer.

Lo scopo è chiarire:
- come avviene l’autenticazione
- come viene gestita l’autorizzazione
- quali endpoint sono pubblici e quali protetti
- come funzionano verifica email, JWT e refresh token
- quali controlli spettano a Spring Security e quali al service layer

---

## 2. Principi guida

Nello stato attuale del progetto, il sistema adotta i seguenti principi:

- autenticazione con **Spring Security**
- sessione **stateless**
- uso di **JWT**
- distinzione chiara tra:
  - **autenticazione**
  - **autorizzazione**
  - **business authorization**
- protezione degli endpoint sensibili
- verifica email obbligatoria e reinvio uniforme per professionista e cliente
- controlli business aggiuntivi nel service layer sulle risorse accessibili

---

## 3. Modello di autenticazione

## 3.1 Strategia scelta
Il sistema usa:

- **Spring Security**
- **JWT stateless**
- **access token**
- **refresh token**

## 3.2 Obiettivo
L’utente, dopo login valido, ottiene un accesso temporaneo senza usare sessioni server-side classiche.

## 3.3 Vantaggi
Questa scelta è adatta perché:
- è coerente con un backend REST separato dal frontend
- si integra bene con un frontend web React + TypeScript + Vite separato dal backend;
- resta compatibile, a livello di API REST e autenticazione JWT, con una possibile futura app mobile React Native + Expo da valutare dopo la stabilizzazione della web app.
- permette un controllo chiaro sugli endpoint
- si adatta bene a un’applicazione scalabile e testabile

---

## 4. Ruoli e specializzazione

## 4.1 Ruoli di sicurezza
I ruoli realmente usati nel codice sono:

- `PROFESSIONAL`
- `CLIENT`

## 4.2 Specializzazione professionista
La specializzazione non è un ruolo Spring Security separato, ma un attributo business del professionista:

- `PERSONAL_TRAINER`
- `NUTRITIONIST`

## 4.3 Regola pratica

I ruoli servono per:

- controllare l’accesso generale alle aree applicative.

La specializzazione serve per:

- applicare regole business specifiche sulle funzionalità professionali.

### Esempi implementati

- un utente con `PROFESSIONAL` può accedere agli endpoint dell’area professionista secondo `SecurityConfig`;
- un utente con `CLIENT` può accedere agli endpoint dell’area cliente;
- solo un professionista `PERSONAL_TRAINER` può creare e gestire slot availability;
- uno slot appartenente a un professionista `NUTRITIONIST` non può essere prenotato;
- un booking anomalo già esistente su slot di un `NUTRITIONIST` non può essere confermato.

---

## 5. Stato account e accesso

## 5.1 Account professionista e cliente
Alla registrazione entrambi i ruoli nascono con:
- `accountStatus = PENDING_VERIFICATION`
- `emailVerified = false`

## 5.2 Attivazione uniforme
Solo dopo verifica email:
- `accountStatus = ACTIVE`
- `emailVerified = true`

## 5.3 Blocco operativo
Finché l'utente non è attivo e verificato non può completare correttamente il login. Il professionista inoltre:
- non può generare codici invito
- non può usare le funzionalità operative che richiedono profilo attivo e verificato

## 5.4 Cliente
Il cliente può registrarsi solo tramite codice invito valido. La registrazione crea subito link e token e consuma l'invito, ma l'account resta pending e il professionista non può leggerlo fino alla conferma. La nuova regola riguarda le nuove registrazioni e non migra i clienti già salvati.

---

## 6. Token model

## 6.1 Access token
L’access token:
- identifica l’utente autenticato
- viene generato al login
- ha scadenza configurabile
- viene inviato nelle richieste protette
- contiene il claim interno `token_type = access`
- è l’unico tipo di JWT accettato come credenziale Bearer sugli endpoint protetti

### Header standard
`Authorization: Bearer <access_token>`

## 6.2 Refresh token
Il refresh token:
- viene generato al login
- ha scadenza più lunga rispetto all’access token
- viene restituito nella `AuthResponse`
- contiene il claim interno `token_type = refresh`
- non è accettato come credenziale Bearer sugli endpoint protetti

## 6.3 Stato attuale del refresh token
Nel codice attuale:
- il **refresh token viene generato**
- il **refresh token viene restituito**
- il filtro JWT lo rifiuta se viene usato come access token Bearer
- **non esiste ancora un endpoint dedicato di refresh**
- **non esiste ancora un lifecycle completo di rinnovo, persistenza, rotazione o revoca**

Quindi il refresh token è già presente nel modello di autenticazione, ma il relativo flusso di rinnovo non è ancora esposto via endpoint.

## 6.4 Contenuto JWT
Nel codice attuale il JWT contiene solo informazioni essenziali:
- `subject` = email dell’utente
- `issuedAt`
- `expiration`
- claim interno `token_type`, valorizzato con `access` oppure `refresh`

Attualmente **non** vengono aggiunti altri claim applicativi come:
- user id
- role
- dati business

## 6.5 Configurazione e validazione JWT

Le proprietà `app.security.jwt.secret`, `app.security.jwt.expiration` e `app.security.jwt.refresh-expiration` sono raccolte in una configurazione tipizzata e validate durante l'avvio.

- il secret è obbligatorio, deve essere Base64 valido e decodificare almeno 32 byte;
- le durate devono essere positive;
- la durata del refresh token deve essere maggiore di quella dell'access token;
- i numeri senza suffisso mantengono la semantica storica in millisecondi; sono ammessi anche valori espliciti come `1h` o `7d`.

Il servizio JWT riceve questa configurazione come unica dipendenza e conserva in memoria la chiave di firma già decodificata. Nessun secret applicativo è previsto come default o deve essere versionato.

---

## 7. Endpoint pubblici e protetti

## 7.1 Endpoint pubblici
In base al codice attuale, gli endpoint pubblici effettivamente implementati sono:

- `POST /api/v1/auth/register/professional`
- `POST /api/v1/auth/register/client`
- `POST /api/v1/auth/email-verification/confirm`
- `POST /api/v1/auth/email-verification/resend`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/register/client/validate-invite`

## 7.2 Regola generale in SecurityConfig
Nel codice, Spring Security consente pubblicamente:
- `/error`
- `/swagger-ui/**`
- `/v3/api-docs/**`
- `/api/v1/auth/**`

## 7.3 Endpoint protetti
Tutti gli altri endpoint richiedono autenticazione valida tramite JWT, salvo regole più specifiche sui ruoli.

---

## 8. Flusso registrazione professionista

## 8.1 Step principali
1. il professionista invia richiesta di registrazione
2. il backend valida i dati
3. il sistema verifica che l’email non sia già registrata
4. il sistema crea il professionista con stato `PENDING_VERIFICATION`
5. il sistema genera un token di verifica email
6. il token viene salvato
7. la registrazione restituisce una `AuthResponse` senza token di login
8. il professionista deve poi verificare l’email tramite endpoint dedicato

## 8.2 Regola importante
Prima della verifica email:
- il professionista non può effettuare login operativo
- l’account non è ancora attivo

---

## 9. Flusso registrazione cliente con invito

## 9.1 Step principali
1. il cliente invia i dati di registrazione insieme al codice invito
2. il backend acquisisce e valida con lock il codice invito
3. il backend verifica il professionista associato e l'unicità dell'email
4. il backend crea l’account cliente
5. il cliente viene impostato come:
   - `PENDING_VERIFICATION`
   - `emailVerified = false`
6. il backend crea il collegamento `ProfessionalClientLink`
7. il backend marca il codice invito come usato
8. il backend crea il token email da 24 ore
9. la registrazione restituisce una `AuthResponse` senza token di login

## 9.2 Regola importante
La registrazione cliente può essere completata solo con codice invito:
- esistente
- attivo
- non usato
- non scaduto

---

## 10. Flusso verifica email

## 10.1 Step principali
1. il professionista o cliente invia `POST /api/v1/auth/email-verification/confirm` con il token nel body
2. il backend cerca il token
3. il backend verifica che il token:
   - esista
   - non sia già usato, salvo stato finale già coerente
   - non sia scaduto
4. il backend aggiorna l’utente:
   - `emailVerified = true`
   - `accountStatus = ACTIVE`
5. il backend marca il token come usato e valorizza `usedAt`
6. un secondo POST sullo stesso stato coerente restituisce 200 senza modificare nuovamente `usedAt`

## 10.2 Errori gestiti
Il flusso gestisce almeno questi casi:
- body o campo `token` obbligatorio mancante/non valido
- token non trovato
- token usato con stato incoerente
- token scaduto, restituito come `410 Gone`

## 10.3 Reinvio uniforme

1. il chiamante invia `POST /api/v1/auth/email-verification/resend` con l'email nel body;
2. il service normalizza l'email e acquisisce un lock pessimista sull'utente, se esiste;
3. account inesistenti, verificati, non pending, incoerenti, con profilo inattivo o in cooldown terminano senza mutazioni;
4. per un account idoneo, se sono trascorsi almeno 60 secondi dal token più recente, i token non usati vengono marcati `used=true` e `usedAt=now`;
5. viene creato un solo nuovo token UUID v4 con durata esatta di 24 ore;
6. ogni richiesta sintatticamente valida restituisce lo stesso `202 Accepted`, senza email, ruolo, stato, cooldown o token.

Al boundary `now == latestToken.createdAt + 60 secondi` il reinvio è consentito. L'invalidazione tramite `used/usedAt` è un compromesso semantico dello schema esistente. Inviti e collegamenti non cambiano. Dopo la persistenza del token, Auth pubblica un evento immutabile nella transazione; il listener sincrono con `fallbackExecution=false` costruisce il link `#token=...` e chiama il sender soltanto `AFTER_COMMIT`. Rollback ed eventi senza transazione non producono invii. Gli errori sono assorbiti e registrati soltanto con correlation ID, motivo e tipo, senza email, token, URL o stack trace. Il sender SMTP reale usa JavaMail con credenziali esterne e trasforma i fallimenti di preparazione/consegna in un'eccezione sanitizzata; consegna durevole, retry e rate limiting distribuito non sono implementati.

---

## 11. Flusso login

## 11.1 Step principali
1. l’utente invia email e password
2. il backend normalizza l’email
3. il backend rifiuta come credenziali non valide una password oltre 72 byte UTF-8
4. `AuthenticationManager` autentica le credenziali
5. il backend recupera l’utente dal database
6. il backend verifica che l’account sia abilitato all’accesso
7. se tutto è valido, genera:
   - access token
   - refresh token
8. il backend restituisce la risposta di login

## 11.2 Controlli aggiuntivi
Nel login vengono verificati almeno:
- credenziali corrette
- utente esistente
- account `ACTIVE`
- per il professionista: email verificata

## 11.3 Risposta login
La `AuthResponse` di login contiene:
- `accessToken`
- `refreshToken`
- `tokenType`
- `userId`
- `email`
- `role`

---

## 12. Refresh token

## 12.1 Stato attuale
Il progetto attualmente:
- genera refresh token
- restituisce refresh token nel login

Ma **non implementa ancora**:
- endpoint di refresh
- rinnovo dell’access token
- rotazione token
- revoca token
- persistenza token

## 12.2 Implicazione pratica
Il refresh token è già previsto nel modello e distinto dall’access token, ma non può autenticare richieste protette. Il flusso completo di rinnovo resta da completare in uno sprint successivo.

---

## 13. Logout

## 13.1 Stato attuale
Nel codice attuale **non esiste ancora un endpoint di logout**.

## 13.2 Significato pratico
In un sistema JWT stateless, il logout lato applicazione potrà in futuro significare:
- rimozione dei token lato client
- eventuale revoca lato backend dei refresh token, se verranno persistiti

---

## 14. Forgot password / reset password

## 14.1 Stato attuale
Nel codice attuale **forgot password** e **reset password** **non sono implementati**.

## 14.2 Implicazione documentale
Questi flussi non devono essere considerati parte della sicurezza già disponibile nel progetto corrente.

---

## 15. Autenticazione vs autorizzazione

## 15.1 Autenticazione
Risponde alla domanda:
- **chi sei?**

È gestita da:
- login
- verifica credenziali
- JWT
- filter chain di Spring Security

## 15.2 Autorizzazione
Risponde alla domanda:
- **puoi entrare in questa area?**

È gestita da:
- authority utente
- protezione endpoint in `SecurityConfig`

## 15.3 Business authorization
Risponde alla domanda:
- **puoi davvero agire su questa specifica risorsa?**

Esempi già implementati:
- questo professionista può vedere questo cliente?
- questo cliente può vedere questo professionista?
- questo utente autenticato è davvero del tipo richiesto dalla funzionalità?

Questa parte non basta farla con i ruoli.  
Va controllata nel **service layer**.

---

## 16. Protezione endpoint per ruolo

## 16.1 Regole attualmente implementate in SecurityConfig

Le regole base reali sono:

- `/api/v1/auth/**` → pubblico
- `/api/v1/clients/**` → solo `PROFESSIONAL`
- `/api/v1/invites/**` → solo `PROFESSIONAL`
- `/api/v1/availability/**` → solo `PROFESSIONAL`
- `/api/v1/professionals/**` → solo `CLIENT`
- `/api/v1/me/**` → qualsiasi utente autenticato

### Booking

Le regole Booking sono definite in modo più specifico:

- `POST /api/v1/bookings` → solo `CLIENT`
- `GET /api/v1/bookings/client` → solo `CLIENT`
- `GET /api/v1/bookings/professional` → solo `PROFESSIONAL`
- `PATCH /api/v1/bookings/{bookingRequestId}/confirm` → solo `PROFESSIONAL`
- `PATCH /api/v1/bookings/{bookingRequestId}/reject` → solo `PROFESSIONAL`
- `GET /api/v1/bookings/{bookingRequestId}` → utente autenticato, con controllo ownership nel service
- `PATCH /api/v1/bookings/{bookingRequestId}/cancel` → utente autenticato, con controllo ownership e stato nel service

Tutto il resto richiede autenticazione valida.

---

## 16.2 Esempi reali di accesso

### Solo professionista

- `GET /api/v1/clients/my`
- `GET /api/v1/clients/{clientId}`
- `POST /api/v1/invites`
- `GET /api/v1/invites`
- `POST /api/v1/availability`
- `GET /api/v1/availability/my`
- `PATCH /api/v1/availability/{slotId}`
- `PATCH /api/v1/availability/{slotId}/block`
- `PATCH /api/v1/availability/{slotId}/unblock`
- `GET /api/v1/bookings/professional`
- `PATCH /api/v1/bookings/{bookingRequestId}/confirm`
- `PATCH /api/v1/bookings/{bookingRequestId}/reject`

### Solo cliente

- `GET /api/v1/professionals/my`
- `GET /api/v1/professionals/{professionalId}`
- `GET /api/v1/professionals/{professionalId}/availability`
- `POST /api/v1/bookings`
- `GET /api/v1/bookings/client`

### Entrambi, se coinvolti nella risorsa

- `GET /api/v1/bookings/{bookingRequestId}`
- `PATCH /api/v1/bookings/{bookingRequestId}/cancel`

### Entrambi autenticati

- `GET /api/v1/me/profile`
- `GET /api/v1/me/account`
- `PATCH /api/v1/me/profile`
- `PATCH /api/v1/me/profile/operational-status`

### Anti-enumerazione nei dettagli profilo

I dettagli `GET /api/v1/clients/{clientId}` e `GET /api/v1/professionals/{professionalId}` interrogano direttamente il perimetro accessibile al principal. La query combina ID richiesto, ID del principal, collegamento attivo e stati di account/profilo già richiesti dal dominio; non esegue prima un lookup del solo ID né una query di esistenza usata per scegliere un errore diverso.

Un risultato vuoto produce sempre il medesimo 404 specifico dell'endpoint, sia per ID inesistente sia per collegamento assente o inattivo e profilo non leggibile. La policy non modifica i confini di Spring Security: richiesta anonima e token non valido restano 401, mentre un ruolo non ammesso sull'endpoint resta 403. Gli stati operativi, come `PAUSA` o `FERIE`, restano informazioni di dominio e non sono criteri di occultamento del dettaglio.

### Minimizzazione del profilo cliente condiviso

Superato il controllo scoped, il professionista non riceve l'entity completa:

- la lista espone soltanto `id`, `firstName`, `lastName` e `profileImageUrl`;
- il dettaglio aggiunge soltanto `primaryGoal`;
- stato operativo, flag `active`, dati fisici, note, stato account e audit non vengono serializzati;
- `PERSONAL_TRAINER` e `NUTRITIONIST` ricevono intenzionalmente lo stesso contratto minimo nell'MVP.

Il profilo owner `/me` resta separato e completo. La modifica non introduce consenso, scope, revoca, audit delle visualizzazioni o una differenziazione per specializzazione; tali aspetti richiedono decisioni future dedicate.

---

## 16.3 Nota su SecurityConfig e service layer

`SecurityConfig` controlla il ruolo minimo necessario per entrare nell’area corretta.

Il service layer controlla invece:

- account attivo;
- email verificata quando richiesta;
- profilo attivo;
- specializzazione del professionista per Availability e Bookings;
- ownership della risorsa;
- relazione attiva professionista-cliente;
- stato e validità temporale dello slot;
- stato del booking;
- transizioni consentite;
- blocco di booking e conferme su slot non coerenti con la specializzazione prevista.

---

## 17. Protezione per specializzazione

## 17.1 Stato attuale

La specializzazione del professionista non è gestita tramite authority Spring Security separate.

Gli utenti professionisti utilizzano il ruolo:

- `PROFESSIONAL`

La distinzione tra:

- `PERSONAL_TRAINER`
- `NUTRITIONIST`

viene applicata nel service layer come regola business.

## 17.2 Controlli attualmente implementati

### Availability

Il modulo Availability è riservato ai professionisti con specializzazione:

- `PERSONAL_TRAINER`

Un professionista `NUTRITIONIST` non può:

- creare slot availability;
- utilizzare il flusso operativo availability riservato al personal trainer.

### Bookings

Il modulo Bookings opera esclusivamente su slot availability appartenenti a professionisti `PERSONAL_TRAINER`.

Il sistema impedisce:

- la creazione di booking su slot appartenenti a un `NUTRITIONIST`;
- la conferma di booking anomali o storici collegati a slot appartenenti a un `NUTRITIONIST`.

## 17.3 Motivazione architetturale

`SecurityConfig` protegge l’area in base al ruolo generale dell’utente.

Il service layer applica invece la regola più specifica:

PROFESSIONAL + PERSONAL_TRAINER -> availability e booking consentiti
PROFESSIONAL + NUTRITIONIST -> availability e booking tramite slot non consentiti

---

## 18. Sicurezza sulle relazioni di dominio

## 18.1 Regola fondamentale

Avere il ruolo corretto non basta.

Bisogna anche verificare che la risorsa appartenga davvero all’utente o sia a lui accessibile.

Questi controlli vengono gestiti nel service layer.

---

## 18.2 Esempi già implementati

### Cliente

Un cliente può:

- vedere solo i professionisti a lui collegati
- vedere gli slot availability solo dei professionisti collegati
- creare booking solo su slot di professionisti collegati
- vedere solo i propri booking
- cancellare solo booking in cui è coinvolto

### Professionista

Un professionista può:

- vedere solo i clienti a lui collegati;
- se `PERSONAL_TRAINER`, creare, modificare, bloccare e sbloccare solo i propri slot availability;
- se `PERSONAL_TRAINER`, ricevere e gestire booking relativi ai propri slot validi;
- confermare o rifiutare solo booking che riguardano i propri slot autorizzati;
- cancellare booking confermati in cui è coinvolto;
- se `NUTRITIONIST`, non creare né confermare flussi booking basati su availability slot.

---

## 18.3 Dove viene controllata

Il controllo sulla relazione cliente-professionista viene fatto tramite verifica dell’esistenza di un `ProfessionalClientLink` attivo tra le due parti.

Il controllo ownership sulle risorse viene fatto nei service specifici:

- `ClientService`
- `ProfessionalService`
- `AvailabilityService`
- `BookingService`

Se il collegamento non esiste o l’utente non è autorizzato:

- viene restituito errore `403 FORBIDDEN`

---

## 19. Password security

## 19.1 Stato attuale della validazione
Nelle registrazioni professionista e cliente la password viene validata con:
- obbligatorietà
- lunghezza minima `8`
- lunghezza massima `72` byte in codifica UTF-8
- almeno una lettera maiuscola
- almeno un numero
- almeno un carattere speciale

Il limite è espresso in byte, non in caratteri: alcuni caratteri Unicode occupano più byte in UTF-8. La password oltre il limite viene rifiutata come dato non valido prima dell’hashing. Il valore non viene troncato, normalizzato o trasformato.

## 19.2 Login
Nel login lo stesso limite è controllato prima della verifica BCrypt. Una password oltre 72 byte produce la stessa risposta generica `401 AUTHENTICATION_ERROR` delle altre credenziali non valide, indipendentemente dall’esistenza dell’account; il dettaglio del limite non viene esposto in questo flusso.

## 19.3 Storage
La password non viene mai salvata in chiaro.

Viene salvata:
- hashata
- tramite `BCryptPasswordEncoder`

Algoritmo, costo e formato degli hash esistenti restano invariati.

---

## 20. Token applicativi aggiuntivi

## 20.1 Token realmente presenti
Oltre ai JWT, nel codice attuale esiste un token applicativo dedicato per:
- verifica email

## 20.2 Regole del token di verifica email
Il token di verifica email è:
- casuale
- con scadenza
- consumabile una sola volta per attivare l’account
- idempotente dopo il consumo quando token, account e profilo sono già nello stato finale coerente
- marcato come usato dopo il primo utilizzo valido
- sostituito dal reinvio dopo un cooldown di 60 secondi, marcando come usati i precedenti token ancora inutilizzati
- mai restituito o registrato dal flusso di reinvio

## 20.3 Token non presenti
Nel codice attuale **non** risulta ancora implementato un token applicativo per:
- reset password

---

## 21. CORS e frontend separato

## 21.1 Stato attuale

- il CORS è abilitato nella `SecurityFilterChain` tramite il bean `CorsConfigurationSource`;
- gli origin consentiti sono letti dalla proprietà tipizzata `app.cors.allowed-origins`;
- la lista è obbligatoria, normalizzata e priva di valori vuoti o duplicati;
- sono accettate solo origini esatte `http` o `https`, senza wildcard, path, query string o fragment;
- sono consentiti `GET`, `POST`, `PATCH` e `OPTIONS`, coerenti con le API correnti;
- sono consentiti gli header `Authorization` e `Content-Type`;
- `allowCredentials` è disabilitato perché l'autenticazione usa Bearer JWT.

## 21.2 Nota importante

La configurazione applicativa di esempio richiede `APP_CORS_ALLOWED_ORIGINS`; il file locale ignorato può continuare a definire direttamente la proprietà e il profilo `test` usa un origin fittizio autonomo. Ogni ambiente deve fornire l'origine esatta del proprio frontend, inclusa l'eventuale porta. Un preflight proveniente da un'origine non configurata viene rifiutato.

Questa configurazione supporta un frontend separato senza usare wildcard e resta sovrascrivibile tramite le normali sorgenti esterne di Spring. Non sono introdotti profili di deploy né valori di produzione nel repository.

---

## 22. CSRF

## 22.1 Stato attuale
Nel `SecurityConfig` il CSRF è disabilitato:

- `csrf(csrf -> csrf.disable())`

## 22.2 Coerenza architetturale
Questa scelta è coerente con:
- API REST stateless
- autenticazione via header Bearer JWT

---

## 23. Security responsibilities

## 23.1 Spring Security gestisce
- autenticazione login
- parsing e validazione JWT
- filtro richieste
- distinzione tra endpoint pubblici e protetti
- controllo base delle authority sugli endpoint configurati

## 23.2 Service layer gestisce

- verifica email e stato account;
- profilo attivo;
- accesso reale alle risorse collegate;
- controllo del tipo utente richiesto;
- controllo della specializzazione professionale;
- controlli business sui link professionista-cliente;
- blocco operativo per professionista non attivo o non verificato;
- validazione slot availability;
- blocco creazione e aggiornamento slot nel passato;
- assenza di sovrapposizioni availability;
- controllo ownership sugli slot;
- esclusione slot scaduti dalla lettura cliente;
- creazione booking solo su slot disponibili e futuri;
- creazione booking solo tra cliente e professionista collegati;
- blocco booking su slot appartenenti a nutrizionisti;
- blocco conferma booking con slot scaduti;
- blocco conferma booking su slot appartenenti a nutrizionisti;
- transizioni di stato booking consentite;
- aggiornamento stato slot dopo conferma o cancellazione booking.

---

## 24. Errori di sicurezza attesi

Nel codice attuale le situazioni seguenti devono produrre errori chiari:

- credenziali non valide
- utente non autenticato
- token mancante, alterato, non valido o scaduto
- refresh token usato impropriamente come Bearer
- route o risorsa inesistente dopo autenticazione
- metodo HTTP non supportato
- media type non supportato
- parametro HTTP obbligatorio mancante
- account non attivo
- email non verificata
- profilo professionista non attivo
- profilo cliente non attivo
- accesso a endpoint con authority errata
- accesso ai dettagli cliente/professionista fuori dal perimetro del principal, esposto come 404 uniforme
- uso di codice invito non valido, non attivo, già usato o scaduto
- creazione slot availability nel passato
- creazione slot availability sovrapposto
- modifica di slot non disponibile
- creazione availability da parte di un professionista `NUTRITIONIST`
- esposizione al cliente di slot availability ormai scaduti
- booking su slot non disponibile
- booking su slot ormai scaduto
- booking su slot appartenente a un professionista `NUTRITIONIST`
- conferma booking pending con slot ormai scaduto
- conferma booking su slot appartenente a un professionista `NUTRITIONIST`
- booking tra cliente e professionista non collegati
- accesso a booking da utente non coinvolto
- transizione booking non consentita

---

## 25. Decisioni confermate

Per Support Trainer, nello stato attuale del progetto, si confermano le seguenti scelte:

- Spring Security + JWT stateless
- access token + refresh token generati al login
- claim interno `token_type` per distinguere access e refresh token
- solo gli access token sono accettati come Bearer sugli endpoint protetti
- ruoli reali: `PROFESSIONAL`, `CLIENT`
- specializzazione business: `PERSONAL_TRAINER`, `NUTRITIONIST`
- verifica email obbligatoria per professionista e cliente
- cliente registrabile solo tramite codice invito valido
- business authorization gestita nel service layer
- password hashata con BCrypt
- refresh token già presente nel modello, ma lifecycle di rinnovo, persistenza, rotazione e revoca non ancora implementato
- forgot password / reset password non ancora implementati
- Availability è modulo backend implementato e protetto
- Bookings è modulo backend implementato e protetto
- gli endpoint booking principali hanno regole esplicite in `SecurityConfig`
- ownership e transizioni booking restano controllate nel service layer
- il cliente può vedere availability solo di professionisti collegati
- il professionista può gestire solo i propri slot availability
- Availability è riservata ai professionisti `PERSONAL_TRAINER`
- Bookings tramite slot availability è riservato ai professionisti `PERSONAL_TRAINER`
- un `NUTRITIONIST` non può creare slot availability
- booking e conferme su slot di nutrizionisti vengono bloccati dal service layer
- slot scaduti non vengono esposti al cliente e non possono essere prenotati o confermati

## 26. Riferimento temporale dei flussi di sicurezza

JWT, verifica email, inviti e timestamp delle risposte di errore usano l'unica fonte temporale applicativa. Il `Clock` tecnico opera in UTC; JWT converte esplicitamente l'`Instant` in `Date` mantenendo invariati claim, algoritmo e durate. Le scadenze di verifica email e invito sono ora `Instant` persistiti in UTC e durano rispettivamente 24 e 168 ore reali. Il timestamp non persistito di `ErrorResponse` resta intenzionalmente nel precedente contratto civile.

I test di sicurezza possono sostituire il bean con `Clock.fixed`, rendendo deterministici issued-at, expiration, consumo dei token e timestamp 401/403. Le scadenze esposte per gli inviti sono serializzate in ISO-8601 UTC con `Z`; endpoint e messaggi restano invariati.
