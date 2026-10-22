import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AssistantReponse, CouvertureCriteres, FiltresHistorique, JetonApi, MessageChat, OutilIA, Page, Probleme, PromptSysteme, RevueDetail, RevueResume, SystemeInfo,
  TableauDeBord, Utilisateur,
} from './models';

/** Accès à l'API REST. Angular ne parle qu'à notre back-end Spring : la clé d'API ne quitte jamais le serveur. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  soumettre(req: { titre?: string; langage: string; code: string; mode: string }): Observable<RevueDetail> {
    return this.http.post<RevueDetail>('/api/revues', req);
  }

  revue(id: number): Observable<RevueDetail> {
    return this.http.get<RevueDetail>(`/api/revues/${id}`);
  }

  supprimer(id: number): Observable<void> {
    return this.http.delete<void>(`/api/revues/${id}`);
  }

  historique(f: FiltresHistorique, page: number, taille: number): Observable<Page<RevueResume>> {
    return this.http.get<Page<RevueResume>>('/api/revues', { params: this.params(f).set('page', page).set('taille', taille) });
  }

  exporterCsv(f: FiltresHistorique): Observable<Blob> {
    return this.http.get('/api/revues/export.csv', { params: this.params(f), responseType: 'blob' });
  }

  fauxPositif(id: number, valeur: boolean): Observable<Probleme> {
    return this.http.post<Probleme>(`/api/problemes/${id}/faux-positif`, { valeur });
  }

  corrige(id: number, valeur: boolean): Observable<Probleme> {
    return this.http.post<Probleme>(`/api/problemes/${id}/corrige`, { valeur });
  }

  utile(id: number, valeur: boolean): Observable<Probleme> {
    return this.http.post<Probleme>(`/api/problemes/${id}/utile`, { valeur });
  }

  copier(id: number): Observable<{ code: string }> {
    return this.http.post<{ code: string }>(`/api/problemes/${id}/correction/copie`, {});
  }

  tableauDeBord(jours: number): Observable<TableauDeBord> {
    return this.http.get<TableauDeBord>('/api/tableau-de-bord', { params: { jours } });
  }

  modifierProfil(nom: string): Observable<Utilisateur> {
    return this.http.put<Utilisateur>('/api/me', { nom });
  }

  changerMotDePasse(ancien: string, nouveau: string): Observable<void> {
    return this.http.put<void>('/api/me/mot-de-passe', { ancien, nouveau });
  }

  // ---------- assistant IA et outils IA
  discuter(messages: MessageChat[], code?: string, langage?: string): Observable<AssistantReponse> {
    return this.http.post<AssistantReponse>('/api/assistant/chat', { messages, code: code || null, langage: langage || null });
  }

  outil(outil: OutilIA, req: { code: string; langage: string; cible?: string }): Observable<AssistantReponse> {
    return this.http.post<AssistantReponse>(`/api/assistant/outils/${outil}`, req);
  }

  /** Vérifie, critère par critère, que le diff de la PR implémente et teste la user story. */
  criteres(req: { criteres: string; diff: string }): Observable<CouvertureCriteres> {
    return this.http.post<CouvertureCriteres>('/api/assistant/criteres', req);
  }

  // ---------- intégrations (CI)
  jetons(): Observable<JetonApi[]> {
    return this.http.get<JetonApi[]>('/api/jetons');
  }

  creerJeton(nom: string): Observable<{ jeton: JetonApi; valeur: string }> {
    return this.http.post<{ jeton: JetonApi; valeur: string }>('/api/jetons', { nom });
  }

  revoquerJeton(id: number): Observable<void> {
    return this.http.delete<void>(`/api/jetons/${id}`);
  }

  sarif(id: number): Observable<Blob> {
    return this.http.get(`/api/revues/${id}/sarif`, { responseType: 'blob' });
  }

  systeme(): Observable<SystemeInfo> {
    return this.http.get<SystemeInfo>('/api/systeme/info');
  }

  // ---------- administration
  utilisateurs(): Observable<Utilisateur[]> {
    return this.http.get<Utilisateur[]>('/api/admin/utilisateurs');
  }

  definirQuota(id: number, quota: number): Observable<Utilisateur> {
    return this.http.put<Utilisateur>(`/api/admin/utilisateurs/${id}/quota`, { quota });
  }

  bloquer(id: number, valeur: boolean): Observable<Utilisateur> {
    return this.http.put<Utilisateur>(`/api/admin/utilisateurs/${id}/blocage`, { valeur });
  }

  prompts(): Observable<PromptSysteme[]> {
    return this.http.get<PromptSysteme[]>('/api/admin/prompts');
  }

  publierPrompt(version: string, contenu: string): Observable<PromptSysteme> {
    return this.http.post<PromptSysteme>('/api/admin/prompts', { version, contenu });
  }

  activerPrompt(id: number): Observable<PromptSysteme> {
    return this.http.put<PromptSysteme>(`/api/admin/prompts/${id}/activer`, {});
  }

  private params(f: FiltresHistorique): HttpParams {
    let p = new HttpParams();
    for (const [k, v] of Object.entries(f)) {
      if (v !== undefined && v !== null && v !== '') {
        p = p.set(k, String(v));
      }
    }
    return p;
  }
}
