# Security Flow — Support Trainer

## 1. Obiettivo del documento
Questo documento definisce il flusso di sicurezza della v1 di Support Trainer.

Lo scopo è chiarire:
- come avviene l’autenticazione
- come viene gestita l’autorizzazione
- quali endpoint sono pubblici e quali protetti
- come funzionano verifica email, JWT, refresh token e password reset
- quali controlli spettano a Spring Security e quali al service layer

---

## 2. Principi guida

Per la v1, il sistema adotta i seguenti principi:

- autenticazione con **Spring Security**
- sessione **stateless**
- uso di **JWT**
- distinzione chiara tra:
  - **autenticazione**
  - **autorizzazione**
  - **business authorization**
- protezione degli endpoint sensibili
- verifica email obbligatoria per il professionista
- supporto a **forgot password / reset password**

---

## 3. Modello di autenticazione

## 3.1 Strategia scelta
Il sistema userà:

- **Spring Security**
- **JWT stateless**
- **access token**
- **refresh token**

## 3.2 Obiettivo
L’utente, dopo login valido, ottiene un accesso temporaneo senza usare sessioni server-side classiche.

## 3.3 Vantaggi
Questa scelta è adatta perché:
- è moderna
- è coerente con backend REST separato dal frontend
- si integra bene con frontend HTML/CSS/JS separato
- permette un controllo chiaro sugli endpoint

---

## 4. Ruoli e specializzazione

## 4.1 Ruoli di sicurezza
I ruoli principali sono:

- `ROLE_PROFESSIONAL`
- `ROLE_CLIENT`

## 4.2 Specializzazione professionista
La specializzazione non è un ruolo Spring Security separato, ma un attributo business:

- `PERSONAL_TRAINER`
- `NUTRITIONIST`

## 4.3 Regola pratica
I ruoli servono per:
- controllare l’accesso generale alle aree

La specializzazione serve per:
- decidere se il professionista può usare una certa funzionalità

### Esempio
- un utente con `ROLE_PROFESSIONAL` può entrare nell’area professionista
- ma solo se `specialization = PERSONAL_TRAINER` può gestire disponibilità e prenotazioni
- solo se `specialization = NUTRITIONIST` può creare piani alimentari

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
- non può usare le funzionalità operative
- non può generare codici invito
- non può gestire clienti o contenuti

## 5.4 Cliente
Nella v1 il cliente:
- non richiede verifica email separata
- può registrarsi solo tramite codice invito valido

---

## 6. Token model

## 6.1 Access token
L’access token:
- identifica l’utente autenticato
- contiene le informazioni minime necessarie
- ha durata breve
- viene inviato nelle richieste protette

### Header standard
`Authorization: Bearer <access_token>`

## 6.2 Refresh token
Il refresh token:
- serve a ottenere un nuovo access token
- ha durata più lunga
- riduce la necessità di rifare login spesso

## 6.3 Approccio consigliato
Per la v1 è consigliabile:

- **access token** breve
- **refresh token** più lungo
- refresh token trattato con maggiore cautela rispetto all’access token

## 6.4 Contenuto JWT
Nel token vanno messe solo informazioni essenziali, ad esempio:
- user id
- email
- role

Meglio non inserirvi logica business complessa o dati troppo volatili.

---

## 7. Endpoint pubblici e protetti

## 7.1 Endpoint pubblici
Nella v1 restano pubblici almeno questi endpoint:

- `POST /api/v1/auth/register/professional`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/verify-email`
- `POST /api/v1/auth/register/client/validate-invite`
- `POST /api/v1/auth/register/client`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`

## 7.2 Endpoint protetti
Tutti gli altri endpoint devono richiedere autenticazione valida tramite JWT.

---

## 8. Flusso registrazione professionista

## 8.1 Step principali
1. il professionista invia richiesta di registrazione
2. il backend valida i dati
3. il sistema crea l’utente con stato `PENDING_VERIFICATION`
4. il sistema genera un token/link di verifica email
5. il sistema invia email di conferma
6. il professionista clicca il link o usa il token
7. il backend verifica il token
8. l’account passa a `ACTIVE`

## 8.2 Regola importante
Prima della verifica email:
- il professionista non può operare realmente nella piattaforma

---

## 9. Flusso registrazione cliente con invito

## 9.1 Step principali
1. il cliente inserisce il codice invito
2. il backend valida il codice
3. il cliente completa la registrazione
4. il backend ricontrolla il codice
5. se tutto è valido, crea l’account cliente
6. il backend marca il codice come usato
7. il backend crea il collegamento con il professionista

## 9.2 Regola importante
La registrazione deve essere completata entro la scadenza del codice invito.

Se il codice è scaduto:
- la registrazione non può essere completata

---

## 10. Flusso login

## 10.1 Step principali
1. l’utente invia email e password
2. Spring Security autentica le credenziali
3. il backend verifica che l’account sia abilitato all’accesso
4. se tutto è valido, genera:
   - access token
   - refresh token
5. il backend restituisce la risposta di login

## 10.2 Controlli aggiuntivi
Nel login vanno verificati almeno:
- email esistente
- password corretta
- account attivo
- per il professionista: email verificata

---

## 11. Flusso refresh token

## 11.1 Scopo
Quando l’access token scade, il frontend può richiedere un nuovo access token senza costringere l’utente a rifare login.

## 11.2 Step principali
1. il frontend invia il refresh token
2. il backend valida il refresh token
3. il backend verifica che l’utente esista ancora e sia abilitato
4. il backend emette un nuovo access token
5. opzionalmente può ruotare anche il refresh token

## 11.3 Regola consigliata
Il refresh token va trattato come credenziale sensibile.

---

## 12. Flusso logout

## 12.1 In un sistema stateless
Il logout non distrugge una sessione server-side classica.

## 12.2 Significato pratico
Il logout significa:
- invalidare lato client i token salvati
- opzionalmente invalidare o revocare il refresh token lato backend, se gestito in forma persistita

## 12.3 Nota
L’access token già emesso, se puro JWT stateless, resta valido fino a scadenza salvo meccanismi ulteriori di revoca.

---

## 13. Forgot password / reset password

## 13.1 Obiettivo
Permettere all’utente di recuperare l’accesso in modo sicuro.

## 13.2 Flusso forgot password
1. l’utente invia la richiesta con la propria email
2. il backend verifica se esiste un account associato
3. il sistema genera un token di reset con scadenza
4. il sistema invia email con link o token di reset

## 13.3 Flusso reset password
1. l’utente apre il link o invia il token
2. il backend verifica validità e scadenza
3. l’utente invia la nuova password
4. il backend valida la password
5. il backend salva la nuova password hashata
6. il token di reset viene invalidato

## 13.4 Regole minime
Il reset password deve rispettare:
- token monouso
- token con scadenza
- password nuova conforme alle regole di sicurezza

---

## 14. Autenticazione vs autorizzazione

## 14.1 Autenticazione
Risponde alla domanda:
- **chi sei?**

È gestita da:
- login
- verifica credenziali
- JWT
- Spring Security filter chain

## 14.2 Autorizzazione
Risponde alla domanda:
- **puoi entrare in questa area?**

È gestita da:
- ruoli
- protezione endpoint
- regole di accesso Spring Security

## 14.3 Business authorization
Risponde alla domanda:
- **puoi davvero agire su questa specifica risorsa?**

Esempi:
- questo cliente è collegato a questo PT?
- questo professionista è davvero nutrizionista?
- questo workout plan appartiene davvero al cliente autenticato?

Questa parte non basta farla con i ruoli.  
Va controllata nel **service layer**.

---

## 15. Protezione endpoint per ruolo

## 15.1 Accesso generale
Regole base consigliate:

- endpoint generici autenticati → utente autenticato
- area professionista → `ROLE_PROFESSIONAL`
- area cliente → `ROLE_CLIENT`

## 15.2 Esempi
### Solo professionista
- generazione inviti
- elenco clienti propri
- creazione workout plan
- creazione nutrition plan
- gestione availability
- conferma/rifiuto booking

### Solo cliente
- visualizzazione propri professionisti
- invio booking request
- invio feedback
- visualizzazione propri piani/schede
- inserimento proprie misurazioni, se consentito

### Entrambi
- area `/me`
- aggiornamento profilo
- cambio password
- logout
- lettura dati account

---

## 16. Protezione endpoint per specializzazione

## 16.1 Regola
La specializzazione del professionista va controllata oltre al ruolo.

## 16.2 Esempi
### Solo PERSONAL_TRAINER
- availability
- bookings lato professionista
- workout plans

### Solo NUTRITIONIST
- nutrition plans

## 16.3 Dove controllarla
Meglio controllarla nel:
- service layer
- eventualmente con helper dedicati o custom checks

---

## 17. Sicurezza sulle relazioni di dominio

## 17.1 Regola fondamentale
Avere il ruolo corretto non basta.

Bisogna anche verificare che la risorsa appartenga davvero all’utente o sia a lui accessibile.

## 17.2 Esempi critici
### Cliente
Un cliente può:
- vedere solo le proprie schede
- vedere solo i propri piani
- prenotare solo con PT collegati
- inviare feedback solo sui propri contenuti

### Professionista
Un professionista può:
- vedere solo i clienti collegati
- creare contenuti solo per clienti collegati
- inserire misurazioni solo per clienti collegati
- gestire solo i propri slot e booking

---

## 18. Password security

## 18.1 Regole password
La password deve avere almeno:
- 8 caratteri
- una maiuscola
- un numero
- un carattere speciale

## 18.2 Storage
La password non deve mai essere salvata in chiaro.

Va salvata:
- hashata
- tramite `PasswordEncoder` adeguato, ad esempio BCrypt

---

## 19. Token aggiuntivi del sistema

## 19.1 Tipologie di token
Oltre ai JWT, il sistema può gestire anche token applicativi dedicati per:

- verifica email
- reset password

## 19.2 Regole minime
Questi token devono essere:
- casuali
- difficili da indovinare
- monouso
- con scadenza
- invalidati dopo utilizzo

---

## 20. CORS e frontend separato

## 20.1 Contesto
Poiché frontend e backend sono separati, sarà necessario configurare correttamente:
- CORS
- metodi consentiti
- header consentiti
- eventuali credenziali, se usate con cookie

## 20.2 Nota
La configurazione CORS non sostituisce la sicurezza:
- consente o blocca richieste cross-origin
- non sostituisce autenticazione e autorizzazione

---

## 21. CSRF

## 21.1 Regola generale
In un’architettura REST stateless con JWT usato via header Bearer, normalmente:
- CSRF può essere disabilitato

## 21.2 Nota
Se in futuro userai cookie autenticati in modo diverso, la valutazione andrà rifatta.

---

## 22. Security responsibilities

## 22.1 Spring Security deve gestire
- autenticazione login
- parsing e validazione JWT
- filtro richieste
- protezione endpoint pubblici/protetti
- controllo base dei ruoli

## 22.2 Service layer deve gestire
- specializzazione professionista
- relazione cliente-professionista
- ownership della risorsa
- regole business complesse
- blocchi su contenuti non appartenenti all’utente

---

## 23. Errori di sicurezza attesi

Le situazioni seguenti devono produrre errori chiari:

- credenziali non valide
- token mancante
- token non valido
- token scaduto
- refresh token non valido
- account non attivo
- email non verificata
- accesso a endpoint di ruolo errato
- accesso a risorsa non appartenente all’utente
- operazione non consentita per specializzazione errata

---

## 24. Decisioni confermate

Per Support Trainer si confermano le seguenti scelte:

- Spring Security + JWT stateless
- access token + refresh token
- ruoli: `ROLE_PROFESSIONAL`, `ROLE_CLIENT`
- specializzazione business: `PERSONAL_TRAINER`, `NUTRITIONIST`
- verifica email obbligatoria solo per il professionista
- endpoint pubblici limitati ad auth, verify email, invite validation, client register, forgot/reset password
- forgot password incluso nella v1
- business authorization gestita nel service layer
- password hashata e validata con regole forti

---

## 25. Prossimo step naturale
Dopo questo documento, il passo più utile è:

- `docs/10-database-schema.md`

per definire:
- tabelle
- colonne principali
- foreign key
- unique constraints
- struttura relazionale iniziale del database