# Sprint 03 — Profile + Clients + Professionals Read

## 1. Obiettivo dello sprint
Questo sprint ha lo scopo di costruire il primo livello di consultazione reale del sistema dopo autenticazione, verifica email, inviti e collegamenti tra utenti.

Alla fine dello sprint il backend dovrà permettere di:

- recuperare i dati del profilo dell’utente autenticato
- aggiornare i dati base del proprio profilo
- aggiornare lo stato operativo del proprio profilo
- permettere al professionista di vedere i clienti collegati
- permettere al cliente di vedere i professionisti collegati
- leggere il dettaglio di un cliente o di un professionista solo se autorizzati

---

## 2. Perché questo sprint è importante
Questo sprint è importante perché consolida la parte già costruita e la rende davvero utilizzabile.

Fino ad ora il sistema sa:
- autenticare utenti
- verificare professionisti
- generare inviti
- registrare clienti
- creare collegamenti professionista-cliente

Adesso serve fare il passo successivo: permettere agli utenti di **leggere e gestire i dati principali** in modo coerente con i collegamenti creati.

In pratica, questo sprint rende il sistema:
- più navigabile
- più realistico
- più vicino a un’app usabile davvero

---

## 3. Risultato atteso
Al termine di questo sprint devo poter testare questi flussi:

### Lato utente autenticato
- leggere il proprio profilo
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
- upload immagine profilo avanzato, se non già necessario
- gestione admin

---

## 5. Moduli coinvolti
In questo sprint si lavora soprattutto su:

- `profile`
- `client`
- `professional`
- `link`

Con possibile supporto di:
- `auth`
- `security`

solo se servono adattamenti minimi ai controlli di accesso.

---

## 6. Obiettivo funzionale della parte
Questa parte del progetto serve a costruire le API di lettura e gestione base dei profili e delle relazioni.

### In pratica deve permettere di:
- capire chi è l’utente autenticato
- vedere i dati rilevanti del proprio profilo
- distinguere chiaramente cliente e professionista
- usare il collegamento professionista-cliente per autorizzare la lettura dei dati
- preparare il terreno per availability, bookings, schede e piani

### Riassunto semplice
Lo Sprint 03 non aggiunge ancora nuove funzioni “pesanti”, ma rende finalmente il sistema **consultabile e navigabile** in modo corretto.

---

## 7. Regole business principali dello sprint

### 7.1 Profilo utente autenticato
Ogni utente autenticato deve poter:
- recuperare i propri dati base
- aggiornare i propri dati modificabili
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

La specializzazione del professionista resta rilevante come dato business, ma in questo sprint l’obiettivo principale è la lettura corretta delle informazioni.

---

## 8. Endpoint previsti nello sprint

## Area Profile / Me
- **GET** `/api/v1/me/profile`
- **PATCH** `/api/v1/me/profile`
- **PATCH** `/api/v1/me/profile/operational-status`
- **GET** `/api/v1/me/account`

## Area Clients
- **GET** `/api/v1/clients/my`
- **GET** `/api/v1/clients/{clientId}`

## Area Professionals
- **GET** `/api/v1/professionals/my`
- **GET** `/api/v1/professionals/{professionalId}`

### Nota
In questo sprint il focus è soprattutto su:
- lettura liste
- lettura dettaglio
- aggiornamento proprio profilo

Non serve aggiungere adesso endpoint inutili o troppo avanzati.

---

## 9. Blocco A — Profile / Me

- [ ] Creare response DTO profilo/account autenticato
- [ ] Implementare endpoint `GET /api/v1/me/profile`
- [ ] Implementare endpoint `GET /api/v1/me/account`
- [ ] Creare request DTO update profilo
- [ ] Implementare endpoint `PATCH /api/v1/me/profile`
- [ ] Creare request DTO update stato operativo
- [ ] Implementare endpoint `PATCH /api/v1/me/profile/operational-status`

### Definition of Done
- l’utente autenticato può leggere il proprio profilo
- l’utente autenticato può leggere i dati account base
- l’utente autenticato può aggiornare i propri dati consentiti
- l’utente autenticato può aggiornare il proprio stato operativo

---

## 10. Blocco B — Clients read

- [ ] Implementare elenco clienti del professionista autenticato
- [ ] Implementare endpoint `GET /api/v1/clients/my`
- [ ] Creare response DTO lista clienti
- [ ] Creare response DTO dettaglio cliente
- [ ] Implementare endpoint `GET /api/v1/clients/{clientId}`
- [ ] Applicare controllo che il cliente richiesto sia collegato al professionista autenticato

### Definition of Done
- il professionista vede solo i propri clienti collegati
- il dettaglio cliente è accessibile solo se il collegamento è valido
- i clienti non collegati vengono bloccati con errore corretto

---

## 11. Blocco C — Professionals read

- [ ] Implementare elenco professionisti del cliente autenticato
- [ ] Implementare endpoint `GET /api/v1/professionals/my`
- [ ] Creare response DTO lista professionisti
- [ ] Creare response DTO dettaglio professionista
- [ ] Implementare endpoint `GET /api/v1/professionals/{professionalId}`
- [ ] Applicare controllo che il professionista richiesto sia collegato al cliente autenticato

### Definition of Done
- il cliente vede solo i propri professionisti collegati
- il dettaglio professionista è accessibile solo se il collegamento è valido
- i professionisti non collegati vengono bloccati con errore corretto

---

## 12. Blocco D — Regole di autorizzazione e coerenza

- [ ] Verificare che gli endpoint `/clients/my` siano accessibili solo a `ROLE_PROFESSIONAL`
- [ ] Verificare che gli endpoint `/professionals/my` siano accessibili solo a `ROLE_CLIENT`
- [ ] Verificare che gli endpoint `/me/...` siano accessibili a utenti autenticati
- [ ] Spostare i controlli di ownership/relazione nel service layer
- [ ] Restituire errori coerenti per accessi non autorizzati

### Definition of Done
- ogni endpoint è coerente con il ruolo corretto
- le relazioni vengono controllate in service
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
I nomi finali possono adattarsi alla tua struttura reale, ma l’importante è mantenere:
- chiarezza
- separazione tra summary e detail
- DTO separati da entity JPA

---

## 14. Service da implementare o completare

### Profile
- `ProfileService`

### Clients
- `ClientService` oppure service equivalente già esistente

### Professionals
- `ProfessionalService` oppure service equivalente già esistente

### Link support
- usare `ProfessionalClientLinkRepository` per verificare i collegamenti attivi

---

## 15. Regole di lettura e aggiornamento

### 15.1 GET `/api/v1/me/profile`
Deve restituire i dati del profilo dell’utente autenticato in base al suo tipo:
- cliente
- professionista

### 15.2 PATCH `/api/v1/me/profile`
Deve aggiornare solo i campi consentiti.

Esempi:
- nome
- cognome
- bio
- city
- website
- primary goal
- note modificabili

### 15.3 PATCH `/api/v1/me/profile/operational-status`
Deve consentire l’aggiornamento del solo stato operativo coerente con il tipo di utente:
- professionista → `ProfessionalOperationalStatus`
- cliente → `ClientOperationalStatus`

### 15.4 GET `/api/v1/clients/my`
Deve leggere i clienti collegati al professionista autenticato usando il link attivo.

### 15.5 GET `/api/v1/professionals/my`
Deve leggere i professionisti collegati al cliente autenticato usando il link attivo.

### 15.6 GET dettaglio
Il dettaglio deve essere leggibile solo se:
- la relazione è attiva
- il ruolo è corretto
- la risorsa appartiene davvero alla rete di collegamenti dell’utente autenticato

---

## 16. Validazioni minime da implementare

### Profile update
- campi obbligatori non vuoti dove previsti
- formati validi per URL, se presenti
- valori enum consentiti per stato operativo

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
- relazione disattivata

---

## 17. Eccezioni applicative attese
Durante questo sprint dovranno essere gestiti in modo chiaro almeno questi errori:

- profilo utente non trovato
- cliente non trovato
- professionista non trovato
- accesso negato per ruolo errato
- accesso negato per relazione non valida
- stato operativo non valido
- update non consentito su campi non ammessi

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
- [ ] Testare `GET /me/profile`
- [ ] Testare `GET /me/account`
- [ ] Testare `PATCH /me/profile`
- [ ] Testare `PATCH /me/profile/operational-status`
- [ ] Testare `GET /clients/my`
- [ ] Testare `GET /clients/{id}` valido e non valido
- [ ] Testare `GET /professionals/my`
- [ ] Testare `GET /professionals/{id}` valido e non valido
- [ ] Testare accessi con ruolo errato
- [ ] Testare accessi senza collegamento attivo

---

## 19. Ordine consigliato di esecuzione
Per non perderti, l’ordine migliore è questo:

1. area `/me/profile`
2. area `/me/account`
3. update profilo
4. update stato operativo
5. elenco clienti del professionista
6. dettaglio cliente
7. elenco professionisti del cliente
8. dettaglio professionista
9. test completi

---

## 20. Definition of Done dello sprint
Lo sprint è completato solo se:

- l’utente autenticato può leggere e aggiornare il proprio profilo base
- il professionista può vedere solo i propri clienti collegati
- il cliente può vedere solo i propri professionisti collegati
- i dettagli sono visibili solo se la relazione è valida
- gli accessi non autorizzati vengono bloccati correttamente
- i test principali sono stati eseguiti con successo

---

## 21. Cose da NON fare in questo sprint
Per non complicarti inutilmente, evita di:

- iniziare availability
- iniziare bookings
- iniziare workout/nutrition
- iniziare feedback/measurements
- introdurre query troppo complesse se non servono
- creare DTO inutilmente giganteschi
- mischiare lettura profili con logiche future di agenda o contenuti

---

## 22. Output finale dello sprint
Alla fine di questo sprint dovresti avere:

- un modulo profile `/me` stabile
- lettura relazioni cliente-professionista funzionante
- controlli di autorizzazione concreti e realistici
- una base perfetta per gli sprint successivi su availability e bookings

---

## 23. Prossimo step dopo questo sprint
Una volta completato questo sprint, il passo successivo più naturale sarà:

- `docs/15-sprint-04-availability.md`