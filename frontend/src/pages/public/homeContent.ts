export interface TextItem {
  title: string;
  description: string;
}

export interface FaqItem {
  id: string;
  question: string;
  answer: string;
  link?: {
    label: string;
    to: string;
  };
}

export const heroContent = {
  eyebrow: 'Per personal trainer, nutrizionisti e clienti',
  title: 'Il tuo lavoro, più semplice da organizzare.',
  introduction:
    'Support Trainer riunisce professionisti e clienti in uno spazio digitale chiaro e condiviso, pensato per rendere più ordinati il lavoro, le relazioni e le attività quotidiane.',
  primaryAction: 'Scopri come funziona',
  inviteAction: 'Ho un codice invito',
  professionalAction: 'Sei un professionista? Registrati',
  reassurance: 'Pensato per essere semplice, anche senza competenze tecniche.',
} as const;

export const projectContent = {
  eyebrow: 'Uno spazio condiviso',
  title: 'Un punto di riferimento condiviso tra professionista e cliente.',
  introduction:
    'Support Trainer aiuta il professionista e il cliente a ritrovare più facilmente i collegamenti e le attività già disponibili.',
  concepts: [
    {
      title: 'Attività più facili da ritrovare',
      description:
        'Il professionista e il cliente sanno dove consultare le attività già gestite dalla piattaforma.',
    },
    {
      title: 'Informazioni coerenti con ogni ruolo',
      description:
        'Ogni utente consulta collegamenti e informazioni coerenti con ciò che può fare nella piattaforma.',
    },
    {
      title: 'Un rapporto più ordinato',
      description:
        'Le attività supportate seguono passaggi più riconoscibili e meno dispersivi.',
    },
  ] satisfies TextItem[],
} as const;

export const advantagesContent = {
  eyebrow: 'Meno frammentazione',
  title: 'Da strumenti separati a una gestione più chiara.',
  introduction:
    'Chat, telefonate e appunti restano strumenti utili. Quando però richieste, orari e informazioni devono essere ricostruiti tra canali diversi, anche le attività più semplici possono richiedere più passaggi.',
  comparisons: [
    {
      title: 'Tra strumenti separati',
      description:
        'Richieste, orari e informazioni possono trovarsi in conversazioni e strumenti diversi, rendendo meno immediato ritrovare ciò che serve.',
      items: [
        {
          title: 'Messaggi da ritrovare',
          description:
            'Informazioni utili possono rimanere distribuite tra conversazioni differenti.',
        },
        {
          title: 'Disponibilità da comunicare più volte',
          description:
            'Il personal trainer può dover ripetere giorni e orari disponibili nelle singole conversazioni.',
        },
        {
          title: 'Richieste da coordinare manualmente',
          description:
            'Individuare un appuntamento può richiedere diversi scambi tra professionista e cliente.',
        },
        {
          title: 'Strumenti da consultare separatamente',
          description:
            'Messaggi, telefonate e appunti possono richiedere consultazioni separate.',
        },
      ] satisfies TextItem[],
    },
    {
      title: 'Con Support Trainer',
      description:
        'Le attività già supportate dalla piattaforma diventano più facili da ritrovare e consultare, nel rispetto del ruolo di ogni utente.',
      items: [
        {
          title: 'Collegamenti più chiari',
          description:
            'Professionisti e clienti ritrovano nella piattaforma i collegamenti attivi e le informazioni che possono consultare.',
        },
        {
          title: 'Elenco dei clienti collegati',
          description:
            'Il professionista può consultare i clienti con i quali esiste un collegamento attivo.',
        },
        {
          title: 'Disponibilità consultabili — Con il personal trainer',
          description:
            'Il cliente collegato può consultare gli orari pubblicati dal proprio personal trainer.',
        },
        {
          title: 'Richieste di prenotazione — Con il personal trainer',
          description:
            'Il personal trainer e il cliente collegato possono consultare le richieste e verificarne lo stato.',
        },
      ] satisfies TextItem[],
    },
  ],
  benefits: [
    {
      title: 'Più ordine e chiarezza',
      description:
        'Collegamenti, inviti e attività disponibili seguono percorsi più riconoscibili e sono più facili da consultare.',
    },
    {
      title: 'Meno passaggi ripetitivi',
      description:
        'Alcune attività, come consultare una disponibilità o verificare una richiesta, richiedono meno scambi separati.',
    },
    {
      title: 'Una collaborazione più lineare',
      description:
        'Il professionista e il cliente utilizzano la stessa piattaforma, ciascuno con informazioni e azioni coerenti con il proprio ruolo.',
    },
  ] satisfies TextItem[],
  conclusion:
    'Quando le attività sono più chiare, diventa più semplice dedicare attenzione al lavoro e al rapporto con il cliente.',
} as const;

export const processContent = {
  eyebrow: 'Tre ruoli, tre percorsi',
  title: 'Scopri come iniziare, in base al tuo ruolo.',
  introduction:
    'Personal trainer, nutrizionisti e clienti iniziano a usare Support Trainer attraverso pochi passaggi, coerenti con il proprio ruolo.',
  clarification:
    'Personal trainer e nutrizionisti possono registrarsi direttamente e devono verificare l’indirizzo email. Il cliente inizia invece da un codice invito valido.',
  paths: [
    {
      role: 'Personal trainer',
      title: 'Inizia a organizzare disponibilità e richieste.',
      steps: [
        {
          title: 'Registrati e verifica l’email',
          description:
            'Crea l’account professionale, scegli la specializzazione personal trainer e verifica l’indirizzo email prima di poter usare il proprio account.',
        },
        {
          title: 'Completa il profilo e invita i clienti',
          description:
            'Aggiungi le informazioni professionali previste e genera gli inviti da condividere con i tuoi clienti.',
        },
        {
          title: 'Pubblica le disponibilità e gestisci le richieste',
          description:
            'Definisci gli orari disponibili e consulta le richieste dei clienti, confermandole o rifiutandole.',
        },
      ] satisfies TextItem[],
    },
    {
      role: 'Nutrizionista',
      title: 'Inizia dal profilo e dai clienti collegati.',
      steps: [
        {
          title: 'Registrati e verifica l’email',
          description:
            'Crea l’account professionale, scegli la specializzazione nutrizionista e verifica l’indirizzo email prima di poter usare il proprio account.',
        },
        {
          title: 'Completa il profilo e invita i clienti',
          description:
            'Aggiungi le informazioni professionali previste e genera gli inviti da condividere con i tuoi clienti.',
        },
        {
          title: 'Consulta i clienti collegati',
          description:
            'Ritrova l’elenco dei clienti collegati e le informazioni disponibili per la tua attività professionale.',
        },
      ] satisfies TextItem[],
    },
    {
      role: 'Cliente',
      title: 'Entra tramite invito e ritrova i tuoi professionisti.',
      steps: [
        {
          title: 'Ricevi e valida il codice',
          description:
            'Utilizza il codice ricevuto dal professionista per verificare la validità dell’invito.',
        },
        {
          title: 'Registrati e attiva il collegamento',
          description:
            'Dopo la validazione, crea il tuo account cliente e attiva il collegamento con il professionista che ti ha invitato.',
        },
        {
          title: 'Consulta professionisti e attività disponibili',
          description:
            'Ritrova i professionisti collegati e, con il personal trainer, consulta le disponibilità e invia richieste di prenotazione.',
        },
      ] satisfies TextItem[],
      contextualAction: 'Hai ricevuto un codice? Inizia dalla validazione',
    },
  ],
} as const;

export const rolesContent = {
  eyebrow: 'Per ogni ruolo',
  title: 'Funzioni pensate per attività diverse.',
  introduction:
    'Personal trainer, nutrizionisti e clienti utilizzano la stessa piattaforma attraverso informazioni e funzioni coerenti con le rispettive attività.',
  commonPrinciple:
    'Profili, inviti e collegamenti costituiscono la base comune. Disponibilità e richieste di prenotazione riguardano invece il personal trainer e i clienti collegati.',
  experiences: [
    {
      label: 'Personal trainer',
      title: 'Collegamenti, disponibilità e richieste più facili da seguire.',
      description:
        'Support Trainer riunisce clienti collegati, orari disponibili e richieste di prenotazione, riducendo la necessità di ricostruire gli appuntamenti tra conversazioni separate.',
      features: [
        {
          title: 'Profilo, inviti e clienti collegati',
          description:
            'Completa il profilo professionale, genera gli inviti e consulta i clienti con i quali esiste un collegamento attivo.',
        },
        {
          title: 'Disponibilità',
          description:
            'Pubblica giorni e orari che i clienti collegati possono consultare.',
        },
        {
          title: 'Richieste di prenotazione',
          description:
            'Ricevi le richieste, verificane lo stato e confermale o rifiutale.',
        },
      ] satisfies TextItem[],
      benefit:
        'Con orari pubblicati e richieste consultabili, organizzare un appuntamento può richiedere meno passaggi separati.',
    },
    {
      label: 'Nutrizionista',
      title: 'Profilo, inviti e clienti collegati più facili da consultare.',
      description:
        'Support Trainer permette al nutrizionista di completare il proprio profilo professionale, invitare i clienti e consultare i collegamenti già attivi.',
      features: [
        {
          title: 'Profilo professionale',
          description:
            'Completa e consulta le informazioni del tuo profilo professionale.',
        },
        {
          title: 'Inviti',
          description: 'Genera gli inviti da condividere con i tuoi clienti.',
        },
        {
          title: 'Clienti collegati',
          description:
            'Consulta l’elenco dei clienti collegati e le informazioni disponibili per la tua attività professionale.',
        },
      ] satisfies TextItem[],
      benefit:
        'Profilo, inviti e collegamenti diventano più facili da ritrovare e consultare.',
      future: 'Strumenti specifici per la nutrizione — In arrivo',
    },
    {
      label: 'Cliente',
      title: 'Un rapporto più semplice con i professionisti collegati.',
      description:
        'Support Trainer permette al cliente di consultare i professionisti collegati e svolgere le attività disponibili per ciascun collegamento.',
      features: [
        {
          title: 'Professionisti collegati',
          description:
            'Consulta i professionisti con i quali hai attivato un collegamento tramite invito e le informazioni professionali disponibili.',
        },
        {
          title: 'Disponibilità del personal trainer',
          description:
            'Consulta i giorni e gli orari pubblicati dal personal trainer collegato.',
        },
        {
          title: 'Richieste di prenotazione',
          description:
            'Scegli uno degli orari disponibili, invia una richiesta e verificane lo stato.',
        },
      ] satisfies TextItem[],
      benefit:
        'Con il personal trainer puoi consultare gli orari pubblicati e inviare una richiesta con maggiore autonomia, senza dover domandare ogni volta la disponibilità.',
    },
  ],
  availableToday: {
    eyebrow: 'Disponibile oggi',
    title: 'Che cosa puoi già fare in Support Trainer.',
    introduction:
      'Le funzioni disponibili sono organizzate in base a chi utilizza la piattaforma: personal trainer, nutrizionista o cliente.',
    items: [
      'Profili e account',
      'Inviti e collegamenti',
      'Consultazione dei clienti e dei professionisti collegati',
      'Disponibilità del personal trainer',
      'Richieste di prenotazione tra personal trainer e cliente',
    ],
  },
} as const;

export const futureContent = {
  eyebrow: 'Presente e futuro',
  title: 'Le funzioni di oggi, con uno sguardo a ciò che verrà.',
  introduction:
    'Support Trainer mette a disposizione gli strumenti descritti nella home. Le nuove aree vengono indicate separatamente, senza confonderle con ciò che è già utilizzabile.',
  availableSummary:
    'Disponibile oggi: profili, inviti e collegamenti; nel rapporto tra personal trainer e cliente collegato, anche disponibilità e richieste di prenotazione.',
  areas: [
    {
      title: 'Allenamento e nutrizione',
      description:
        'Nuovi strumenti potranno affiancare i percorsi di allenamento e nutrizione, mantenendo distinte le attività dei diversi professionisti.',
    },
    {
      title: 'Progressi e misurazioni',
      description:
        'Strumenti dedicati all’andamento nel tempo potranno aiutare a leggere dati e misurazioni del percorso.',
    },
    {
      title: 'Supporto al percorso',
      description:
        'Feedback sul percorso e altri strumenti potranno aiutare il professionista e il cliente a seguire le attività con maggiore continuità.',
    },
  ] satisfies TextItem[],
  reassurances: [
    {
      title: 'Disponibile oggi e In arrivo restano distinti',
      description:
        'Ogni funzionalità futura viene indicata esplicitamente come In arrivo.',
    },
    {
      title: 'Percorsi pensati per restare semplici',
      description:
        'Accessibilità, facilità d’uso e percorsi comprensibili restano obiettivi dell’evoluzione della piattaforma.',
    },
    {
      title: 'Un’evoluzione attenta all’esperienza reale',
      description:
        'Segnalazioni, suggerimenti ed esigenze concrete potranno contribuire a orientare le priorità della piattaforma.',
    },
  ] satisfies TextItem[],
} as const;

export const faqContent = {
  eyebrow: 'Domande frequenti',
  title: 'Le risposte ai dubbi più comuni.',
  introduction:
    'Registrazione, inviti, ruoli e funzionalità: qui trovi le informazioni essenziali per iniziare a usare Support Trainer.',
  items: [
    {
      id: 'direct-registration',
      question: 'Chi può registrarsi direttamente a Support Trainer?',
      answer:
        'Personal trainer e nutrizionisti possono creare direttamente un account professionale e devono verificare l’indirizzo email prima di poter usare il proprio account. Il cliente può registrarsi soltanto dopo aver ricevuto e validato un codice invito.',
    },
    {
      id: 'client-invite',
      question: 'Perché il cliente ha bisogno di un codice invito?',
      answer:
        'L’invito collega il cliente al professionista che lo ha generato. Il cliente entra quindi nella piattaforma attraverso un rapporto professionale già definito, senza una registrazione libera o una ricerca pubblica dei professionisti.',
    },
    {
      id: 'email-verification',
      question: 'Come funziona la verifica dell’indirizzo email?',
      answer:
        'Dopo la registrazione, il professionista riceve un collegamento per verificare l’indirizzo email. La verifica deve essere completata prima di poter usare il proprio account.',
    },
    {
      id: 'personal-trainer-today',
      question: 'Che cosa può fare oggi un personal trainer?',
      answer:
        'Può completare il profilo professionale, generare inviti, consultare i clienti collegati, pubblicare giorni e orari disponibili e gestire le richieste di prenotazione ricevute, confermandole o rifiutandole.',
    },
    {
      id: 'nutritionist-today',
      question: 'Che cosa può fare oggi un nutrizionista?',
      answer:
        'Può completare il profilo professionale, generare inviti e consultare i clienti con i quali esiste un collegamento attivo. Le aree specifiche dedicate alla nutrizione sono indicate separatamente come In arrivo.',
    },
    {
      id: 'availability-and-bookings',
      question: 'Come funzionano disponibilità e richieste di prenotazione?',
      answer:
        'Il personal trainer pubblica i giorni e gli orari disponibili. Il cliente collegato può consultare gli orari proposti e inviare una richiesta di prenotazione. La richiesta non è confermata automaticamente: il personal trainer può confermarla o rifiutarla ed entrambi possono verificarne lo stato.',
    },
    {
      id: 'coming-soon',
      question: 'Quali strumenti sono ancora in arrivo?',
      answer:
        'Le aree attualmente indicate come In arrivo riguardano allenamento e nutrizione, progressi e misurazioni, oltre a nuovi strumenti di supporto al percorso. Non sono ancora utilizzabili e non rappresentano un ordine o una data di rilascio.',
    },
    {
      id: 'existing-account',
      question: 'Ho già un account. Come posso accedere?',
      answer:
        'Utilizza il collegamento Accedi per raggiungere la pagina di login e inserire le tue credenziali.',
      link: {
        label: 'Accedi',
        to: '/login',
      },
    },
    {
      id: 'feedback-channel',
      question: 'Posso già segnalare un problema o proporre un’idea?',
      answer:
        'Il canale dedicato non è ancora disponibile e viene indicato come In arrivo. In futuro potrà raccogliere problemi tecnici, suggerimenti e proposte di miglioramento.',
    },
  ] satisfies FaqItem[],
} as const;

export const closingContent = {
  eyebrow: 'Il prossimo passo',
  title: 'Porta più chiarezza nel rapporto con i tuoi clienti.',
  introduction:
    'Crea il tuo account come personal trainer o nutrizionista e inizia a usare gli strumenti già disponibili.',
  primaryAction: 'Registrati come professionista',
  inviteAction: 'Ho un codice invito',
  loginAction: 'Hai già un account? Accedi',
  reassurance:
    'Personal trainer e nutrizionisti possono registrarsi direttamente. Il cliente inizia invece dalla validazione del codice invito.',
} as const;

export const footerAccessLinks = [
  { label: 'Accedi', to: '/login' },
  {
    label: 'Registrati come professionista',
    to: '/register/professional',
  },
  { label: 'Ho un codice invito', to: '/invite/validate' },
] as const;

export const footerExploreLinks = [
  { label: 'Come funziona', to: '#come-funziona' },
  { label: 'FAQ', to: '#faq' },
  { label: 'Informazioni sul progetto', to: '#il-progetto' },
] as const;

export const footerContent = {
  description:
    'Una piattaforma pensata per organizzare il rapporto tra professionisti e clienti.',
  futureSupport: [
    'Segnala un problema o proponi un’idea — In arrivo',
    'Instagram — In arrivo',
  ],
  legal: {
    title: 'Informazioni legali — In preparazione',
    items: ['Privacy', 'Cookie', 'Termini di utilizzo', 'Accessibilità'],
  },
} as const;
