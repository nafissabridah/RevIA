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
  /** Offre commerciale (mise à jour par les webhooks Paddle). */
  plan?: Plan;
  /** Vrai si l'administrateur a fixé un quota qui remplace celui de l'offre. */
  quotaPersonnalise?: boolean;
  abonnementStatut?: string | null;
  abonnementFinPeriode?: string | null;
}

export type Plan = 'GRATUIT' | 'PRO' | 'EQUIPE';

/** Configuration publique du paiement (le jeton client Paddle est public par conception). */
export interface AbonnementConfig {
  actif: boolean;
  environnement: 'sandbox' | 'production';
  clientToken: string;
  prixPro: string;
  prixEquipe: string;
  portail: boolean;
  quotas: Record<Plan, number>;
}

export interface Abonnement {
  plan: Plan;
  quotaOffre: number;
  quotaJournalier: number | null;
  quotaPersonnalise: boolean;
  statut: string | null;
  finPeriode: string | null;
  abonnementEnCours: boolean;
  portailDisponible: boolean;
}

export interface SuppressionCompte {
  supprime: boolean;
  avertissement: string | null;
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

export interface JetonApi {
  id: number;
  nom: string;
  apercu: string;
  creeLe: string;
  dernierUsage: string | null;
}

export interface SystemeInfo {
  essaiActif?: boolean;
  essaiMaxLignes?: number;
  application: string;
  version: string;
  fournisseur: string;
  modele: string;
  /** Chaîne de relais des fournisseurs (« gemini/gemini-flash-lite-latest », …). */
  chaine?: string[];
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

export type OutilIA = 'tests' | 'documentation' | 'explication' | 'commit' | 'refactoring' | 'conversion'
  | 'cas-de-test' | 'e2e' | 'tests-api' | 'rapport-bug' | 'analyse-logs' | 'donnees-test' | 'revue-tests';

export interface MessageChat {
  role: 'utilisateur' | 'assistant';
  contenu: string;
}

export interface AssistantReponse {
  contenu: string;
  outil: OutilIA | 'chat';
  langage: string | null;
  nbMasquages: number;
  dureeMs: number;
  fournisseur: string;
  modele: string;
}

/** Couverture des critères d'acceptation : la PR remplit-elle la user story ? */
export type StatutCritere = 'couvert' | 'partiel' | 'non_couvert';

export interface CritereVerifie {
  id: string;
  texte: string;
  statut: StatutCritere;
  explication: string;
  preuves: string[];
  testManquant: string | null;
}

export interface CouvertureCriteres {
  criteres: CritereVerifie[];
  couverture: number;
  nbCouverts: number;
  nbPartiels: number;
  nbNonCouverts: number;
  resume: string;
  nbMasquages: number;
  dureeMs: number;
  fournisseur: string;
  modele: string;
}

export interface ErreurApi {
  code: string;
  message: string;
  status: number;
  champs?: Record<string, string>;
}
