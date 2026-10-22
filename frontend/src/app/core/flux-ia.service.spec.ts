import { lireEvenement } from './flux-ia.service';

describe('lireEvenement (flux SSE de l\'assistant)', () => {
  it('lit un morceau de texte, retours à la ligne compris', () => {
    expect(lireEvenement('event:morceau\ndata:{"t":"## Titre\\nligne"}')).toEqual({ type: 'morceau', texte: '## Titre\nligne' });
  });

  it('lit la fin et les erreurs', () => {
    const fin = lireEvenement('event:fin\ndata:{"dureeMs":1200,"modele":"gemini-flash-lite-latest"}');
    expect(fin?.type).toBe('fin');
    expect(lireEvenement('event:erreur\ndata:{"code":"LLM_QUOTA","message":"Service très sollicité."}'))
      .toEqual({ type: 'erreur', message: 'Service très sollicité.' });
  });

  it('ignore les blocs vides ou illisibles', () => {
    expect(lireEvenement('')).toBeNull();
    expect(lireEvenement('event:morceau\ndata:pas du json')).toBeNull();
  });
});
