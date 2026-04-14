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
- controllare l’accesso generale alle aree applicative

La specializzazione serve per:
- distinguere il tipo di professionista a livello dominio/business

### Esempio
- un utente con `PROFESSIONAL` può accedere agli endpoint dell’area professionista
- un utente con `CLIENT` può accedere agli endpoint dell’area cliente
- la specializzazione esiste nel modello dati, ma al momento non ci sono endpoint protetti in modo diverso tra `PERSONAL_TRAINER` e `NUTRITIONIST`

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
- `/api/v1/professionals/**` → solo `CLIENT`
- `/api/v1/me/**` → qualsiasi utente autenticato
- tutto il resto → autenticato

## 16.2 Caso particolare: invites
Gli endpoint `/api/v1/invites/**` non hanno nel `SecurityConfig` una regola esplicita `hasAuthority("PROFESSIONAL")`, ma sono comunque:
- protetti da autenticazione
- ulteriormente controllati nel service layer

Infatti `InviteCodeService` consente l’operazione solo se l’utente autenticato è:
- un professionista esistente
- attivo
- con email verificata
- con account `ACTIVE`

## 16.3 Esempi reali di accesso
### Solo professionista
- `GET /api/v1/clients/my`
- `GET /api/v1/clients/{clientId}`
- `POST /api/v1/invites`
- `GET /api/v1/invites`

### Solo cliente
- `GET /api/v1/professionals/my`
- `GET /api/v1/professionals/{professionalId}`

### Entrambi
- `GET /api/v1/me/profile`
- `GET /api/v1/me/account`
- `PATCH /api/v1/me/profile`
- `PATCH /api/v1/me/profile/operational-status`

---

## 17. Protezione endpoint per specializzazione

## 17.1 Stato attuale
La specializzazione del professionista esiste nel dominio, ma nel codice attuale:
- non ci sono endpoint protetti in modo diverso per `PERSONAL_TRAINER` o `NUTRITIONIST`
- non ci sono ancora controlli di autorizzazione basati sulla specializzazione

## 17.2 Implicazione pratica
Per ora la specializzazione è:
- un dato di dominio
- utile per il modello applicativo
- non ancora usata per differenziare gli accessi agli endpoint

---

## 18. Sicurezza sulle relazioni di dominio

## 18.1 Regola fondamentale
Avere il ruolo corretto non basta.

Bisogna anche verificare che la risorsa appartenga davvero all’utente o sia a lui accessibile.

## 18.2 Esempi già implementati
### Cliente
Un cliente può:
- vedere solo i professionisti a lui collegati

### Professionista
Un professionista può:
- vedere solo i clienti a lui collegati

## 18.3 Dove viene controllata
Questo controllo viene fatto nel service layer tramite verifica dell’esistenza di un
`ProfessionalClientLink` attivo tra le due parti.

Se il collegamento non esiste:
- viene restituito errore `403 FORBIDDEN`

---

## 19. Password security

## 19.1 Stato attuale della validazione
Nel codice attuale la password viene validata con:
- obbligatorietà
- lunghezza minima `8`
- lunghezza massima `100`

## 19.2 Cosa non è ancora implementato
Attualmente **non** risultano implementate regole automatiche obbligatorie su:
- maiuscola
- numero
- carattere speciale

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
Nel `SecurityConfig` il CORS è abilitato con:

- `cors(Customizer.withDefaults())`

## 21.2 Nota importante
Nel materiale attualmente analizzato non è presente una configurazione CORS dedicata più dettagliata.  
Quindi sappiamo che il CORS è abilitato, ma la policy completa va eventualmente verificata in file di configurazione non inclusi oppure in step successivi del progetto.

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
- verifica email e stato account
- accesso reale alle risorse collegate
- controllo del tipo utente richiesto
- controlli business sui link professionista-cliente
- blocco operativo per professionista non attivo o non verificato

---

## 24. Errori di sicurezza attesi

Nel codice attuale le situazioni seguenti devono produrre errori chiari:

- credenziali non valide
- utente non autenticato
- token mancante o non valido
- account non attivo
- email non verificata
- accesso a endpoint con authority errata
- accesso a risorsa non collegata all’utente
- uso di codice invito non valido, non attivo, già usato o scaduto

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