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

Il conteggio è di **36 endpoint applicativi**: Auth 8, Me 4, Client 2, Professional 3, Invite 2, Availability 10 e Booking 7. `/error` è un endpoint tecnico di fallback e non è contato fra le API funzionali.

Gli endpoint futuri o ancora da definire non vengono elencati qui.  
Devono essere mantenuti in un documento separato dedicato agli endpoint pianificati.

---

## 4. Modulo Auth

Le mutazioni Auth richiedono header CSRF ottenuto da `GET /api/v1/auth/csrf` (tipicamente `X-CSRF-TOKEN`). Non esiste autenticazione Bearer/JWT.

### 4.1 Token CSRF
**GET** `/api/v1/auth/csrf`
Restituisce `{ "token": "...", "headerName": "X-CSRF-TOKEN" }` con `Cache-Control: no-store`. Il client deve usare `headerName` come nome dell’header sulle mutazioni. Dopo un login riuscito il CSRF è ruotato: va richiesto di nuovo.

### 4.2 Registrazione professionista
**POST** `/api/v1/auth/register/professional`  
Valida una nuova registrazione professionista e restituisce sempre `202 Accepted` con un DTO neutro. Lo stesso status e payload vengono restituiti per un'email già esistente: non sono esposti ID, ruolo, email, token o `EMAIL_ALREADY_REGISTERED`. Solo una nuova email crea profilo pending, token e richiesta email after-commit. Richiede CSRF.

### 4.3 Login
**POST** `/api/v1/auth/login`  
Autentica l’utente con email/password e CSRF. Risponde `204 No Content` senza body: nessun `accessToken`, `refreshToken` o `Authorization`. Imposta il cookie di sessione HttpOnly (`__Host-STSESSION` in produzione, `STSESSION` in locale/test). Eligibilità: account `ACTIVE` e `emailVerified=true`; `profile.active=false` non blocca il login. Dopo il login il client deve richiamare `GET /csrf` e fare bootstrap con `GET /api/v1/me/account` e `GET /api/v1/me/profile`.

### 4.4 Logout
**POST** `/api/v1/auth/logout`
Invalida la sessione server-side. Richiede CSRF e risponde `204 No Content`.

### 4.5 Conferma email uniforme
**POST** `/api/v1/auth/email-verification/confirm`
Conferma l’account professionista o cliente tramite body JSON `{"token":"..."}`. Il token è obbligatorio, non blank e lungo al massimo 500 caratteri. Il primo consumo valido restituisce `200`, attiva l'account e marca il token usato; il secondo consumo coerente restituisce nuovamente `200` senza cambiare `usedAt`. Token inesistente e scaduto producono rispettivamente `404` e `410 Gone`. Il precedente `GET /api/v1/auth/verify-email` non è più esposto. Richiede CSRF.

### 4.6 Reinvio verifica email
**POST** `/api/v1/auth/email-verification/resend`
Accetta `{"email":"utente@example.com"}` e restituisce sempre `202 Accepted` con messaggio neutro per richieste sintatticamente valide. Supporta entrambi i ruoli senza rivelare esistenza, stato, cooldown o creazione del token. Solo account pending, non verificati e con profilo attivo generano un nuovo token; il cooldown è 60 secondi dal token più recente e termina al boundary esatto. I precedenti token non usati vengono invalidati tramite `used/usedAt`, lasciando un solo token utilizzabile per 24 ore. Nessun token viene restituito o registrato; invito e link restano invariati. Richiede CSRF.

Per registrazione professionista, registrazione cliente e reinvio idoneo, il backend pubblica la richiesta di consegna dentro la transazione e la esegue soltanto dopo il commit. Il link destinato al frontend ha forma `{verification-page-url}#token={tokenEncoded}`. Un errore del sender non cambia il `202 Accepted`; non esiste alcun endpoint per consultare i messaggi in-memory.

### 4.7 Validazione codice invito cliente
**POST** `/api/v1/auth/register/client/validate-invite`  
Verifica che il codice invito esista, sia attivo, non sia scaduto e non sia già usato. Richiede CSRF.

### 4.8 Registrazione cliente con invito
**POST** `/api/v1/auth/register/client`  
Completa la registrazione cliente usando un codice invito valido e restituisce sempre lo stesso `202 Accepted` neutro. L'invito viene validato prima del controllo email; per un'email già esistente, con invito ancora valido, non vengono creati profilo, link, token o messaggio e l'invito non viene consumato. Per una nuova email cliente, link, consumo invito e token email sono atomici; il login resta vietato fino alla conferma. Richiede CSRF.

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
Aggiorna i dati modificabili del profilo dell’utente autenticato.

### Regole URL profilo professionista

Per i campi facoltativi:

- `instagramUrl`;
- `websiteUrl`;

il comportamento implementato è:

| Valore inviato | Risultato |
|---|---|
| campo omesso oppure `null` | il valore esistente non viene modificato |
| valore che inizia con `http://` oppure `https://` | il valore viene validato e salvato |
| stringa vuota oppure composta solo da spazi | il valore esistente viene rimosso e salvato come `null` |
| valore senza protocollo valido | la richiesta viene rifiutata per errore di validazione |

Questa regola permette al frontend di distinguere tra:

- link non modificato;
- link aggiornato;
- link rimosso esplicitamente dall’utente.

### 5.4 Aggiornamento stato operativo
**PATCH** `/api/v1/me/profile/operational-status`  
Aggiorna lo stato operativo dell’utente autenticato.

---

## 6. Modulo Clients

### 6.1 Elenco clienti del professionista autenticato
**GET** `/api/v1/clients/my`  
Restituisce l’elenco clienti collegati al professionista autenticato con il seguente payload minimo per elemento:

- `id`;
- `firstName`;
- `lastName`;
- `profileImageUrl`.

La lista non espone obiettivo, stato operativo, stato tecnico, dati fisici o note. Il frontend la usa nella route implementata `/app/professional/clients` per PT e nutrizionisti.

### 6.2 Dettaglio cliente
**GET** `/api/v1/clients/{clientId}`  
Restituisce il dettaglio di un cliente solo se una ricerca scoped trova ID, professionista autenticato, collegamento attivo e stati leggibili. ID inesistente, collegamento assente o inattivo e profilo non leggibile producono lo stesso `404 CLIENT_NOT_FOUND`; un principal con ruolo `CLIENT` riceve invece `403`.

Il payload di successo contiene esclusivamente:

- `id`;
- `firstName`;
- `lastName`;
- `profileImageUrl`;
- `primaryGoal`;
- `operationalStatus`;
- `birthDate`;
- `heightCm`;
- `gender`.

PT e nutrizionista usano lo stesso contratto. Il link attivo autorizza il profilo condiviso approvato, non l'intero profilo personale: note sensibili, dati account, stato tecnico e dati del collegamento restano esclusi. Il frontend usa il dettaglio nella route implementata `/app/professional/clients/:clientId`.

---

## 7. Modulo Professionals

### 7.1 Professionisti collegati al cliente autenticato
**GET** `/api/v1/professionals/my`  
Restituisce i professionisti collegati al cliente autenticato con `id`, `firstName`, `lastName`, `profileImageUrl`, `specialization`, `operationalStatus` e il flag tecnico `active`. Il frontend valida il payload ma non presenta `active`; la lista è implementata in `/app/client/professionals`.

### 7.2 Dettaglio professionista
**GET** `/api/v1/professionals/{professionalId}`  
Restituisce il dettaglio di un professionista solo se una ricerca scoped trova ID, cliente autenticato, collegamento attivo e stati leggibili. ID inesistente, collegamento assente o inattivo e profilo non leggibile producono lo stesso `404 PROFESSIONAL_NOT_FOUND`; un principal con ruolo `PROFESSIONAL` riceve invece `403`.

Il payload aggiunge alla summary `phoneNumber`, `bio`, `workplaceName`, `city`, `instagramUrl` e `websiteUrl`. Il frontend usa il dettaglio nella route implementata `/app/client/professionals/:professionalId`, non mostra `active` e rende link esterni soltanto per URL HTTP/HTTPS validi.

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

### 9.1 Creazione regola settimanale

**POST** `/api/v1/availability/weekly-rules`
Crea una finestra ricorrente del Personal Trainer e materializza immediatamente una occorrenza per data nella rolling horizon oggi → sei mesi. Il payload contiene `allowedDurations`: insieme non vuoto di durate uniche, multiple di 15, comprese fra 15 e 180 minuti e contenute nella finestra. Gli estremi della finestra sono allineati a 15 minuti.

### 9.2 Elenco regole settimanali

**GET** `/api/v1/availability/weekly-rules/my`
Restituisce le regole attive del Personal Trainer autenticato.

### 9.3 Anteprima impatto modifica

**GET** `/api/v1/availability/weekly-rules/{ruleId}/impact`
Conta i Booking `PENDING` e `CONFIRMED` coinvolti da una modifica immediata, senza applicarla.

### 9.4 Modifica regola settimanale

**PUT** `/api/v1/availability/weekly-rules/{ruleId}`
Sostituisce immediatamente giorno, finestra, durate consentite, luogo e capacità per le occorrenze future. Se l'impatto è maggiore di zero, `changeReason` è obbligatorio. La capacità non può scendere sotto la massima occupancy concorrente delle finestre interessate.

### 9.5 Disattivazione regola settimanale

**PATCH** `/api/v1/availability/weekly-rules/{ruleId}/deactivate`
Disattiva immediatamente la ricorrenza futura senza eliminare o spostare Booking e senza riscrivere il passato. Anche questa operazione richiede `changeReason` quando coinvolge prenotazioni.

### 9.6 Creazione manuale slot — compatibilità legacy
**POST** `/api/v1/availability`  
Crea un singolo slot per il professionista autenticato. Resta disponibile per compatibilità, ma la nuova UI usa le regole settimanali.

Payload temporale:

```json
{
  "startDateTime": "2026-07-13T17:30:00+02:00",
  "endDateTime": "2026-07-13T18:30:00+02:00"
}
```

In inverno l'offset atteso è normalmente `+01:00`, per esempio `2026-01-13T17:30:00+01:00`. Lo stesso formato con offset è restituito dalle response.

### 9.7 Elenco slot del professionista autenticato
**GET** `/api/v1/availability/my`  
Restituisce gli slot di disponibilità del professionista autenticato.

### 9.8 Elenco slot disponibili di un professionista
**GET** `/api/v1/professionals/{professionalId}/availability`  
Restituisce al cliente collegato gli slot realmente prenotabili di un professionista.

Professional inesistente, inattivo, non verificato o non collegato al Client producono lo stesso `404 PROFESSIONAL_NOT_FOUND`; il ruolo errato o uno stato non idoneo del principal resta `403`.

Vengono restituiti solo slot:

- attivi;
- non bloccati;
- con data iniziale futura;
- per i quali esiste almeno una combinazione inizio/durata futura con capacità temporale disponibile, contando `PENDING` e `CONFIRMED`.

La response minimizzata contiene `occurrenceId`, `windowStart`, `windowEnd`, `allowedDurations`, `startIntervalMinutes`, `location`, `capacity` e `bookableOptions`. Ogni elemento di `bookableOptions` espone uno `startDateTime` e le sole `allowedDurations` ancora prenotabili per quello start; non espone occupancy, capacità residua o dati degli altri Client. Le opzioni sono calcolate server-side su un caricamento batch dei Booking occupanti.

### 9.9 Aggiornamento manuale slot — compatibilità legacy
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

Un'occorrenza generata da una regola settimanale non è mai ripianificabile tramite questo endpoint e produce `WEEKLY_AVAILABILITY_OCCURRENCE_NOT_PATCHABLE`.

Per proporre una disponibilità in un nuovo intervallo temporale, il professionista deve creare un nuovo slot.

### 9.10 Blocco slot disponibilità
**PATCH** `/api/v1/availability/{slotId}/block`  
Blocca una occorrenza futura appartenente al professionista autenticato. I Booking esistenti sono preservati; se sono presenti Booking `PENDING` o `CONFIRMED`, il body deve fornire `changeReason`. L'operazione viene auditata.

### 9.11 Sblocco slot disponibilità
**PATCH** `/api/v1/availability/{slotId}/unblock`  
Sblocca una occorrenza futura appartenente al professionista autenticato e registra l'audit.

### 9.12 Regole attualmente implementate

Le operazioni Availability applicano i seguenti controlli:

- solo il Personal Trainer autenticato può gestire regole e slot
- il professionista deve avere account attivo, email verificata e profilo attivo
- un cliente può leggere gli slot disponibili solo di un professionista a lui collegato
- slot inesistente e slot di altro Professional producono lo stesso `404 AVAILABILITY_SLOT_NOT_FOUND` sulle mutate; la query scoped acquisisce il lock solo dopo aver applicato la ownership
- l’intervallo temporale deve essere valido
- l'offset è obbligatorio e deve essere coerente con `Europe/Rome`;
- gap e overlap DST sono rifiutati, senza normalizzazione o scelta automatica dell'offset;
- regole dello stesso giorno non possono sovrapporsi, mentre possono essere adiacenti;
- ogni regola genera una sola occorrenza-finestra per data, non righe per tutte le combinazioni inizio/durata;
- inizi e durate sono multipli di 15 minuti; le durate ammesse sono 15–180 minuti e devono rientrare nella finestra;
- la materializzazione è idempotente, coordinata tramite lock con update/deactivate e usa una regola ricaricata dal database dopo il lock anche se la persistence context ne aveva precaricato una versione precedente;
- il caricamento iniziale PT sincronizza l'horizon tramite la lettura delle occurrence; l'elenco regole non avvia una seconda sincronizzazione;
- `PENDING` e `CONFIRMED` occupano capacità, `REJECTED` e `CANCELLED` la liberano;
- la capacità è verificata sugli intervalli temporali sovrapposti, non come contatore globale dell'occorrenza;
- la verifica della capacità e la creazione Booking sono atomiche sotto lock pessimista dell'occorrenza;
- il Client è lockato prima della creazione: due Booking occupanti dello stesso Client non possono sovrapporsi neppure in race;
- la precisione massima è al secondo e le frazioni non nulle sono rifiutate;
- inizio e fine sono confrontati come istanti;
- uno slot creato o aggiornato deve iniziare nel futuro
- non sono ammessi slot sovrapposti per lo stesso professionista
- solo slot manuali legacy `AVAILABLE` possono essere ripianificati; le occorrenze generate devono essere gestite dalla regola
- solo slot `BLOCKED` possono essere sbloccati
- la lettura lato cliente esclude gli slot `AVAILABLE` ormai scaduti;
- le letture operative e le azioni block/unblock escludono o rifiutano le occorrenze passate;
- uno slot già coinvolto in una richiesta booking non può essere ripianificato modificandone data o ora;
- la regola di immutabilità temporale preserva lo storico della richiesta originaria;
- dopo un booking rifiutato o cancellato, lo slot può essere nuovamente prenotabile solo sullo stesso intervallo temporale originario.

---

## 10. Modulo Bookings

Le liste restituiscono `BookingSummaryResponse`; create, dettaglio e mutazioni restituiscono `BookingDetailResponse`. I rispettivi orari `scheduledStart` e `scheduledEnd` sono snapshot storici, convertiti in `OffsetDateTime` con l'offset `Europe/Rome`; audit e timestamp di transizione sono `Instant` ISO-8601 UTC con `Z`. Nessuna response Booking dipende da `slotStatus` live.

### 10.1 Creazione richiesta prenotazione
**POST** `/api/v1/bookings`  
Permette al cliente autenticato di creare una richiesta di prenotazione su uno slot disponibile di un professionista collegato.

Slot inesistente, non collegato o non visibile al Client producono lo stesso `404 AVAILABILITY_SLOT_NOT_FOUND`; gli stati reali dello slot, come blocco, scadenza o conflitto, conservano i rispettivi errori business.

Regole attuali:

- la richiesta viene creata a partire da un singolo `availabilitySlotId`/`occurrenceId`;
- il Client invia sempre `startDateTime` e `durationMinutes`; la fine è derivata dal server;
- soltanto le occurrence generate da una regola settimanale accettano nuove prenotazioni; uno slot manuale legacy usa il contratto neutro `404 AVAILABILITY_SLOT_NOT_FOUND`, come una risorsa assente o non accessibile;
- l'inizio deve essere allineato a 15 minuti, la durata deve essere ammessa e l'intervallo deve rientrare nella finestra;
- gap e overlap DST sono rifiutati;
- lo stesso Client non può avere Booking `PENDING` o `CONFIRMED` temporalmente sovrapposti;
- la `note` è facoltativa
- la `note`, se presente, viene normalizzata rimuovendo gli spazi iniziali e finali
- una `note` vuota dopo la normalizzazione viene salvata come assente
- la `note` non può superare `1000` caratteri

### Integrità storica dello slot

Quando uno slot viene utilizzato in una richiesta booking, il relativo intervallo temporale diventa parte dello storico della richiesta.

Anche in caso di booking successivamente `REJECTED` o `CANCELLED`, lo slot non può essere modificato in data o ora. Può eventualmente ricevere nuove richieste sul medesimo intervallo, se ancora prenotabile.

### Contratto response

- `BookingSummaryResponse` contiene `id`, `status`, `counterparty`, `scheduledStart`, `scheduledEnd`, `durationMinutes`, `note` e `createdAt`.
- `BookingDetailResponse` contiene `id`, `status`, partecipanti, aggregati temporali, `note`, audit, timestamp di transizione e `items`.
- Un partecipante ha `id`, `displayName` storico, `profileImageUrl` corrente opzionale e, solo per il professionista, `specialization` corrente.
- Un item ha `id`, `availabilitySlotId`, orari snapshot, durata e `locationLabel` snapshot. I nomi, gli orari, il luogo e la nota rendono la response autosufficiente; non sono esposti `primaryGoal`, dati sanitari, email, telefono o campi operativi dei profili.

Le liste sono ordinate per `createdAt DESC, id DESC`; paginazione, filtri e ricerca sono rinviati. Lo storico creato resta leggibile dai partecipanti originari anche dopo la disattivazione del `ProfessionalClientLink`, che resta invece obbligatorio per creare nuove richieste.

### 10.2 Elenco prenotazioni del cliente autenticato
**GET** `/api/v1/bookings/client`  
Restituisce le richieste di prenotazione del cliente autenticato.

### 10.3 Elenco prenotazioni del professionista autenticato
**GET** `/api/v1/bookings/professional`  
Restituisce le richieste di prenotazione ricevute dal professionista autenticato.

### 10.4 Dettaglio richiesta prenotazione
**GET** `/api/v1/bookings/{bookingRequestId}`  
Restituisce il dettaglio solo a Client o Professional partecipanti. Booking inesistente e booking di un estraneo producono lo stesso `404 BOOKING_REQUEST_NOT_FOUND`, anche per la cancellazione; lo storico resta accessibile ai partecipanti dopo la disattivazione del link.

### 10.5 Conferma richiesta prenotazione
**PATCH** `/api/v1/bookings/{bookingRequestId}/confirm`  
Permette al professionista proprietario dello slot di confermare una richiesta `PENDING`.

Quando la richiesta viene confermata:
- la booking passa a `CONFIRMED`
- l'occupancy non cambia perché il posto era già riservato in `PENDING`
- `confirmedAt` viene valorizzato e la response restituisce il dettaglio completo

### 10.6 Rifiuto richiesta prenotazione
**PATCH** `/api/v1/bookings/{bookingRequestId}/reject`  
Permette al professionista proprietario dello slot di rifiutare una richiesta `PENDING`.

Quando la richiesta viene rifiutata:
- la booking passa a `REJECTED`
- il posto occupato dal `PENDING` viene liberato
- `rejectedAt` viene valorizzato e la response restituisce il dettaglio completo

### 10.7 Cancellazione richiesta prenotazione
**PATCH** `/api/v1/bookings/{bookingRequestId}/cancel`  
Permette la cancellazione di una richiesta secondo le regole di autorizzazione definite nel service.

Regole attuali:
- il cliente può cancellare una richiesta `PENDING`
- il cliente può cancellare una richiesta `CONFIRMED`
- il professionista proprietario può cancellare una richiesta `CONFIRMED`

Quando una richiesta `CONFIRMED` viene cancellata:
- la booking passa a `CANCELLED`
- il posto occupato viene liberato senza mutare uno stato binario dello slot

Ogni cancellazione consentita valorizza `cancelledAt`, conserva un eventuale `confirmedAt` già presente e restituisce il dettaglio completo. Non sono ancora previsti motivo o autore della cancellazione.

---

## 11. Regole generali di accesso

### 11.1 Endpoint pubblici
Attualmente sono pubblici gli endpoint sotto:

`/api/v1/auth/**`

In particolare:
- `GET /api/v1/auth/csrf`
- `POST /api/v1/auth/register/professional`
- `POST /api/v1/auth/register/client`
- `POST /api/v1/auth/register/client/validate-invite`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/email-verification/confirm`
- `POST /api/v1/auth/email-verification/resend`

Le mutazioni pubbliche e protette richiedono CSRF. Il logout è pubblico come URL ma invalida la sessione corrente quando presente.

### 11.2 Endpoint protetti
Tutti gli altri endpoint richiedono una sessione autenticata valida (cookie HttpOnly + readiness). Non si usa `Authorization: Bearer`.

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

### 11.4 Contratto uniforme degli errori

Endpoint, entry point Security, handler Access Denied e fallback `/error` restituiscono lo stesso JSON: `timestamp` UTC, `status`, `code`, `message` e `path` senza query. Solo `VALIDATION_ERROR` aggiunge `fieldErrors`, lista ordinata di `{field, code, message}` che può contenere più errori sullo stesso campo o un errore globale senza `field`.

I codici dominio 4xx restano invariati. I casi framework usano `MALFORMED_REQUEST`, `MISSING_REQUEST_PARAMETER`, `INVALID_REQUEST_PARAMETER`, `RESOURCE_NOT_FOUND`, `METHOD_NOT_ALLOWED`, `NOT_ACCEPTABLE` e `UNSUPPORTED_MEDIA_TYPE`. Le 401 tipiche usano `UNAUTHORIZED` e **non** includono `WWW-Authenticate: Bearer`. Il login errato mantiene `AUTHENTICATION_ERROR`. Un CSRF non valido produce `403 CSRF_VALIDATION_FAILED`. Le 405 conservano `Allow`, le 415 i media type supportati quando disponibili. Ogni 500 è esposto come `INTERNAL_SERVER_ERROR` con messaggio generico e senza dettagli interni.

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
- modulo Availability implementato con finestre settimanali, durate multiple, rolling horizon, capacità temporale e blocco auditato delle occorrenze
- modulo bookings implementato con creazione, lettura, conferma, rifiuto e cancellazione delle richieste
- creazione booking basata su una singola occorrenza con inizio e durata selezionati
- regole di ruolo Booking esplicitate in `SecurityConfig`
- ownership delle risorse e transizioni di stato controllate nel service layer
- Availability valida che gli slot creati o modificati inizino nel futuro
- il Client vede soltanto finestre con almeno una combinazione prenotabile e un payload minimizzato senza occupancy o dati sugli altri partecipanti;
- le modifiche alle regole non eliminano né spostano Booking e conservano la motivazione quando hanno impatto;
- gli snapshot Booking impediscono che lo storico mostri orari o luogo diversi da quelli originariamente selezionati;
- `instagramUrl` e `websiteUrl` del profilo professionista, se valorizzati, devono iniziare con `http://` oppure `https://`;
- nel `PATCH /api/v1/me/profile`, l’invio di un valore vuoto per `instagramUrl` o `websiteUrl` rimuove il link precedentemente salvato;
- il frontend dovrà gestire separatamente valore invariato, nuovo URL e rimozione esplicita del link.

## 21. Formati temporali delle response

- `createdAt`, `updatedAt`, `expiresAt` e `usedAt` esposti da account, booking e inviti sono `Instant` ISO-8601 UTC con `Z`;
- gli orari degli slot Availability e degli item Booking restano `OffsetDateTime` con l'offset valido di `Europe/Rome` e precisione al secondo;
- le date civili, inclusa `birthDate`, restano `LocalDate`;
- il frontend non deve applicare una timezone globale ai payload: può localizzare gli `Instant` per la presentazione, ma deve preservare gli offset degli slot ricevuti dal backend.
