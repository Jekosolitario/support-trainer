import { useEffect, useRef, useState } from 'react';

import {
  createWeeklyAvailabilityRule,
  deactivateWeeklyAvailabilityRule,
  listMyAvailabilitySlots,
  listMyWeeklyAvailabilityRules,
  previewWeeklyAvailabilityRuleImpact,
  setAvailabilitySlotBlocked,
  updateWeeklyAvailabilityRule,
} from '../../api/availabilityApi';
import {
  DAY_OF_WEEK_VALUES,
  DURATION_OPTIONS,
  MAX_DURATION_MINUTES,
  MIN_DURATION_MINUTES,
  START_INTERVAL_MINUTES,
  type AvailabilitySlot,
  type DayOfWeek,
  type WeeklyAvailabilityRule,
} from '../../api/availabilityTypes';
import { HttpApiError } from '../../api/types';
import { PageTemplate } from '../../components/page/PageTemplate';
import styles from './ProfessionalAvailabilityPage.module.css';

const DAY_LABELS: Record<DayOfWeek, string> = {
  MONDAY: 'Lunedì',
  TUESDAY: 'Martedì',
  WEDNESDAY: 'Mercoledì',
  THURSDAY: 'Giovedì',
  FRIDAY: 'Venerdì',
  SATURDAY: 'Sabato',
  SUNDAY: 'Domenica',
};

type FormMode =
  | { readonly kind: 'create' }
  | { readonly kind: 'edit'; readonly rule: WeeklyAvailabilityRule }
  | { readonly kind: 'deactivate'; readonly rule: WeeklyAvailabilityRule };

interface RuleFormState {
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
  allowedDurations: number[];
  locationLabel: string;
  capacityPerSlot: string;
  validFrom: string;
  changeReason: string;
}

function todayInRome(): string {
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Europe/Rome',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
}

function emptyForm(): RuleFormState {
  return {
    dayOfWeek: 'MONDAY',
    startTime: '09:00',
    endTime: '13:00',
    allowedDurations: [60],
    locationLabel: '',
    capacityPerSlot: '1',
    validFrom: todayInRome(),
    changeReason: '',
  };
}

function formFromRule(rule: WeeklyAvailabilityRule): RuleFormState {
  return {
    dayOfWeek: rule.dayOfWeek,
    startTime: rule.startTime,
    endTime: rule.endTime,
    allowedDurations: [...rule.allowedDurations],
    locationLabel: rule.locationLabel ?? '',
    capacityPerSlot: String(rule.capacityPerSlot),
    validFrom: todayInRome(),
    changeReason: '',
  };
}

function isAbortError(error: unknown): boolean {
  return (
    error !== null &&
    typeof error === 'object' &&
    'name' in error &&
    error.name === 'AbortError'
  );
}

function errorMessage(error: unknown): string {
  return error instanceof HttpApiError && error.body?.message
    ? error.body.message
    : 'Operazione non riuscita. Riprova.';
}

function positiveInteger(value: string): number | null {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

function timeMinutes(value: string): number | null {
  const match = /^(\d{2}):(\d{2})$/.exec(value);
  return match === null ? null : Number(match[1]) * 60 + Number(match[2]);
}

function windowMinutes(form: RuleFormState): number {
  const start = timeMinutes(form.startTime);
  const end = timeMinutes(form.endTime);
  return start === null || end === null || end <= start ? 0 : end - start;
}

export function ProfessionalAvailabilityPage() {
  const [rules, setRules] = useState<WeeklyAvailabilityRule[]>([]);
  const [slots, setSlots] = useState<AvailabilitySlot[]>([]);
  const [listStatus, setListStatus] = useState<'loading' | 'success' | 'error'>(
    'loading',
  );
  const [mode, setMode] = useState<FormMode | null>(null);
  const [form, setForm] = useState<RuleFormState>(emptyForm);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [impactMessage, setImpactMessage] = useState<string | null>(null);
  const [blockTarget, setBlockTarget] = useState<AvailabilitySlot | null>(null);
  const [blockReason, setBlockReason] = useState('');
  const [pending, setPending] = useState(false);
  const mountedRef = useRef(true);
  const mutationLockRef = useRef(false);
  const loadGenerationRef = useRef(0);
  const abortRef = useRef<AbortController | null>(null);

  async function loadRules(): Promise<void> {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    const generation = ++loadGenerationRef.current;
    setListStatus('loading');
    setError(null);
    try {
      const [rulesResponse, slotsResponse] = await Promise.all([
        listMyWeeklyAvailabilityRules({ signal: controller.signal }),
        listMyAvailabilitySlots({ signal: controller.signal }),
      ]);
      if (
        !mountedRef.current ||
        controller.signal.aborted ||
        generation !== loadGenerationRef.current
      )
        return;
      setRules(rulesResponse);
      setSlots(slotsResponse);
      setListStatus('success');
    } catch (loadError) {
      if (
        isAbortError(loadError) ||
        !mountedRef.current ||
        generation !== loadGenerationRef.current
      )
        return;
      setListStatus('error');
      setError(errorMessage(loadError));
    }
  }

  useEffect(() => {
    mountedRef.current = true;
    let cancelled = false;
    queueMicrotask(() => {
      if (!cancelled) void loadRules();
    });
    return () => {
      cancelled = true;
      mountedRef.current = false;
      abortRef.current?.abort();
      loadGenerationRef.current += 1;
    };
  }, []);

  function open(nextMode: FormMode): void {
    setMode(nextMode);
    setForm(
      nextMode.kind === 'create' ? emptyForm() : formFromRule(nextMode.rule),
    );
    setFeedback(null);
    setError(null);
    setImpactMessage(null);
  }

  function validateForm(): string | null {
    if (mode?.kind === 'create' && form.validFrom < todayInRome())
      return 'La data di efficacia non può essere nel passato.';
    if (mode?.kind === 'deactivate') return null;
    const start = timeMinutes(form.startTime);
    const end = timeMinutes(form.endTime);
    if (
      start === null ||
      end === null ||
      end <= start ||
      start % START_INTERVAL_MINUTES !== 0 ||
      end % START_INTERVAL_MINUTES !== 0
    )
      return 'Gli orari devono definire una fascia valida e allineata a 15 minuti.';
    if (form.allowedDurations.length === 0)
      return 'Seleziona almeno una durata disponibile.';
    if (
      form.allowedDurations.some(
        (duration) =>
          duration < MIN_DURATION_MINUTES ||
          duration > MAX_DURATION_MINUTES ||
          duration % START_INTERVAL_MINUTES !== 0 ||
          duration > end - start,
      )
    )
      return 'Ogni durata deve essere multipla di 15 minuti e rientrare nella fascia.';
    if (positiveInteger(form.capacityPerSlot) === null)
      return 'La capacità deve essere almeno 1.';
    return null;
  }

  async function handleSubmit(): Promise<void> {
    if (mode === null || mutationLockRef.current) return;
    const validation = validateForm();
    if (validation !== null) {
      setError(validation);
      return;
    }

    mutationLockRef.current = true;
    setPending(true);
    setError(null);
    setFeedback(null);
    try {
      if (mode.kind === 'create') {
        const capacity = positiveInteger(form.capacityPerSlot);
        if (capacity === null) throw new Error('Invalid numeric input');
        await createWeeklyAvailabilityRule({
          dayOfWeek: form.dayOfWeek,
          startTime: form.startTime,
          endTime: form.endTime,
          allowedDurations: form.allowedDurations,
          locationLabel: form.locationLabel.trim() || null,
          capacityPerSlot: capacity,
          validFrom: form.validFrom,
        });
        if (!mountedRef.current) return;
        setFeedback('Fascia settimanale creata e calendario aggiornato.');
        setMode(null);
        await loadRules();
        return;
      }

      const impact = await previewWeeklyAvailabilityRuleImpact(mode.rule.id);
      if (!mountedRef.current) return;
      if (impact.impactDetected) {
        setImpactMessage(
          `La modifica coinvolge ${String(impact.impactedBookingCount)} prenotazioni in attesa o confermate. Le prenotazioni non saranno spostate o eliminate.`,
        );
        if (form.changeReason.trim() === '') {
          setError('Inserisci una motivazione prima di applicare la modifica.');
          return;
        }
      }

      if (mode.kind === 'edit') {
        const capacity = positiveInteger(form.capacityPerSlot);
        if (capacity === null) throw new Error('Invalid numeric input');
        await updateWeeklyAvailabilityRule(mode.rule.id, {
          dayOfWeek: form.dayOfWeek,
          startTime: form.startTime,
          endTime: form.endTime,
          allowedDurations: form.allowedDurations,
          locationLabel: form.locationLabel.trim() || null,
          capacityPerSlot: capacity,
          changeReason: form.changeReason.trim() || null,
        });
        if (!mountedRef.current) return;
        setFeedback('Fascia aggiornata e disponibilità future rigenerate.');
      } else {
        await deactivateWeeklyAvailabilityRule(
          mode.rule.id,
          form.changeReason.trim() || null,
        );
        if (!mountedRef.current) return;
        setFeedback(
          'Fascia disattivata. Le prenotazioni esistenti restano valide.',
        );
      }
      setMode(null);
      await loadRules();
    } catch (submitError) {
      if (mountedRef.current && !isAbortError(submitError))
        setError(errorMessage(submitError));
    } finally {
      mutationLockRef.current = false;
      if (mountedRef.current) setPending(false);
    }
  }

  function handleSlotAction(slot: AvailabilitySlot): void {
    if (!slot.blocked && slot.maximumOccupancy > 0) {
      setBlockTarget(slot);
      setBlockReason('');
      setError(null);
      return;
    }
    void performSlotAction(slot, null);
  }

  async function performSlotAction(
    slot: AvailabilitySlot,
    changeReason: string | null,
  ): Promise<void> {
    if (mutationLockRef.current) return;
    mutationLockRef.current = true;
    setPending(true);
    setError(null);
    setFeedback(null);
    try {
      const updated = await setAvailabilitySlotBlocked(
        slot.id,
        !slot.blocked,
        changeReason,
      );
      if (!mountedRef.current) return;
      setSlots((current) =>
        current.map((candidate) =>
          candidate.id === updated.id ? updated : candidate,
        ),
      );
      setFeedback(
        updated.blocked ? 'Occorrenza bloccata.' : 'Occorrenza sbloccata.',
      );
      setBlockTarget(null);
      setBlockReason('');
    } catch (slotError) {
      if (mountedRef.current && !isAbortError(slotError)) {
        setError(errorMessage(slotError));
      }
    } finally {
      mutationLockRef.current = false;
      if (mountedRef.current) setPending(false);
    }
  }

  return (
    <PageTemplate
      eyebrow="Area personal trainer"
      title="Disponibilità"
      description="Configura finestre ricorrenti con più durate: Support Trainer mantiene le occorrenze concrete dei prossimi sei mesi."
    >
      <div className={styles.toolbar}>
        <button
          type="button"
          className={styles.primaryButton}
          disabled={pending}
          onClick={() => open({ kind: 'create' })}
        >
          Aggiungi fascia
        </button>
      </div>

      {feedback ? (
        <p className={`${styles.feedback} ${styles.success}`} role="status">
          {feedback}
        </p>
      ) : null}
      {error && mode === null && listStatus !== 'error' ? (
        <p className={`${styles.feedback} ${styles.failure}`} role="alert">
          {error}
        </p>
      ) : null}

      {mode !== null ? (
        <RuleForm
          form={form}
          mode={mode}
          pending={pending}
          error={error}
          impactMessage={impactMessage}
          onChange={setForm}
          onCancel={() => {
            if (!pending) {
              setMode(null);
              setError(null);
              setImpactMessage(null);
            }
          }}
          onSubmit={() => {
            void handleSubmit();
          }}
        />
      ) : null}

      {blockTarget !== null ? (
        <section className={styles.formCard} aria-labelledby="block-form-title">
          <h2 id="block-form-title">Blocca occorrenza con prenotazioni</h2>
          <p className={styles.warningText}>
            Le prenotazioni esistenti restano valide. Inserisci una motivazione
            per registrare il cambiamento.
          </p>
          <label className={styles.wideField}>
            Motivazione del blocco
            <textarea
              rows={3}
              maxLength={1000}
              value={blockReason}
              disabled={pending}
              required
              onChange={(event) => setBlockReason(event.target.value)}
            />
          </label>
          <div className={styles.formActions}>
            <button
              type="button"
              className={styles.dangerButton}
              disabled={pending || blockReason.trim() === ''}
              onClick={() => {
                void performSlotAction(blockTarget, blockReason.trim());
              }}
            >
              Conferma blocco
            </button>
            <button
              type="button"
              className={styles.secondaryButton}
              disabled={pending}
              onClick={() => {
                setBlockTarget(null);
                setBlockReason('');
              }}
            >
              Annulla
            </button>
          </div>
        </section>
      ) : null}

      {listStatus === 'loading' ? (
        <p className={styles.muted} role="status">
          Caricamento fasce…
        </p>
      ) : null}
      {listStatus === 'error' ? (
        <div className={`${styles.feedback} ${styles.failure}`} role="alert">
          <p>{error ?? 'Impossibile caricare le fasce.'}</p>
          <button
            type="button"
            className={styles.secondaryButton}
            onClick={() => {
              void loadRules();
            }}
          >
            Riprova
          </button>
        </div>
      ) : null}
      {listStatus === 'success' && rules.length === 0 ? (
        <section className={styles.emptyState} aria-labelledby="empty-title">
          <h2 id="empty-title">Nessuna fascia configurata</h2>
          <p>
            Aggiungi il primo intervallo ricorrente. Puoi creare più fasce nello
            stesso giorno, purché non si sovrappongano.
          </p>
        </section>
      ) : null}
      {listStatus === 'success' && rules.length > 0 ? (
        <ul className={styles.ruleList} aria-label="Fasce settimanali">
          {rules
            .slice()
            .sort(compareRules)
            .map((rule) => (
              <RuleCard
                key={rule.id}
                rule={rule}
                disabled={pending}
                onEdit={() => open({ kind: 'edit', rule })}
                onDeactivate={() => open({ kind: 'deactivate', rule })}
              />
            ))}
        </ul>
      ) : null}

      {listStatus === 'success' && slots.length > 0 ? (
        <section
          className={styles.occurrences}
          aria-labelledby="occurrences-title"
        >
          <header>
            <h2 id="occurrences-title">Prossime occorrenze</h2>
            <p>Blocca una singola data senza modificare la settimana tipo.</p>
          </header>
          <ul className={styles.slotList}>
            {slots.map((slot) => (
              <SlotCard
                key={slot.id}
                slot={slot}
                disabled={pending}
                onClick={() => {
                  handleSlotAction(slot);
                }}
              />
            ))}
          </ul>
        </section>
      ) : null}
    </PageTemplate>
  );
}

function RuleForm({
  form,
  mode,
  pending,
  error,
  impactMessage,
  onChange,
  onCancel,
  onSubmit,
}: {
  readonly form: RuleFormState;
  readonly mode: FormMode;
  readonly pending: boolean;
  readonly error: string | null;
  readonly impactMessage: string | null;
  readonly onChange: (value: RuleFormState) => void;
  readonly onCancel: () => void;
  readonly onSubmit: () => void;
}) {
  const isDeactivate = mode.kind === 'deactivate';
  const title =
    mode.kind === 'create'
      ? 'Nuova fascia'
      : mode.kind === 'edit'
        ? `Modifica ${DAY_LABELS[mode.rule.dayOfWeek]}`
        : `Disattiva ${DAY_LABELS[mode.rule.dayOfWeek]}`;
  function change<Key extends keyof RuleFormState>(
    key: Key,
    value: RuleFormState[Key],
  ): void {
    onChange({ ...form, [key]: value });
  }

  return (
    <section
      className={styles.formCard}
      aria-labelledby="availability-form-title"
    >
      <h2 id="availability-form-title">{title}</h2>
      {!isDeactivate ? (
        <div className={styles.formGrid}>
          <label>
            Giorno
            <select
              value={form.dayOfWeek}
              disabled={pending}
              onChange={(event) =>
                change('dayOfWeek', event.target.value as DayOfWeek)
              }
            >
              {DAY_OF_WEEK_VALUES.map((day) => (
                <option key={day} value={day}>
                  {DAY_LABELS[day]}
                </option>
              ))}
            </select>
          </label>
          <label>
            Dalle
            <input
              type="time"
              step={START_INTERVAL_MINUTES * 60}
              value={form.startTime}
              disabled={pending}
              required
              onChange={(event) => change('startTime', event.target.value)}
            />
          </label>
          <label>
            Alle
            <input
              type="time"
              step={START_INTERVAL_MINUTES * 60}
              value={form.endTime}
              disabled={pending}
              required
              onChange={(event) => change('endTime', event.target.value)}
            />
          </label>
          <fieldset className={styles.durationFieldset}>
            <legend>Durate disponibili</legend>
            <div className={styles.durationOptions}>
              {DURATION_OPTIONS.map((duration) => (
                <label key={duration}>
                  <input
                    type="checkbox"
                    checked={form.allowedDurations.includes(duration)}
                    disabled={pending}
                    onChange={(event) =>
                      change(
                        'allowedDurations',
                        event.target.checked
                          ? [...form.allowedDurations, duration].sort(
                              (left, right) => left - right,
                            )
                          : form.allowedDurations.filter(
                              (candidate) => candidate !== duration,
                            ),
                      )
                    }
                  />
                  {duration} min
                </label>
              ))}
            </div>
          </fieldset>
          <label>
            Luogo
            <input
              type="text"
              maxLength={255}
              value={form.locationLabel}
              disabled={pending}
              placeholder="Palestra, studio, parco o online"
              onChange={(event) => change('locationLabel', event.target.value)}
            />
          </label>
          <label>
            Clienti contemporanei
            <input
              type="number"
              min="1"
              step="1"
              value={form.capacityPerSlot}
              disabled={pending}
              required
              onChange={(event) =>
                change('capacityPerSlot', event.target.value)
              }
            />
          </label>
        </div>
      ) : (
        <p className={styles.warningText}>
          Le finestre future della fascia non saranno più prenotabili. Le
          richieste già presenti non vengono cancellate né spostate.
        </p>
      )}

      <div className={styles.formGrid}>
        {mode.kind === 'create' ? (
          <label>
            Valida dal
            <input
              type="date"
              min={todayInRome()}
              value={form.validFrom}
              disabled={pending}
              required
              onChange={(event) => change('validFrom', event.target.value)}
            />
          </label>
        ) : null}
        {mode.kind !== 'create' ? (
          <label className={styles.wideField}>
            Motivazione della modifica
            <textarea
              rows={3}
              maxLength={1000}
              value={form.changeReason}
              disabled={pending}
              placeholder="Obbligatoria se sono coinvolte prenotazioni"
              onChange={(event) => change('changeReason', event.target.value)}
            />
          </label>
        ) : null}
      </div>

      {!isDeactivate && windowMinutes(form) > 0 ? (
        <p className={styles.slotPreview} role="status">
          Finestra di {String(windowMinutes(form))} minuti con partenze ogni 15
          minuti e {String(form.allowedDurations.length)} durate selezionate.
        </p>
      ) : null}
      {impactMessage ? (
        <p className={`${styles.feedback} ${styles.warning}`} role="status">
          {impactMessage}
        </p>
      ) : null}
      {error ? (
        <p className={`${styles.feedback} ${styles.failure}`} role="alert">
          {error}
        </p>
      ) : null}
      <div className={styles.formActions}>
        <button
          type="button"
          className={isDeactivate ? styles.dangerButton : styles.primaryButton}
          disabled={pending}
          onClick={onSubmit}
        >
          {pending
            ? 'Salvataggio…'
            : isDeactivate
              ? 'Disattiva fascia'
              : 'Salva fascia'}
        </button>
        <button
          type="button"
          className={styles.secondaryButton}
          disabled={pending}
          onClick={onCancel}
        >
          Annulla
        </button>
      </div>
    </section>
  );
}

function RuleCard({
  rule,
  disabled,
  onEdit,
  onDeactivate,
}: {
  readonly rule: WeeklyAvailabilityRule;
  readonly disabled: boolean;
  readonly onEdit: () => void;
  readonly onDeactivate: () => void;
}) {
  return (
    <li className={styles.ruleCard}>
      <div className={styles.ruleHeading}>
        <div>
          <p className={styles.day}>{DAY_LABELS[rule.dayOfWeek]}</p>
          <h2>
            {rule.startTime}–{rule.endTime}
          </h2>
        </div>
        <span className={styles.capacityBadge}>
          {rule.capacityPerSlot}{' '}
          {rule.capacityPerSlot === 1 ? 'cliente' : 'clienti'}
        </span>
      </div>
      <dl className={styles.ruleMeta}>
        <div>
          <dt>Durate</dt>
          <dd>
            {rule.allowedDurations
              .map((value) => `${String(value)} min`)
              .join(', ')}
          </dd>
        </div>
        <div>
          <dt>Luogo</dt>
          <dd>{rule.locationLabel ?? 'Non specificato'}</dd>
        </div>
        <div>
          <dt>Valida dal</dt>
          <dd>{formatDate(rule.validFrom)}</dd>
        </div>
      </dl>
      <div className={styles.cardActions}>
        <button
          type="button"
          className={styles.secondaryButton}
          disabled={disabled}
          onClick={onEdit}
        >
          Modifica
        </button>
        <button
          type="button"
          className={styles.textDangerButton}
          disabled={disabled}
          onClick={onDeactivate}
        >
          Disattiva
        </button>
      </div>
    </li>
  );
}

function SlotCard({
  slot,
  disabled,
  onClick,
}: {
  readonly slot: AvailabilitySlot;
  readonly disabled: boolean;
  readonly onClick: () => void;
}) {
  const start = new Date(slot.startDateTime);
  const end = new Date(slot.endDateTime);
  const dateLabel = new Intl.DateTimeFormat('it-IT', {
    weekday: 'long',
    day: 'numeric',
    month: 'short',
    timeZone: 'Europe/Rome',
  }).format(start);
  const timeFormatter = new Intl.DateTimeFormat('it-IT', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: 'Europe/Rome',
  });

  return (
    <li className={styles.slotCard}>
      <div>
        <p className={styles.slotDate}>{dateLabel}</p>
        <p className={styles.slotTime}>
          {timeFormatter.format(start)}–{timeFormatter.format(end)}
        </p>
        <p className={styles.slotLocation}>
          {slot.locationLabel ?? 'Luogo non specificato'}
        </p>
      </div>
      <div className={styles.slotStatus}>
        <span>
          {slot.blocked
            ? 'Bloccato'
            : `${String(slot.minimumRemainingCapacity)} di ${String(slot.capacity)} posti minimi liberi`}
        </span>
        <button
          type="button"
          className={
            slot.blocked ? styles.secondaryButton : styles.textDangerButton
          }
          disabled={disabled}
          onClick={onClick}
        >
          {slot.blocked ? 'Sblocca' : 'Blocca'}
        </button>
      </div>
    </li>
  );
}

function compareRules(
  left: WeeklyAvailabilityRule,
  right: WeeklyAvailabilityRule,
): number {
  const byDay =
    DAY_OF_WEEK_VALUES.indexOf(left.dayOfWeek) -
    DAY_OF_WEEK_VALUES.indexOf(right.dayOfWeek);
  return byDay !== 0 ? byDay : left.startTime.localeCompare(right.startTime);
}

function formatDate(value: string): string {
  const [year, month, day] = value.split('-').map(Number);
  return new Intl.DateTimeFormat('it-IT', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: 'Europe/Rome',
  }).format(new Date(Date.UTC(year, month - 1, day)));
}
