import { celluleCsv, versCsv } from './csv';

describe('csv', () => {
  it('laisse les cellules simples telles quelles et aplatit les retours à la ligne', () => {
    expect(celluleCsv('TC-01')).toBe('TC-01');
    expect(celluleCsv(42)).toBe('42');
    expect(celluleCsv(null)).toBe('');
    expect(celluleCsv('a\nb')).toBe('a b');
  });

  it('entoure de guillemets et double les guillemets quand il le faut', () => {
    expect(celluleCsv('Montant ; devise')).toBe('"Montant ; devise"');
    expect(celluleCsv('dit "oui"')).toBe('"dit ""oui"""');
  });

  it('neutralise l’injection de formule (=, +, -, @)', () => {
    expect(celluleCsv('=HYPERLINK("x")')).toBe(`"'=HYPERLINK(""x"")"`);
    expect(celluleCsv('-1')).toBe(`"'-1"`);
    expect(celluleCsv('@SUM(A1)')).toBe(`"'@SUM(A1)"`);
  });

  it('produit un fichier avec BOM, séparateur « ; » et fins de ligne CRLF', () => {
    expect(versCsv([['ID', 'Titre'], ['TC-01', 'Chemin nominal']])).toBe('﻿ID;Titre\r\nTC-01;Chemin nominal\r\n');
  });
});
