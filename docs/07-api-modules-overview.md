# API Modules Overview — Support Trainer

## 1. Obiettivo del documento
Questo documento definisce i principali moduli API del backend di Support Trainer.

Lo scopo è:
- suddividere il backend in aree funzionali chiare
- capire quali responsabilità avrà ogni gruppo di endpoint
- evitare confusione tra controller, service e logica di business
- preparare la base per la mappa dettagliata degli endpoint

---

## 2. Principio di organizzazione
Le API del progetto saranno organizzate per **moduli funzionali**, non per pagine frontend.

Questo approccio è utile perché:
- rende il backend più ordinato
- separa meglio le responsabilità
- facilita manutenzione e crescita del progetto
- riflette un’impostazione più professionale

---

## 3. Moduli principali previsti

Per la v1, il backend sarà suddiviso nei seguenti moduli:

- **Auth**
- **Profile / Account**
- **Professionals**
- **Clients**
- **Invites**
- **Links**
- **Availability**
- **Bookings**
- **Workout Plans**
- **Nutrition Plans**
- **Feedback**
- **Measurements**

---

## 4. Modulo Auth

### Responsabilità principali
Il modulo **Auth** gestisce:
- registrazione professionista
- registrazione cliente con codice invito
- login
- verifica email professionista
- eventuali controlli iniziali di autenticazione

### Cosa non deve gestire
Non deve occuparsi di:
- logica completa del profilo
- gestione clienti/professionisti
- business logic di prenotazioni, schede o piani

### Obiettivo del modulo
Gestire l’accesso al sistema e il ciclo iniziale di attivazione account.

---

## 5. Modulo Profile / Account

### Responsabilità principali
Il modulo **Profile / Account** gestisce i dati del profilo dell’utente autenticato, ad esempio:
- visualizzazione del proprio profilo
- aggiornamento dati personali
- aggiornamento foto profilo
- aggiornamento stato operativo
- eventuali modifiche base dell’account

### Obiettivo del modulo
Separare chiaramente la gestione del **proprio profilo** dalle operazioni su altri utenti.

---

## 6. Modulo Professionals

### Responsabilità principali
Il modulo **Professionals** gestisce informazioni e operazioni legate ai professionisti, ad esempio:
- recupero dati professionista
- visualizzazione professionisti collegati a un cliente
- eventuali dettagli pubblici/professionali

### Obiettivo del modulo
Fornire una gestione dedicata del lato professionista senza mischiarla con autenticazione o prenotazioni.

---

## 7. Modulo Clients

### Responsabilità principali
Il modulo **Clients** gestisce informazioni e operazioni legate ai clienti, ad esempio:
- visualizzazione dati cliente
- elenco clienti di un professionista
- recupero dettagli cliente

### Obiettivo del modulo
Separare la gestione anagrafica e funzionale dei clienti dalle altre aree del sistema.

---

## 8. Modulo Invites

### Responsabilità principali
Il modulo **Invites** gestisce:
- generazione codici invito
- elenco codici generati
- stato dei codici
- eventuale invalidazione logica di un codice non ancora usato

### Obiettivo del modulo
Gestire il meccanismo di accesso controllato dei clienti.

---

## 9. Modulo Links

### Responsabilità principali
Il modulo **Links** gestisce i collegamenti tra:
- professionisti
- clienti

Ad esempio:
- creare il collegamento finale dopo registrazione valida
- visualizzare collegamenti attivi
- disattivare un collegamento
- controllare relazione attiva tra cliente e professionista

### Obiettivo del modulo
Rendere esplicita la gestione della relazione molti-a-molti tramite `ProfessionalClientLink`.

---

## 10. Modulo Availability

### Responsabilità principali
Il modulo **Availability** gestisce:
- creazione slot di disponibilità del PT
- aggiornamento slot
- visualizzazione slot disponibili
- blocco o disattivazione slot quando necessario

### Ambito
Questo modulo riguarda solo professionisti con specializzazione:
- `PERSONAL_TRAINER`

### Obiettivo del modulo
Gestire in modo ordinato la disponibilità prenotabile del PT.

---

## 11. Modulo Bookings

### Responsabilità principali
Il modulo **Bookings** gestisce:
- creazione richieste di prenotazione
- visualizzazione richieste
- conferma richiesta
- rifiuto richiesta
- storico prenotazioni

### Obiettivo del modulo
Gestire il flusso cliente → richiesta → risposta del PT, separandolo dalla pura disponibilità.

---

## 12. Modulo Workout Plans

### Responsabilità principali
Il modulo **Workout Plans** gestisce:
- creazione scheda workout
- aggiornamento tramite nuova versione
- visualizzazione schede attive e storiche
- dettaglio settimane, giorni ed esercizi

### Ambito
Questo modulo riguarda solo professionisti con specializzazione:
- `PERSONAL_TRAINER`

### Obiettivo del modulo
Gestire tutta l’area allenamento senza mischiarla con nutrizione o feedback.

---

## 13. Modulo Nutrition Plans

### Responsabilità principali
Il modulo **Nutrition Plans** gestisce:
- creazione piano alimentare
- aggiornamento tramite nuova versione
- visualizzazione piano attivo e storico
- dettaglio settimane, giorni e contenuti alimentari

### Ambito
Questo modulo riguarda solo professionisti con specializzazione:
- `NUTRITIONIST`

### Obiettivo del modulo
Gestire separatamente tutta la parte nutrizione.

---

## 14. Modulo Feedback

### Responsabilità principali
Il modulo **Feedback** gestisce:
- invio feedback workout
- invio feedback nutrizione
- visualizzazione feedback ricevuti
- eventuale storico feedback inviati

### Obiettivo del modulo
Consentire al cliente di segnalare problemi o richieste su contenuti specifici assegnati.

---

## 15. Modulo Measurements

### Responsabilità principali
Il modulo **Measurements** gestisce:
- inserimento misurazioni cliente
- visualizzazione storico misurazioni
- eventuale filtro per data
- eventuale inserimento da parte del cliente o del professionista autorizzato

### Obiettivo del modulo
Gestire il monitoraggio fisico del cliente in modo storico e strutturato.

---

## 16. Moduli esclusi dalla v1

Nella prima versione non sono previsti moduli dedicati per:

- **Admin**
- **Notifiche**
- **Promemoria**
- **Pagamenti**
- **Chat real time**
- **Statistiche avanzate**

### Nota
Queste aree potranno essere aggiunte in una fase successiva, ma non fanno parte del perimetro iniziale del backend.

---

## 17. Ruolo dei controller
Ogni modulo potrà avere uno o più controller dedicati.

### Regola consigliata
I controller devono:
- ricevere request DTO
- delegare al service
- restituire response DTO
- non contenere business logic complessa

### Obiettivo
Tenere il controller leggero e focalizzato sull’orchestrazione HTTP.

---

## 18. Ruolo dei service
I service rappresentano il punto centrale della business logic.

### Devono gestire
- validazioni di business
- controlli di autorizzazione logica
- coerenza tra entità
- coordinamento tra repository
- creazione/aggiornamento di dati complessi

### Non devono diventare
- contenitori caotici di logica senza separazione
- sostituti dei controller
- sostituti dei mapper

---

## 19. Ruolo dei repository
I repository devono occuparsi di:
- accesso ai dati
- query semplici o mirate
- ricerca per chiavi, stati, collegamenti, intervalli temporali

### Regola importante
Nei repository non deve finire la business logic vera.  
La logica resta nei service.

---

## 20. Organizzazione consigliata dei package backend

Esempio di struttura iniziale:

```text
backend/
└─ src/main/java/.../supporttrainer/
   ├─ auth/
   ├─ profile/
   ├─ professional/
   ├─ client/
   ├─ invite/
   ├─ link/
   ├─ availability/
   ├─ booking/
   ├─ workout/
   ├─ nutrition/
   ├─ feedback/
   ├─ measurement/
   ├─ common/
   ├─ security/
   └─ config/