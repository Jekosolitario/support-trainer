# Planned Endpoints Roadmap — Support Trainer

## 1. Obiettivo del documento
Questo documento raccoglie gli endpoint **pianificati o ipotizzati** per le prossime fasi di Support Trainer.

Lo scopo è:
- separare chiaramente ciò che è già implementato da ciò che è futuro
- conservare una traccia ordinata delle API probabili
- evitare confusione tra mappa reale e roadmap tecnica

---

## 2. Regola fondamentale
Gli endpoint presenti in questo documento:

- **non sono da considerare già implementati**
- **non sono da considerare contratto finale**
- possono cambiare in:
  - naming
  - verbo HTTP
  - path
  - payload
  - regole di autorizzazione

Questo file serve come **roadmap tecnica preliminare**, non come mappa API definitiva.

---

## 3. Stato attuale del progetto
Alla data attuale risultano già implementati:

- auth base
- verifica email professionista
- invite code
- registrazione cliente con invito
- link professionista-cliente
- area `/me`
- lettura clienti del professionista
- lettura professionisti del cliente

Il prossimo blocco naturale è l’area **availability**, seguita da **bookings**.

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

## 5. Endpoint pianificati — area clients/professionals estesa

### 5.1 Professionisti collegati a un cliente specifico
**GET** `/api/v1/clients/{clientId}/professionals`  
Possibile endpoint futuro, da valutare con attenzione lato autorizzazione.

### 5.2 Aggiornamento dati cliente specifico
**PATCH** `/api/v1/clients/{clientId}`  
Possibile endpoint futuro, ma da definire meglio:
- soggetto autorizzato
- campi modificabili
- business rules

### Nota
Questi endpoint non sono ancora parte del backend attuale e vanno confermati solo quando emergerà un caso d’uso reale.

---

## 6. Endpoint pianificati — area invites estesa

### 6.1 Dettaglio codice invito
**GET** `/api/v1/invites/{inviteId}`  
Possibile endpoint futuro per leggere il dettaglio di un invito specifico.

### 6.2 Disattivazione logica codice invito
**PATCH** `/api/v1/invites/{inviteId}/deactivate`  
Possibile endpoint futuro, se si vorrà permettere la disattivazione di inviti non ancora usati.

### Nota
Prima di confermare questi endpoint andrà definito se il caso d’uso esiste davvero nella UX del sistema.

---

## 7. Endpoint pianificati — area links

### 7.1 Elenco collegamenti del professionista autenticato
**GET** `/api/v1/links/professional`

### 7.2 Elenco collegamenti del cliente autenticato
**GET** `/api/v1/links/client`

### 7.3 Dettaglio collegamento
**GET** `/api/v1/links/{linkId}`

### 7.4 Disattivazione collegamento
**PATCH** `/api/v1/links/{linkId}/deactivate`

### Nota
Attualmente il collegamento professionista-cliente esiste come dominio e repository, ma non come modulo API dedicato.  
Prima di introdurre questi endpoint va chiarito se servono davvero come API autonome oppure se bastano i moduli `clients` e `professionals`.

---

## 8. Endpoint pianificati — area availability

### 8.1 Creazione slot disponibilità
**POST** `/api/v1/availability`

### 8.2 Elenco slot del professionista autenticato
**GET** `/api/v1/availability/my`

### 8.3 Elenco slot disponibili di un professionista
**GET** `/api/v1/professionals/{professionalId}/availability`

### 8.4 Aggiornamento slot
**PATCH** `/api/v1/availability/{slotId}`

### 8.5 Blocco slot
**PATCH** `/api/v1/availability/{slotId}/block`

### 8.6 Sblocco slot
**PATCH** `/api/v1/availability/{slotId}/unblock`

### Query param ipotizzati
- `status=AVAILABLE|BOOKED|BLOCKED`
- `from=...`
- `to=...`
- `active=true|false`

### Nota
Questo è il prossimo modulo naturale, ma il contratto finale degli endpoint andrà confermato quando inizierà lo sprint relativo.

---

## 9. Endpoint pianificati — area bookings

### 9.1 Creazione richiesta prenotazione
**POST** `/api/v1/bookings`

### 9.2 Elenco richieste del cliente autenticato
**GET** `/api/v1/bookings/client`

### 9.3 Elenco richieste del professionista autenticato
**GET** `/api/v1/bookings/professional`

### 9.4 Dettaglio richiesta
**GET** `/api/v1/bookings/{bookingRequestId}`

### 9.5 Conferma richiesta
**PATCH** `/api/v1/bookings/{bookingRequestId}/confirm`

### 9.6 Rifiuto richiesta
**PATCH** `/api/v1/bookings/{bookingRequestId}/reject`

### 9.7 Cancellazione richiesta
**PATCH** `/api/v1/bookings/{bookingRequestId}/cancel`

### Query param ipotizzati
- `status=PENDING|CONFIRMED|REJECTED|CANCELLED`
- `active=true|false`
- `from=...`
- `to=...`

### Nota
Questi endpoint restano pianificati e non ancora confermati come design finale.

---

## 10. Endpoint pianificati — area workout plans

### 10.1 Creazione scheda workout
**POST** `/api/v1/workout-plans`

### 10.2 Elenco schede create dal professionista
**GET** `/api/v1/workout-plans/professional`

### 10.3 Elenco schede del cliente autenticato
**GET** `/api/v1/workout-plans/client`

### 10.4 Dettaglio scheda
**GET** `/api/v1/workout-plans/{workoutPlanId}`

### 10.5 Nuova versione scheda
**POST** `/api/v1/workout-plans/{workoutPlanId}/versions`

### 10.6 Disattivazione scheda
**PATCH** `/api/v1/workout-plans/{workoutPlanId}/deactivate`

### 10.7 Sostituzione completa scheda
**PUT** `/api/v1/workout-plans/{workoutPlanId}`

### Nota
Modulo ancora interamente futuro.

---

## 11. Endpoint pianificati — area nutrition plans

### 11.1 Creazione piano alimentare
**POST** `/api/v1/nutrition-plans`

### 11.2 Elenco piani creati dal professionista
**GET** `/api/v1/nutrition-plans/professional`

### 11.3 Elenco piani del cliente autenticato
**GET** `/api/v1/nutrition-plans/client`

### 11.4 Dettaglio piano
**GET** `/api/v1/nutrition-plans/{nutritionPlanId}`

### 11.5 Nuova versione piano
**POST** `/api/v1/nutrition-plans/{nutritionPlanId}/versions`

### 11.6 Disattivazione piano
**PATCH** `/api/v1/nutrition-plans/{nutritionPlanId}/deactivate`

### 11.7 Sostituzione completa piano
**PUT** `/api/v1/nutrition-plans/{nutritionPlanId}`

### Nota
Modulo ancora interamente futuro.

---

## 12. Endpoint pianificati — area feedback

### 12.1 Invio feedback workout
**POST** `/api/v1/feedback/workout`

### 12.2 Invio feedback nutrizione
**POST** `/api/v1/feedback/nutrition`

### 12.3 Elenco feedback workout ricevuti
**GET** `/api/v1/feedback/workout/professional`

### 12.4 Elenco feedback nutrizione ricevuti
**GET** `/api/v1/feedback/nutrition/professional`

### 12.5 Elenco feedback inviati dal cliente
**GET** `/api/v1/feedback/client`

### Nota
Endpoint ancora solo pianificati.

---

## 13. Endpoint pianificati — area measurements

### 13.1 Inserimento misurazione
**POST** `/api/v1/measurements`

### 13.2 Elenco misurazioni del cliente autenticato
**GET** `/api/v1/measurements/client`

### 13.3 Elenco misurazioni di un cliente specifico
**GET** `/api/v1/clients/{clientId}/measurements`

### 13.4 Dettaglio misurazione
**GET** `/api/v1/measurements/{measurementId}`

### 13.5 Correzione misurazione
**PATCH** `/api/v1/measurements/{measurementId}`

### Nota
Anche questo modulo è ancora futuro e richiederà un’analisi separata di autorizzazione e ownership.

---

## 14. Endpoint trasversali da trattare con attenzione
Quando questi moduli verranno implementati, servirà particolare attenzione su:

- conferma/rifiuto prenotazioni
- creazione schede e piani
- inserimento misurazioni
- disattivazione collegamenti
- disattivazione inviti
- accesso a risorse collegate tramite link attivo

Molti controlli dipenderanno non solo dall’autenticazione, ma anche da:
- relazione attiva tra utente e risorsa
- tipo utente
- ownership logica della risorsa
- eventuale specializzazione del professionista

---

## 15. Regola documentale per il futuro
Quando un endpoint verrà davvero implementato:

1. va aggiunto o confermato nel documento tecnico dello sprint relativo
2. va inserito nella mappa reale `08-endpoint-map.md`
3. va rimosso o marcato come completato in questo documento roadmap

---

## 16. Conclusione
Questo documento non rappresenta API già disponibili.

Rappresenta solo:
- endpoint pianificati
- idee già emerse nella documentazione
- direzioni probabili di sviluppo

La mappa API affidabile del backend reale resta sempre:
- `08-endpoint-map.md`