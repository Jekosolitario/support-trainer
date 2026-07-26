/**
 * Non-React notification channel for session invalidation.
 * Subscribers (e.g. AuthProvider in a later lot) register cleanup-safe callbacks.
 */

export type SessionInvalidationCallback = () => void;

const subscribers = new Set<SessionInvalidationCallback>();

export function subscribe(callback: SessionInvalidationCallback): () => void {
  subscribers.add(callback);

  return () => {
    subscribers.delete(callback);
  };
}

export function notifySessionInvalidated(): void {
  for (const callback of [...subscribers]) {
    try {
      callback();
    } catch {
      // A subscriber must not prevent the remaining notifications or replace
      // the HTTP error observed by the request caller.
    }
  }
}
