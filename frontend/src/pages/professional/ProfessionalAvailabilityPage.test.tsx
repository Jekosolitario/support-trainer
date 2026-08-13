import { StrictMode } from 'react';
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  createWeeklyAvailabilityRule,
  deactivateWeeklyAvailabilityRule,
  listMyAvailabilitySlots,
  listMyWeeklyAvailabilityRules,
  previewWeeklyAvailabilityRuleImpact,
  setAvailabilitySlotBlocked,
  updateWeeklyAvailabilityRule,
} from '../../api/availabilityApi';
import type {
  AvailabilitySlot,
  WeeklyAvailabilityRule,
} from '../../api/availabilityTypes';
import { ProfessionalAvailabilityPage } from './ProfessionalAvailabilityPage';

vi.mock('../../api/availabilityApi', () => ({
  createWeeklyAvailabilityRule: vi.fn(),
  deactivateWeeklyAvailabilityRule: vi.fn(),
  listMyAvailabilitySlots: vi.fn(),
  listMyWeeklyAvailabilityRules: vi.fn(),
  previewWeeklyAvailabilityRuleImpact: vi.fn(),
  setAvailabilitySlotBlocked: vi.fn(),
  updateWeeklyAvailabilityRule: vi.fn(),
}));

const rule: WeeklyAvailabilityRule = {
  id: 7,
  dayOfWeek: 'MONDAY',
  startTime: '09:00',
  endTime: '13:00',
  allowedDurations: [45, 60, 90],
  locationLabel: 'Palestra X',
  capacityPerSlot: 3,
  active: true,
  validFrom: '2026-08-12',
  createdAt: '2026-08-11T08:00:00Z',
  updatedAt: '2026-08-11T08:00:00Z',
};

const slot: AvailabilitySlot = {
  id: 31,
  startDateTime: '2026-08-20T09:00:00+02:00',
  endDateTime: '2026-08-20T10:00:00+02:00',
  locationLabel: 'Palestra X',
  capacity: 3,
  maximumOccupancy: 1,
  minimumRemainingCapacity: 2,
  allowedDurations: [45, 60],
  startIntervalMinutes: 15,
  blocked: false,
  active: true,
  bookable: true,
};

function deferred<Value>() {
  let resolve!: (value: Value) => void;
  const promise = new Promise<Value>((nextResolve) => {
    resolve = nextResolve;
  });
  return { promise, resolve };
}

describe('ProfessionalAvailabilityPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listMyWeeklyAvailabilityRules).mockResolvedValue([rule]);
    vi.mocked(listMyAvailabilitySlots).mockResolvedValue([]);
    vi.mocked(previewWeeklyAvailabilityRuleImpact).mockResolvedValue({
      impactDetected: false,
      impactedBookingCount: 0,
      changeReasonRequired: false,
    });
    vi.mocked(updateWeeklyAvailabilityRule).mockResolvedValue(rule);
    vi.mocked(deactivateWeeklyAvailabilityRule).mockResolvedValue(undefined);
    vi.mocked(setAvailabilitySlotBlocked).mockRejectedValue(
      new Error('Unexpected slot mutation'),
    );
    vi.mocked(createWeeklyAvailabilityRule).mockResolvedValue(rule);
  });

  it('mostra giorno, orario, luogo e capacità della settimana tipo', async () => {
    render(<ProfessionalAvailabilityPage />);

    expect(await screen.findByText('Lunedì')).toBeVisible();
    expect(screen.getByRole('heading', { name: '09:00–13:00' })).toBeVisible();
    expect(screen.getByText('Palestra X')).toBeVisible();
    expect(screen.getByText('3 clienti')).toBeVisible();
  });

  it('invia una nuova fascia con i dettagli configurati', async () => {
    vi.mocked(listMyWeeklyAvailabilityRules).mockResolvedValue([]);
    const user = userEvent.setup();
    render(<ProfessionalAvailabilityPage />);

    await screen.findByRole('heading', { name: 'Nessuna fascia configurata' });
    await user.click(screen.getByRole('button', { name: 'Aggiungi fascia' }));
    await user.selectOptions(screen.getByLabelText('Giorno'), 'TUESDAY');
    await user.clear(screen.getByLabelText('Luogo'));
    await user.type(screen.getByLabelText('Luogo'), 'Studio privato');
    await user.click(screen.getByLabelText('45 min'));
    await user.click(screen.getByLabelText('90 min'));
    await user.clear(screen.getByLabelText('Clienti contemporanei'));
    await user.type(screen.getByLabelText('Clienti contemporanei'), '2');
    await user.click(screen.getByRole('button', { name: 'Salva fascia' }));

    await waitFor(() => {
      expect(createWeeklyAvailabilityRule).toHaveBeenCalledWith(
        expect.objectContaining({
          dayOfWeek: 'TUESDAY',
          locationLabel: 'Studio privato',
          capacityPerSlot: 2,
          allowedDurations: [45, 60, 90],
        }),
      );
    });
  });

  it('ferma la modifica impattante finché non viene richiesto il motivo', async () => {
    vi.mocked(previewWeeklyAvailabilityRuleImpact).mockResolvedValue({
      impactDetected: true,
      impactedBookingCount: 2,
      changeReasonRequired: true,
    });
    const user = userEvent.setup();
    render(<ProfessionalAvailabilityPage />);

    await screen.findByText('Lunedì');
    await user.click(screen.getByRole('button', { name: 'Modifica' }));
    await user.click(screen.getByRole('button', { name: 'Salva fascia' }));

    expect(
      await screen.findByText(
        'Inserisci una motivazione prima di applicare la modifica.',
      ),
    ).toBeVisible();
    expect(updateWeeklyAvailabilityRule).not.toHaveBeenCalled();

    await user.type(
      screen.getByLabelText('Motivazione della modifica'),
      'Cambio sede dal mese prossimo',
    );
    await user.click(screen.getByRole('button', { name: 'Salva fascia' }));

    await waitFor(() => {
      expect(updateWeeklyAvailabilityRule).toHaveBeenCalledWith(
        rule.id,
        expect.objectContaining({
          changeReason: 'Cambio sede dal mese prossimo',
        }),
      );
    });
  });

  it('disattiva una fascia non impattante con effetto immediato', async () => {
    const user = userEvent.setup();
    render(<ProfessionalAvailabilityPage />);

    await screen.findByText('Lunedì');
    await user.click(screen.getByRole('button', { name: 'Disattiva' }));
    await user.click(screen.getByRole('button', { name: 'Disattiva fascia' }));

    await waitFor(() => {
      expect(deactivateWeeklyAvailabilityRule).toHaveBeenCalledWith(
        rule.id,
        null,
      );
    });
  });

  it('richiede almeno una durata prima della rete', async () => {
    const user = userEvent.setup();
    render(<ProfessionalAvailabilityPage />);

    await screen.findByText('Lunedì');
    await user.click(screen.getByRole('button', { name: 'Modifica' }));
    await user.click(screen.getByLabelText('45 min'));
    await user.click(screen.getByLabelText('60 min'));
    await user.click(screen.getByLabelText('90 min'));
    await user.click(screen.getByRole('button', { name: 'Salva fascia' }));

    expect(
      screen.getByText('Seleziona almeno una durata disponibile.'),
    ).toBeVisible();
    expect(previewWeeklyAvailabilityRuleImpact).not.toHaveBeenCalled();
    expect(updateWeeklyAvailabilityRule).not.toHaveBeenCalled();
  });

  it('blocca una singola occorrenza senza modificare la regola', async () => {
    vi.mocked(listMyAvailabilitySlots).mockResolvedValue([slot]);
    vi.mocked(setAvailabilitySlotBlocked).mockResolvedValue({
      ...slot,
      blocked: true,
      bookable: false,
    });
    const user = userEvent.setup();
    render(<ProfessionalAvailabilityPage />);

    await user.click(await screen.findByRole('button', { name: 'Blocca' }));
    await user.type(
      screen.getByLabelText('Motivazione del blocco'),
      'Manutenzione straordinaria',
    );
    await user.click(screen.getByRole('button', { name: 'Conferma blocco' }));

    await waitFor(() => {
      expect(setAvailabilitySlotBlocked).toHaveBeenCalledWith(
        slot.id,
        true,
        'Manutenzione straordinaria',
      );
    });
    expect(screen.getByText('Bloccato')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Sblocca' })).toBeVisible();
    expect(updateWeeklyAvailabilityRule).not.toHaveBeenCalled();
  });

  it('sblocca direttamente una singola occorrenza', async () => {
    const blockedSlot = { ...slot, blocked: true, bookable: false };
    vi.mocked(listMyAvailabilitySlots).mockResolvedValue([blockedSlot]);
    vi.mocked(setAvailabilitySlotBlocked).mockResolvedValue({
      ...blockedSlot,
      blocked: false,
      bookable: true,
    });
    const user = userEvent.setup();
    render(<ProfessionalAvailabilityPage />);

    await user.click(await screen.findByRole('button', { name: 'Sblocca' }));

    await waitFor(() => {
      expect(setAvailabilitySlotBlocked).toHaveBeenCalledWith(
        slot.id,
        false,
        null,
      );
    });
  });

  it('isola il double-submit same-tick mentre la create è pending', async () => {
    vi.mocked(listMyWeeklyAvailabilityRules).mockResolvedValue([]);
    const pendingCreate = deferred<WeeklyAvailabilityRule>();
    vi.mocked(createWeeklyAvailabilityRule).mockReturnValue(
      pendingCreate.promise,
    );
    const user = userEvent.setup();
    render(<ProfessionalAvailabilityPage />);
    await screen.findByRole('heading', { name: 'Nessuna fascia configurata' });
    await user.click(screen.getByRole('button', { name: 'Aggiungi fascia' }));
    const submit = screen.getByRole('button', { name: 'Salva fascia' });

    fireEvent.click(submit);
    fireEvent.click(submit);
    expect(createWeeklyAvailabilityRule).toHaveBeenCalledTimes(1);

    await act(async () => {
      pendingCreate.resolve(rule);
      await pendingCreate.promise;
    });
  });

  it('carica una sola generazione utile in StrictMode', async () => {
    render(
      <StrictMode>
        <ProfessionalAvailabilityPage />
      </StrictMode>,
    );

    expect(await screen.findByText('Lunedì')).toBeVisible();
    expect(listMyWeeklyAvailabilityRules).toHaveBeenCalledTimes(1);
    expect(listMyAvailabilitySlots).toHaveBeenCalledTimes(1);
  });

  it('ignora risposte stale dopo unmount', async () => {
    const rulesRequest = deferred<WeeklyAvailabilityRule[]>();
    const slotsRequest = deferred<AvailabilitySlot[]>();
    vi.mocked(listMyWeeklyAvailabilityRules).mockReturnValue(
      rulesRequest.promise,
    );
    vi.mocked(listMyAvailabilitySlots).mockReturnValue(slotsRequest.promise);
    const view = render(<ProfessionalAvailabilityPage />);
    expect(await screen.findByText('Caricamento fasce…')).toBeVisible();

    view.unmount();
    await act(async () => {
      rulesRequest.resolve([rule]);
      slotsRequest.resolve([slot]);
      await Promise.all([rulesRequest.promise, slotsRequest.promise]);
    });

    expect(screen.queryByText('Lunedì')).not.toBeInTheDocument();
  });

  it('rende gestibili tutte le occorrenze restituite dal rolling horizon', async () => {
    vi.mocked(listMyAvailabilitySlots).mockResolvedValue(
      Array.from({ length: 25 }, (_, index) => ({
        ...slot,
        id: slot.id + index,
      })),
    );
    render(<ProfessionalAvailabilityPage />);

    await screen.findByRole('heading', { name: 'Prossime occorrenze' });
    expect(screen.getAllByRole('button', { name: 'Blocca' })).toHaveLength(25);
  });

  it('mostra un solo errore di caricamento e consente il retry', async () => {
    vi.mocked(listMyWeeklyAvailabilityRules)
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce([rule]);
    const user = userEvent.setup();
    render(<ProfessionalAvailabilityPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Operazione non riuscita. Riprova.',
    );
    expect(screen.getAllByRole('alert')).toHaveLength(1);

    await user.click(screen.getByRole('button', { name: 'Riprova' }));
    expect(await screen.findByText('Lunedì')).toBeVisible();
  });
});
