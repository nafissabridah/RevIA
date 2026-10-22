import { badgeStatutProjet, corpsVerdict, couverture } from './tracabilite';

describe('traçabilité projet', () => {
  it('calcule la couverture comme le serveur (partiel = 0,5, non vérifié = 0)', () => {
    expect(couverture([])).toBe(0);
    expect(couverture([{ statut: 'couvert' }, { statut: 'non_couvert' }])).toBe(50);
    expect(couverture([{ statut: 'couvert' }, { statut: 'partiel' }, { statut: 'non_verifie' }])).toBe(50);
    expect(couverture([{ statut: 'partiel' }, { statut: 'partiel' }, { statut: 'couvert' }])).toBe(67);
  });

  it('affiche « Non vérifié » et retombe sur les badges de couverture', () => {
    expect(badgeStatutProjet('non_verifie').libelle).toBe('Non vérifié');
    expect(badgeStatutProjet('couvert').libelle).toBe('Couvert');
    expect(badgeStatutProjet('inconnu').libelle).toBe('Non couvert');
  });

  it('prépare le corps du PATCH du verdict QA', () => {
    expect(corpsVerdict('valide', '  ok en recette ')).toEqual({ verdictQa: 'valide', commentaireQa: 'ok en recette' });
    expect(corpsVerdict('', '   ')).toEqual({ verdictQa: null, commentaireQa: null });
    expect(corpsVerdict('peut-etre', 'x')).toEqual({ verdictQa: null, commentaireQa: 'x' });
  });
});
