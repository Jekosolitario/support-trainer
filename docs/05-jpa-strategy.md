# JPA Strategy — Support Trainer

## 1. Obiettivo del documento
Questo documento definisce la strategia tecnica generale per tradurre il domain model di Support Trainer in entity JPA reali.

Lo scopo è stabilire linee guida chiare su:
- ereditarietà
- mapping degli enum
- gestione dei timestamp
- strategia soft delete
- relazioni tra entity
- comportamento consigliato con JPA/Hibernate

---

## 2. Principi guida
Per questo progetto si adottano i seguenti principi:

- struttura chiara e leggibile
- mapping il più possibile esplicito
- approccio prudente con le relazioni
- conservazione dello storico
- evitare complessità inutile nella v1
- separare entity JPA, DTO e logica API

---

## 3. Strategia di ereditarietà utenti

## 3.1 Gerarchia prevista
La gerarchia utenti è composta da:

- `User` *(astratta)*
- `ProfessionalProfile`
- `ClientProfile`

## 3.2 Strategia scelta
La strategia JPA scelta è:

- `@Inheritance(strategy = InheritanceType.JOINED)`

## 3.3 Motivazione
La strategia `JOINED` è adatta a questo progetto perché:

- mantiene separati i campi comuni da quelli specifici
- evita una tabella unica troppo larga e dispersiva
- rende più leggibile il database
- è una scelta più pulita per una gerarchia con differenze reali tra figli

## 3.4 Traduzione logica nel database
La struttura attesa sarà simile a:

- tabella `users`
- tabella `professional_profiles`
- tabella `client_profiles`

La tabella figlia userà la chiave primaria dell’utente base anche come chiave verso la tabella padre.

---

## 4. Strategia base per le entity

## 4.1 Identificativi
Tutte le entity principali avranno:

- chiave primaria `id`
- tipo `Long`
- generazione automatica

Scelta consigliata:
- `@Id`
- `@GeneratedValue(strategy = GenerationType.IDENTITY)`

## 4.2 Base comune consigliata
È consigliabile introdurre una classe base tecnica astratta per i campi comuni, ad esempio:

- `id`
- `createdAt`
- `updatedAt`

Questa base tecnica può essere separata dalla gerarchia business `User`.

### Esempio concettuale
Si possono avere due livelli distinti:

- `BaseEntity` o `AbstractEntity` → campi tecnici comuni
- `User` → campi business comuni degli utenti

## 4.3 Costruttori
Per le entity JPA:
- costruttore vuoto obbligatorio
- eventuali costruttori di comodità facoltativi

---

## 5. Gestione timestamp

## 5.1 Strategia scelta
I campi temporali automatici saranno gestiti con callback JPA:

- `@PrePersist`
- `@PreUpdate`

## 5.2 Campi previsti
Dove necessario:
- `createdAt`
- `updatedAt`

## 5.3 Regola
- `createdAt` viene valorizzato alla creazione
- `updatedAt` viene aggiornato a ogni modifica

## 5.4 Motivazione
Questa soluzione è adatta alla v1 perché:
- è semplice
- non richiede auditing Spring avanzato
- è facile da capire e mantenere

---

## 6. Mapping degli enum

## 6.1 Strategia scelta
Tutti gli enum devono essere salvati con:

- `@Enumerated(EnumType.STRING)`

## 6.2 Motivazione
Questa scelta è preferibile perché:
- il database resta leggibile
- evita problemi se in futuro cambia l’ordine degli enum
- è più sicura rispetto a `ORDINAL`

## 6.3 Enum previsti
Esempi:
- `Role`
- `AccountStatus`
- `ProfessionalSpecialization`
- `ProfessionalOperationalStatus`
- `ClientOperationalStatus`
- `AvailabilitySlotStatus`
- `BookingRequestStatus`
- `WorkoutDayType`
- `NutritionDayType`
- `Gender`

---

## 7. Strategia soft delete

## 7.1 Scelta tecnica
Per le entity principali si adotta soft delete logico tramite:

- campo `active`

## 7.2 Regola generale
Le entity principali non devono essere eliminate fisicamente dal database, salvo casi eccezionali e amministrativi.

Invece:
- il record resta nel database
- viene marcato come non attivo

## 7.3 Entità coinvolte principalmente
Soft delete particolarmente utile per:
- `ProfessionalProfile`
- `ClientProfile`
- `ProfessionalClientLink`
- `WorkoutPlan`
- `NutritionPlan`

## 7.4 Strategia query
Le query applicative dovranno filtrare i record attivi quando necessario.

### Approccio consigliato nella v1
Meglio iniziare con:
- filtri espliciti nei repository/service

Invece di introdurre subito:
- `@Where`
- filtri Hibernate più avanzati

## 7.5 Motivazione
Questo approccio:
- è più trasparente
- è più facile da debuggare
- riduce magia nascosta nella v1

---

## 8. Strategia relazioni JPA

## 8.1 Approccio generale
Si adotta un approccio prudente:

- relazioni bidirezionali solo dove servono davvero
- relazioni unidirezionali dove possibile
- evitare grafo entity troppo fitto

## 8.2 Regola pratica
Nella maggior parte dei casi:
- lato figlio: `@ManyToOne`
- lato contenitore: `@OneToMany(mappedBy = ...)` solo se utile davvero

## 8.3 Motivazione
Questo approccio riduce problemi legati a:
- serializzazione JSON infinita
- gestione confusa di `equals/hashCode`
- caricamento lazy inatteso
- codice troppo accoppiato

---

## 9. Fetch strategy

## 9.1 Regola generale
Non affidarsi ai default senza pensarci.  
Come linea guida:

- `@ManyToOne` → valutare esplicitamente `fetch = LAZY`
- `@OneToMany` → preferire `LAZY`

## 9.2 Scelta consigliata
Per questo progetto conviene favorire il caricamento lazy quasi ovunque, soprattutto sulle collezioni.

## 9.3 Motivazione
Questo evita:
- query pesanti inutili
- caricamenti eccessivi
- problemi di performance iniziali

## 9.4 Nota importante
Poiché si useranno DTO per esporre i dati via API:
- non bisogna restituire direttamente le entity JPA al frontend
- la conversione a DTO aiuta a controllare cosa caricare e cosa esporre

---

## 10. Ownership delle relazioni

## 10.1 Regola generale
La parte che possiede la foreign key è il lato owner della relazione.

## 10.2 Regole pratiche per questo progetto

### Relazioni figlio → padre
Nelle relazioni come:
- `WorkoutWeek -> WorkoutPlan`
- `WorkoutDay -> WorkoutWeek`
- `WorkoutExercise -> WorkoutDay`
- `NutritionWeek -> NutritionPlan`
- `NutritionDay -> NutritionWeek`
- `NutritionEntry -> NutritionDay`
- `BookingRequestItem -> BookingRequest`

il lato owner sarà normalmente:
- il lato figlio con `@ManyToOne`

### Relazioni contenitore
Il lato contenitore con `@OneToMany(mappedBy = ...)` sarà usato quando serve navigare facilmente dal padre ai figli.

---

## 11. Cascade e orphan removal

## 11.1 Strategia prudente
Non usare `CascadeType.ALL` ovunque in automatico.  
Va applicato solo dove la dipendenza logica è forte e reale.

## 11.2 Dove ha senso usarlo
Ha senso valutarlo per strutture contenitore/figli fortemente dipendenti, ad esempio:

### Area workout
- `WorkoutPlan -> WorkoutWeek`
- `WorkoutWeek -> WorkoutDay`
- `WorkoutDay -> WorkoutExercise`

### Area nutrition
- `NutritionPlan -> NutritionWeek`
- `NutritionWeek -> NutritionDay`
- `NutritionDay -> NutritionEntry`

### Area booking
- `BookingRequest -> BookingRequestItem`

## 11.3 Dove essere più cauti
Essere più prudenti su relazioni come:
- `ProfessionalProfile <-> ClientProfile`
- `InviteCode -> ProfessionalProfile`
- `ClientMeasurement -> ClientProfile`
- `BookingRequest -> ClientProfile`
- `BookingRequest -> ProfessionalProfile`

## 11.4 orphanRemoval
`orphanRemoval = true` ha senso soprattutto nelle strutture interne dove il figlio:
- non ha significato fuori dal padre
- deve sparire se rimosso dalla collezione del contenitore

Esempi tipici:
- `WorkoutExercise`
- `WorkoutDay`
- `WorkoutWeek`
- `NutritionEntry`
- `NutritionDay`
- `NutritionWeek`
- `BookingRequestItem`

---

## 12. Strategia per la relazione molti-a-molti professionista-cliente

## 12.1 Scelta corretta
La relazione molti-a-molti non va modellata con `@ManyToMany` diretto.

Va invece modellata con entità intermedia esplicita:

- `ProfessionalClientLink`

## 12.2 Motivazione
Questa scelta è corretta perché il collegamento ha dati propri:
- `createdAt`
- `active`

e in futuro potrebbe avere:
- note
- stato del rapporto
- metadata aggiuntivi

## 12.3 Vantaggio
L’entità intermedia rende il modello:
- più professionale
- più estendibile
- più controllabile lato business

---

## 13. Strategia per InviteCode

## 13.1 Relazione
- molti codici invito possono appartenere a un professionista
- ogni codice appartiene a un solo professionista

## 13.2 Vincoli tecnici consigliati
- `code` univoco
- `used` obbligatorio
- `expiresAt` obbligatorio

## 13.3 Regola business non JPA
JPA non basta da sola a garantire:
- scadenza valida
- monouso corretto
- generazione solo da account verificato

Queste regole vanno presidiate nel service layer.

---

## 14. Strategia per BookingRequest e AvailabilitySlot

## 14.1 Modello scelto
La prenotazione è modellata in due livelli:

- `BookingRequest`
- `BookingRequestItem`

Ogni item punta a uno `AvailabilitySlot`.

## 14.2 Motivazione
Questo permette:
- richieste multi-giorno
- più slot in un’unica richiesta
- struttura più flessibile e ordinata

## 14.3 Regole da non delegare a JPA
JPA non garantisce da sola:
- assenza di sovrapposizioni logiche
- coerenza tra PT della richiesta e PT degli slot
- booking unico dello slot

Queste regole devono stare nella business logic.

---

## 15. Strategia per WorkoutPlan e NutritionPlan

## 15.1 Modellazione gerarchica
Le due aree saranno modellate con strutture ad albero:

### Workout
- `WorkoutPlan`
- `WorkoutWeek`
- `WorkoutDay`
- `WorkoutExercise`

### Nutrition
- `NutritionPlan`
- `NutritionWeek`
- `NutritionDay`
- `NutritionEntry`

## 15.2 Regola di dipendenza
Le entità figlie:
- non hanno senso fuori dal proprio contenitore
- devono dipendere fortemente dal padre

## 15.3 Implicazione JPA
Qui ha senso una gestione più strutturata di:
- cascade
- orphan removal
- collezioni ordinate, se utile

---

## 16. Strategia per ClientMeasurement

## 16.1 Relazione
- un cliente può avere molte misurazioni
- ogni misurazione appartiene a un solo cliente

## 16.2 Regola tecnica
Le misurazioni sono record storici e non semplici campi aggiornabili del profilo.

## 16.3 Implicazione progettuale
Meglio trattare `ClientMeasurement` come entità autonoma storicizzata, non come semplice appendice del profilo cliente.

## 16.4 Estensione futura consigliata
In futuro può essere utile aggiungere:
- `recordedByUser`
- `sourceType`

---

## 17. Equals e HashCode

## 17.1 Regola prudente
Con entity JPA è meglio evitare implementazioni ingenue di `equals()` e `hashCode()` basate su tutti i campi.

## 17.2 Rischi
Approcci sbagliati possono creare problemi con:
- proxy Hibernate
- collezioni
- entità non ancora persistite
- confronti incoerenti

## 17.3 Strategia consigliata
Per la v1:
- approccio semplice e prudente
- evitare di includere relazioni nelle comparazioni
- massima attenzione se si usa Lombok

---

## 18. Lombok: uso consigliato

## 18.1 Approccio consigliato
Se usi Lombok, evita sulle entity JPA:
- `@Data`

## 18.2 Preferire
Meglio usare in modo selettivo:
- `@Getter`
- `@Setter`
- `@NoArgsConstructor`
- eventualmente `@AllArgsConstructor` con cautela

## 18.3 Motivazione
`@Data` su entity JPA può creare facilmente problemi con:
- `equals/hashCode`
- `toString`
- lazy loading
- relazioni bidirezionali

---

## 19. Serializzazione JSON e DTO

## 19.1 Regola fondamentale
Le entity JPA non devono essere esposte direttamente nelle API REST.

## 19.2 Motivazione
Questo evita problemi di:
- lazy initialization
- loop infiniti di serializzazione
- esposizione eccessiva del modello interno
- accoppiamento forte tra DB e API

## 19.3 Strategia corretta
Il flusso deve essere:

- Entity JPA
- Service
- Mapper
- DTO request/response
- Controller

## 19.4 Conseguenza pratica
Non bisogna usare le entity come response dirette del controller.

---

## 20. Validazioni: confine tra entity e service

## 20.1 In entity
Nelle entity ha senso mettere vincoli base come:
- non null
- unicità
- forma generale del dato

## 20.2 Nel service
Le regole più importanti di business vanno nel service layer, per esempio:
- cliente massimo 3 professionisti
- divieto di self-link
- slot non sovrapposti
- booking coerente col professionista
- un solo piano attivo per coppia
- un solo workout plan attivo per coppia
- codice invito scaduto/non valido

## 20.3 Principio
JPA modella la struttura.  
La business logic protegge le regole vere del sistema.

---

## 21. Convenzioni consigliate finali

### 21.1 Nomi tabelle
Usare nomi chiari e coerenti, ad esempio:
- `users`
- `professional_profiles`
- `client_profiles`
- `invite_codes`
- `availability_slots`
- `booking_requests`
- `booking_request_items`
- `workout_plans`
- `workout_weeks`
- `workout_days`
- `workout_exercises`
- `nutrition_plans`
- `nutrition_weeks`
- `nutrition_days`
- `nutrition_entries`
- `workout_feedbacks`
- `nutrition_feedbacks`
- `client_measurements`
- `professional_client_links`

### 21.2 Nomi colonne
Preferire convenzione chiara e coerente, ad esempio snake_case nel database.

### 21.3 Nullabilità
Essere espliciti sui campi obbligatori:
- `nullable = false` dove ha davvero senso

### 21.4 Unicità
Definire vincoli univoci almeno per:
- `users.email`
- `invite_codes.code`

---

## 22. Decisioni tecniche confermate

Per Support Trainer si confermano le seguenti scelte:

- ereditarietà utenti con `JOINED`
- enum salvati come `EnumType.STRING`
- soft delete logico con campo `active`
- timestamp automatici via `@PrePersist` e `@PreUpdate`
- relazioni bidirezionali solo dove davvero utili
- entity intermedia esplicita per il legame professionista-cliente
- DTO separati dalle entity JPA

---