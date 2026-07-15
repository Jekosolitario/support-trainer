# Planned Endpoints Roadmap — Support Trainer

## 1. Obiettivo del documento

Questo documento raccoglie gli endpoint futuri, pianificati o ipotizzati per le prossime fasi di Support Trainer.

Lo scopo è:

- separare chiaramente ciò che è già implementato da ciò che è futuro
- conservare una traccia ordinata delle API probabili
- evitare confusione tra mappa reale e roadmap tecnica

La mappa ufficiale degli endpoint già implementati resta sempre:

- `08-endpoint-map.md`

---

## 2. Regola fondamentale

Gli endpoint presenti in questo documento:

- non sono da considerare già implementati
- non sono da considerare contratto finale
- possono cambiare in:
  - naming
  - verbo HTTP
  - path
  - payload
  - regole di autorizzazione

Questo file serve come roadmap tecnica preliminare, non come mappa API definitiva.

---

## 3. Stato attuale del progetto

Alla data attuale risultano già implementati:

- auth base
- verifica email obbligatoria per professionista e cliente
- reinvio verifica email uniforme e anti-enumerazione
- invite code
- registrazione cliente con invito
- link professionista-cliente
- area `/me`
- lettura clienti del professionista
- lettura professionisti del cliente
- availability
- bookings

Availability e Bookings non devono più essere considerati endpoint pianificati.

La stabilizzazione backend, i test, la documentazione, il Wrapper, la CI e Flyway sono stati certificati sulla baseline applicativa `3cf48902b6c193c5f25740eab7e774ce26e3dcc3`. Prima della migrazione reale restano obbligatori il controllo di `flyway_schema_history` e una nuova validazione MySQL su schema isolato.

I prossimi blocchi da valutare sono:

1. integrazione frontend reale sui 29 endpoint applicativi già pronti
2. modulo workout
3. modulo nutrition
4. feedback
5. measurements
6. password reset / refresh token / logout
7. preparazione deploy

---

## 4. Endpoint pianificati — area profilo/account

### 4.1 Upload foto profilo

**POST** `/api/v1/me/profile/image`

Possibile endpoint futuro per upload immagine profilo.

### 4.2 Cambio password

**PATCH** `/api/v1/me/account/password`

Possibile endpoint futuro per cambio password da utente autenticato.

### Nota

Questi endpoint erano stati ipotizzati nella documentazione iniziale, ma non risultano ancora implementati né confermati nel contratto finale.

---

## 5. Endpoint pianificati — area security/account lifecycle

L'infrastruttura applicativa di richiesta email è già presente: pubblicazione nella transazione, consegna sincrona `AFTER_COMMIT`, sender locale disabilitato, sender in-memory per test/CI e adapter SMTP JavaMail configurabile. Non introduce endpoint. Restano futuri un provider API, template HTML, una convenzione production esplicita e, se richiesta affidabilità di consegna, outbox e retry.

### 5.1 Forgot password

**POST** `/api/v1/auth/forgot-password`

Endpoint futuro per avviare il recupero password.

### 5.2 Reset password

**POST** `/api/v1/auth/reset-password`

Endpoint futuro per impostare una nuova password tramite token valido.

### 5.3 Refresh token

**POST** `/api/v1/auth/refresh`

Endpoint futuro per rinnovare l’access token usando un refresh token persistente.

### 5.4 Logout

**POST** `/api/v1/auth/logout`

Endpoint futuro per invalidare il refresh token o chiudere la sessione applicativa.

### Nota

La documentazione iniziale prevede già concetti come refresh token e password reset, ma nel codice attuale non risultano ancora implementati.

---

## 6. Endpoint pianificati — area clients/professionals estesa

### 6.1 Professionisti collegati a un cliente specifico

**GET** `/api/v1/clients/{clientId}/professionals`

Possibile endpoint futuro, da valutare con attenzione lato autorizzazione.

### 6.2 Aggiornamento dati cliente specifico

**PATCH** `/api/v1/clients/{clientId}`

Possibile endpoint futuro, ma da definire meglio:

- soggetto autorizzato
- campi modificabili
- business rules

### Nota

Questi endpoint non sono ancora parte del backend attuale e vanno confermati solo quando emergerà un caso d’uso reale.

---

## 7. Endpoint pianificati — area invites estesa

### 7.1 Dettaglio codice invito

**GET** `/api/v1/invites/{inviteId}`

Possibile endpoint futuro per leggere il dettaglio di un invito specifico.

### 7.2 Disattivazione logica codice invito

**PATCH** `/api/v1/invites/{inviteId}/deactivate`

Possibile endpoint futuro, se si vorrà permettere la disattivazione di inviti non ancora usati.

### Nota

Prima di confermare questi endpoint andrà definito se il caso d’uso esiste davvero nella UX del sistema.

---

## 8. Endpoint pianificati — area links

### 8.1 Elenco collegamenti del professionista autenticato

**GET** `/api/v1/links/professional`

### 8.2 Elenco collegamenti del cliente autenticato

**GET** `/api/v1/links/client`

### 8.3 Dettaglio collegamento

**GET** `/api/v1/links/{linkId}`

### 8.4 Disattivazione collegamento

**PATCH** `/api/v1/links/{linkId}/deactivate`

### Nota

Attualmente il collegamento professionista-cliente esiste come dominio e repository, ma non come modulo API dedicato.

Prima di introdurre questi endpoint va chiarito se servono davvero come API autonome oppure se bastano i moduli `clients` e `professionals`.

---

## 9. Endpoint pianificati — area workout plans

### 9.1 Creazione scheda workout

**POST** `/api/v1/workout-plans`

### 9.2 Elenco schede create dal professionista

**GET** `/api/v1/workout-plans/professional`

### 9.3 Elenco schede del cliente autenticato

**GET** `/api/v1/workout-plans/client`

### 9.4 Dettaglio scheda

**GET** `/api/v1/workout-plans/{workoutPlanId}`

### 9.5 Nuova versione scheda

**POST** `/api/v1/workout-plans/{workoutPlanId}/versions`

### 9.6 Disattivazione scheda

**PATCH** `/api/v1/workout-plans/{workoutPlanId}/deactivate`

### 9.7 Sostituzione completa scheda

**PUT** `/api/v1/workout-plans/{workoutPlanId}`

### Nota

Modulo ancora interamente futuro.

---

## 10. Endpoint pianificati — area nutrition plans

### 10.1 Creazione piano alimentare

**POST** `/api/v1/nutrition-plans`

### 10.2 Elenco piani creati dal professionista

**GET** `/api/v1/nutrition-plans/professional`

### 10.3 Elenco piani del cliente autenticato

**GET** `/api/v1/nutrition-plans/client`

### 10.4 Dettaglio piano

**GET** `/api/v1/nutrition-plans/{nutritionPlanId}`

### 10.5 Nuova versione piano

**POST** `/api/v1/nutrition-plans/{nutritionPlanId}/versions`

### 10.6 Disattivazione piano

**PATCH** `/api/v1/nutrition-plans/{nutritionPlanId}/deactivate`

### 10.7 Sostituzione completa piano

**PUT** `/api/v1/nutrition-plans/{nutritionPlanId}`

### Nota

Modulo ancora interamente futuro.

---

## 11. Endpoint pianificati — area feedback

### 11.1 Invio feedback workout

**POST** `/api/v1/feedback/workout`

### 11.2 Invio feedback nutrizione

**POST** `/api/v1/feedback/nutrition`

### 11.3 Elenco feedback workout ricevuti

**GET** `/api/v1/feedback/workout/professional`

### 11.4 Elenco feedback nutrizione ricevuti

**GET** `/api/v1/feedback/nutrition/professional`

### 11.5 Elenco feedback inviati dal cliente

**GET** `/api/v1/feedback/client`

### Nota

Endpoint ancora solo pianificati.

---

## 12. Endpoint pianificati — area measurements

### 12.1 Inserimento misurazione

**POST** `/api/v1/measurements`

### 12.2 Elenco misurazioni del cliente autenticato

**GET** `/api/v1/measurements/client`

### 12.3 Elenco misurazioni di un cliente specifico

**GET** `/api/v1/clients/{clientId}/measurements`

### 12.4 Dettaglio misurazione

**GET** `/api/v1/measurements/{measurementId}`

### 12.5 Correzione misurazione

**PATCH** `/api/v1/measurements/{measurementId}`

### Nota

Anche questo modulo è ancora futuro e richiederà un’analisi separata di autorizzazione e ownership.

---

## 13. Endpoint trasversali da trattare con attenzione

Quando questi moduli verranno implementati, servirà particolare attenzione su:

- creazione schede workout
- creazione piani nutrizione
- inserimento misurazioni
- disattivazione collegamenti
- disattivazione inviti
- accesso a risorse collegate tramite link attivo
- gestione versioni di schede e piani

Molti controlli dipenderanno non solo dall’autenticazione, ma anche da:

- relazione attiva tra utente e risorsa
- tipo utente
- ownership logica della risorsa
- eventuale specializzazione del professionista
- stato attivo/inattivo della risorsa

---

## 14. Regola documentale per il futuro

Quando un endpoint verrà davvero implementato:

1. va aggiunto o confermato nel documento tecnico dello sprint relativo
2. va inserito nella mappa reale `08-endpoint-map.md`
3. va rimosso o marcato come completato in questo documento roadmap
4. va coperto da test automatici quando il flusso è critico

---

## 15. Conclusione

Questo documento non rappresenta API già disponibili.

Rappresenta solo:

- endpoint pianificati
- idee già emerse nella documentazione
- direzioni probabili di sviluppo

La mappa API affidabile del backend reale resta sempre:

- `08-endpoint-map.md`
