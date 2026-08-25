import { act, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { CLIENT_ACCESS_PROFILE } from '../../app/config/access';
import { MobileNavigationDrawer } from './MobileNavigationDrawer';

const DESKTOP_MEDIA_QUERY = '(min-width: 48rem)';
const originalMatchMedia = window.matchMedia;

function createDesktopMediaMock(initialMatches: boolean) {
  let matches = initialMatches;
  const listeners = new Set<(event: MediaQueryListEvent) => void>();

  const mediaQueryList = {
    media: DESKTOP_MEDIA_QUERY,
    onchange: null,
    get matches() {
      return matches;
    },
    addEventListener(
      type: string,
      listener: EventListenerOrEventListenerObject,
    ) {
      if (type !== 'change' || typeof listener !== 'function') {
        return;
      }

      listeners.add(listener as (event: MediaQueryListEvent) => void);
    },
    removeEventListener(
      type: string,
      listener: EventListenerOrEventListenerObject,
    ) {
      if (type !== 'change' || typeof listener !== 'function') {
        return;
      }

      listeners.delete(listener as (event: MediaQueryListEvent) => void);
    },
    addListener() {},
    removeListener() {},
    dispatchEvent() {
      return true;
    },
  } as MediaQueryList;

  window.matchMedia = (query: string) => {
    if (query === DESKTOP_MEDIA_QUERY) {
      return mediaQueryList;
    }

    return {
      matches: false,
      media: query,
      onchange: null,
      addEventListener() {},
      removeEventListener() {},
      addListener() {},
      removeListener() {},
      dispatchEvent() {
        return true;
      },
    } as MediaQueryList;
  };

  return {
    setMatches(nextMatches: boolean) {
      matches = nextMatches;
      const event = {
        matches: nextMatches,
        media: DESKTOP_MEDIA_QUERY,
      } as MediaQueryListEvent;
      listeners.forEach((listener) => {
        listener(event);
      });
    },
    getListenerCount() {
      return listeners.size;
    },
    restore() {
      window.matchMedia = originalMatchMedia;
    },
  };
}

function renderDrawer() {
  return render(
    <MemoryRouter initialEntries={['/app/client/dashboard']}>
      <MobileNavigationDrawer
        profile={CLIENT_ACCESS_PROFILE}
        footer={<button type="button">Esci</button>}
      />
    </MemoryRouter>,
  );
}

async function openDrawer() {
  const user = userEvent.setup();
  const trigger = screen.getByRole('button', { name: 'Menu' });
  await user.click(trigger);

  return {
    user,
    trigger,
    dialog: screen.getByRole('dialog', { name: 'Navigazione' }),
  };
}

function getDrawerLayer(trigger: HTMLElement) {
  return document.getElementById(trigger.getAttribute('aria-controls') ?? '');
}

let desktopMedia: ReturnType<typeof createDesktopMediaMock>;

beforeEach(() => {
  desktopMedia = createDesktopMediaMock(false);
});

afterEach(() => {
  desktopMedia.restore();
  document.body.style.overflow = '';
});

describe('MobileNavigationDrawer', () => {
  it('è chiuso di default e collega trigger e drawer con aria-controls', () => {
    renderDrawer();

    const trigger = screen.getByRole('button', { name: 'Menu' });
    const drawerId = trigger.getAttribute('aria-controls');

    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    expect(drawerId).toBeTruthy();
    expect(document.getElementById(drawerId ?? '')).toHaveAttribute(
      'aria-hidden',
      'true',
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('si apre dal trigger, espone la semantica modal e sposta il focus', async () => {
    renderDrawer();

    const { trigger, dialog } = await openDrawer();

    expect(trigger).toHaveAttribute('aria-expanded', 'true');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(
      within(dialog).getByRole('button', { name: 'Chiudi menu' }),
    ).toHaveFocus();
  });

  it('intrappola il focus in avanti e all’indietro', async () => {
    renderDrawer();
    const { dialog } = await openDrawer();
    const closeButton = within(dialog).getByRole('button', {
      name: 'Chiudi menu',
    });
    const lastButton = within(dialog).getByRole('button', { name: 'Esci' });

    lastButton.focus();
    fireEvent.keyDown(document, { key: 'Tab' });
    expect(closeButton).toHaveFocus();

    closeButton.focus();
    fireEvent.keyDown(document, { key: 'Tab', shiftKey: true });
    expect(lastButton).toHaveFocus();
  });

  it('recupera il focus se viene spostato programmaticamente fuori dal drawer', async () => {
    renderDrawer();
    const { trigger, dialog } = await openDrawer();
    const closeButton = within(dialog).getByRole('button', {
      name: 'Chiudi menu',
    });

    trigger.focus();

    expect(closeButton).toHaveFocus();
  });

  it('si chiude con Escape e ripristina il focus sul trigger', async () => {
    renderDrawer();
    const { trigger } = await openDrawer();

    fireEvent.keyDown(document, { key: 'Escape' });

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    expect(trigger).toHaveFocus();
  });

  it('si chiude con click sull’overlay', async () => {
    renderDrawer();
    const { user, trigger, dialog } = await openDrawer();

    await user.click(
      within(dialog).getByRole('button', {
        name: 'Chiudi il menu di navigazione',
      }),
    );

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it('si chiude dopo la selezione di una route', async () => {
    renderDrawer();
    const { user, dialog, trigger } = await openDrawer();

    await user.click(
      within(dialog).getByRole('link', { name: 'Professionisti' }),
    );

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    expect(trigger).toHaveFocus();
  });

  it('blocca lo scroll e ripristina il valore precedente alla chiusura', async () => {
    document.body.style.overflow = 'clip';
    renderDrawer();

    const { user, dialog } = await openDrawer();
    expect(document.body.style.overflow).toBe('hidden');

    await user.click(
      within(dialog).getByRole('button', { name: 'Chiudi menu' }),
    );
    expect(document.body.style.overflow).toBe('clip');
  });

  it('ripristina il body scroll lock anche su unmount', async () => {
    document.body.style.overflow = 'scroll';
    const view = renderDrawer();

    await openDrawer();
    expect(document.body.style.overflow).toBe('hidden');

    view.unmount();
    expect(document.body.style.overflow).toBe('scroll');
    expect(desktopMedia.getListenerCount()).toBe(0);
  });

  it('permette cicli multipli di apertura e chiusura', async () => {
    renderDrawer();

    const firstOpen = await openDrawer();
    expect(document.body.style.overflow).toBe('hidden');
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(firstOpen.trigger).toHaveFocus();
    expect(document.body.style.overflow).toBe('');

    const secondOpen = await openDrawer();
    expect(secondOpen.trigger).toHaveAttribute('aria-expanded', 'true');
    expect(document.body.style.overflow).toBe('hidden');
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(secondOpen.trigger).toHaveAttribute('aria-expanded', 'false');
    expect(document.body.style.overflow).toBe('');
  });

  it('al passaggio desktop chiude il drawer e disattiva lo stato modal', async () => {
    document.body.style.overflow = 'clip';
    renderDrawer();

    const { trigger } = await openDrawer();
    expect(trigger).toHaveAttribute('aria-expanded', 'true');
    expect(document.body.style.overflow).toBe('hidden');

    act(() => {
      desktopMedia.setMatches(true);
    });

    const layer = getDrawerLayer(trigger);
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(layer).toHaveAttribute('aria-hidden', 'true');
    expect(layer).not.toHaveAttribute('aria-modal');
    expect(document.body.style.overflow).toBe('clip');
    expect(trigger).not.toHaveFocus();
  });

  it('non mantiene focus trap né listener modal dopo il passaggio desktop', async () => {
    renderDrawer();

    const { trigger } = await openDrawer();

    act(() => {
      desktopMedia.setMatches(true);
    });

    trigger.focus();
    expect(trigger).toHaveFocus();

    fireEvent.keyDown(document, { key: 'Tab' });
    expect(trigger).toHaveFocus();

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    expect(document.body.style.overflow).toBe('');
  });

  it('restando chiuso al ritorno mobile può essere riaperto', async () => {
    renderDrawer();

    const { trigger } = await openDrawer();
    expect(document.body.style.overflow).toBe('hidden');

    act(() => {
      desktopMedia.setMatches(true);
    });
    act(() => {
      desktopMedia.setMatches(false);
    });

    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(document.body.style.overflow).toBe('');

    const reopened = await openDrawer();
    expect(reopened.trigger).toHaveAttribute('aria-expanded', 'true');
    expect(reopened.dialog).toHaveAttribute('aria-modal', 'true');
    expect(document.body.style.overflow).toBe('hidden');
    expect(
      within(reopened.dialog).getByRole('button', { name: 'Chiudi menu' }),
    ).toHaveFocus();
  });

  it('gestisce cambi breakpoint ripetuti senza riaprire il drawer', async () => {
    renderDrawer();
    await openDrawer();

    act(() => {
      desktopMedia.setMatches(true);
    });
    act(() => {
      desktopMedia.setMatches(false);
    });
    act(() => {
      desktopMedia.setMatches(true);
    });

    expect(screen.getByRole('button', { name: 'Menu' })).toHaveAttribute(
      'aria-expanded',
      'false',
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(document.body.style.overflow).toBe('');
    expect(desktopMedia.getListenerCount()).toBe(1);
  });

  it('ignora un tentativo di apertura mentre il viewport è desktop', async () => {
    desktopMedia.restore();
    desktopMedia = createDesktopMediaMock(true);
    const user = userEvent.setup();
    renderDrawer();

    await user.click(screen.getByRole('button', { name: 'Menu' }));

    expect(screen.getByRole('button', { name: 'Menu' })).toHaveAttribute(
      'aria-expanded',
      'false',
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(document.body.style.overflow).toBe('');

    act(() => {
      desktopMedia.setMatches(false);
    });

    expect(screen.getByRole('button', { name: 'Menu' })).toHaveAttribute(
      'aria-expanded',
      'false',
    );
    expect(document.body.style.overflow).toBe('');
  });
});
