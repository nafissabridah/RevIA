import { HttpErrorResponse } from '@angular/common/http';
import { couleurScore, initiales, libelleLangage } from './libelles';
import { messageErreur } from './http';

describe('messageErreur (aucune erreur technique brute à l\'écran)', () => {
  it('reprend le message clair renvoyé par le back-end', () => {
    const e = new HttpErrorResponse({ status: 504, error: { code: 'LLM_TIMEOUT', message: "L'analyse prend plus de temps que prévu. Réessayez dans un instant." } });
    expect(messageErreur(e)).toBe("L'analyse prend plus de temps que prévu. Réessayez dans un instant.");
  });

  it('serveur injoignable (statut 0)', () => {
    expect(messageErreur(new HttpErrorResponse({ status: 0 }))).toContain('Impossible de joindre le serveur');
  });

  it('message générique pour une erreur inconnue', () => {
    expect(messageErreur(new Error('NullPointerException at line 42'))).not.toContain('NullPointer');
  });
});

describe('libellés', () => {
  it('initiales du nom', () => {
    expect(initiales('Sarra A.')).toBe('SA');
    expect(initiales(null)).toBe('?');
  });

  it('couleur du score selon les seuils', () => {
    expect(couleurScore(38)).toBe('var(--critique)');
    expect(couleurScore(72)).toBe('var(--mineure)');
    expect(couleurScore(91)).toBe('var(--ok)');
  });

  it('libellé des langages', () => {
    expect(libelleLangage('typescript')).toBe('TypeScript');
    expect(libelleLangage('autre')).toBe('Autre');
  });
});
