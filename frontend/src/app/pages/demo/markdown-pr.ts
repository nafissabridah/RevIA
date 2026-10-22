/**
 * Lecture minimale du Markdown d'un commentaire de PR RevIA (titres, paragraphes, listes à puces, tableaux GFM, notes <sub>)
 * en une STRUCTURE de données, rendue ensuite par le gabarit Angular avec l'interpolation {{ }} : aucune chaîne
 * n'est jamais injectée comme HTML (pas d'innerHTML). Les seules « balises » reconnues sont <sub>…</sub> en ligne
 * entière ; tout autre HTML reste du texte. Les liens ne sont gardés que s'ils sont en http(s).
 */

export type Segment =
  | { type: 'texte'; texte: string }
  | { type: 'gras'; texte: string }
  | { type: 'code'; texte: string }
  | { type: 'lien'; texte: string; url: string };

export type Alignement = 'left' | 'center' | 'right' | null;

export type BlocMd =
  | { type: 'titre'; niveau: number; contenu: Segment[] }
  | { type: 'paragraphe'; contenu: Segment[] }
  | { type: 'note'; contenu: Segment[] }
  | { type: 'liste'; elements: Segment[][] }
  | { type: 'tableau'; entetes: Segment[][]; alignements: Alignement[]; lignes: Segment[][][] };

const EN_LIGNE = /`([^`]+)`|\*\*([^*]+)\*\*|\[([^\]]+)\]\(([^)\s]+)\)/g;
const SEPARATEUR = /^\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?$/;

/** Retire les échappements Markdown (\| \* \` …) produits par md_escape côté CI. */
function desechapper(s: string): string {
  return s.replace(/\\([\\`*_[\]<>|])/g, '$1');
}

/** Échappe le HTML d'un texte (utile si une structure doit un jour être sérialisée hors gabarit Angular). */
export function echapperHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

export function urlSure(url: string): boolean {
  return /^https?:\/\/[^\s"'<>]+$/i.test(url);
}

/** Texte en ligne → segments (code, gras, liens http(s)) ; le reste est du texte brut. */
export function segments(s: string): Segment[] {
  const res: Segment[] = [];
  const pousserTexte = (t: string) => {
    if (!t) {
      return;
    }
    const der = res[res.length - 1];
    if (der?.type === 'texte') {
      der.texte += t;
    } else {
      res.push({ type: 'texte', texte: t });
    }
  };
  let pos = 0;
  for (const m of s.matchAll(EN_LIGNE)) {
    const debut = m.index ?? 0;
    // Délimiteur précédé d'un antislash : ce n'est pas de la mise en forme
    if (debut > 0 && s[debut - 1] === '\\') {
      continue;
    }
    pousserTexte(desechapper(s.slice(pos, debut)));
    if (m[1] !== undefined) {
      res.push({ type: 'code', texte: m[1] });
    } else if (m[2] !== undefined) {
      res.push({ type: 'gras', texte: desechapper(m[2]) });
    } else if (urlSure(m[4])) {
      res.push({ type: 'lien', texte: desechapper(m[3]), url: m[4] });
    } else {
      pousserTexte(desechapper(m[3]));
    }
    pos = debut + m[0].length;
  }
  pousserTexte(desechapper(s.slice(pos)));
  return res;
}

/** Découpe une ligne de tableau sur les « | » non échappés, sans les bords. */
export function cellules(ligne: string): string[] {
  let l = ligne.trim();
  if (l.startsWith('|')) {
    l = l.slice(1);
  }
  if (l.endsWith('|') && !l.endsWith('\\|')) {
    l = l.slice(0, -1);
  }
  const res: string[] = [];
  let courant = '';
  for (let i = 0; i < l.length; i++) {
    if (l[i] === '\\' && l[i + 1] === '|') {
      courant += '\\|';
      i++;
    } else if (l[i] === '|') {
      res.push(courant.trim());
      courant = '';
    } else {
      courant += l[i];
    }
  }
  res.push(courant.trim());
  return res;
}

export function alignement(sep: string): Alignement {
  const s = sep.trim();
  const g = s.startsWith(':');
  const d = s.endsWith(':');
  return g && d ? 'center' : d ? 'right' : g ? 'left' : null;
}

export function lireMarkdownPr(md: string): BlocMd[] {
  const lignes = (md ?? '').replace(/\r\n?/g, '\n').split('\n');
  const blocs: BlocMd[] = [];
  let paragraphe: string[] = [];
  const fermer = () => {
    if (paragraphe.length) {
      blocs.push({ type: 'paragraphe', contenu: segments(paragraphe.join(' ')) });
      paragraphe = [];
    }
  };
  for (let i = 0; i < lignes.length; i++) {
    const ligne = lignes[i];
    const titre = /^(#{1,6})\s+(.*)$/.exec(ligne);
    const note = /^\s*<sub>(.*)<\/sub>\s*$/.exec(ligne);
    const puce = /^\s*[-*]\s+(.*)$/.exec(ligne);
    if (titre) {
      fermer();
      blocs.push({ type: 'titre', niveau: titre[1].length, contenu: segments(titre[2].trim()) });
    } else if (note) {
      fermer();
      blocs.push({ type: 'note', contenu: segments(note[1].trim()) });
    } else if (puce) {
      fermer();
      const der = blocs[blocs.length - 1];
      if (der?.type === 'liste' && lignes[i - 1]?.trim() !== '') {
        der.elements.push(segments(puce[1].trim()));
      } else {
        blocs.push({ type: 'liste', elements: [segments(puce[1].trim())] });
      }
    } else if (ligne.trim().startsWith('|') && i + 1 < lignes.length && SEPARATEUR.test(lignes[i + 1].trim())) {
      fermer();
      const entetes = cellules(ligne);
      const alignements = cellules(lignes[i + 1]).map(alignement);
      const corps: Segment[][][] = [];
      i += 2;
      while (i < lignes.length && lignes[i].trim().startsWith('|')) {
        const c = cellules(lignes[i]);
        // Autant de cellules que d'en-têtes : on complète ou on tronque, comme GitHub
        corps.push(entetes.map((_e, k) => segments(c[k] ?? '')));
        i++;
      }
      i--;
      blocs.push({
        type: 'tableau',
        entetes: entetes.map(segments),
        alignements: entetes.map((_e, k) => alignements[k] ?? null),
        lignes: corps,
      });
    } else if (ligne.trim() === '') {
      fermer();
    } else {
      paragraphe.push(ligne.trim());
    }
  }
  fermer();
  return blocs;
}
