import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from 'react';

import { isAbortError } from './bookingPresentation';

export type BookingLoadState<T> =
  | { readonly status: 'loading' }
  | { readonly status: 'success'; readonly data: T }
  | { readonly status: 'error'; readonly error: unknown };

export function useBookingLoad<T>(
  key: string,
  loader: (signal: AbortSignal) => Promise<T>,
): readonly [BookingLoadState<T>, () => void, (data: T) => void] {
  const [entry, setEntry] = useState<{
    readonly key: string;
    readonly state: BookingLoadState<T>;
  }>({ key, state: { status: 'loading' } });
  const [reloadToken, setReloadToken] = useState(0);
  const generationRef = useRef(0);
  const keyRef = useRef(key);
  const state: BookingLoadState<T> =
    entry.key === key ? entry.state : { status: 'loading' };

  useLayoutEffect(() => {
    keyRef.current = key;
  }, [key]);

  useEffect(() => {
    const controller = new AbortController();
    const generation = generationRef.current + 1;
    generationRef.current = generation;
    let mounted = true;

    queueMicrotask(() => {
      if (!mounted) return;
      setEntry({ key, state: { status: 'loading' } });
      void loader(controller.signal)
        .then((data) => {
          if (
            mounted &&
            !controller.signal.aborted &&
            generation === generationRef.current &&
            keyRef.current === key
          ) {
            setEntry({ key, state: { status: 'success', data } });
          }
        })
        .catch((error: unknown) => {
          if (
            mounted &&
            !isAbortError(error) &&
            generation === generationRef.current &&
            keyRef.current === key
          ) {
            setEntry({ key, state: { status: 'error', error } });
          }
        });
    });

    return () => {
      mounted = false;
      controller.abort();
      generationRef.current += 1;
    };
  }, [key, loader, reloadToken]);

  const reload = useCallback(() => {
    if (keyRef.current === key) {
      setReloadToken((value) => value + 1);
    }
  }, [key]);
  const replaceData = useCallback(
    (data: T) => {
      if (keyRef.current !== key) return;
      generationRef.current += 1;
      setEntry({ key, state: { status: 'success', data } });
    },
    [key],
  );
  return [state, reload, replaceData] as const;
}
