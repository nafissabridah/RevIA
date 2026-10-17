/** Types échangés avec l'API Spring Boot (miroir des DTO Java). */

export type Gravite = 'critique' | 'majeure' | 'mineure' | 'info';
export type Categorie = 'securite' | 'bug' | 'bonne_pratique' | 'performance';
export type Statut = 'a_corriger' | 'a_revoir' | 'corrigee';
export type Mode = 'complet' | 'securite' | 'qualite';

export interface Utilisateur {
  id: number;
  nom: string;
  email: string;
  role: 'DEVELOPPEUR' | 'ADMIN';
  quotaJournalier: number | null;
  revuesDuJour: number | null;
  bloque: boolean;
  creeLe: string;
  nbRevues: number | null;
}

export interface AuthResponse {
  token: string;
  expireDansSecondes: number;
  utilisateur: Utilisateur;
}

export interface Correction {
  codeAvant: string;
  codeApres: string;
  justification: string;
  copiee: boolean;
}

export interface Probleme {
  id: number;
  ligne: number;
  categorie: Categorie;
  gravite: Gravite;
  titre: string;
  explication: string;
  owasp: string | null;
  fauxPositif: boolean;
  corrige: boolean;
  utile: boolean | null;
  correction: Correction | null;
  occurrencesPrecedentes: number;
}

export interface RevueDetail {
  id: number;
  titre: string;
  langage: string;
  code: string;
  score: number;
  resume: string;
  mode: Mode;
  creeLe: string;
  dureeMs: number;
  fournisseur: string;
  modele: string;
  promptVersion: string;
  nbLignes: number;
  nbMasquages: number;
  problemesRejetes: number;
  statut: Statut;
  parGravite: Record<Gravite, number>;
  parCategorie: Record<Categorie, number>;
  problemes: Probleme[];
}

export interface RevueResume {
  id: number;
  titre: string;
  langage: string;
  creeLe: string;
  score: number;
  nbProblemes: number;
  nbCritiques: number;
  statut: Statut;
}

export interface Page<T> {
  contenu: T[];
  page: number;
  taille: number;
  total: number;
  pages: number;
}

export interface FiltresHistorique {
  q?: string;
  langage?: string;
  scoreMin?: number | null;
  scoreMax?: number | null;
  statut?: string;
  jours?: number | null;
  tri?: string;
}

export interface TableauDeBord {
  debut: string;
  fin: string;
  scoreMoyen: number;
  nbRevues: number;
  nbRevuesSemaine: number;
  nbProblemes: number;
  nbCritiques: number;
  nbCorriges: number;
  progression: number;
  erreursFrequentes: string[];
  erreursDetaillees: { titre: string; categorie: Categorie; occurrences: number; corrigees: number }[];
  parCategorie: Record<Categorie, number>;
  evolution: { jour: string; scoreMoyenJour: number; moyenneGlissante: number; nbRevues: number }[];
}

export interface SystemeInfo {
  application: string;
  version: string;
  fournisseur: string;
  modele: string;
  disponible: boolean;
  temperature: number;
  timeoutSec: number;
  quotaParDefaut: number;
  maxLignes: number;
}

export interface PromptSysteme {
  id: number;
  version: string;
  contenu: string;
  actif: boolean;
  creeLe: string;
}

export interface ErreurApi {
  code: string;
  message: string;
  status: number;
  champs?: Record<string, string>;
}
