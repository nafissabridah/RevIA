import { Categorie, Gravite, Statut } from './models';

export const LANGAGES: { valeur: string; libelle: string; icone: string }[] = [
  { valeur: 'auto', libelle: 'Détection auto', icone: '✨' },
  { valeur: 'java', libelle: 'Java', icone: '☕' },
  { valeur: 'javascript', libelle: 'JavaScript', icone: 'JS' },
  { valeur: 'typescript', libelle: 'TypeScript', icone: 'TS' },
  { valeur: 'python', libelle: 'Python', icone: '🐍' },
  { valeur: 'php', libelle: 'PHP', icone: '🐘' },
  { valeur: 'c', libelle: 'C', icone: 'C' },
  { valeur: 'cpp', libelle: 'C++', icone: 'C++' },
  { valeur: 'csharp', libelle: 'C#', icone: 'C#' },
  { valeur: 'go', libelle: 'Go', icone: 'Go' },
  { valeur: 'kotlin', libelle: 'Kotlin', icone: 'Kt' },
  { valeur: 'sql', libelle: 'SQL', icone: 'SQL' },
];

/** Langage RevIA → identifiant de langage Monaco. */
export const MONACO_LANG: Record<string, string> = {
  java: 'java', javascript: 'javascript', typescript: 'typescript', python: 'python', php: 'php',
  c: 'c', cpp: 'cpp', csharp: 'csharp', go: 'go', kotlin: 'kotlin', sql: 'sql', autre: 'plaintext', auto: 'plaintext',
};

export const EXTENSIONS: Record<string, string> = {
  java: 'java', js: 'javascript', mjs: 'javascript', jsx: 'javascript', ts: 'typescript', tsx: 'typescript',
  py: 'python', php: 'php', c: 'c', h: 'c', cpp: 'cpp', cc: 'cpp', hpp: 'cpp', cs: 'csharp', go: 'go', kt: 'kotlin', sql: 'sql',
};

export function libelleLangage(v: string): string {
  return LANGAGES.find((l) => l.valeur === v)?.libelle ?? (v === 'autre' ? 'Autre' : v);
}

export const GRAVITES: Record<Gravite, string> = {
  critique: 'Critique', majeure: 'Majeure', mineure: 'Mineure', info: 'Info',
};

export const GRAVITES_PLURIEL: Record<Gravite, string> = {
  critique: 'critiques', majeure: 'majeures', mineure: 'mineures', info: 'infos',
};

export const CATEGORIES: Record<Categorie, string> = {
  securite: 'Sécurité', bug: 'Bug', bonne_pratique: 'Bonnes pratiques', performance: 'Performance',
};

export const STATUTS: Record<Statut, string> = {
  a_corriger: 'À corriger', a_revoir: 'À revoir', corrigee: 'Corrigée',
};

export const MODES = [
  { valeur: 'complet', libelle: 'Sécurité + qualité' },
  { valeur: 'securite', libelle: 'Sécurité uniquement' },
  { valeur: 'qualite', libelle: 'Qualité uniquement' },
];

export function couleurScore(score: number): string {
  if (score >= 80) return 'var(--ok)';
  if (score >= 60) return 'var(--mineure)';
  if (score >= 45) return 'var(--majeure)';
  return 'var(--critique)';
}

export function initiales(nom: string | undefined | null): string {
  if (!nom) return '?';
  return nom.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('');
}
