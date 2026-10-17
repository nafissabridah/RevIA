import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatMenuModule } from '@angular/material/menu';
import { Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, takeUntil } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { messageErreur } from '../../core/http';
import { LANGAGES, couleurScore, libelleLangage } from '../../core/libelles';
import { FiltresHistorique, Page, RevueResume } from '../../core/models';
import { NotifService } from '../../core/notif.service';
import { StatutBadgeComponent } from '../../shared/badges';
import { IconeComponent } from '../../shared/icone.component';

const SCORES: Record<string, [number | null, number | null]> = {
  tous: [null, null], faible: [0, 49], moyen: [50, 79], bon: [80, 100],
};

/** Historique (maquette diapo 14) : recherche, filtres par langage, score et statut, export CSV. */
@Component({
  selector: 'app-historique',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, MatMenuModule, StatutBadgeComponent, IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="barre">
      <div class="fil">Espace de travail / <b>Historique</b></div>
      <button class="btn" type="button" (click)="exporter()" [disabled]="export()"><app-icone nom="export" />Exporter en CSV</button>
      <a class="btn primaire" routerLink="/revues/nouvelle"><app-icone nom="nouvelle" />Nouvelle revue</a>
    </header>

    <div class="page">
      <h1 class="titre-page">Historique des revues</h1>
      <p class="sous-titre">Toutes vos analyses sont conservées : vous pouvez rouvrir une revue, comparer les scores et suivre vos progrès.</p>

      <div class="carte">
        <div class="filtres">
          <div class="recherche">
            <app-icone nom="recherche" />
            <input class="input" type="search" placeholder="Rechercher un fichier, une méthode…" [ngModel]="q()"
                   (ngModelChange)="saisie$.next($event)" aria-label="Rechercher" />
          </div>
          <select class="input" [ngModel]="langage()" (ngModelChange)="langage.set($event); recharger()" aria-label="Langage">
            <option value="">Langage : tous</option>
            @for (l of langages; track l.valeur) { <option [value]="l.valeur">{{ l.libelle }}</option> }
          </select>
          <select class="input" [ngModel]="score()" (ngModelChange)="score.set($event); recharger()" aria-label="Score">
            <option value="tous">Score : tous</option>
            <option value="faible">Score &lt; 50</option>
            <option value="moyen">Score 50 – 79</option>
            <option value="bon">Score ≥ 80</option>
          </select>
          <select class="input" [ngModel]="statut()" (ngModelChange)="statut.set($event); recharger()" aria-label="Statut">
            <option value="">Statut : tous</option>
            <option value="a_corriger">À corriger</option>
            <option value="a_revoir">À revoir</option>
            <option value="corrigee">Corrigée</option>
          </select>
          <select class="input" [ngModel]="jours()" (ngModelChange)="jours.set(+$event); recharger()" aria-label="Période">
            <option [value]="7">7 derniers jours</option>
            <option [value]="30">30 derniers jours</option>
            <option [value]="90">90 derniers jours</option>
            <option [value]="0">Toute la période</option>
          </select>
        </div>

        <div class="defile">
          <table>
            <thead>
              <tr><th>Extrait analysé</th><th>Langage</th><th>Date</th><th>Score</th><th>Problèmes</th><th>Statut</th><th><span class="sr-only">Actions</span></th></tr>
            </thead>
            <tbody>
              @for (r of page()?.contenu ?? []; track r.id) {
                <tr (click)="ouvrir(r)" tabindex="0" (keydown.enter)="ouvrir(r)">
                  <td class="titre">{{ r.titre }}</td>
                  <td><span class="badge langage">{{ libelleLangage(r.langage) }}</span></td>
                  <td class="date">{{ r.creeLe | date: 'dd/MM/yyyy HH:mm' }}</td>
                  <td>
                    <div class="score"><span class="barre-score"><span [style.width.%]="r.score" [style.background]="couleur(r.score)"></span></span>
                      <b [style.color]="couleur(r.score)">{{ r.score }}</b></div>
                  </td>
                  <td>{{ r.nbProblemes }} @if (r.nbCritiques) { <span class="crit">({{ r.nbCritiques }} crit.)</span> }</td>
                  <td><app-statut [valeur]="r.statut" /></td>
                  <td class="plus" (click)="$event.stopPropagation()">
                    <button class="btn petit" type="button" [matMenuTriggerFor]="menu" aria-label="Actions">⋯</button>
                    <mat-menu #menu="matMenu">
                      <button mat-menu-item (click)="ouvrir(r)">Ouvrir la revue</button>
                      <button mat-menu-item (click)="supprimer(r)">Supprimer</button>
                    </mat-menu>
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="7">
                  <div class="vide">
                    @if (chargement()) { Chargement… } @else {
                      <div class="grand">🗂️</div>Aucune revue ne correspond à ces filtres.
                      <br /><a routerLink="/revues/nouvelle">Lancer une nouvelle revue</a>
                    }
                  </div>
                </td></tr>
              }
            </tbody>
          </table>
        </div>

        @if (page(); as p) {
          @if (p.total > 0) {
            <div class="pagination">
              <span>Affichage de {{ p.page * p.taille + 1 }} à {{ min(p.total, (p.page + 1) * p.taille) }} sur {{ p.total }} revues</span>
              <div class="pages">
                <button class="btn petit" type="button" [disabled]="p.page === 0" (click)="aller(p.page - 1)" aria-label="Page précédente">‹</button>
                @for (n of numeros(); track n) {
                  <button class="btn petit" type="button" [class.courante]="n === p.page" (click)="aller(n)">{{ n + 1 }}</button>
                }
                <button class="btn petit" type="button" [disabled]="p.page >= p.pages - 1" (click)="aller(p.page + 1)" aria-label="Page suivante">›</button>
              </div>
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [`
    .filtres { display: flex; gap: 8px; padding: 14px; flex-wrap: wrap; border-bottom: 1px solid var(--bord); }
    .filtres select { height: 34px; font-size: 13px; }
    .recherche { display: flex; align-items: center; gap: 8px; flex: 1; min-width: 220px; border: 1px solid var(--bord); border-radius: 9px; padding-left: 10px; color: var(--texte-3); }
    .recherche .input { border: none; flex: 1; height: 32px; padding-left: 0; box-shadow: none; }
    .defile { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; min-width: 760px; }
    th { text-align: left; font-size: 11px; letter-spacing: .08em; text-transform: uppercase; color: var(--texte-3); font-weight: 600; padding: 10px 14px; border-bottom: 1px solid var(--bord); }
    td { padding: 11px 14px; border-bottom: 1px solid var(--bord); font-size: 13px; }
    tbody tr { cursor: pointer; }
    tbody tr:hover td { background: var(--surface-2); }
    tbody tr:focus-visible { outline: 2px solid var(--primaire); outline-offset: -2px; }
    .titre { font-weight: 600; }
    .date { color: var(--texte-2); white-space: nowrap; }
    .score { display: flex; align-items: center; gap: 8px; }
    .barre-score { width: 46px; height: 5px; background: var(--bord); border-radius: 3px; overflow: hidden; }
    .barre-score span { display: block; height: 100%; border-radius: 3px; }
    .crit { color: var(--critique); font-size: 12px; }
    .plus { text-align: right; width: 50px; }
    .pagination { display: flex; justify-content: space-between; align-items: center; padding: 12px 14px; color: var(--texte-2); font-size: 12.5px; flex-wrap: wrap; gap: 8px; }
    .pages { display: flex; gap: 4px; }
    .courante { background: var(--primaire); border-color: var(--primaire); color: #fff; }
  `],
})
export class HistoriqueComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notif = inject(NotifService);
  private readonly fin$ = new Subject<void>();
  readonly saisie$ = new Subject<string>();

  readonly langages = LANGAGES.filter((l) => l.valeur !== 'auto');
  readonly libelleLangage = libelleLangage;
  readonly couleur = couleurScore;
  readonly min = Math.min;

  readonly q = signal('');
  readonly langage = signal('');
  readonly score = signal('tous');
  readonly statut = signal('');
  readonly jours = signal(30);
  readonly page = signal<Page<RevueResume> | null>(null);
  readonly chargement = signal(false);
  readonly export = signal(false);
  private numPage = 0;

  readonly numeros = computed(() => {
    const p = this.page();
    if (!p) return [];
    const debut = Math.max(0, Math.min(p.page - 2, p.pages - 5));
    return Array.from({ length: Math.min(5, p.pages) }, (_, i) => debut + i);
  });

  ngOnInit(): void {
    this.saisie$.pipe(debounceTime(300), takeUntil(this.fin$)).subscribe((v) => {
      this.q.set(v);
      this.recharger();
    });
    this.charger();
  }

  ngOnDestroy(): void {
    this.fin$.next();
    this.fin$.complete();
  }

  private filtres(): FiltresHistorique {
    const [scoreMin, scoreMax] = SCORES[this.score()] ?? [null, null];
    return { q: this.q(), langage: this.langage(), statut: this.statut(), scoreMin, scoreMax, jours: this.jours() || null };
  }

  recharger(): void {
    this.numPage = 0;
    this.charger();
  }

  aller(n: number): void {
    this.numPage = n;
    this.charger();
  }

  private charger(): void {
    this.chargement.set(true);
    this.api.historique(this.filtres(), this.numPage, 8).subscribe({
      next: (p) => {
        this.page.set(p);
        this.chargement.set(false);
      },
      error: (e) => {
        this.chargement.set(false);
        this.notif.erreur(messageErreur(e));
      },
    });
  }

  ouvrir(r: RevueResume): void {
    this.router.navigate(['/revues', r.id]);
  }

  supprimer(r: RevueResume): void {
    if (!confirm(`Supprimer définitivement la revue « ${r.titre} » ?`)) {
      return;
    }
    this.api.supprimer(r.id).subscribe({
      next: () => {
        this.notif.succes('Revue supprimée.');
        this.auth.rafraichir().subscribe();
        this.charger();
      },
      error: (e) => this.notif.erreur(messageErreur(e)),
    });
  }

  exporter(): void {
    this.export.set(true);
    this.api.exporterCsv(this.filtres()).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `revia-historique-${new Date().toISOString().slice(0, 10)}.csv`;
        a.click();
        URL.revokeObjectURL(url);
        this.export.set(false);
      },
      error: (e) => {
        this.export.set(false);
        this.notif.erreur(messageErreur(e));
      },
    });
  }
}
