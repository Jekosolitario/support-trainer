# Endpoint Map — Support Trainer

## 1. Obiettivo del documento
Questo documento definisce la prima mappa degli endpoint REST del backend di Support Trainer.

Lo scopo è:
- organizzare gli endpoint per modulo funzionale
- assegnare a ogni endpoint una responsabilità chiara
- mantenere coerenza tra URL, verbo HTTP e caso d’uso
- preparare il backend alla fase di implementazione reale

---

## 2. Convenzioni generali

### 2.1 Prefisso API
Tutti gli endpoint della v1 useranno il prefisso:

`/api/v1`

### 2.2 Convenzione naming
Si useranno nomi:
- chiari
- coerenti
- al plurale dove ha senso per le risorse
- orientati alla risorsa, non all’azione, quando possibile

### 2.3 Endpoint “self”
Le operazioni sul proprio account/profilo useranno l’area:

`/api/v1/me`

### 2.4 Update
Regola generale:
- `PATCH` per aggiornamenti parziali
- `PUT` solo quando si sostituisce una struttura intera

### 2.5 Filtri storico/attivo
Per distinguere record attivi o storici si preferiscono:
- query param

Esempi:
- `?active=true`
- `?active=false`
- `?status=PENDING`

---

## 3. Modulo Auth

### 3.1 Registrazione professionista
**POST** `/api/v1/auth/register/professional`  
Registra un nuovo professionista.

### 3.2 Login
**POST** `/api/v1/auth/login`  
Autentica l’utente e restituisce la risposta di login.

### 3.3 Verifica email professionista
**GET** `/api/v1/auth/verify-email`  
Conferma l’account professionista tramite token/link di verifica.

### 3.4 Validazione codice invito cliente
**POST** `/api/v1/auth/register/client/validate-invite`  
Verifica che il codice invito esista, non sia scaduto e non sia già usato.

### 3.5 Registrazione cliente con invito
**POST** `/api/v1/auth/register/client`  
Completa la registrazione cliente usando un codice invito valido.

---

## 4. Modulo Profile / Account

### 4.1 Recupero profilo autenticato
**GET** `/api/v1/me/profile`  
Restituisce i dati principali del profilo autenticato.

### 4.2 Aggiornamento dati profilo base
**PATCH** `/api/v1/me/profile`  
Aggiorna i dati modificabili del proprio profilo.

### 4.3 Upload foto profilo
**POST** `/api/v1/me/profile/image`  
Carica la foto profilo tramite `multipart/form-data`.

### 4.4 Aggiornamento stato operativo
**PATCH** `/api/v1/me/profile/operational-status`  
Aggiorna lo stato operativo dell’utente autenticato.

### 4.5 Cambio password
**PATCH** `/api/v1/me/account/password`  
Permette di cambiare la password.

### 4.6 Recupero dati account
**GET** `/api/v1/me/account`  
Restituisce dati account essenziali dell’utente autenticato.

---

## 5. Modulo Professionals

### 5.1 Dettaglio professionista
**GET** `/api/v1/professionals/{professionalId}`  
Restituisce il dettaglio di un professionista.

### 5.2 Professionisti collegati al cliente autenticato
**GET** `/api/v1/professionals/my`  
Restituisce i professionisti collegati al cliente autenticato.

### 5.3 Elenco professionisti collegati a un cliente
**GET** `/api/v1/clients/{clientId}/professionals`  
Restituisce i professionisti collegati a un cliente specifico, se autorizzato.

---

## 6. Modulo Clients

### 6.1 Elenco clienti del professionista autenticato
**GET** `/api/v1/clients/my`  
Restituisce l’elenco clienti collegati al professionista autenticato.

### 6.2 Dettaglio cliente
**GET** `/api/v1/clients/{clientId}`  
Restituisce il dettaglio di un cliente, se autorizzato.

### 6.3 Aggiornamento note cliente
**PATCH** `/api/v1/clients/{clientId}`  
Aggiorna dati/annotazioni cliente consentiti al soggetto autorizzato.

---

## 7. Modulo Invites

### 7.1 Generazione codice invito
**POST** `/api/v1/invites`  
Genera un nuovo codice invito per cliente.

### 7.2 Elenco codici invito del professionista
**GET** `/api/v1/invites`  
Restituisce i codici invito generati dal professionista autenticato.

#### Query param possibili
- `used=true|false`
- `expired=true|false`
- `active=true|false`

### 7.3 Dettaglio codice invito
**GET** `/api/v1/invites/{inviteId}`  
Restituisce il dettaglio di un codice invito.

### 7.4 Disattivazione logica codice invito
**PATCH** `/api/v1/invites/{inviteId}/deactivate`  
Disattiva logicamente un codice invito non ancora usato, se previsto.

---

## 8. Modulo Links

### 8.1 Elenco collegamenti del professionista autenticato
**GET** `/api/v1/links/professional`  
Restituisce i collegamenti professionista-cliente del professionista autenticato.

### 8.2 Elenco collegamenti del cliente autenticato
**GET** `/api/v1/links/client`  
Restituisce i collegamenti attivi/storici del cliente autenticato.

### 8.3 Dettaglio collegamento
**GET** `/api/v1/links/{linkId}`  
Restituisce il dettaglio di un collegamento.

### 8.4 Disattivazione collegamento
**PATCH** `/api/v1/links/{linkId}/deactivate`  
Disattiva un collegamento professionista-cliente.

#### Query param possibili
- `active=true|false`

---

## 9. Modulo Availability

### 9.1 Creazione slot disponibilità
**POST** `/api/v1/availability`  
Crea uno slot di disponibilità del PT.

### 9.2 Elenco slot del PT autenticato
**GET** `/api/v1/availability/my`  
Restituisce gli slot del PT autenticato.

#### Query param possibili
- `status=AVAILABLE|BOOKED|BLOCKED`
- `from=...`
- `to=...`
- `active=true|false`

### 9.3 Elenco slot disponibili di un PT
**GET** `/api/v1/professionals/{professionalId}/availability`  
Restituisce gli slot disponibili di un PT specifico.

#### Query param possibili
- `from=...`
- `to=...`
- `status=AVAILABLE`

### 9.4 Aggiornamento slot
**PATCH** `/api/v1/availability/{slotId}`  
Aggiorna parzialmente uno slot.

### 9.5 Blocco slot
**PATCH** `/api/v1/availability/{slotId}/block`  
Imposta lo slot come bloccato.

### 9.6 Sblocco/ripristino slot
**PATCH** `/api/v1/availability/{slotId}/unblock`  
Ripristina uno slot bloccato, se consentito.

---

## 10. Modulo Bookings

### 10.1 Creazione richiesta prenotazione
**POST** `/api/v1/bookings`  
Crea una nuova richiesta di prenotazione.

### 10.2 Elenco richieste del cliente autenticato
**GET** `/api/v1/bookings/client`  
Restituisce le richieste di prenotazione del cliente autenticato.

### 10.3 Elenco richieste ricevute dal PT autenticato
**GET** `/api/v1/bookings/professional`  
Restituisce le richieste ricevute dal PT autenticato.

#### Query param possibili
- `status=PENDING|CONFIRMED|REJECTED|CANCELLED`
- `active=true|false`
- `from=...`
- `to=...`

### 10.4 Dettaglio richiesta
**GET** `/api/v1/bookings/{bookingRequestId}`  
Restituisce il dettaglio di una richiesta di prenotazione.

### 10.5 Conferma richiesta
**PATCH** `/api/v1/bookings/{bookingRequestId}/confirm`  
Conferma la richiesta e marca gli slot come `BOOKED`.

### 10.6 Rifiuto richiesta
**PATCH** `/api/v1/bookings/{bookingRequestId}/reject`  
Rifiuta la richiesta.

### 10.7 Cancellazione richiesta
**PATCH** `/api/v1/bookings/{bookingRequestId}/cancel`  
Permette l’annullamento della richiesta, se consentito dalle regole di business.

---

## 11. Modulo Workout Plans

### 11.1 Creazione scheda workout
**POST** `/api/v1/workout-plans`  
Crea una nuova scheda workout.

### 11.2 Elenco schede create dal PT autenticato
**GET** `/api/v1/workout-plans/professional`  
Restituisce le schede workout create dal PT autenticato.

#### Query param possibili
- `clientId=...`
- `active=true|false`

### 11.3 Elenco schede del cliente autenticato
**GET** `/api/v1/workout-plans/client`  
Restituisce le schede workout del cliente autenticato.

#### Query param possibili
- `professionalId=...`
- `active=true|false`

### 11.4 Dettaglio scheda workout
**GET** `/api/v1/workout-plans/{workoutPlanId}`  
Restituisce il dettaglio completo della scheda.

### 11.5 Aggiornamento tramite nuova versione
**POST** `/api/v1/workout-plans/{workoutPlanId}/versions`  
Crea una nuova versione della scheda workout.

### 11.6 Disattivazione logica scheda
**PATCH** `/api/v1/workout-plans/{workoutPlanId}/deactivate`  
Disattiva logicamente una scheda workout.

### 11.7 Sostituzione struttura completa scheda
**PUT** `/api/v1/workout-plans/{workoutPlanId}`  
Sostituisce interamente la struttura della scheda, se scegli questo approccio in casi specifici.

---

## 12. Modulo Nutrition Plans

### 12.1 Creazione piano nutrizione
**POST** `/api/v1/nutrition-plans`  
Crea un nuovo piano alimentare.

### 12.2 Elenco piani creati dal nutrizionista autenticato
**GET** `/api/v1/nutrition-plans/professional`  
Restituisce i piani creati dal nutrizionista autenticato.

#### Query param possibili
- `clientId=...`
- `active=true|false`

### 12.3 Elenco piani del cliente autenticato
**GET** `/api/v1/nutrition-plans/client`  
Restituisce i piani alimentari del cliente autenticato.

#### Query param possibili
- `professionalId=...`
- `active=true|false`

### 12.4 Dettaglio piano alimentare
**GET** `/api/v1/nutrition-plans/{nutritionPlanId}`  
Restituisce il dettaglio completo del piano.

### 12.5 Aggiornamento tramite nuova versione
**POST** `/api/v1/nutrition-plans/{nutritionPlanId}/versions`  
Crea una nuova versione del piano alimentare.

### 12.6 Disattivazione logica piano
**PATCH** `/api/v1/nutrition-plans/{nutritionPlanId}/deactivate`  
Disattiva logicamente un piano alimentare.

### 12.7 Sostituzione struttura completa piano
**PUT** `/api/v1/nutrition-plans/{nutritionPlanId}`  
Sostituisce interamente la struttura del piano, se previsto.

---

## 13. Modulo Feedback

### 13.1 Invio feedback workout
**POST** `/api/v1/feedback/workout`  
Invia un feedback relativo a un `WorkoutDay`.

### 13.2 Invio feedback nutrizione
**POST** `/api/v1/feedback/nutrition`  
Invia un feedback relativo a un `NutritionDay`.

### 13.3 Elenco feedback workout ricevuti
**GET** `/api/v1/feedback/workout/professional`  
Restituisce i feedback workout ricevuti dal PT autenticato.

### 13.4 Elenco feedback nutrizione ricevuti
**GET** `/api/v1/feedback/nutrition/professional`  
Restituisce i feedback nutrizione ricevuti dal nutrizionista autenticato.

### 13.5 Elenco feedback inviati dal cliente
**GET** `/api/v1/feedback/client`  
Restituisce i feedback inviati dal cliente autenticato.

#### Query param possibili
- `type=WORKOUT|NUTRITION`
- `professionalId=...`
- `from=...`
- `to=...`

---

## 14. Modulo Measurements

### 14.1 Inserimento misurazione
**POST** `/api/v1/measurements`  
Crea una nuova misurazione cliente.

### 14.2 Elenco misurazioni del cliente autenticato
**GET** `/api/v1/measurements/client`  
Restituisce lo storico misurazioni del cliente autenticato.

### 14.3 Elenco misurazioni di un cliente specifico
**GET** `/api/v1/clients/{clientId}/measurements`  
Restituisce lo storico misurazioni di un cliente, se autorizzato.

#### Query param possibili
- `from=...`
- `to=...`
- `recordedBy=...`

### 14.4 Dettaglio misurazione
**GET** `/api/v1/measurements/{measurementId}`  
Restituisce il dettaglio di una misurazione.

### 14.5 Correzione misurazione
**PATCH** `/api/v1/measurements/{measurementId}`  
Permette la correzione di una misurazione, se consentito dalle regole di business.

---

## 15. Endpoint trasversali da trattare con attenzione

### 15.1 Operazioni “sensibili”
I seguenti endpoint richiedono attenzione speciale su autorizzazione e business logic:
- conferma/rifiuto prenotazioni
- creazione schede e piani
- inserimento misurazioni
- disattivazione collegamenti
- generazione codici invito

### 15.2 Nota importante
Molti controlli non dipendono solo dall’autenticazione, ma anche da:
- relazione attiva tra utente e risorsa
- specializzazione del professionista
- proprietà logica della risorsa

---

## 16. Decisioni confermate

Per Support Trainer si confermano le seguenti scelte:
- prefisso globale `/api/v1`
- area `/me` per operazioni sul proprio account/profilo
- `PATCH` quasi ovunque per aggiornamenti parziali
- `PUT` solo per sostituzioni strutturali complete
- doppio endpoint per validazione invito + registrazione cliente
- uso di query param per storico, attivi, stati e filtri
- upload foto profilo con endpoint dedicato `multipart/form-data`

---

## 17. Nota metodologica
Questa mappa non rappresenta ancora il contratto finale di ogni endpoint.

Nei prossimi step andranno definiti:
- request DTO
- response DTO
- codici HTTP attesi
- regole di autorizzazione endpoint per endpoint
- payload e formato errori

---