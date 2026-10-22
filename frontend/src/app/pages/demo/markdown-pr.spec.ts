import { COMMENTAIRE_PR, RUN_DEMO, SOURCE_PANIER, TESTS_PANIER, nombreDeTests, scoreMutation, scoreRevue } from './demo-donnees';
import { BlocMd, alignement, cellules, echapperHtml, lireMarkdownPr, segments } from './markdown-pr';

describe('markdown-pr', () => {
  it('lit titres, paragraphes, tableaux et notes <sub> du vrai commentaire de PR', () => {
    const blocs = lireMarkdownPr(COMMENTAIRE_PR);
    expect(blocs.map((b) => b.type)).toEqual(['titre', 'paragraphe', 'tableau', 'titre', 'paragraphe', 'tableau', 'titre', 'paragraphe', 'liste', 'note', 'note']);
    const t = blocs[2] as Extract<BlocMd, { type: 'tableau' }>;
    expect(t.entetes.map((e) => e[0].texte)).toEqual(['File', 'Score', 'Critical', 'Gate', 'Report']);
    expect(t.lignes.length).toBe(1);
    expect(t.lignes[0][0]).toEqual([{ type: 'code', texte: 'panier.py' }]);
    expect(t.lignes[0][4]).toEqual([{ type: 'lien', texte: 'open', url: 'http://127.0.0.1:8096/revues/129' }]);
    expect(blocs[1]).toEqual({ type: 'paragraphe', contenu: [
      { type: 'texte', texte: '✅ ' }, { type: 'gras', texte: 'Quality gate passed' },
      { type: 'texte', texte: ' — score >= 70 and no critical issue.' }] });
  });

  it('ne garde aucune balise : le HTML reste du texte, les liens non http(s) deviennent du texte', () => {
    const blocs = lireMarkdownPr('<script>alert(1)</script> [x](javascript:void) <b>gras</b>');
    expect(blocs).toEqual([{ type: 'paragraphe', contenu: [{ type: 'texte', texte: '<script>alert(1)</script> x <b>gras</b>' }] }]);
    expect(echapperHtml('<a href="x">\'&')).toBe('&lt;a href=&quot;x&quot;&gt;&#39;&amp;');
  });

  it('lit la liste des contradictions doc / code, code en ligne compris', () => {
    const liste = lireMarkdownPr(COMMENTAIRE_PR).find((b) => b.type === 'liste') as Extract<BlocMd, { type: 'liste' }>;
    expect(liste.elements.length).toBe(1);
    expect(liste.elements[0][0]).toEqual({ type: 'code', texte: 'panier.py' });
    expect(liste.elements[0]).toContain({ type: 'code', texte: 'total > 50' });
    expect(liste.elements[0][liste.elements[0].length - 1]).toEqual({ type: 'code', texte: 'AssertionError: 50.0 != 45.0' });
    expect(lireMarkdownPr('- a\n- b\n\n- c').map((b) => b.type === 'liste' ? b.elements.length : 0)).toEqual([2, 1]);
  });

  it('respecte l’alignement des colonnes et les « | » échappés', () => {
    expect(['---', ':---', '---:', ':---:'].map(alignement)).toEqual([null, 'left', 'right', 'center']);
    expect(cellules('| a \\| b | `c` |')).toEqual(['a \\| b', '`c`']);
    const [t] = lireMarkdownPr('| G | C | D |\n|:--|:-:|--:|\n| 1 | 2 |') as Extract<BlocMd, { type: 'tableau' }>[];
    expect(t.alignements).toEqual(['left', 'center', 'right']);
    expect(t.lignes[0].length).toBe(3);
    expect(segments('a \\*b\\* \\| c')).toEqual([{ type: 'texte', texte: 'a *b* | c' }]);
  });

  it('les chiffres de la démo sont lus dans les vrais fichiers du run, pas recopiés', () => {
    expect(nombreDeTests(TESTS_PANIER)).toBe(9);
    expect(scoreMutation(COMMENTAIRE_PR)).toBe(100);
    expect(scoreRevue(COMMENTAIRE_PR, RUN_DEMO.fichierSource)).toBe(100);
    expect(SOURCE_PANIER).toContain('if code == "LIVRAISON" and total > 50:');
    expect(COMMENTAIRE_PR).not.toMatch(/\?\?|�/);
  });
});
