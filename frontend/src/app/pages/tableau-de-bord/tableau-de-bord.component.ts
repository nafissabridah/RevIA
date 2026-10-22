import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ChartConfiguration } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { messageErreur } from '../../core/http';
import { CATEGORIES } from '../../core/libelles';
import { Categorie, TableauDeBord } from '../../core/models';
import { TPipe, tr } from '../../core/i18n';

const COULEURS_CAT: Record<Categorie, string> = {
  bug: '#5b5bf6', bonne_pratique: '#9aa0bd', securite: '#e5484d', performance: '#f59e0b',
};

/** Tableau de bord (maquette diapo 14) : score moyen, problèmes récurrents, progression sur 30 jours. */
@Component({
  selector: 'app-tableau-de-bord',
  standalone: true,
  imports: [TPipe, FormsModule, RouterLink, DecimalPipe, DatePipe, BaseChartDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="barre">
      <div class="fil">{{ 'Espace de travail /' | t }} <b>{{ 'Tableau de bord' | t }}</b></div>
      <select class="input periode" [ngModel]="jours()" (ngModelChange)="jours.set(+$event); charger()" [attr.aria-label]="'Période' | t">
        <option [value]="7">{{ '7 derniers jours' | t }}</option>
        <option [value]="30">{{ '30 derniers jours' | t }}</option>
        <option [value]="90">{{ '90 derniers jours' | t }}</option>
      </select>
    </header>

    <div class="page">
      <h1 class="titre-page">{{ 'Bonjour' | t }} {{ prenom() }} 👋</h1>
      <p class="sous-titre">{{ 'Voici l\\'évolution de la qualité de votre code sur' | t }} {{ jours() === 30 ? ('le dernier mois' | t) : ('les ' | t) + jours() + (' derniers jours' | t) }}.</p>

      @if (erreur()) { <div class="carte vide">{{ erreur() | t }}</div> }

      @if (tdb(); as t) {
        <div class="kpis">
          <div class="carte kpi">
            <div class="k-titre">{{ 'Revues effectuées' | t }}</div>
            <div class="k-valeur">{{ t.nbRevues }}</div>
            <div class="k-sous ok">+{{ t.nbRevuesSemaine }} {{ 'cette semaine' | t }}</div>
          </div>
          <div class="carte kpi">
            <div class="k-titre">{{ 'Problèmes détectés' | t }}</div>
            <div class="k-valeur">{{ t.nbProblemes }}</div>
            <div class="k-sous">{{ 'dont' | t }} {{ t.nbCritiques }} {{ 'critiques' | t }}</div>
          </div>
          <div class="carte kpi">
            <div class="k-titre">{{ 'Score moyen' | t }}</div>
            <div class="k-valeur">{{ t.scoreMoyen | number: '1.0-0' }}<small>/100</small></div>
            <div class="k-sous" [class.ok]="t.progression >= 0" [class.ko]="t.progression < 0">
              {{ t.progression >= 0 ? '▲ +' : '▼ ' }}{{ t.progression | number: '1.0-1' }} {{ 'pts en' | t }} {{ jours() }} {{ 'jours' | t }}</div>
          </div>
          <div class="carte kpi">
            <div class="k-titre">{{ 'Corrections appliquées' | t }}</div>
            <div class="k-valeur">{{ t.nbProblemes ? (t.nbCorriges / t.nbProblemes * 100 | number: '1.0-0') : 0 }} %</div>
            <div class="k-sous">{{ t.nbCorriges }} {{ 'sur' | t }} {{ t.nbProblemes }}</div>
          </div>
        </div>

        <div class="graphes">
          <div class="carte g">
            <h2>{{ 'Évolution du score moyen' | t }}</h2>
            <p class="g-sous">{{ 'Score qualité calculé par l\\'IA, moyenne glissante sur 5 revues' | t }}</p>
            @if (t.evolution.length) {
              <div class="toile"><canvas baseChart [type]="'line'" [data]="ligne()" [options]="optionsLigne"></canvas></div>
            } @else { <div class="vide">{{ 'Pas encore de revue sur la période.' | t }}</div> }
          </div>
          <div class="carte g">
            <h2>{{ 'Problèmes par catégorie' | t }}</h2>
            <p class="g-sous">{{ t.nbProblemes }} {{ 'problèmes sur' | t }} {{ t.nbRevues }} {{ 'revues' | t }}</p>
            @if (t.nbProblemes) {
              <div class="toile petite"><canvas baseChart [type]="'bar'" [data]="barres()" [options]="optionsBarres"></canvas></div>
            } @else { <div class="vide">{{ 'Aucun problème : bravo !' | t }}</div> }
          </div>
        </div>

        <div class="recurrents">
          @for (e of t.erreursDetaillees.slice(0, 3); track e.titre) {
            <div class="carte rec">
              <span class="nb">{{ e.occurrences }}</span>
              <div><b>{{ e.titre }}</b><span>{{ libelleCat[e.categorie] | t }} · {{ e.corrigees }} {{ 'corrigé(s)' | t }}</span></div>
            </div>
          } @empty {
            <div class="carte rec vide-rec">{{ 'Aucun problème récurrent.' | t }} <a routerLink="/revues/nouvelle">{{ 'Lancer une revue' | t }}</a></div>
          }
        </div>
        <p class="periode-txt">{{ 'Période du' | t }} {{ t.debut | date: 'dd/MM/yyyy' }} {{ 'au' | t }} {{ t.fin | date: 'dd/MM/yyyy' }}</p>
      }
    </div>
  `,
  styles: [`
    .periode { height: 34px; font-size: 13px; }
    .kpis { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
    .kpi { padding: 16px 18px; }
    .k-titre { font-size: 12px; color: var(--texte-2); }
    .k-valeur { font-size: 28px; font-weight: 800; margin: 6px 0 2px; }
    .k-valeur small { font-size: 13px; color: var(--texte-3); font-weight: 600; }
    .k-sous { font-size: 12px; color: var(--texte-2); }
    .ok { color: var(--ok); } .ko { color: var(--critique); }
    .graphes { display: grid; grid-template-columns: 1.4fr 1fr; gap: 12px; margin-top: 12px; }
    .g { padding: 16px 18px; min-width: 0; }
    .g h2 { font-size: 14.5px; }
    .g-sous { font-size: 12px; color: var(--texte-3); margin: 4px 0 10px; }
    .toile { position: relative; height: 260px; }
    .toile.petite { height: 230px; }
    .recurrents { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-top: 12px; }
    .rec { display: flex; gap: 14px; align-items: center; padding: 14px 16px; }
    .rec .nb { font-size: 22px; font-weight: 800; color: var(--primaire); min-width: 30px; }
    .rec b { display: block; font-size: 13px; }
    .rec span { font-size: 12px; color: var(--texte-3); }
    .vide-rec { grid-column: 1 / -1; color: var(--texte-2); }
    .periode-txt { color: var(--texte-3); font-size: 12px; }
    @media (max-width: 1000px) {
      .kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      .graphes, .recurrents { grid-template-columns: 1fr; }
    }
  `],
})
export class TableauDeBordComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  readonly jours = signal(30);
  readonly tdb = signal<TableauDeBord | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly libelleCat = CATEGORIES;
  readonly prenom = computed(() => (this.auth.utilisateur()?.nom ?? '').split(/\s+/)[0]);

  readonly ligne = computed<ChartConfiguration<'line'>['data']>(() => {
    const ev = this.tdb()?.evolution ?? [];
    return {
      labels: ev.map((p) => new Date(p.jour).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' })),
      datasets: [
        {
          data: ev.map((p) => p.moyenneGlissante), label: tr('Moyenne glissante'), borderColor: '#5b5bf6', backgroundColor: 'rgba(91,91,246,.12)',
          fill: true, tension: .35, pointRadius: 3, pointBackgroundColor: '#5b5bf6',
        },
        {
          data: ev.map((p) => p.scoreMoyenJour), label: tr('Score du jour'), borderColor: '#c7c9e8', backgroundColor: 'transparent',
          borderDash: [4, 4], pointRadius: 2, tension: .2,
        },
      ],
    };
  });

  readonly barres = computed<ChartConfiguration<'bar'>['data']>(() => {
    const pc = this.tdb()?.parCategorie;
    const cats: Categorie[] = ['bug', 'bonne_pratique', 'securite', 'performance'];
    return {
      labels: cats.map((c) => CATEGORIES[c]),
      datasets: [{ data: cats.map((c) => pc?.[c] ?? 0), backgroundColor: cats.map((c) => COULEURS_CAT[c]), borderRadius: 6, barThickness: 14 }],
    };
  });

  readonly optionsLigne: ChartConfiguration<'line'>['options'] = {
    responsive: true, maintainAspectRatio: false,
    plugins: { legend: { display: true, position: 'bottom', labels: { boxWidth: 10, font: { size: 11 } } } },
    scales: { y: { min: 0, max: 100, ticks: { stepSize: 20 }, grid: { color: '#eef0f6' } }, x: { grid: { display: false } } },
  };

  readonly optionsBarres: ChartConfiguration<'bar'>['options'] = {
    indexAxis: 'y', responsive: true, maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: { x: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: '#eef0f6' } }, y: { grid: { display: false } } },
  };

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.api.tableauDeBord(this.jours()).subscribe({
      next: (t) => this.tdb.set(t),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }
}
