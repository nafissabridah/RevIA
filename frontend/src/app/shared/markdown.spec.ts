import { blocsDeCode, markdownVersHtml } from './markdown';

describe('markdownVersHtml', () => {
  it('met en forme titres, listes, gras et code en ligne', () => {
    const html = markdownVersHtml('## Résumé\n- **Bug** ligne 3\n- appel `find()`\n\n1. un\n2. deux');
    expect(html).toContain('<h4>Résumé</h4>');
    expect(html).toContain('<ul><li><strong>Bug</strong> ligne 3</li><li>appel <code>find()</code></li></ul>');
    expect(html).toContain('<ol><li>un</li><li>deux</li></ol>');
  });

  it('rend les blocs de code sans interpréter leur contenu', () => {
    const html = markdownVersHtml('Voici :\n```java\nif (a < b) { x = "<b>"; }\n```');
    expect(html).toContain('<span class="md-lang">java</span>');
    expect(html).toContain('if (a &lt; b) { x = &quot;&lt;b&gt;&quot;; }');
  });

  it('échappe le HTML venant du modèle', () => {
    expect(markdownVersHtml('<img src=x onerror=alert(1)>')).toBe('<p>&lt;img src=x onerror=alert(1)&gt;</p>');
  });

  it('extrait les blocs de code pour la copie', () => {
    expect(blocsDeCode('a\n```python\nprint(1)\n```\nb\n```\nx\n```')).toEqual(['print(1)', 'x']);
  });
});
