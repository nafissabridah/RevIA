import { CritereProjet, StatutCritereProjet, VerdictQa } from '../core/models';
import { BadgeCritere, badgeCritere } from './couverture';

/** Badge d'un critère de projet : comme la page de couverture, plus « Non vérifié » (aucun diff vérifié encore). */
export function badgeStatutProjet(statut: StatutCritereProjet | string | null | undefined): BadgeCritere {
  return statut === 'non_verifie' ? { icone: '⏳', libelle: 'Non vérifié', classe: 'neutre' } : badgeCritere(statut);
}

/** Verdicts QA proposés dans le sélecteur (libellés français, traduits par le pipe « t »). */
export const VERDICTS_QA: { valeur: VerdictQa; libelle: string }[] = [
  { valeur: 'a_tester', libelle: 'À tester' },
  { valeur: 'valide', libelle: 'Validé' },
  { valeur: 'rejete', libelle: 'Rejeté' },
];

/** Couverture en % : (couverts + 0,5 × partiels) / nombre de critères — même formule que le serveur. */
export function couverture(criteres: Pick<CritereProjet, 'statut'>[]): number {
  if (!criteres.length) {
    return 0;
  }
  const points = criteres.reduce((s, c) => s + (c.statut === 'couvert' ? 1 : c.statut === 'partiel' ? 0.5 : 0), 0);
  return Math.round((points * 100) / criteres.length);
}

/** Valeur du sélecteur → corps du PATCH (chaîne vide = pas de verdict ; commentaire vide = null). */
export function corpsVerdict(verdict: string, commentaire: string): { verdictQa: VerdictQa | null; commentaireQa: string | null } {
  const v = VERDICTS_QA.some((x) => x.valeur === verdict) ? (verdict as VerdictQa) : null;
  return { verdictQa: v, commentaireQa: commentaire.trim() ? commentaire.trim() : null };
}
