# Backend Implementation Roadmap — Support Trainer

## 1. Obiettivo del documento
Questo documento definisce l’ordine corretto di implementazione del backend di Support Trainer.

Lo scopo è:
- evitare caos durante lo sviluppo
- costruire il progetto per fasi progressive
- ridurre il rischio di errori strutturali
- arrivare a un backend funzionante passo dopo passo

---

## 2. Principio guida
Il backend non va sviluppato “per moduli isolati tutti insieme”, ma per **strati progressivi**.

Ordine corretto:
1. base progetto
2. persistenza dati
3. sicurezza e autenticazione
4. registrazione e collegamenti
5. primi moduli business
6. moduli avanzati
7. rifinitura, test e preparazione al deploy

---

## 3. Strategia consigliata
Per ogni fase bisogna lavorare così:

1. definire entity e repository del blocco
2. creare service con business logic minima necessaria
3. creare controller e DTO
4. testare tutto con Postman
5. solo dopo passare al blocco successivo

### Regola importante
Non iniziare il frontend vero finché il blocco backend non è:
- coerente
- testato
- minimamente stabile

---

## 4. Ordine corretto di sviluppo

## FASE 0 — Setup iniziale progetto
### Obiettivo
Preparare il progetto Spring Boot in modo pulito.

### Da fare
- creazione progetto Spring Boot
- configurazione package base
- configurazione `application.properties`
- collegamento MySQL
- configurazione JPA/Hibernate
- gestione environment base
- creazione struttura package iniziale:
  - `config`
  - `security`
  - `common`
  - `auth`
  - `profile`
  - `professional`
  - `client`
  - `invite`
  - `link`
  - `availability`
  - `booking`
  - `workout`
  - `nutrition`
  - `feedback`
  - `measurement`

### Definition of Done
- progetto avviabile
- connessione DB funzionante
- struttura package pronta
- build pulita

---

## FASE 1 — Fondazioni tecniche
### Obiettivo
Costruire la base riutilizzabile del backend.

### Da fare
- `BaseEntity` con `id`, `createdAt`, `updatedAt`
- enum principali
- eccezioni custom base
- global exception handler
- response/error model coerente
- utility comuni
- configurazione CORS iniziale

### Definition of Done
- base tecnica pronta
- gestione errori già presente
- convenzioni riutilizzabili in tutto il progetto

---

## FASE 2 — Modello utenti
### Obiettivo
Implementare la gerarchia utenti e la persistenza base.

### Da fare
- `User`
- `ProfessionalProfile`
- `ClientProfile`
- strategia JPA `JOINED`
- repository utenti
- vincoli base su email
- test persistenza entity

### Definition of Done
- utenti salvabili correttamente
- gerarchia utenti stabile
- query base funzionanti

---

## FASE 3 — Sicurezza e autenticazione
### Obiettivo
Far funzionare accesso, ruoli e protezione endpoint.

### Da fare
- Spring Security config
- `PasswordEncoder`
- JWT access token
- refresh token
- filtro JWT
- `UserDetailsService`
- login
- endpoint protetti/pubblici
- ruoli:
  - `ROLE_PROFESSIONAL`
  - `ROLE_CLIENT`

### Definition of Done
- login funzionante
- token validi
- endpoint protetti correttamente
- accesso base funzionante con Postman

---

## FASE 4 — Verifica email e password reset
### Obiettivo
Chiudere bene il ciclo di accesso reale.

### Da fare
- `EmailVerificationToken`
- `PasswordResetToken`
- endpoint verify email
- endpoint forgot password
- endpoint reset password
- blocco professionista non verificato

### Definition of Done
- professionista registrabile ma non operativo finché non verifica email
- password reset funzionante
- flusso auth realistico

---

## FASE 5 — Registrazione professionista + cliente con invito
### Obiettivo
Realizzare il primo flusso business completo.

### Da fare
- registrazione professionista
- generazione `InviteCode`
- validazione codice invito
- registrazione cliente con codice
- creazione `ProfessionalClientLink`
- regole:
  - max 3 professionisti
  - no self-link
  - no duplicati attivi

### Definition of Done
- professionista si registra
- verifica email
- genera codice
- cliente si registra con codice
- collegamento creato correttamente

### Nota
Questa è la **prima milestone vera del progetto**.

---

## FASE 6 — Modulo profilo/account
### Obiettivo
Permettere agli utenti di gestire i propri dati base.

### Da fare
- `/me/profile`
- `/me/account`
- update profilo
- update stato operativo
- cambio password
- upload foto profilo

### Definition of Done
- utente autenticato gestisce il proprio profilo
- aggiornamenti base funzionanti

---

## FASE 7 — Modulo clienti e professionisti
### Obiettivo
Consentire navigazione e lettura delle relazioni create.

### Da fare
- elenco clienti del professionista autenticato
- elenco professionisti del cliente autenticato
- dettaglio cliente
- dettaglio professionista
- filtri base sui collegamenti

### Definition of Done
- lato lettura relazioni pronto
- professionista e cliente vedono i propri collegamenti

---

## FASE 8 — Availability
### Obiettivo
Permettere al PT di definire le proprie disponibilità.

### Da fare
- `AvailabilitySlot`
- creazione slot
- update slot
- blocco/sblocco slot
- query per intervallo date
- validazioni:
  - solo PT
  - no sovrapposizioni
  - intervalli validi

### Definition of Done
- PT crea disponibilità valide
- lettura slot disponibile
- regole principali rispettate

---

## FASE 9 — Bookings
### Obiettivo
Implementare il flusso cliente → richiesta → conferma/rifiuto.

### Da fare
- `BookingRequest`
- `BookingRequestItem`
- creazione richiesta
- elenco richieste cliente
- elenco richieste PT
- conferma richiesta
- rifiuto richiesta
- update stato slot

### Definition of Done
- cliente invia richiesta
- PT la vede
- PT conferma/rifiuta
- slot aggiornati correttamente

### Nota
Questa è la **seconda grande milestone**.

---

## FASE 10 — Workout module
### Obiettivo
Implementare l’area schede di allenamento.

### Da fare
- `WorkoutPlan`
- `WorkoutWeek`
- `WorkoutDay`
- `WorkoutExercise`
- creazione scheda
- lettura scheda cliente
- lettura schede PT
- nuova versione scheda
- storico schede
- regola una sola scheda attiva per coppia PT-cliente

### Definition of Done
- PT crea scheda completa
- cliente la visualizza
- nuova versione archivia la precedente

---

## FASE 11 — Nutrition module
### Obiettivo
Implementare l’area piani alimentari.

### Da fare
- `NutritionPlan`
- `NutritionWeek`
- `NutritionDay`
- `NutritionEntry`
- creazione piano
- lettura piano cliente
- lettura piani nutrizionista
- nuova versione piano
- storico
- regola un solo piano attivo per coppia nutrizionista-cliente

### Definition of Done
- nutrizionista crea piano
- cliente lo visualizza
- storico e versione base funzionanti

---

## FASE 12 — Feedback
### Obiettivo
Permettere al cliente di segnalare problemi o richieste su schede e piani.

### Da fare
- `WorkoutFeedback`
- `NutritionFeedback`
- invio feedback
- lettura feedback lato professionista
- storico feedback inviati lato cliente

### Definition of Done
- cliente invia feedback solo su contenuti propri
- professionista riceve feedback corretti

---

## FASE 13 — Measurements
### Obiettivo
Implementare lo storico misurazioni cliente.

### Da fare
- `ClientMeasurement`
- inserimento misurazione
- elenco storico
- dettaglio misurazione
- controlli autorizzazione:
  - cliente stesso
  - professionista collegato

### Definition of Done
- storico misurazioni funzionante
- permessi corretti
- base pronta per grafici futuri

---

## FASE 14 — Hardening e pulizia
### Obiettivo
Ripulire il backend prima dell’integrazione forte col frontend e del deploy.

### Da fare
- rifinitura DTO
- rifinitura mapper
- standardizzazione response
- controllo naming endpoint
- test dei casi di errore
- test autorizzazioni
- log puliti
- seed dati demo, se utile

### Definition of Done
- backend coerente
- errori gestiti bene
- struttura leggibile
- pronto per integrazione frontend seria

---

## 5. Ordine reale consigliato delle milestone
Se vuoi vedere il progetto in blocchi psicologicamente sostenibili, ragiona così:

### Milestone 1
- setup
- utenti
- auth
- verify email
- inviti
- registrazione cliente
- link professionista-cliente

### Milestone 2
- profilo
- clienti/professionisti
- availability
- bookings

### Milestone 3
- workout
- nutrition
- feedback
- measurements

### Milestone 4
- pulizia
- test
- integrazione completa
- preparazione deploy

---

## 6. Cosa NON fare
Per non perderti, evita questi errori:

- fare backend e frontend insieme da subito
- scrivere tutte le entity e poi lasciare i service vuoti
- creare 30 endpoint senza testarli
- passare a un nuovo modulo quando quello prima è rotto
- fare security troppo tardi
- saltare Postman e testare solo dal frontend

---

## 7. Metodo di lavoro consigliato per ogni modulo
Per ogni modulo usa sempre questo mini-flusso:

1. entity
2. repository
3. service
4. DTO
5. mapper
6. controller
7. test Postman
8. rifinitura eccezioni/validazioni

---

## 8. Primo sprint consigliato
Il primo sprint concreto che ti consiglio è questo:

### Sprint 1
- setup progetto
- MySQL collegato
- `User`, `ProfessionalProfile`, `ClientProfile`
- enum base
- repository base
- Spring Security base
- login base
- JWT base

### Perché questo sprint
Perché ti costruisce il terreno sotto i piedi.  
Senza questo, tutto il resto crolla o diventa confuso.

---

## 9. Secondo sprint consigliato
### Sprint 2
- verifica email
- registrazione professionista completa
- invite code
- validazione invite
- registrazione cliente
- professional-client link

### Perché questo sprint
Perché realizza il primo flusso business completo e ti fa sentire che il progetto esiste davvero.

---

## 10. Criterio per capire se puoi passare allo step successivo
Puoi passare oltre solo se il blocco corrente è:

- compilato senza errori
- testato con Postman
- con validazioni minime presenti
- con errori gestiti in modo decente
- comprensibile anche se lo riapri dopo giorni

---

## 11. Decisione pratica finale
L’ordine consigliato definitivo è:

1. setup
2. fondazioni tecniche
3. utenti
4. security
5. verify/reset
6. inviti + registrazione cliente
7. profilo
8. relazioni clienti/professionisti
9. availability
10. bookings
11. workout
12. nutrition
13. feedback
14. measurements
15. pulizia finale

---

## 12. Prossimo passo operativo
Il prossimo step concreto non è ancora “scrivere tutto”.

Il prossimo step concreto è:

- preparare `docs/12-sprint-01-setup-auth-base.md`

con checklist precisa di cosa fare nel **primo sprint reale**:
- dipendenze Spring Boot
- struttura package
- application.properties
- MySQL
- base entity
- enum base
- user model
- security base
- login base