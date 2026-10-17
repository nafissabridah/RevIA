import { desindenter, diffLignes } from './diff';

describe('diffLignes (diff avant / après)', () => {
  it('marque les lignes retirées et ajoutées comme une pull request', () => {
    const d = diffLignes('a\nb\nc', 'a\nB\nc');
    expect(d.map((l) => l.type)).toEqual(['egal', 'retire', 'ajoute', 'egal']);
    expect(d.find((l) => l.type === 'retire')!.texte).toBe('b');
    expect(d.find((l) => l.type === 'ajoute')!.texte).toBe('B');
  });

  it('gère un ajout pur en fin de bloc', () => {
    const d = diffLignes('x', 'x\ny\nz');
    expect(d.filter((l) => l.type === 'ajoute').length).toBe(2);
    expect(d[0]).toEqual({ type: 'egal', avant: 1, apres: 1, texte: 'x' });
  });

  it("ignore les différences d'indentation pour l'alignement", () => {
    const d = diffLignes('    foo();', 'foo();');
    expect(d.length).toBe(1);
    expect(d[0].type).toBe('egal');
  });

  it("retire l'indentation commune", () => {
    expect(desindenter('    a\n      b')).toBe('a\n  b');
  });
});
