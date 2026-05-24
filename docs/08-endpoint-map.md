# Endpoint Map — Support Trainer

## 1. Obiettivo del documento
Questo documento definisce la mappa degli endpoint REST **attualmente implementati** nel backend di Support Trainer.

Lo scopo è:
- organizzare gli endpoint per modulo funzionale
- avere una vista affidabile delle API realmente disponibili
- mantenere coerenza tra documentazione e codice
- fornire una base chiara per test manuali, Postman e frontend

---

## 2. Convenzioni generali

### 2.1 Prefisso API
Tutti gli endpoint della v1 usano il prefisso:

`/api/v1`

### 2.2 Convenzione naming
Si usano nomi:
- chiari
- coerenti
- orientati alla risorsa
- al plurale dove ha senso

### 2.3 Endpoint “self”
Le operazioni sul proprio account/profilo usano l’area:

`/api/v1/me`

### 2.4 Update
Regola generale:
- `PATCH` per aggiornamenti parziali
- `GET` per lettura
- `POST` per creazione o operazioni di ingresso nel sistema

---

## 3. Stato del documento
Questo file include **solo endpoint realmente presenti nel codice attuale**.

Gli endpoint futuri o ancora da definire non vengono elencati qui.  
Devono essere mantenuti in un documento separato dedicato agli endpoint pianificati.

---

## 4. Modulo Auth

### 4.1 Registrazione professionista
**POST** `/api/v1/auth/register/professional`  
Registra un nuovo professionista.

### 4.2 Login
**POST** `/api/v1/auth/login`  
Autentica l’utente e restituisce la risposta di login.

### 4.3 Verifica email professionista
**GET** `/api/v1/auth/verify-email`  
Conferma l’account professionista tramite token di verifica.

### 4.4 Validazione codice invito cliente
**POST** `/api/v1/auth/register/client/validate-invite`  
Verifica che il codice invito esista, sia attivo, non sia scaduto e non sia già usato.

### 4.5 Registrazione cliente con invito
**POST** `/api/v1/auth/register/client`  
Completa la registrazione cliente usando un codice invito valido.

---

## 5. Modulo Profile / Me

### 5.1 Recupero profilo autenticato
**GET** `/api/v1/me/profile`  
Restituisce i dati principali del profilo autenticato.

### 5.2 Recupero dati account autenticato
**GET** `/api/v1/me/account`  
Restituisce i dati account essenziali dell’utente autenticato.

### 5.3 Aggiornamento dati profilo base
**PATCH** `/api/v1/me/profile`  
Aggiorna i dati modificabili del proprio profilo.

### 5.4 Aggiornamento stato operativo
**PATCH** `/api/v1/me/profile/operational-status`  
Aggiorna lo stato operativo dell’utente autenticato.

---

## 6. Modulo Clients

### 6.1 Elenco clienti del professionista autenticato
**GET** `/api/v1/clients/my`  
Restituisce l’elenco clienti collegati al professionista autenticato.

### 6.2 Dettaglio cliente
**GET** `/api/v1/clients/{clientId}`  
Restituisce il dettaglio di un cliente, solo se autorizzato tramite collegamento valido.

---

## 7. Modulo Professionals

### 7.1 Professionisti collegati al cliente autenticato
**GET** `/api/v1/professionals/my`  
Restituisce i professionisti collegati al cliente autenticato.

### 7.2 Dettaglio professionista
**GET** `/api/v1/professionals/{professionalId}`  
Restituisce il dettaglio di un professionista, solo se autorizzato tramite collegamento valido.

---

## 8. Modulo Invites

### 8.1 Generazione codice invito
**POST** `/api/v1/invites`  
Genera un nuovo codice invito per cliente.

### 8.2 Elenco codici invito del professionista autenticato
**GET** `/api/v1/invites`  
Restituisce i codici invito generati dal professionista autenticato.

---

## 9. Modulo Availability

### 9.1 Creazione slot disponibilità
**POST** `/api/v1/availability`  
Crea un nuovo slot di disponibilità per il professionista autenticato.

### 9.2 Elenco slot del professionista autenticato
**GET** `/api/v1/availability/my`  
Restituisce gli slot di disponibilità del professionista autenticato.

### 9.3 Elenco slot disponibili di un professionista
**GET** `/api/v1/professionals/{professionalId}/availability`  
Restituisce al cliente collegato gli slot realmente prenotabili di un professionista.

Vengono restituiti solo slot:

- attivi;
- in stato `AVAILABLE`;
- con data iniziale futura;
- senza una richiesta booking `PENDING` attiva collegata.

### 9.4 Aggiornamento slot disponibilità
**PATCH** `/api/v1/availability/{slotId}`  
Aggiorna parzialmente data/ora di uno slot appartenente al professionista autenticato.

L’aggiornamento è consentito solo se:

- lo slot appartiene al professionista autenticato;
- lo slot è `AVAILABLE`;
- il nuovo intervallo è valido e futuro;
- il nuovo intervallo non genera sovrapposizioni;
- non esiste una richiesta booking `PENDING` attiva collegata allo slot;
- lo slot non è mai stato coinvolto in una richiesta booking.

Uno slot già collegato ad almeno una richiesta booking non può essere ripianificato modificandone data o ora, anche se la richiesta è stata successivamente rifiutata o cancellata.

Per proporre una disponibilità in un nuovo intervallo temporale, il professionista deve creare un nuovo slot.

### 9.5 Blocco slot disponibilità
**PATCH** `/api/v1/availability/{slotId}/block`  
Blocca uno slot disponibile appartenente al professionista autenticato.

Il blocco non è consentito se sullo slot esiste una richiesta booking `PENDING` attiva.  
In tale situazione il professionista deve prima gestire la richiesta pendente tramite il flusso Booking previsto.

### 9.6 Sblocco slot disponibilità
**PATCH** `/api/v1/availability/{slotId}/unblock`  
Sblocca uno slot bloccato appartenente al professionista autenticato.

### 9.7 Regole attualmente implementate

Le operazioni Availability applicano i seguenti controlli:

- solo il professionista autenticato può creare e gestire i propri slot;
- solo un professionista con specializzazione `PERSONAL_TRAINER` può creare e gestire slot availability;
- il professionista deve avere account attivo, email verificata e profilo attivo;
- un cliente può leggere gli slot disponibili solo di un professionista a lui collegato;
- l’intervallo temporale deve essere valido;
- uno slot creato o aggiornato deve iniziare nel futuro;
- non sono ammessi slot sovrapposti per lo stesso professionista;
- solo slot `AVAILABLE` possono essere aggiornati o bloccati;
- solo slot `BLOCKED` possono essere sbloccati;
- uno slot con richiesta booking `PENDING` attiva non può essere modificato;
- uno slot con richiesta booking `PENDING` attiva non può essere bloccato;
- la lettura lato cliente esclude gli slot `AVAILABLE` ormai scaduti.
- solo il professionista autenticato può creare e gestire i propri slot
- il professionista deve avere account attivo, email verificata e profilo attivo
- un cliente può leggere gli slot disponibili solo di un professionista a lui collegato
- l’intervallo temporale deve essere valido
- uno slot creato o aggiornato deve iniziare nel futuro
- non sono ammessi slot sovrapposti per lo stesso professionista
- solo slot `AVAILABLE` possono essere aggiornati o bloccati
- solo slot `BLOCKED` possono essere sbloccati
- la lettura lato cliente esclude gli slot `AVAILABLE` ormai scaduti;
- la lettura lato cliente esclude gli slot che hanno già una richiesta booking `PENDING` attiva.
- uno slot già coinvolto in una richiesta booking non può essere ripianificato modificandone data o ora;
- la regola di immutabilità temporale preserva lo storico della richiesta originaria;
- dopo un booking rifiutato o cancellato, lo slot può essere nuovamente prenotabile solo sullo stesso intervallo temporale originario.

---

## 10. Modulo Bookings

### 10.1 Creazione richiesta prenotazione
**POST** `/api/v1/bookings`  
Permette al cliente autenticato di creare una richiesta di prenotazione su uno slot disponibile di un professionista collegato.

Regole attuali:

- la richiesta viene creata a partire da un singolo `availabilitySlotId`;
- il cliente deve essere collegato al professionista proprietario dello slot;
- lo slot deve appartenere a un professionista `PERSONAL_TRAINER`;
- lo slot deve essere attivo, `AVAILABLE` e non scaduto;
- non deve esistere già una richiesta `PENDING` attiva sullo stesso slot;
- la `note` è facoltativa;
- la `note`, se presente, viene normalizzata rimuovendo gli spazi iniziali e finali;
- una `note` vuota dopo la normalizzazione viene salvata come assente;
- la `note` non può superare `1000` caratteri.

### Integrità storica dello slot

Quando uno slot viene utilizzato in una richiesta booking, il relativo intervallo temporale diventa parte dello storico della richiesta.

Anche in caso di booking successivamente `REJECTED` o `CANCELLED`, lo slot non può essere modificato in data o ora. Può eventualmente ricevere nuove richieste sul medesimo intervallo, se ancora prenotabile.

### 10.2 Elenco prenotazioni del cliente autenticato
**GET** `/api/v1/bookings/client`  
Restituisce le richieste di prenotazione del cliente autenticato.

### 10.3 Elenco prenotazioni del professionista autenticato
**GET** `/api/v1/bookings/professional`  
Restituisce le richieste di prenotazione ricevute dal professionista autenticato.

### 10.4 Dettaglio richiesta prenotazione
**GET** `/api/v1/bookings/{bookingRequestId}`  
Restituisce il dettaglio di una richiesta solo se l’utente autenticato è autorizzato.

### 10.5 Conferma richiesta prenotazione
**PATCH** `/api/v1/bookings/{bookingRequestId}/confirm`  
Permette al professionista coinvolto di confermare una richiesta `PENDING`.

La conferma è consentita solo se:

- la richiesta appartiene al professionista autenticato;
- la richiesta è ancora `PENDING`;
- lo slot appartiene a un professionista `PERSONAL_TRAINER`;
- lo slot è ancora `AVAILABLE`;
- lo slot non è scaduto.

Quando la richiesta viene confermata:

- il booking passa a `CONFIRMED`;
- lo slot collegato passa a `BOOKED`.

### 10.6 Rifiuto richiesta prenotazione
**PATCH** `/api/v1/bookings/{bookingRequestId}/reject`  
Permette al professionista proprietario dello slot di rifiutare una richiesta `PENDING`.

Quando la richiesta viene rifiutata:
- la booking passa a `REJECTED`
- lo slot resta disponibile se non era già occupato

### 10.7 Cancellazione richiesta prenotazione
**PATCH** `/api/v1/bookings/{bookingRequestId}/cancel`  
Permette la cancellazione di una richiesta secondo le regole di autorizzazione definite nel service.

Regole attuali:
- il cliente può cancellare una richiesta `PENDING`
- il cliente può cancellare una richiesta `CONFIRMED`
- il professionista proprietario può cancellare una richiesta `CONFIRMED`

Quando una richiesta `CONFIRMED` viene cancellata:
- la booking passa a `CANCELLED`
- lo slot collegato torna `AVAILABLE`

---

## 11. Regole generali di accesso

### 11.1 Endpoint pubblici
Attualmente sono pubblici gli endpoint sotto:

`/api/v1/auth/**`

In particolare:
- `POST /api/v1/auth/register/professional`
- `POST /api/v1/auth/register/client`
- `POST /api/v1/auth/register/client/validate-invite`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/verify-email`

### 11.2 Endpoint protetti
Tutti gli altri endpoint richiedono autenticazione valida tramite JWT.

### 11.3 Regole per area
- `/api/v1/clients/**` → solo `PROFESSIONAL`
- `/api/v1/professionals/**` → solo `CLIENT`
- `/api/v1/me/**` → utente autenticato
- `/api/v1/invites/**` → solo `PROFESSIONAL`, con controlli business aggiuntivi lato service
- `/api/v1/availability/**` → solo `PROFESSIONAL`, con controlli business aggiuntivi lato service

### Booking

- `POST /api/v1/bookings` → solo `CLIENT`
- `GET /api/v1/bookings/client` → solo `CLIENT`
- `GET /api/v1/bookings/professional` → solo `PROFESSIONAL`
- `PATCH /api/v1/bookings/{bookingRequestId}/confirm` → solo `PROFESSIONAL`
- `PATCH /api/v1/bookings/{bookingRequestId}/reject` → solo `PROFESSIONAL`
- `GET /api/v1/bookings/{bookingRequestId}` → utente autenticato, con controllo accesso nel service
- `PATCH /api/v1/bookings/{bookingRequestId}/cancel` → utente autenticato, con controllo accesso e transizione nel service

---

## 12. Nota metodologica
Questa mappa rappresenta **solo lo stato reale attuale** del backend.

Per ogni endpoint, nei documenti tecnici di dettaglio o nei prossimi sprint andranno eventualmente definiti meglio:
- request DTO
- response DTO
- codici HTTP attesi
- casi di errore
- regole di autorizzazione più granulari

---

## 13. Decisioni confermate
Per Support Trainer si confermano le seguenti scelte:

- prefisso globale `/api/v1`
- area `/me` per operazioni sul proprio account/profilo
- separazione tra endpoint pubblici e protetti
- lettura relazioni professionista-cliente già disponibile
- inviti già esposti come modulo reale
- endpoint futuri mantenuti fuori da questa mappa, in documento separato
- modulo availability implementato con creazione, lettura, update, block e unblock degli slot
- modulo bookings implementato con creazione, lettura, conferma, rifiuto e cancellazione delle richieste
- creazione booking attualmente basata su un singolo slot
- regole di ruolo Booking esplicitate in `SecurityConfig`
- ownership delle risorse e transizioni di stato controllate nel service layer
- Availability valida che gli slot creati o modificati inizino nel futuro
- Availability e Bookings basati su slot sono riservati ai professionisti `PERSONAL_TRAINER`;
- gli slot availability scaduti non vengono mostrati al cliente;
- booking e conferma booking non sono consentiti su slot scaduti;
- uno slot con booking `PENDING` attivo non può essere modificato o bloccato manualmente;
- non è consentita una seconda richiesta `PENDING` attiva sullo stesso slot.
- uno slot con booking `PENDING` attivo non viene più esposto come disponibilità prenotabile al cliente.
- uno slot già coinvolto in una richiesta booking mantiene immutabile il proprio intervallo temporale;
- la ripianificazione richiede la creazione di un nuovo slot availability;
- questa regola impedisce che lo storico booking mostri date diverse da quelle originariamente selezionate dal cliente.
