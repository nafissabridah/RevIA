/** Diff ligne à ligne (plus longue sous-séquence commune) pour l'affichage avant / après. */
export interface LigneDiff {
  type: 'egal' | 'retire' | 'ajoute';
  avant: number | null;
  apres: number | null;
  texte: string;
}

export function diffLignes(avant: string, apres: string): LigneDiff[] {
  const a = avant.replace(/\r\n/g, '\n').split('\n');
  const b = apres.replace(/\r\n/g, '\n').split('\n');
  const n = a.length;
  const m = b.length;
  const lcs: number[][] = Array.from({ length: n + 1 }, () => new Array<number>(m + 1).fill(0));
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      lcs[i][j] = a[i].trim() === b[j].trim() ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
    }
  }
  const res: LigneDiff[] = [];
  let i = 0;
  let j = 0;
  while (i < n && j < m) {
    if (a[i].trim() === b[j].trim()) {
      res.push({ type: 'egal', avant: i + 1, apres: j + 1, texte: b[j] });
      i++;
      j++;
    } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
      res.push({ type: 'retire', avant: i + 1, apres: null, texte: a[i] });
      i++;
    } else {
      res.push({ type: 'ajoute', avant: null, apres: j + 1, texte: b[j] });
      j++;
    }
  }
  while (i < n) {
    res.push({ type: 'retire', avant: i + 1, apres: null, texte: a[i++] });
  }
  while (j < m) {
    res.push({ type: 'ajoute', avant: null, apres: j + 1, texte: b[j++] });
  }
  return res;
}

/** Retire l'indentation commune pour un affichage compact. */
export function desindenter(code: string): string {
  const lignes = code.replace(/\r\n/g, '\n').split('\n');
  const ind = Math.min(...lignes.filter((l) => l.trim()).map((l) => l.match(/^\s*/)![0].length), 1000);
  return lignes.map((l) => l.slice(Number.isFinite(ind) ? ind : 0)).join('\n');
}
