import {
  HttpApiError,
  NetworkError,
  UnexpectedResponseError,
  type FieldErrorResponse,
} from '../../../api/types';

export type ProfileErrorContext = 'profile' | 'operational-status';

export type ProfileApiFieldErrors = Readonly<Record<string, string>>;

export interface ProfileErrorPresentation {
  readonly summary: string | null;
  readonly fieldErrors: ProfileApiFieldErrors;
}

const GENERIC_ERROR = 'Operazione non completata. Riprova.';
const GENERIC_VALIDATION_ERROR = 'Controlla i dati inseriti e riprova.';

function fieldValidationMessage(
  field: string | null | undefined,
  code: string,
): string | null {
  if (field === 'firstName') {
    switch (code) {
      case 'Size':
        return 'Il nome non può superare 100 caratteri.';
      default:
        return null;
    }
  }

  if (field === 'lastName') {
    switch (code) {
      case 'Size':
        return 'Il cognome non può superare 100 caratteri.';
      default:
        return null;
    }
  }

  if (field === 'primaryGoal') {
    switch (code) {
      case 'Size':
        return 'L’obiettivo principale non può superare 255 caratteri.';
      default:
        return null;
    }
  }

  if (field === 'birthDate') {
    switch (code) {
      case 'Past':
        return 'La data di nascita deve essere nel passato.';
      default:
        return null;
    }
  }

  if (field === 'heightCm') {
    switch (code) {
      case 'DecimalMin':
        return 'L’altezza deve essere maggiore di 0.';
      case 'Digits':
        return 'L’altezza deve avere massimo 3 cifre intere e 2 decimali.';
      default:
        return null;
    }
  }

  if (field === 'phoneNumber' && code === 'Size') {
    return 'Il numero di telefono non può superare 30 caratteri.';
  }

  if (field === 'bio' && code === 'Size') {
    return 'La bio non può superare 5000 caratteri.';
  }

  if (field === 'workplaceName' && code === 'Size') {
    return 'Il nome del luogo di lavoro non può superare 150 caratteri.';
  }

  if (field === 'city' && code === 'Size') {
    return 'La città non può superare 100 caratteri.';
  }

  if (field === 'instagramUrl') {
    switch (code) {
      case 'Size':
        return 'L’URL Instagram non può superare 255 caratteri.';
      case 'Pattern':
        return 'L’URL Instagram deve iniziare con http:// o https://.';
      default:
        return null;
    }
  }

  if (field === 'websiteUrl') {
    switch (code) {
      case 'Size':
        return 'L’URL del sito web non può superare 255 caratteri.';
      case 'Pattern':
        return 'L’URL del sito web deve iniziare con http:// o https://.';
      default:
        return null;
    }
  }

  if (
    (field === 'medicalNotes' ||
      field === 'injuryNotes' ||
      field === 'notes') &&
    code === 'Size'
  ) {
    if (field === 'medicalNotes') {
      return 'Le note mediche non possono superare 5000 caratteri.';
    }
    if (field === 'injuryNotes') {
      return 'Le note sugli infortuni non possono superare 5000 caratteri.';
    }
    return 'Le note non possono superare 5000 caratteri.';
  }

  if (field === 'operationalStatus') {
    switch (code) {
      case 'NotBlank':
        return 'Lo stato operativo è obbligatorio.';
      case 'Size':
        return 'Lo stato operativo non può superare 50 caratteri.';
      default:
        return null;
    }
  }

  return null;
}

function validationPresentation(
  fieldErrors: FieldErrorResponse[] | undefined,
): ProfileErrorPresentation {
  const mapped: Record<string, string> = {};
  let hasUnmapped = fieldErrors === undefined || fieldErrors.length === 0;

  for (const fieldError of fieldErrors ?? []) {
    const message = fieldValidationMessage(fieldError.field, fieldError.code);

    if (message === null) {
      hasUnmapped = true;
      continue;
    }

    if (
      fieldError.field !== undefined &&
      fieldError.field !== null &&
      mapped[fieldError.field] === undefined
    ) {
      mapped[fieldError.field] = message;
    }
  }

  return {
    summary: hasUnmapped ? GENERIC_VALIDATION_ERROR : null,
    fieldErrors: mapped,
  };
}

export function getProfileErrorPresentation(
  error: unknown,
  context: ProfileErrorContext = 'profile',
): ProfileErrorPresentation {
  if (
    error instanceof NetworkError ||
    error instanceof UnexpectedResponseError
  ) {
    return { summary: GENERIC_ERROR, fieldErrors: {} };
  }

  if (!(error instanceof HttpApiError)) {
    return { summary: GENERIC_ERROR, fieldErrors: {} };
  }

  if (error.status < 400 || error.status >= 500) {
    return { summary: GENERIC_ERROR, fieldErrors: {} };
  }

  switch (error.body?.code) {
    case 'VALIDATION_ERROR':
      return validationPresentation(error.body.fieldErrors);
    case 'PROFILE_FIELDS_NOT_ALLOWED':
      return {
        summary: 'Alcuni campi non sono consentiti per il tuo profilo.',
        fieldErrors: {},
      };
    case 'INVALID_OPERATIONAL_STATUS':
      return {
        summary:
          context === 'operational-status'
            ? 'Stato operativo non valido.'
            : 'Lo stato operativo indicato non è valido.',
        fieldErrors: {},
      };
    case 'INVALID_REQUEST':
      return {
        summary: 'I dati inviati non sono validi. Controlla i campi e riprova.',
        fieldErrors: {},
      };
    case 'MALFORMED_REQUEST':
      return {
        summary: 'La richiesta non è valida. Controlla i dati e riprova.',
        fieldErrors: {},
      };
    default:
      return { summary: GENERIC_ERROR, fieldErrors: {} };
  }
}
