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

## 4.2 Base comune implementata

Nel backend è presente una classe tecnica astratta:

- `BaseEntity`

Questa classe contiene i campi comuni:

- `id`
- `createdAt`
- `updatedAt`

### Mapping implementato

- `id` → `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- `createdAt` → `@CreatedDate`
- `updatedAt` → `@LastModifiedDate`

La gerarchia business `User` estende `BaseEntity`, mentre le altre entity principali utilizzano direttamente `BaseEntity` dove appropriato.

### Entity che estendono `BaseEntity`

- `User`
- `ProfessionalClientLink`
- `InviteCode`
- `AvailabilitySlot`
- `BookingRequest`
- `BookingRequestItem`

## 4.3 Costruttori
Per le entity JPA:
- costruttore vuoto obbligatorio
- eventuali costruttori di comodità facoltativi

---

## 5. Gestione timestamp

## 5.1 Strategia implementata

I campi temporali automatici comuni sono gestiti nella classe `BaseEntity` tramite Spring Data JPA Auditing:

- `@CreatedDate`
- `@LastModifiedDate`
- `@EntityListeners(AuditingEntityListener.class)`

## 5.2 Campi implementati

Le entity che estendono `BaseEntity` ricevono:

- `createdAt`
- `updatedAt`

## 5.3 Regola applicativa

- `createdAt` viene valorizzato automaticamente alla creazione del record;
- `updatedAt` viene valorizzato automaticamente alla creazione e aggiornato alle successive modifiche.

## 5.4 Eccezione presente

`EmailVerificationToken` non estende `BaseEntity` e contiene solo:

- `createdAt`

senza campo `updatedAt`.

## 5.5 Nota di coerenza

`@EnableJpaAuditing` usa un `DateTimeProvider` basato esclusivamente su `ApplicationTimeProvider.nowInstant()`. Non vengono iniettati bean nelle entity e non sono usate callback `@PrePersist` o `@PreUpdate`.

## 5.6 Precisione canonica temporanea per il dato legacy

Durante l'adozione iniziale di Flyway, `booking_request_items.updated_at` usa temporaneamente `DATETIME(6) NOT NULL` come contratto canonico. La V2 valorizza esclusivamente gli eventuali valori nulli e preserva senza arrotondamenti tutti i timestamp legacy già presenti.

Questa decisione è locale al campo e serve a evitare perdita di dati durante la migrazione. Non definisce ancora il modello temporale globale dell'applicazione: l'eventuale uniformazione delle precisioni resta rinviata all'intervento CM-05.

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

## 6.3 Enum attualmente implementati

- `Role`
- `AccountStatus`
- `ProfessionalSpecialization`
- `ProfessionalOperationalStatus`
- `ClientOperationalStatus`
- `Gender`
- `AvailabilitySlotStatus`
- `BookingRequestStatus`

## 6.4 Enum pianificati per moduli futuri

- `WorkoutDayType`
- `NutritionDayType`

Gli enum futuri non rappresentano componenti già presenti nel backend attuale.

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

## 7.3 Entity attualmente dotate di flag `active`

Nel backend reale il campo `active` è presente in:

- `ProfessionalProfile`
- `ClientProfile`
- `ProfessionalClientLink`
- `InviteCode`
- `AvailabilitySlot`
- `BookingRequest`

Non è presente in:

- `User`
- `BookingRequestItem`
- `EmailVerificationToken`

Per i moduli futuri, la strategia di attivazione/disattivazione logica dovrà essere confermata al momento dell’implementazione.

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

## 10.2 Relazioni attualmente implementate

### Relazioni con lato owner `@ManyToOne`

Nel backend reale il lato che possiede la foreign key è rappresentato da:

- `ProfessionalClientLink -> ProfessionalProfile`
- `ProfessionalClientLink -> ClientProfile`
- `InviteCode -> ProfessionalProfile`
- `AvailabilitySlot -> ProfessionalProfile`
- `BookingRequest -> ClientProfile`
- `BookingRequest -> ProfessionalProfile`
- `BookingRequestItem -> BookingRequest`
- `BookingRequestItem -> AvailabilitySlot`

Tutte queste relazioni sono mappate con:

- `@ManyToOne(fetch = FetchType.LAZY, optional = false)`
- `@JoinColumn(..., nullable = false)`

### Relazione contenitore implementata

`BookingRequest` contiene:

- `List<BookingRequestItem> items`

mappata con:

- `@OneToMany(mappedBy = "bookingRequest")`
- `cascade = CascadeType.ALL`
- `orphanRemoval = true`

### Relazioni pianificate

Le relazioni relative a workout, nutrition, feedback e measurements restano ipotesi future e dovranno essere definite durante i rispettivi sprint.

---

## 11. Cascade e orphan removal

## 11.1 Strategia generale

`CascadeType.ALL` e `orphanRemoval = true` devono essere usati solo quando il figlio dipende realmente dal contenitore.

## 11.2 Mapping attualmente implementato

Nel backend reale questa strategia è utilizzata in:

### Area booking

- `BookingRequest -> BookingRequestItem`

La collection `items` di `BookingRequest` è configurata con:

- `cascade = CascadeType.ALL`
- `orphanRemoval = true`

Questa scelta è coerente perché un `BookingRequestItem` non ha significato autonomo senza la relativa richiesta booking.

## 11.3 Mapping futuri da valutare

Per i moduli futuri la stessa strategia potrà essere valutata per:

### Area workout

- `WorkoutPlan -> WorkoutWeek`
- `WorkoutWeek -> WorkoutDay`
- `WorkoutDay -> WorkoutExercise`

### Area nutrition

- `NutritionPlan -> NutritionWeek`
- `NutritionWeek -> NutritionDay`
- `NutritionDay -> NutritionEntry`

Questi mapping non risultano ancora implementati nel backend reale.

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

## 14.1 Stato di implementazione

I moduli Availability e Bookings risultano implementati nel backend reale.

Le entity coinvolte sono:

- `AvailabilitySlot`
- `BookingRequest`
- `BookingRequestItem`

## 14.2 Mapping Availability

`AvailabilitySlot` appartiene a un solo professionista tramite:

- `@ManyToOne(fetch = FetchType.LAZY, optional = false)`
- foreign key `professional_id`

Contiene inoltre:

- stato salvato come enum stringa;
- flag `active`;
- timestamp ereditati da `BaseEntity`.

## 14.3 Mapping Booking

`BookingRequest` appartiene a:

- un solo cliente;
- un solo professionista.

Contiene una collection:

- `List<BookingRequestItem> items`

Ogni `BookingRequestItem` punta a:

- una sola `BookingRequest`;
- un solo `AvailabilitySlot`.

## 14.4 Contratto API attuale

Nel backend attuale una richiesta booking viene creata a partire da:

- un singolo `availabilitySlotId`.

Di conseguenza, il flusso API attuale crea:

- una `BookingRequest`;
- un solo `BookingRequestItem`.

La struttura dati resta estendibile per un eventuale scenario multi-slot futuro, ma tale funzionalità non è attualmente implementata.

## 14.5 Regole gestite nel service layer

JPA modella le relazioni, mentre il service layer controlla:

- slot nel futuro;
- assenza di sovrapposizioni availability;
- relazione attiva cliente-professionista;
- slot disponibile prima della richiesta;
- assenza di booking `PENDING` duplicato sullo stesso slot;
- transizioni consentite del booking;
- aggiornamento coerente dello stato slot dopo conferma o cancellazione.

## 14.6 Gestione concorrenza implementata

Il modulo Bookings utilizza lock pessimisti in scrittura per proteggere i flussi che potrebbero produrre inconsistenze in presenza di richieste simultanee.

### Creazione e aggiornamento availability

Durante la creazione o l’aggiornamento di slot availability, il backend protegge il professionista proprietario tramite lock pessimista in scrittura.

Il lock viene applicato a:

- `ProfessionalProfile`

tramite repository con:

- `@Lock(LockModeType.PESSIMISTIC_WRITE)`

Questa scelta è necessaria perché, durante la creazione di un nuovo slot, potrebbe non esistere ancora una riga `AvailabilitySlot` da bloccare.

Il flusso consente di serializzare le operazioni dello stesso professionista e rendere affidabile il controllo:

- assenza di sovrapposizioni temporali tra slot attivi.

In questo modo due richieste concorrenti dello stesso professionista non possono entrambe superare il controllo overlap e salvare slot incompatibili.

### Creazione booking

Durante la creazione di una richiesta booking, lo slot selezionato viene caricato tramite repository con:

- `@Lock(LockModeType.PESSIMISTIC_WRITE)`

Il lock sullo slot resta attivo per la durata della transazione `@Transactional` del service.

Questo consente di verificare in modo coerente:

- disponibilità dello slot;
- validità dello slot;
- assenza di richieste `PENDING` già presenti sullo stesso slot.

L’obiettivo è impedire che due richieste concorrenti possano essere entrambe create come `PENDING` sullo stesso slot.

### Transizioni booking

Le operazioni che cambiano lo stato di una richiesta booking caricano la richiesta tramite lock pessimista in scrittura.

Sono protette:

- conferma;
- rifiuto;
- cancellazione.

Questa scelta impedisce che due operazioni simultanee possano leggere lo stesso stato iniziale e applicare transizioni incompatibili sulla medesima richiesta.

### Conferma booking

Durante la conferma, oltre alla richiesta booking vengono bloccati anche gli slot collegati.

Il flusso è:

1. lock della `BookingRequest`;
2. lock degli `AvailabilitySlot` collegati;
3. verifica che gli slot siano ancora validi, disponibili, futuri e appartenenti a un `PERSONAL_TRAINER`;
4. aggiornamento booking a `CONFIRMED`;
5. aggiornamento slot a `BOOKED`.

Questa protezione impedisce conferme concorrenti incoerenti sullo stesso slot.

### Coordinamento Availability / Booking su richiesta pending

Uno slot può restare in stato `AVAILABLE` mentre esiste una richiesta booking in stato `PENDING`.

In questa situazione lo slot è considerato logicamente impegnato rispetto alle operazioni manuali del professionista.

Durante:

- aggiornamento dello slot;
- blocco manuale dello slot;

il backend:

1. carica lo slot con lock pessimista in scrittura;
2. verifica l’assenza di booking `PENDING` attivi collegati;
3. consente l’operazione solo se non esistono richieste in attesa.

Questa strategia impedisce che data, ora o disponibilità dello slot vengano alterate mentre un cliente attende una risposta alla propria richiesta.

### Repository coinvolti

I repository interessati dalla strategia di lock sono:

- `ProfessionalProfileRepository`, per serializzare creazione e aggiornamento availability dello stesso professionista;
- `AvailabilitySlotRepository`, per proteggere creazione booking, conferma booking e operazioni sul singolo slot;
- `BookingRequestRepository`, per proteggere le transizioni di stato della richiesta booking.

Il controllo dell’esistenza di booking `PENDING` collegati allo slot utilizza inoltre:

- `BookingRequestItemRepository`.

### Nota architetturale

I lock pessimisti proteggono l’integrità del flusso operativo in condizioni concorrenti.

Le regole business restano comunque espresse e validate nel service layer; il lock non sostituisce le validazioni, ma ne rende affidabile l’esecuzione durante transazioni simultanee.

---

## 15. Strategia futura per WorkoutPlan e NutritionPlan — Non implementata

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

## 16. Strategia futura per ClientMeasurement — Non implementata

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

## 20.1 Vincoli strutturali presenti nel mapping

Nel mapping JPA attuale sono presenti vincoli strutturali come:

- campi obbligatori con `nullable = false`;
- `users.email` univoca;
- `invite_codes.code` univoco;
- enum salvati come stringa;
- foreign key obbligatorie nelle relazioni implementate.

## 20.2 Regole gestite nel service layer

Le regole business già implementate comprendono:

- massimo 3 professionisti attivi per cliente;
- divieto di auto-collegamento;
- controllo collegamento attivo cliente-professionista;
- controllo account/profilo attivo;
- email verificata dove richiesta;
- slot availability con intervallo valido;
- slot availability nel futuro;
- assenza di sovrapposizioni slot;
- booking coerente con professionista collegato;
- booking solo su slot disponibile;
- assenza di richiesta `PENDING` duplicata sullo stesso slot;
- transizioni booking consentite;
- aggiornamento stato slot dopo conferma o cancellazione booking.
- protezione dello slot tramite lock pessimista durante la creazione booking;
- protezione della richiesta booking tramite lock pessimista durante conferma, rifiuto e cancellazione;
- protezione dello slot tramite lock pessimista durante la conferma booking;
- prevenzione di richieste o conferme concorrenti incoerenti sullo stesso slot.
- protezione del professionista tramite lock pessimista durante creazione e aggiornamento availability;
- prevenzione di slot sovrapposti creati o aggiornati tramite richieste concorrenti;
- blocco modifica di uno slot con booking `PENDING` attivo;
- blocco del blocco manuale di uno slot con booking `PENDING` attivo;
- coordinamento transazionale tra Availability e Bookings sulle operazioni concorrenti relative allo stesso slot.

## 20.3 Regole future

Le regole relative a workout, nutrition, feedback e measurements dovranno essere definite e implementate nei rispettivi sprint futuri.

## 20.4 Principio

JPA modella struttura, relazioni e vincoli tecnici di base.  
Il service layer protegge le regole operative reali del sistema.

## 20.5 Lock pessimisti e confini transazionali

### Strategia implementata

Per i flussi Booking critici è utilizzato:

- `LockModeType.PESSIMISTIC_WRITE`

tramite annotazione repository:

- `@Lock(...)`

### Condizione necessaria

I metodi service che utilizzano query con lock devono operare all’interno di una transazione attiva:

- `@Transactional`

Il lock rimane valido fino alla conclusione della transazione.

### Motivazione

La strategia è stata introdotta perché un semplice controllo applicativo sequenziale non protegge da due richieste HTTP elaborate contemporaneamente.

Senza lock, potrebbero verificarsi scenari come:

- due booking `PENDING` creati quasi simultaneamente sullo stesso slot;
- due transizioni concorrenti sulla stessa richiesta;
- due conferme concorrenti che leggono lo stesso slot come ancora `AVAILABLE`.

### Ambito di utilizzo

Il lock pessimistico è utilizzato soltanto nei flussi che modificano dati critici condivisi.

### Availability

- lock del `ProfessionalProfile` durante creazione slot;
- lock del `ProfessionalProfile` durante aggiornamento slot;
- lock dello slot durante aggiornamento;
- lock dello slot durante blocco manuale.

### Bookings

- lock dello slot durante creazione booking;
- lock della richiesta durante conferma, rifiuto e cancellazione;
- lock dello slot durante conferma booking.

### Coordinamento tra moduli

Quando uno slot possiede una richiesta booking `PENDING`, le operazioni Availability che potrebbero alterare la proposta fatta al cliente vengono bloccate.

Non sono quindi consentiti:

- aggiornamento dello slot;
- blocco manuale dello slot.

Le normali operazioni di sola lettura continuano a utilizzare metodi repository senza lock.

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

Per Support Trainer risultano implementate e confermate le seguenti scelte:

- ereditarietà utenti con `InheritanceType.JOINED`;
- classe tecnica comune `BaseEntity`;
- identificativi `Long` generati con `GenerationType.IDENTITY`;
- timestamp automatici tramite `@CreationTimestamp` e `@UpdateTimestamp`;
- enum salvati come `EnumType.STRING`;
- flag `active` sulle entity che richiedono gestione logica dello stato;
- relazioni `@ManyToOne` caricate in modalità `LAZY` nei moduli implementati;
- entity intermedia esplicita `ProfessionalClientLink` per il legame professionista-cliente;
- relazione `BookingRequest -> BookingRequestItem` con cascade e orphan removal;
- DTO separati dalle entity JPA;
- regole business complesse gestite nel service layer;
- moduli Availability e Bookings integrati nel modello persistente reale.
- lock pessimisti `PESSIMISTIC_WRITE` per proteggere la creazione booking sullo stesso slot;
- lock pessimisti sulla richiesta booking durante conferma, rifiuto e cancellazione;
- lock pessimisti sugli slot durante la conferma booking;
- confine transazionale nel service layer per mantenere validi i lock fino al completamento dell’operazione.
- lock pessimista sul `ProfessionalProfile` per serializzare creazione e aggiornamento availability;
- lock sul singolo slot durante update e block quando può esistere un booking pendente;
- impossibilità di modificare o bloccare availability slot con richiesta booking `PENDING`;
- coordinamento transazionale tra moduli Availability e Bookings sullo stesso slot.

---

## 23. Versionamento dello schema con Flyway

### 23.1 Ownership

Flyway è il proprietario esclusivo dello schema MySQL delle nove tabelle runtime:

- `users`;
- `professional_profiles`;
- `client_profiles`;
- `professional_client_links`;
- `invite_codes`;
- `email_verification_tokens`;
- `availability_slots`;
- `booking_requests`;
- `booking_request_items`.

Hibernate usa `ddl-auto=validate` sugli ambienti MySQL e non deve creare o aggiornare lo schema. Le tredici tabelle legacy dei moduli futuri non appartengono ancora al perimetro Flyway.

### 23.2 Migrazioni runtime

- `V1__create_legacy_compatible_runtime_schema.sql` riproduce lo schema runtime legacy rilevato, incluse le dimensioni non restrittive e i timestamp aggiuntivi delle tabelle JOINED.
- `V2__align_runtime_schema_contract.sql` allarga `client_profiles.primary_goal`, rende `booking_request_items.updated_at` obbligatorio preservandone la precisione legacy a sei cifre e aggiunge gli indici composti richiesti dalle query correnti.
- Le migrazioni `V3_1`–`V3_9`, una per tabella runtime, ampliano esclusivamente a `DATETIME(6)` le colonne che rappresentano istanti. Conservano nullability, default e `ON UPDATE`, non contengono DML e non modificano date civili, vincoli, indici o relazioni.
- La migrazione Java V4 converte i valori legacy da `Europe/Rome` a UTC soltanto dopo una preflight completa su struttura, precisione, gap, overlap, conteggi e integrità dei dati.
- Le migrazioni `V5_1`–`V5_9` trasferiscono l'ownership degli audit all'applicazione, rimuovendo default e `ON UPDATE`; i quattro timestamp ombra dei profili diventano nullable e restano congelati.

L'ampliamento strutturale aggiunge frazione zero ai valori precedentemente memorizzati a precisione di secondo. Non interpreta i `DATETIME` legacy, non applica conversioni di timezone e non completa la persistenza UTC.

Le migrazioni già applicate sono immutabili. Le evoluzioni successive devono essere aggiunte come nuove migrazioni versionate e sono forward-only.

### 23.3 Database vuoto ed esistente

Su uno schema MySQL 8 nuovo e vuoto Flyway applica in ordine V1, V2, `V3_1`–`V3_9`, V4 e `V5_1`–`V5_9`, quindi Hibernate valida il mapping.

Uno schema esistente non deve essere migrato automaticamente. Prima della baseline manuale sono obbligatori:

1. backup verificato;
2. clone isolato;
3. confronto tra clone e V1;
4. prova sul clone della baseline e dell'intera sequenza da V2 a `V5_9`;
5. verifica applicativa e dei conteggi;
6. approvazione esplicita per operare sul database reale.

`baseline-on-migrate` resta disabilitato. Flyway `clean` è vietato sugli ambienti persistenti e la configurazione applicativa lo disabilita.

### 23.4 Strategia di test

La suite ordinaria usa H2 con Flyway disabilitato e `ddl-auto=create-drop`. È una verifica rapida del comportamento applicativo, non una certificazione del DDL MySQL.

MySQL 8 è il riferimento per migrazioni, charset, collation, foreign key, indici, precisione temporale e lock. Su MySQL 8.0.44 sono riusciti sia il percorso da database vuoto `V1` → `V5.9` sia quello da clone legacy `BASELINE 1` → `V2` → `V5.9`, inclusa la successiva validazione Hibernate. Timestamp e microsecondi, dati, vincoli e indici sono stati preservati.

## 24. Modello temporale UTC

I flussi applicativi leggono il tempo tramite un unico `ApplicationTimeProvider`. Il provider usa un bean `Clock` tecnico configurato in UTC e converte il medesimo `Instant` nella zona business esplicita `Europe/Rome` quando il modello corrente richiede un `LocalDateTime`. Nessun servizio applicativo deve dipendere da `ZoneId.systemDefault()` o da `user.timezone`; nei test il `Clock` viene sostituito con `Clock.fixed`.

Gli istanti runtime mappati sono ora `Instant`, normalizzati ai microsecondi e associati a `DATETIME(6)` senza `TIMESTAMP WITH TIME ZONE`. Hibernate usa `hibernate.jdbc.time_zone=UTC`; la connessione MySQL deve impostare la sessione a `+00:00`. Non è configurata una timezone Jackson globale: gli audit e le scadenze sono serializzati con `Z`, mentre gli slot restano `OffsetDateTime` `Europe/Rome` al confine HTTP.

La V4 Java converte i 23 campi legacy da `Europe/Rome` a UTC dopo una preflight completa; le V5 rimuovono default e `ON UPDATE`, trasferendo l'ownership all'applicazione. I timestamp ombra dei profili vengono convertiti e poi congelati come nullable. La validazione MySQL ha convertito 70 valori valorizzati sul clone legacy, preservando due null e cinque valori con microsecondi, e ha verificato che gap, overlap o schema inatteso interrompano V4 prima del primo DML. Il mapping `Instant` e l'auditing basato sul `Clock` applicativo sono stati verificati con Hibernate `ddl-auto=validate`. Il database locale reale `support_trainer` non è stato baselinato o migrato.

---
