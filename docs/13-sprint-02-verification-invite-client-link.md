# Sprint 02 — Verification + Invite + Client Link

## 1. Obiettivo dello sprint
Questo sprint ha lo scopo di completare il primo vero flusso business del progetto dopo l’auth base.

Alla fine dello sprint il sistema dovrà permettere di:

- registrare un professionista
- verificare il suo account tramite email
- bloccare le funzionalità operative finché non verifica l’email
- generare un codice invito cliente
- validare un codice invito
- registrare un cliente tramite codice invito valido
- creare il collegamento tra professionista e cliente
- applicare le principali regole di business sui collegamenti

---

## 2. Risultato atteso
Al termine di questo sprint devo poter testare questo flusso completo:

1. il professionista si registra
2. il professionista riceve/verifica il token email
3. il suo account diventa attivo
4. il professionista genera un codice invito
5. il cliente valida il codice invito
6. il cliente si registra usando quel codice
7. il codice viene marcato come usato
8. viene creato il collegamento professionista-cliente

---

## 3. Fuori scope di questo sprint
In questo sprint non si implementano ancora:

- forgot password / reset password completi, se non già presenti come base
- availability
- bookings
- workout plans
- nutrition plans
- feedback
- measurements
- frontend integrato
- notifiche automatiche reali
- upload file avanzati

---

## 4. Dipendenze da questo sprint
Questo sprint si appoggia a ciò che è già stato completato nello Sprint 01:

- setup progetto
- fondazioni tecniche
- modello utenti
- security base
- auth base
- test base auth

---

## 5. Moduli coinvolti
In questo sprint si lavora soprattutto su:

- `auth`
- `invite`
- `link`
- `professional`
- `client`
- `security` *(solo per blocchi/accessi coerenti con email verification)*

---

## 6. Regole business principali dello sprint

### 6.1 Verifica email professionista
- il professionista si registra con:
  - `accountStatus = PENDING_VERIFICATION`
  - `emailVerified = false`
- finché non verifica email:
  - non può usare funzionalità operative
  - non può generare codici invito
- dopo verifica:
  - `accountStatus = ACTIVE`
  - `emailVerified = true`

### 6.2 Invite code
- il codice invito può essere generato solo da un professionista:
  - esistente
  - autenticato
  - attivo
  - con email verificata
- il codice:
  - deve essere univoco
  - deve avere scadenza
  - deve essere monouso
  - può essere disattivato logicamente, se previsto
- un professionista può avere più codici attivi contemporaneamente

### 6.3 Registrazione cliente con invito
- il cliente non può registrarsi liberamente
- il cliente deve usare un codice invito valido
- il codice deve:
  - esistere
  - non essere scaduto
  - non essere già usato
- la registrazione deve concludersi entro la validità del codice
- dopo registrazione valida:
  - il codice viene marcato come usato
  - viene creato il collegamento professionista-cliente

### 6.4 ProfessionalClientLink
- un cliente può avere massimo 3 professionisti attivi
- non può esistere più di un collegamento attivo tra la stessa coppia:
  - professionista
  - cliente
- il sistema non deve permettere self-link logici
- la fine del rapporto non elimina il record:
  - il collegamento viene disattivato logicamente

---

## 7. Entity da implementare o completare

### Da creare
- `EmailVerificationToken`
- `InviteCode`
- `ProfessionalClientLink`

### Da verificare/aggiornare
- `User`
- `ProfessionalProfile`
- `ClientProfile`

### Tabelle previste
- `email_verification_tokens`
- `invite_codes`
- `professional_client_links`

---

## 8. Repository da creare

### Minimi necessari
- `EmailVerificationTokenRepository`
- `InviteCodeRepository`
- `ProfessionalClientLinkRepository`

### Repository esistenti da usare
- `UserRepository`
- `ProfessionalProfileRepository`
- `ClientProfileRepository`

---

## 9. DTO da creare

## Auth / verification
- `VerifyEmailRequest` *(solo se scegli token via body; se usi query param può non servire)*
- eventuale `MessageResponse` o risposta semplice per verifica email

## Invite
- `CreateInviteCodeRequest` *(solo se vuoi passare dati come durata/scadenza custom)*
- `InviteCodeResponse`
- `ValidateInviteCodeRequest`
- `ValidateInviteCodeResponse`

## Client registration
- `RegisterClientRequest`

## Link
- eventuale `ProfessionalClientLinkResponse`

---

## 10. Endpoint previsti nello sprint

## Auth
- **GET** `/api/v1/auth/verify-email`
- **POST** `/api/v1/auth/register/client/validate-invite`
- **POST** `/api/v1/auth/register/client`

## Invites
- **POST** `/api/v1/invites`
- **GET** `/api/v1/invites`
- **GET** `/api/v1/invites/{inviteId}`
- **PATCH** `/api/v1/invites/{inviteId}/deactivate` *(se lo implementi già ora)*

## Links
- **GET** `/api/v1/links/professional`
- **GET** `/api/v1/links/client`
- **GET** `/api/v1/links/{linkId}`
- **PATCH** `/api/v1/links/{linkId}/deactivate` *(opzionale nello sprint, ma utile)*

---

## 11. Ordine corretto di implementazione

## Blocco A — Email verification
- [ ] Creare `EmailVerificationToken`
- [ ] Creare `EmailVerificationTokenRepository`
- [ ] Aggiornare register professional per generare token verifica
- [ ] Implementare logica di verifica email
- [ ] Aggiornare stato account dopo verifica
- [ ] Bloccare funzionalità operative del professionista non verificato

### Definition of Done
- il professionista registrato riceve/genera token di verifica
- il token può essere validato
- l’account passa da `PENDING_VERIFICATION` a `ACTIVE`
- `emailVerified` diventa `true`

---

## Blocco B — Invite code
- [ ] Creare `InviteCode`
- [ ] Creare `InviteCodeRepository`
- [ ] Implementare generazione codice invito
- [ ] Implementare scadenza
- [ ] Implementare monouso
- [ ] Implementare risposta endpoint invite

### Definition of Done
- un professionista verificato può generare un codice invito
- il codice viene salvato correttamente
- il codice ha scadenza valida
- il codice è leggibile e tracciabile

---

## Blocco C — Validazione invite
- [ ] Implementare endpoint validate invite
- [ ] Verificare:
  - esistenza codice
  - scadenza
  - non utilizzo precedente
  - stato logico coerente

### Definition of Done
- il sistema distingue correttamente:
  - codice valido
  - codice inesistente
  - codice scaduto
  - codice già usato

---

## Blocco D — Registrazione cliente con invito
- [ ] Creare `RegisterClientRequest`
- [ ] Implementare service registrazione cliente
- [ ] Validare nuovamente il codice prima del salvataggio
- [ ] Creare `ClientProfile`
- [ ] Salvare cliente
- [ ] Marcare codice come usato
- [ ] valorizzare `usedAt`

### Definition of Done
- il cliente si registra solo con codice valido
- il cliente viene salvato correttamente
- il codice viene consumato correttamente

---

## Blocco E — ProfessionalClientLink
- [ ] Creare `ProfessionalClientLink`
- [ ] Creare `ProfessionalClientLinkRepository`
- [ ] Implementare creazione collegamento dopo registrazione cliente
- [ ] Applicare regole:
  - max 3 professionisti
  - no duplicati attivi
  - no self-link

### Definition of Done
- dopo registrazione cliente il collegamento viene creato
- le regole di business vengono rispettate
- i casi non validi vengono bloccati con errore corretto

---

## Blocco F — Test
- [ ] Testare registrazione professionista
- [ ] Testare generazione token verifica email
- [ ] Testare verifica email valida
- [ ] Testare verifica email con token non valido/scaduto
- [ ] Testare generazione invite code da professionista verificato
- [ ] Testare blocco generazione invite da professionista non verificato
- [ ] Testare validazione invite corretta
- [ ] Testare validazione invite non valida/scaduta/usata
- [ ] Testare registrazione cliente con codice valido
- [ ] Testare consumo codice dopo uso
- [ ] Testare creazione `ProfessionalClientLink`
- [ ] Testare blocco quarto professionista
- [ ] Testare blocco duplicato attivo
- [ ] Testare blocco self-link logico

### Definition of Done
- tutto il flusso professionista -> verifica -> invito -> cliente -> link è testato
- i casi di errore principali sono coperti
- i dati risultano coerenti nel DB

---