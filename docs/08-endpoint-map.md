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
Restituisce gli slot disponibili e attivi di un professionista.

### 9.4 Aggiornamento slot disponibilità
**PATCH** `/api/v1/availability/{slotId}`  
Aggiorna parzialmente data/ora di uno slot appartenente al professionista autenticato.

### 9.5 Blocco slot disponibilità
**PATCH** `/api/v1/availability/{slotId}/block`  
Blocca uno slot disponibile appartenente al professionista autenticato.

### 9.6 Sblocco slot disponibilità
**PATCH** `/api/v1/availability/{slotId}/unblock`  
Sblocca uno slot bloccato appartenente al professionista autenticato.

---

## 10. Regole generali di accesso

### 10.1 Endpoint pubblici
Attualmente sono pubblici gli endpoint sotto:

`/api/v1/auth/**`

In particolare:
- `POST /api/v1/auth/register/professional`
- `POST /api/v1/auth/register/client`
- `POST /api/v1/auth/register/client/validate-invite`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/verify-email`

### 10.2 Endpoint protetti
Tutti gli altri endpoint richiedono autenticazione valida tramite JWT.

### 10.3 Regole per area
- `/api/v1/clients/**` → solo `PROFESSIONAL`
- `/api/v1/professionals/**` → solo `CLIENT`
- `/api/v1/me/**` → utente autenticato
- `/api/v1/invites/**` → solo `PROFESSIONAL`, con controlli business aggiuntivi lato service
- `/api/v1/availability/**` → solo `PROFESSIONAL`, con controlli business aggiuntivi lato service

---

## 11. Nota metodologica
Questa mappa rappresenta **solo lo stato reale attuale** del backend.

Per ogni endpoint, nei documenti tecnici di dettaglio o nei prossimi sprint andranno eventualmente definiti meglio:
- request DTO
- response DTO
- codici HTTP attesi
- casi di errore
- regole di autorizzazione più granulari

---

## 12. Decisioni confermate
Per Support Trainer si confermano le seguenti scelte:

- prefisso globale `/api/v1`
- area `/me` per operazioni sul proprio account/profilo
- separazione tra endpoint pubblici e protetti
- lettura relazioni professionista-cliente già disponibile
- inviti già esposti come modulo reale
- endpoint futuri mantenuti fuori da questa mappa, in documento separato
- modulo availability implementato con creazione, lettura, update, block e unblock degli slot