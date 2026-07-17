import { useState } from 'react';

import {
  CLIENT_ACCESS_PROFILE,
  NUTRITIONIST_ACCESS_PROFILE,
  PERSONAL_TRAINER_ACCESS_PROFILE,
  type UserAccessProfile,
} from '../../app/config/access';
import { AuthenticatedLayout } from '../../layouts/authenticated/AuthenticatedLayout';
import { DashboardPage } from '../shared/DashboardPage';
import styles from './RolePreviewPage.module.css';

type PreviewProfileId = 'client' | 'personal-trainer' | 'nutritionist';

interface PreviewOption {
  id: PreviewProfileId;
  label: string;
  profile: UserAccessProfile;
}

const previewOptions: PreviewOption[] = [
  { id: 'client', label: 'Cliente', profile: CLIENT_ACCESS_PROFILE },
  {
    id: 'personal-trainer',
    label: 'Personal Trainer',
    profile: PERSONAL_TRAINER_ACCESS_PROFILE,
  },
  {
    id: 'nutritionist',
    label: 'Nutrizionista',
    profile: NUTRITIONIST_ACCESS_PROFILE,
  },
];

export function RolePreviewPage() {
  const [selectedId, setSelectedId] = useState<PreviewProfileId>('client');
  const selectedProfile =
    previewOptions.find((option) => option.id === selectedId)?.profile ??
    CLIENT_ACCESS_PROFILE;

  return (
    <div className={styles.page}>
      <header className={styles.controls}>
        <h1>Anteprima tecnica dei ruoli</h1>
        <p>
          Selettore locale di sviluppo: non rappresenta una sessione
          autenticata.
        </p>
        <fieldset className={styles.options}>
          <legend className="visually-hidden">Profilo da visualizzare</legend>
          {previewOptions.map((option) => (
            <label className={styles.option} key={option.id}>
              <input
                type="radio"
                name="preview-profile"
                value={option.id}
                checked={selectedId === option.id}
                onChange={() => setSelectedId(option.id)}
              />
              {option.label}
            </label>
          ))}
        </fieldset>
      </header>
      <AuthenticatedLayout profile={selectedProfile}>
        <DashboardPage profile={selectedProfile} />
      </AuthenticatedLayout>
    </div>
  );
}
