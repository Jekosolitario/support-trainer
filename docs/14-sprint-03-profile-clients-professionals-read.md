# Sprint 03 — Profile + Clients + Professionals Read

> **Documento storico.** Conserva scope e stato dello Sprint 03 (profilo, lettura clienti/professionisti). Non è una reference dello stato HEAD.
>
> Per endpoint, security e overview correnti consultare: [`08-endpoint-map.md`](./08-endpoint-map.md), [`09-security-flow.md`](./09-security-flow.md), [`README.md`](../README.md).

## 1. Obiettivo dello sprint
Questo sprint ha lo scopo di costruire il primo livello reale di consultazione e aggiornamento base del sistema dopo autenticazione, verifica email, inviti e collegamenti tra utenti.

Alla fine dello sprint il backend deve permettere di:

- recuperare i dati del profilo dell’utente autenticato
- aggiornare i dati base del proprio profilo
- aggiornare lo stato operativo del proprio profilo
- permettere al professionista di vedere i clienti collegati
- permettere al cliente di vedere i professionisti collegati
- leggere il dettaglio di un cliente o di un professionista solo se autorizzati

---

## 2. Perché questo sprint è importante
Questo sprint è importante perché consolida la parte già costruita e la rende davvero utilizzabile.

Fino a questo punto il sistema sa:
- autenticare utenti
- verificare professionisti
- generare inviti
- registrare clienti
- creare collegamenti professionista-cliente

Con questo sprint il sistema diventa finalmente:
- consultabile
- navigabile
- coerente con i collegamenti di dominio già creati
- pronto per funzionalità future più operative

---

## 3. Risultato atteso
Al termine di questo sprint devono essere testabili questi flussi:

### Lato utente autenticato
- leggere il proprio profilo
- leggere i propri dati account
- aggiornare i propri dati base
- aggiornare il proprio stato operativo

### Lato professionista
- vedere l’elenco dei clienti collegati
- leggere il dettaglio di un cliente collegato

### Lato cliente
- vedere l’elenco dei professionisti collegati
- leggere il dettaglio di un professionista collegato

---

## 4. Fuori scope di questo sprint
In questo sprint non si implementano ancora:

- availability
- bookings
- workout plans
- nutrition plans
- feedback
- measurements
- dashboard avanzate
- notifiche
- gestione admin
- logout dedicato
- differenziazione accessi per specializzazione
- logiche avanzate di upload immagine profilo

---

## 5. Moduli coinvolti
In questo sprint si lavora soprattutto su:

- `profile`
- `client`
- `professional`
- `link`

Con supporto di:
- `security`
- `common`

e con dipendenza dalla parte già esistente di:
- `auth`

---

## 6. Obiettivo funzionale della parte
Questa parte del progetto serve a costruire le API di lettura e gestione base dei profili e delle relazioni.

### In pratica deve permettere di:
- capire chi è l’utente autenticato
- vedere i dati rilevanti del proprio profilo
- vedere i dati account base del proprio utente
- distinguere chiaramente cliente e professionista
- usare il collegamento professionista-cliente per autorizzare la lettura dei dati
- preparare il terreno per moduli futuri come availability e bookings

### Riassunto semplice
Lo Sprint 03 non aggiunge ancora nuove funzioni operative pesanti, ma rende il sistema consultabile e navigabile in modo coerente con i ruoli e con i collegamenti attivi.

---

## 7. Regole business principali dello sprint

### 7.1 Profilo utente autenticato
Ogni utente autenticato deve poter:
- recuperare il proprio profilo
- recuperare i propri dati account
- aggiornare i propri dati consentiti
- aggiornare il proprio stato operativo

### 7.2 Lettura clienti da parte del professionista
Un professionista può vedere:
- solo i clienti collegati attivamente a lui

Non deve poter vedere:
- clienti non collegati
- dettagli di clienti fuori dalla propria relazione attiva

### 7.3 Lettura professionisti da parte del cliente
Un cliente può vedere:
- solo i professionisti collegati attivamente a lui

Non deve poter vedere:
- professionisti non collegati
- dettagli di professionisti fuori dalla propria relazione attiva

### 7.4 Separazione ruolo / specializzazione
I controlli devono rispettare:
- ruolo utente
- relazione attiva
- ownership logica della risorsa

La specializzazione del professionista resta un dato di dominio importante, ma in questo sprint non viene ancora usata per differenziare l’accesso agli endpoint.

---

## 8. Endpoint dello sprint

## Area Profile / Me
- **GET** `/api/v1/me/profile`
- **GET** `/api/v1/me/account`
- **PATCH** `/api/v1/me/profile`
- **PATCH** `/api/v1/me/profile/operational-status`

## Area Clients
- **GET** `/api/v1/clients/my`
- **GET** `/api/v1/clients/{clientId}`

## Area Professionals
- **GET** `/api/v1/professionals/my`
- **GET** `/api/v1/professionals/{professionalId}`

### Nota
Il focus dello sprint è su:
- lettura profilo
- lettura account
- aggiornamento profilo
- aggiornamento stato operativo
- lettura lista relazioni
- lettura dettaglio relazioni autorizzate

---

## 9. Blocco A — Profile / Me

- [ ] Creare `MyProfileResponse`
- [ ] Creare `MyAccountResponse`
- [ ] Creare `UpdateMyProfileRequest`
- [ ] Creare `UpdateOperationalStatusRequest`
- [ ] Implementare `GET /api/v1/me/profile`
- [ ] Implementare `GET /api/v1/me/account`
- [ ] Implementare `PATCH /api/v1/me/profile`
- [ ] Implementare `PATCH /api/v1/me/profile/operational-status`

### Definition of Done
- l’utente autenticato può leggere il proprio profilo
- l’utente autenticato può leggere i propri dati account base
- l’utente autenticato può aggiornare i propri dati consentiti
- l’utente autenticato può aggiornare il proprio stato operativo

---

## 10. Blocco B — Clients read

- [ ] Implementare elenco clienti del professionista autenticato
- [ ] Implementare `GET /api/v1/clients/my`
- [ ] Creare `ClientSummaryResponse`
- [ ] Creare `ClientDetailResponse`
- [ ] Implementare `GET /api/v1/clients/{clientId}`
- [ ] Applicare controllo che il cliente richiesto sia collegato al professionista autenticato

### Definition of Done
- il professionista vede solo i propri clienti collegati
- il dettaglio cliente è accessibile solo se il collegamento è valido
- i clienti non collegati vengono bloccati con errore corretto

---

## 11. Blocco C — Professionals read

- [ ] Implementare elenco professionisti del cliente autenticato
- [ ] Implementare `GET /api/v1/professionals/my`
- [ ] Creare `ProfessionalSummaryResponse`
- [ ] Creare `ProfessionalDetailResponse`
- [ ] Implementare `GET /api/v1/professionals/{professionalId}`
- [ ] Applicare controllo che il professionista richiesto sia collegato al cliente autenticato

### Definition of Done
- il cliente vede solo i propri professionisti collegati
- il dettaglio professionista è accessibile solo se il collegamento è valido
- i professionisti non collegati vengono bloccati con errore corretto

---

## 12. Blocco D — Regole di autorizzazione e coerenza

- [ ] Verificare che gli endpoint `/api/v1/clients/**` siano accessibili solo a `PROFESSIONAL`
- [ ] Verificare che gli endpoint `/api/v1/professionals/**` siano accessibili solo a `CLIENT`
- [ ] Verificare che gli endpoint `/api/v1/me/**` siano accessibili a utenti autenticati
- [ ] Mantenere i controlli di ownership/relazione nel service layer
- [ ] Restituire errori coerenti per accessi non autorizzati

### Definition of Done
- ogni endpoint è coerente con il ruolo corretto
- le relazioni vengono controllate nel service layer
- accessi non validi sono bloccati in modo chiaro

---

## 13. DTO da creare

## Area profile
- `MyProfileResponse`
- `MyAccountResponse`
- `UpdateMyProfileRequest`
- `UpdateOperationalStatusRequest`

## Area clients
- `ClientSummaryResponse`
- `ClientDetailResponse`

## Area professionals
- `ProfessionalSummaryResponse`
- `ProfessionalDetailResponse`

### Nota
I DTO devono restare separati dalle entity JPA e distinguere chiaramente:
- response di profilo/account
- request di update
- summary response
- detail response

---

## 14. Service e controller da implementare o completare

### Profile
- `MeController`
- `MeService`

### Clients
- `ClientController`
- `ClientService`

### Professionals
- `ProfessionalController`
- `ProfessionalService`

### Link support
- usare `ProfessionalClientLinkRepository` per verificare i collegamenti attivi

---

## 15. Regole di lettura e aggiornamento

### 15.1 GET `/api/v1/me/profile`
Deve restituire i dati del profilo dell’utente autenticato in base al suo tipo:
- cliente
- professionista

### 15.2 GET `/api/v1/me/account`
Deve restituire i dati account base dell’utente autenticato:
- id
- email
- role
- accountStatus
- emailVerified
- createdAt
- updatedAt

### 15.3 PATCH `/api/v1/me/profile`
Deve aggiornare solo i campi consentiti.

#### Campi aggiornabili comuni
- `firstName`
- `lastName`

#### Campi aggiornabili professionista
- `phoneNumber`
- `bio`
- `workplaceName`
- `city`
- `instagramUrl`
- `websiteUrl`

#### Campi aggiornabili cliente
- `birthDate`
- `heightCm`
- `primaryGoal`
- `gender`
- `medicalNotes`
- `injuryNotes`
- `notes`

### 15.4 PATCH `/api/v1/me/profile/operational-status`
Deve consentire l’aggiornamento del solo stato operativo coerente con il tipo di utente:
- professionista → `ProfessionalOperationalStatus`
- cliente → `ClientOperationalStatus`

### 15.5 GET `/api/v1/clients/my`
Deve leggere i clienti collegati al professionista autenticato usando il link attivo.

### 15.6 GET `/api/v1/professionals/my`
Deve leggere i professionisti collegati al cliente autenticato usando il link attivo.

### 15.7 GET dettaglio
Il dettaglio deve essere leggibile solo se:
- la relazione è attiva
- il ruolo è corretto
- la risorsa appartiene davvero alla rete di collegamenti dell’utente autenticato

---

## 16. Validazioni implementate e consolidate

### Profile update

Risultano implementate le seguenti validazioni:

- limiti di lunghezza sui campi testuali;
- `birthDate` nel passato;
- `heightCm` maggiore di zero;
- `heightCm` con massimo 3 cifre intere e 2 decimali;
- `operationalStatus` obbligatorio nel relativo endpoint;
- validazione formale dei campi URL del profilo professionista.

### URL profilo professionista

I campi:

- `instagramUrl`;
- `websiteUrl`;

sono facoltativi e seguono queste regole nel `PATCH /api/v1/me/profile`:

| Valore inviato | Comportamento |
|---|---|
| campo omesso oppure `null` | il valore esistente non viene modificato |
| URL che inizia con `http://` oppure `https://` | il valore viene validato e salvato |
| stringa vuota oppure composta solo da spazi | il valore esistente viene rimosso e salvato come `null` |
| URL senza protocollo valido | la richiesta viene rifiutata per errore di validazione |

La rimozione esplicita tramite valore vuoto è stata introdotta durante la fase di riallineamento precedente alla progettazione frontend, così da permettere al form profilo di eliminare correttamente un link già salvato.

### Clients / Professionals read
- utente autenticato valido
- ruolo corretto
- risorsa esistente
- relazione attiva esistente

### Accessi non validi
Bloccare con errore corretto almeno:
- risorsa inesistente
- ruolo errato
- relazione non esistente

---

## 17. Eccezioni applicative attese
Durante questo sprint devono essere gestiti in modo chiaro almeno questi errori:

- utente autenticato non trovato
- utente non autenticato
- tipo utente non supportato
- cliente non trovato
- professionista non trovato
- accesso negato per ruolo errato
- accesso negato per relazione non valida
- stato operativo non valido
- richiesta non valida per campi vuoti dopo trim

---

## 18. Checklist operativa dello sprint

## Blocco A — Profile / Me
- [ ] Creare DTO profile/account
- [ ] Implementare `GET /api/v1/me/profile`
- [ ] Implementare `GET /api/v1/me/account`
- [ ] Creare DTO update profilo
- [ ] Implementare `PATCH /api/v1/me/profile`
- [ ] Creare DTO update stato operativo
- [ ] Implementare `PATCH /api/v1/me/profile/operational-status`

## Blocco B — Clients read
- [ ] Implementare lista clienti del professionista autenticato
- [ ] Implementare dettaglio cliente autorizzato
- [ ] Creare DTO summary/detail cliente

## Blocco C — Professionals read
- [ ] Implementare lista professionisti del cliente autenticato
- [ ] Implementare dettaglio professionista autorizzato
- [ ] Creare DTO summary/detail professionista

## Blocco D — Autorizzazione
- [ ] Verificare ruoli per endpoint
- [ ] Verificare controllo relazione attiva
- [ ] Gestire errori coerenti

## Blocco E — Test
- [ ] Testare `GET /api/v1/me/profile`
- [ ] Testare `GET /api/v1/me/account`
- [ ] Testare `PATCH /api/v1/me/profile`
- [ ] Testare `PATCH /api/v1/me/profile/operational-status`
- [ ] Testare `GET /api/v1/clients/my`
- [ ] Testare `GET /api/v1/clients/{id}` valido e non valido
- [ ] Testare `GET /api/v1/professionals/my`
- [ ] Testare `GET /api/v1/professionals/{id}` valido e non valido
- [ ] Testare accessi con ruolo errato
- [ ] Testare accessi senza collegamento attivo

---

## 19. Ordine consigliato di esecuzione
Per procedere con ordine, l’ordine consigliato è questo:

1. `GET /api/v1/me/profile`
2. `GET /api/v1/me/account`
3. `PATCH /api/v1/me/profile`
4. `PATCH /api/v1/me/profile/operational-status`
5. `GET /api/v1/clients/my`
6. `GET /api/v1/clients/{clientId}`
7. `GET /api/v1/professionals/my`
8. `GET /api/v1/professionals/{professionalId}`
9. test completi di autorizzazione e casi negativi

---

## 19.1 Remediation anti-enumerazione dei dettagli

Lo stato corrente applica una ricerca scoped ai due endpoint di dettaglio. Il repository dei collegamenti restituisce direttamente un profilo soltanto quando coincidono ID target, ID del principal, collegamento attivo e stati leggibili. Non viene caricato prima il profilo per stabilire se esiste e non viene eseguito un controllo separato del collegamento per produrre un errore differente.

Per `GET /api/v1/clients/{clientId}`, ID inesistente, cliente mai collegato al principal, collegamento inattivo e profilo non leggibile restituiscono lo stesso `404 CLIENT_NOT_FOUND`. Per `GET /api/v1/professionals/{professionalId}` gli stessi casi restituiscono `404 PROFESSIONAL_NOT_FOUND`. Il ruolo errato continua a essere respinto da Spring Security con 403 e una richiesta anonima con 401. Liste, DTO e campi restituiti non sono stati modificati da questa remediation.

## 19.2 Remediation successiva: minimizzazione del profilo cliente condiviso

Una remediation successiva mantiene invariata la ricerca scoped introdotta al punto precedente e restringe soltanto i DTO Clients:

- `ClientSummaryResponse`: `id`, `firstName`, `lastName`, `profileImageUrl`;
- `ClientDetailResponse`: gli stessi campi e `primaryGoal`.

Non vengono più restituiti ai professionisti stato operativo, flag `active`, nascita, altezza, genere, note mediche, note sugli infortuni, note generiche, stati account o audit. PT e nutrizionista ricevono lo stesso payload minimo. Profilo personale, registrazione, persistenza, Availability, Booking e Invite non cambiano.

---

## 20. Definition of Done dello sprint
Lo sprint è completato solo se:

- l’utente autenticato può leggere il proprio profilo base
- l’utente autenticato può leggere i propri dati account
- l’utente autenticato può aggiornare il proprio profilo base
- l’utente autenticato può aggiornare il proprio stato operativo
- il professionista può valorizzare `instagramUrl` e `websiteUrl` solo con URL che iniziano con `http://` o `https://`
- il professionista può rimuovere `instagramUrl` e `websiteUrl` inviando un valore vuoto nel form profilo
- il professionista può vedere solo i propri clienti collegati
- il cliente può vedere solo i propri professionisti collegati
- i dettagli sono visibili solo se la relazione è valida
- gli accessi non autorizzati vengono bloccati correttamente
- i test principali sono stati eseguiti con successo

---

## 20.1 Consolidamento successivo all’audit frontend

Durante la preparazione della progettazione frontend è stato chiarito il contratto dei campi URL del profilo professionista.

Sono stati aggiunti test automatici per verificare:

- rimozione di `instagramUrl` e `websiteUrl` tramite valori vuoti;
- rifiuto di URL privi di protocollo `http://` o `https://`.

Questa integrazione non modifica lo scope funzionale dello Sprint 03, ma rende completo e utilizzabile dal frontend il comportamento del form profilo.

---

## 21. Cose da NON fare in questo sprint
Per non complicare inutilmente questa fase, evitare di:

- iniziare availability
- iniziare bookings
- iniziare workout plans o nutrition plans
- iniziare feedback o measurements
- introdurre query più complesse del necessario
- creare DTO inutilmente troppo grandi
- mescolare la lettura profili con logiche future di agenda o contenuti
- introdurre regole di autorizzazione per specializzazione non ancora richieste

---

## 22. Output finale dello sprint
Alla fine di questo sprint il progetto deve avere:

- un modulo `/api/v1/me` stabile
- lettura relazioni cliente-professionista funzionante
- controlli di autorizzazione concreti e realistici
- una base pronta per gli sprint successivi

---

## 23. Prossimo step dopo questo sprint
Una volta completato questo sprint, il passo successivo naturale sarà aprire il nuovo sprint dedicato alle funzionalità operative successive, partendo da una documentazione coerente con la struttura ormai consolidata del progetto.
