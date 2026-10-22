/**
 * Rendu Markdown minimal et sûr pour les réponses de l'IA (titres, listes, gras, code, citations).
 * Tout le texte est échappé AVANT la mise en forme : aucune balise venant du modèle n'est interprétée.
 */
const BLOC = /```([\w+#.-]*)[^\n]*\n([\s\S]*?)(?:```|$)/g;

function echapper(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function enLigne(s: string): string {
  return echapper(s)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[\s(])\*([^*\s][^*]*)\*/g, '$1<em>$2</em>');
}

export function markdownVersHtml(md: string): string {
  const blocs: string[] = [];
  const texte = (md ?? '').replace(BLOC, (_t, lang: string, code: string) => {
    const etiquette = lang ? `<span class="md-lang">${echapper(lang)}</span>` : '';
    blocs.push(`<pre class="md-code">${etiquette}<code>${echapper(code.replace(/\n$/, ''))}</code></pre>`);
    return `\n\u0000${blocs.length - 1}\u0000\n`;
  });

  const html: string[] = [];
  let liste: 'ul' | 'ol' | null = null;
  let paragraphe: string[] = [];
  const fermerParagraphe = () => {
    if (paragraphe.length) {
      html.push(`<p>${paragraphe.join('<br>')}</p>`);
      paragraphe = [];
    }
  };
  const fermerListe = () => {
    if (liste) {
      html.push(`</${liste}>`);
      liste = null;
    }
  };

  for (const ligne of texte.split('\n')) {
    const bloc = /^\u0000(\d+)\u0000$/.exec(ligne.trim());
    const titre = /^(#{1,4})\s+(.*)$/.exec(ligne);
    const puce = /^\s*[-*•]\s+(.*)$/.exec(ligne);
    const numero = /^\s*\d+[.)]\s+(.*)$/.exec(ligne);
    if (bloc) {
      fermerParagraphe(); fermerListe();
      html.push(blocs[Number(bloc[1])]);
    } else if (titre) {
      fermerParagraphe(); fermerListe();
      const niveau = Math.min(5, titre[1].length + 2);
      html.push(`<h${niveau}>${enLigne(titre[2])}</h${niveau}>`);
    } else if (puce || numero) {
      fermerParagraphe();
      const type = puce ? 'ul' : 'ol';
      if (liste !== type) {
        fermerListe();
        html.push(`<${type}>`);
        liste = type;
      }
      html.push(`<li>${enLigne((puce ?? numero)![1])}</li>`);
    } else if (/^\s*>\s?/.test(ligne)) {
      fermerParagraphe(); fermerListe();
      html.push(`<blockquote>${enLigne(ligne.replace(/^\s*>\s?/, ''))}</blockquote>`);
    } else if (ligne.trim() === '') {
      fermerParagraphe(); fermerListe();
    } else {
      fermerListe();
      paragraphe.push(enLigne(ligne));
    }
  }
  fermerParagraphe(); fermerListe();
  return html.join('');
}

/** Contenu des blocs de code d'une réponse (pour le bouton « Copier le code »). */
export function blocsDeCode(md: string): string[] {
  return [...(md ?? '').matchAll(BLOC)].map((m) => m[2].replace(/\n$/, ''));
}
