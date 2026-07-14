# Sprint 01 — Setup + Auth Base

## 1. Obiettivo dello sprint
Questo sprint ha lo scopo di costruire la base tecnica del backend di Support Trainer.

Alla fine dello sprint il progetto dovrà avere:
- progetto Spring Boot configurato
- connessione MySQL funzionante
- struttura package ordinata
- entity base utenti pronte
- repository base pronti
- Spring Security configurata in modo iniziale
- login funzionante con JWT
- endpoint pubblici e protetti già distinti

---

## 2. Risultato atteso
Al termine di questo sprint devo poter:

- avviare il backend senza errori
- collegarmi al database MySQL
- salvare e leggere utenti dal database
- autenticare un utente con email e password
- ricevere un access token e un refresh token
- proteggere gli endpoint base con Spring Security

---

## 3. Fuori scope di questo sprint
In questo sprint **non** si implementano ancora:

- verifica email
- forgot password / reset password
- registrazione cliente con invito
- invite code
- professional-client link
- availability
- bookings
- workout
- nutrition
- feedback
- measurements
- frontend integrato

---

## 4. Dipendenze Spring Boot consigliate
Creare il progetto includendo almeno:

- Spring Web
- Spring Data JPA
- Spring Security
- MySQL Driver
- Validation
- Lombok *(se vuoi usarlo con cautela)*

### Librerie aggiuntive
Aggiungere poi:
- libreria JWT scelta per access token / refresh token

---

## Stato successivo — remediation STEP 7B-A

Questo documento conserva lo scope storico dello Sprint 1. Nello stato applicativo successivo, la verifica email è obbligatoria sia per `PROFESSIONAL` sia per `CLIENT`: entrambi nascono con account `PENDING_VERIFICATION` ed `emailVerified = false` e vengono attivati tramite `POST /api/v1/auth/email-verification/confirm`. La remediation non modifica retroattivamente gli account cliente già presenti.

## Stato successivo — remediation STEP 7B-B

Il reinvio è ora disponibile tramite `POST /api/v1/auth/email-verification/resend` per entrambi i ruoli. Ogni email sintatticamente valida riceve lo stesso `202 Accepted`; solo account pending, non verificati e con profilo attivo generano un nuovo token. Il cooldown è 60 secondi e i token precedenti non usati vengono invalidati tramite `used/usedAt`. Il sender reale e il rate limiting distribuito restano fuori perimetro.
