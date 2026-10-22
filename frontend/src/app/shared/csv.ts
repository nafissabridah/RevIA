/**
 * Export CSV côté navigateur, au même format que l'export de l'historique (serveur) : séparateur « ; »,
 * BOM UTF-8 et fins de ligne CRLF (Excel l'ouvre directement en français comme en anglais).
 */

/** Une cellule : retours à la ligne aplatis, guillemets doublés, formules neutralisées (=, +, -, @ en tête). */
export function celluleCsv(valeur: unknown): string {
  let s = valeur == null ? '' : String(valeur).replace(/\r?\n|\r/g, ' ');
  if (/^[=+\-@\t]/.test(s)) {
    s = "'" + s; // injection de formule dans Excel / LibreOffice
  }
  return /[;"']/.test(s) || s !== s.trim() ? `"${s.replace(/"/g, '""')}"` : s;
}

/** Tableau de lignes → texte CSV (avec BOM). */
export function versCsv(lignes: unknown[][]): string {
  return '﻿' + lignes.map((l) => l.map(celluleCsv).join(';')).join('\r\n') + '\r\n';
}

/** Propose le fichier au téléchargement. */
export function telechargerCsv(nomFichier: string, contenu: string): void {
  const url = URL.createObjectURL(new Blob([contenu], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = nomFichier;
  a.click();
  URL.revokeObjectURL(url);
}
