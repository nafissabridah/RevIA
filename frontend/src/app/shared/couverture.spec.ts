import { langueReponses } from '../core/langue.service';
import { badgeCritere } from './couverture';

describe('badgeCritere (statut d\'un critère d\'acceptation)', () => {
  it('associe chaque statut à son icône et à sa couleur', () => {
    expect(badgeCritere('couvert')).toEqual({ icone: '✅', libelle: 'Couvert', classe: 's-corrigee' });
    expect(badgeCritere('partiel').icone).toBe('🟡');
    expect(badgeCritere('non_couvert').classe).toBe('s-a_corriger');
  });

  it('traite un statut inconnu comme non couvert', () => {
    expect(badgeCritere('???').libelle).toBe('Non couvert');
    expect(badgeCritere(null).icone).toBe('❌');
  });
});

describe('langueReponses (en-tête X-RevIA-Langue)', () => {
  it('suit la langue de l\'interface en mode automatique', () => {
    expect(langueReponses('auto', 'fr')).toBe('fr');
    expect(langueReponses('auto', 'en')).toBe('en');
  });

  it('envoie la langue choisie pour l\'IA sinon', () => {
    expect(langueReponses('ar', 'fr')).toBe('ar');
    expect(langueReponses('ja', 'en')).toBe('ja');
  });
});
