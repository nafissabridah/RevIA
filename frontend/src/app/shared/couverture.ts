import { StatutCritere } from '../core/models';

/** Badge d'un critère d'acceptation : icône, libellé (français, traduit par le pipe « t ») et classe CSS de styles.scss. */
export interface BadgeCritere {
  icone: string;
  libelle: string;
  classe: string;
}

const BADGES: Record<StatutCritere, BadgeCritere> = {
  couvert: { icone: '✅', libelle: 'Couvert', classe: 's-corrigee' },
  partiel: { icone: '🟡', libelle: 'Partiel', classe: 's-a_revoir' },
  non_couvert: { icone: '❌', libelle: 'Non couvert', classe: 's-a_corriger' },
};

/** Statut renvoyé par l'API → badge affiché ; un statut inconnu est traité comme « non couvert » (prudence). */
export function badgeCritere(statut: StatutCritere | string | null | undefined): BadgeCritere {
  return BADGES[statut as StatutCritere] ?? BADGES.non_couvert;
}
