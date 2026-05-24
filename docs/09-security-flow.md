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
- verifica email obbligatoria per il professionista
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
- si integra bene con frontend HTML/CSS/JS separato
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

## 5.1 Account professionista
Alla registrazione il professionista nasce con:
- `accountStatus = PENDING_VERIFICATION`
- `emailVerified = false`

## 5.2 Attivazione professionista
Solo dopo verifica email:
- `accountStatus = ACTIVE`
- `emailVerified = true`

## 5.3 Blocco operativo
Finché il professionista non è attivo e verificato:
- non può completare correttamente il login
- non può generare codici invito
- non può usare le funzionalità operative che richiedono profilo attivo e verificato

## 5.4 Cliente
Nel codice attuale il cliente:
- non richiede verifica email separata
- può registrarsi solo tramite codice invito valido
- in registrazione viene portato direttamente a:
  - `accountStatus = ACTIVE`
  - `emailVerified = true`

---

## 6. Token model

## 6.1 Access token
L’access token:
- identifica l’utente autenticato
- viene generato al login
- ha scadenza configurabile
- viene inviato nelle richieste protette

### Header standard
`Authorization: Bearer <access_token>`

## 6.2 Refresh token
Il refresh token:
- viene generato al login
- ha scadenza più lunga rispetto all’access token
- viene restituito nella `AuthResponse`

## 6.3 Stato attuale del refresh token
Nel codice attuale:
- il **refresh token viene generato**
- il **refresh token viene restituito**
- **non esiste ancora un endpoint dedicato di refresh**
- **non esiste ancora persistenza o revoca del refresh token**

Quindi il refresh token è già presente nel modello di autenticazione, ma il relativo flusso di rinnovo non è ancora esposto via endpoint.

## 6.4 Contenuto JWT
Nel codice attuale il JWT contiene solo informazioni essenziali:
- `subject` = email dell’utente
- `issuedAt`
- `expiration`

Attualmente **non** vengono aggiunti claim custom come:
- user id
- role
- dati business

---

## 7. Endpoint pubblici e protetti

## 7.1 Endpoint pubblici
In base al codice attuale, gli endpoint pubblici effettivamente implementati sono:

- `POST /api/v1/auth/register/professional`
- `POST /api/v1/auth/register/client`
- `GET /api/v1/auth/verify-email`
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
2. il backend verifica che l’email non sia già registrata
3. il backend valida il codice invito
4. il backend recupera il professionista associato al codice
5. il backend crea l’account cliente
6. il cliente viene impostato come:
   - `ACTIVE`
   - `emailVerified = true`
7. il backend crea il collegamento `ProfessionalClientLink`
8. il backend marca il codice invito come usato
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
1. il professionista richiama `GET /api/v1/auth/verify-email?token=...`
2. il backend cerca il token
3. il backend verifica che il token:
   - esista
   - non sia già usato
   - non sia scaduto
4. il backend aggiorna l’utente:
   - `emailVerified = true`
   - `accountStatus = ACTIVE`
5. il backend marca il token come usato

## 10.2 Errori gestiti
Il flusso gestisce almeno questi casi:
- token non trovato
- token già usato
- token scaduto

---

## 11. Flusso login

## 11.1 Step principali
1. l’utente invia email e password
2. il backend normalizza l’email
3. `AuthenticationManager` autentica le credenziali
4. il backend recupera l’utente dal database
5. il backend verifica che l’account sia abilitato all’accesso
6. se tutto è valido, genera:
   - access token
   - refresh token
7. il backend restituisce la risposta di login

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
- rotazione token
- revoca token
- persistenza token

## 12.2 Implicazione pratica
Il refresh token è già previsto nel modello, ma il flusso completo di rinnovo è ancora da completare in uno sprint successivo.

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
- assenza di modifiche o blocchi manuali su slot con booking `PENDING` attivo;
- protezione delle operazioni critiche Availability/Bookings in presenza di richieste concorrenti.

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

## 18.4 Riserva logica dello slot con booking pending

Una richiesta booking in stato `PENDING` non rende ancora lo slot definitivamente prenotato, perché la conferma del professionista non è ancora avvenuta.

Tuttavia, lo slot viene considerato logicamente impegnato rispetto alle operazioni che potrebbero alterare la richiesta già inviata dal cliente.

Finché esiste una richiesta booking `PENDING` attiva sullo slot, il professionista non può:

- modificarne data o orario;
- bloccarlo manualmente.

Questa regola impedisce che il cliente attenda una risposta su una disponibilità che nel frattempo viene modificata o resa indisponibile.

Il professionista deve prima gestire la richiesta pendente tramite il flusso Booking previsto, ad esempio rifiutandola.

---

## 19. Password security

## 19.1 Stato attuale della validazione
Nel codice attuale la password viene validata con:
- obbligatorietà
- lunghezza minima `8`
- lunghezza massima `100`
- almeno una lettera maiuscola
- almeno un numero
- almeno un carattere speciale

## 19.2 Ambito della validazione forte

La validazione forte della password è attualmente applicata in fase di:

- registrazione professionista;
- registrazione cliente.

## 19.3 Storage
La password non viene mai salvata in chiaro.

Viene salvata:
- hashata
- tramite `BCryptPasswordEncoder`

---

## 20. Token applicativi aggiuntivi

## 20.1 Token realmente presenti
Oltre ai JWT, nel codice attuale esiste un token applicativo dedicato per:
- verifica email

## 20.2 Regole del token di verifica email
Il token di verifica email è:
- casuale
- con scadenza
- monouso
- invalidato dopo utilizzo

## 20.3 Token non presenti
Nel codice attuale **non** risulta ancora implementato un token applicativo per:
- reset password

---

## 21. CORS e frontend separato

## 21.1 Stato attuale
il CORS è abilitato in SecurityConfig
è presente un bean CorsConfigurationSource
gli origin consentiti sono letti da property applicativa (app.cors.allowed-origins)
metodi e header consentiti sono configurati esplicitamente

## 21.2 Nota importante
Nel materiale attualmente analizzato non è presente una configurazione CORS dedicata più dettagliata.  
Quindi sappiamo che il CORS è abilitato, ma la policy completa va eventualmente verificata in file di configurazione non inclusi oppure in step successivi del progetto.

il backend è predisposto per frontend separato
gli origin cambiano per ambiente tramite configuration/properties
per JWT in header Authorization non è richiesto allowCredentials(true)

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
- riserva logica dello slot quando esiste un booking `PENDING`;
- blocco modifica slot con booking `PENDING` attivo;
- blocco del blocco manuale slot con booking `PENDING` attivo;
- protezione da creazione concorrente di booking sullo stesso slot;
- protezione da transizioni concorrenti della stessa richiesta booking;
- protezione da conferme concorrenti sullo stesso slot;
- protezione da overlap availability in operazioni concorrenti dello stesso professionista.

---

## 24. Errori di sicurezza attesi

Nel codice attuale le situazioni seguenti devono produrre errori chiari:

- credenziali non valide
- utente non autenticato
- token mancante o non valido
- account non attivo
- email non verificata
- profilo professionista non attivo
- profilo cliente non attivo
- accesso a endpoint con authority errata
- accesso a risorsa non collegata all’utente
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
- modifica di slot con richiesta booking `PENDING` attiva;
- blocco manuale di slot con richiesta booking `PENDING` attiva;

---

## 25. Decisioni confermate

Per Support Trainer, nello stato attuale del progetto, si confermano le seguenti scelte:

- Spring Security + JWT stateless
- access token + refresh token generati al login
- ruoli reali: `PROFESSIONAL`, `CLIENT`
- specializzazione business: `PERSONAL_TRAINER`, `NUTRITIONIST`
- verifica email obbligatoria solo per il professionista
- cliente registrabile solo tramite codice invito valido
- business authorization gestita nel service layer
- password hashata con BCrypt
- refresh token già presente nel modello, ma flusso refresh non ancora esposto via endpoint
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
- uno slot con booking `PENDING` attivo è logicamente riservato rispetto a modifica e blocco manuale;
- il professionista deve gestire la richiesta pendente prima di alterare lo slot;
- i flussi critici Availability e Bookings proteggono la coerenza dei dati anche in presenza di operazioni concorrenti.