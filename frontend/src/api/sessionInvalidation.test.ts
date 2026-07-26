import { afterEach, describe, expect, it, vi } from 'vitest';

import { notifySessionInvalidated, subscribe } from './sessionInvalidation';

describe('sessionInvalidation', () => {
  const cleanups: Array<() => void> = [];

  afterEach(() => {
    while (cleanups.length > 0) {
      cleanups.pop()?.();
    }
  });

  function trackSubscribe(callback: () => void) {
    const unsubscribe = subscribe(callback);
    cleanups.push(unsubscribe);
    return unsubscribe;
  }

  it('notifica il subscriber registrato', () => {
    const callback = vi.fn();
    trackSubscribe(callback);

    notifySessionInvalidated();

    expect(callback).toHaveBeenCalledTimes(1);
  });

  it('unsubscribe rimuove esattamente il subscriber', () => {
    const kept = vi.fn();
    const removed = vi.fn();

    trackSubscribe(kept);
    const unsubscribeRemoved = trackSubscribe(removed);
    unsubscribeRemoved();

    notifySessionInvalidated();

    expect(kept).toHaveBeenCalledTimes(1);
    expect(removed).not.toHaveBeenCalled();
  });

  it('il subscriber vecchio non viene notificato dopo cleanup', () => {
    const previous = vi.fn();
    const unsubscribePrevious = trackSubscribe(previous);
    unsubscribePrevious();

    notifySessionInvalidated();

    expect(previous).not.toHaveBeenCalled();
  });

  it('una nuova subscription funziona dopo cleanup della precedente', () => {
    const previous = vi.fn();
    const next = vi.fn();

    const unsubscribePrevious = trackSubscribe(previous);
    unsubscribePrevious();
    trackSubscribe(next);

    notifySessionInvalidated();

    expect(previous).not.toHaveBeenCalled();
    expect(next).toHaveBeenCalledTimes(1);
  });

  it('isola le eccezioni dei subscriber e continua la notifica', () => {
    const throwing = vi.fn(() => {
      throw new Error('subscriber failure');
    });
    const next = vi.fn();

    const unsubscribeThrowing = trackSubscribe(throwing);
    trackSubscribe(next);

    expect(() => notifySessionInvalidated()).not.toThrow();
    expect(throwing).toHaveBeenCalledTimes(1);
    expect(next).toHaveBeenCalledTimes(1);

    unsubscribeThrowing();
    notifySessionInvalidated();

    expect(throwing).toHaveBeenCalledTimes(1);
    expect(next).toHaveBeenCalledTimes(2);
  });
});
