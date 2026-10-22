import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { messageErreur } from '../../core/http';
import { TPipe, tr } from '../../core/i18n';
import { CritereProjet, Exigence, ProjetDetail, ProjetResume } from '../../core/models';
import { NotifService } from '../../core/notif.service';
import { IconeComponent } from '../../shared/icone.component';
import { VERDICTS_QA, badgeStatutProjet, corpsVerdict } from '../../shared/tracabilite';

/** Limites de l'API (miroir de la validation côté serveur). */
const MAX_TEXTE = 20000;
const MAX_DIFF = 400000;
const MAX_LIGNES_DIFF = 4000;

/**
 * Projets : une seule vue de la chaîne exigence → critère d'acceptation → code → test → verdict QA,
 * pour que BA, dev et QA suivent la couverture de bout en bout. Liste des projets sur /projets, détail sur /projets/:id.
 */
@Component({
  selector: 'app-projets',
  standalone: true,
  imports: [TPipe, RouterLink, IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="barre">
      <div class="fil"><a routerLink="/revues/nouvelle">RevIA</a> /
        @if (projet(); as p) { <a routerLink="/projets">{{ 'Projets' | t }}</a> / <b dir="auto">{{ p.nom }}</b> }
        @else { <b>{{ 'Projets' | t }}</b> }</div>
      @if (projet(); as p) {
        <button class="btn" type="button" (click)="exporter(p)" [disabled]="!p.synthese.nbCriteres"><app-icone nom="export" />{{ 'Matrice CSV' | t }}</button>
        <button class="btn danger" type="button" (click)="supprimer(p)"><app-icone nom="poubelle" />{{ 'Supprimer' | t }}</button>
      }
    </header>

    <div class="page">
      @if (chargement()) {
        <div class="vide"><span class="spinner sombre grand"></span></div>
      } @else if (erreur()) {
        <div class="erreur" role="alert"><b>{{ erreur()! | t }}</b><a class="btn petit" routerLink="/projets">{{ 'Retour aux projets' | t }}</a></div>
      } @else if (projet()) { @if (projet(); as p) {
        <h1 class="titre-page" dir="auto">{{ p.nom }}</h1>
        @if (p.description) { <p class="sous-titre" dir="auto">{{ p.description }}</p> }

        <section class="carte synthese" [attr.aria-label]="'Couverture' | t">
          <div class="ligne-couv">
            <b>{{ 'Couverture' | t }} {{ p.synthese.couverture }} %</b>
            <span class="meta">{{ p.synthese.nbExigences }} {{ 'exigence(s)' | t }} · {{ p.synthese.nbCriteres }} {{ 'critère(s)' | t }}</span>
          </div>
          <div class="jauge" role="progressbar" [attr.aria-valuenow]="p.synthese.couverture" aria-valuemin="0" aria-valuemax="100">
            <span [style.width.%]="p.synthese.couverture"></span>
          </div>
          <div class="compteurs">
            <span class="badge s-corrigee">✅ {{ p.synthese.nbCouverts }} {{ 'couvert(s)' | t }}</span>
            <span class="badge s-a_revoir">🟡 {{ p.synthese.nbPartiels }} {{ 'partiel(s)' | t }}</span>
            <span class="badge s-a_corriger">❌ {{ p.synthese.nbNonCouverts }} {{ 'non couvert(s)' | t }}</span>
            <span class="badge neutre">⏳ {{ p.synthese.nbNonVerifies }} {{ 'non vérifié(s)' | t }}</span>
            <span class="badge s-corrigee">QA ✔ {{ p.synthese.nbValidesQa }} {{ 'validé(s)' | t }}</span>
            @if (p.synthese.nbRejetesQa) { <span class="badge s-a_corriger">QA ✖ {{ p.synthese.nbRejetesQa }} {{ 'rejeté(s)' | t }}</span> }
          </div>
        </section>

        <section class="carte bloc" [attr.aria-label]="'Ajouter une exigence' | t">
          <h2>{{ 'Ajouter une exigence' | t }}</h2>
          <p class="info">{{ 'Collez une user story avec ses critères (lignes AC-n, scénarios Gherkin ou puces) ou la sortie de l’outil « Rédaction d’exigences » : les critères sont extraits automatiquement.' | t }}</p>
          <div class="rangee">
            <input class="input ref" [value]="refExigence()" (input)="refExigence.set($any($event.target).value)" maxlength="40"
                   [placeholder]="'Référence (ex. US-01)' | t" [attr.aria-label]="'Référence' | t" />
            <input class="input" [value]="titreExigence()" (input)="titreExigence.set($any($event.target).value)" maxlength="200"
                   [placeholder]="'Titre de l’exigence' | t" [attr.aria-label]="'Titre' | t" />
          </div>
          <textarea class="input" rows="6" [attr.maxlength]="maxTexte" [value]="texteExigence()" (input)="texteExigence.set($any($event.target).value)"
                    [placeholder]="'User story et critères d’acceptation' | t" [attr.aria-label]="'User story et critères d’acceptation' | t"></textarea>
          <div><button class="btn primaire" type="button" (click)="ajouterExigence(p)"
                       [disabled]="attente() || !titreExigence().trim() || !texteExigence().trim()">
            <app-icone nom="nouvelle" />{{ 'Ajouter et extraire les critères' | t }}</button></div>
        </section>

        @for (e of p.exigences; track e.id) {
          <section class="carte exigence" [attr.aria-label]="e.reference">
            <div class="tete-exigence">
              <span class="id">{{ e.reference }}</span>
              <b dir="auto">{{ e.titre }}</b>
              <button class="btn petit" type="button" (click)="ouvrirVerification(e)"><app-icone nom="criteres" />{{ 'Vérifier sur un diff' | t }}</button>
            </div>
            @if (verification() === e.id) {
              <div class="verif">
                <input class="input" [value]="cible()" (input)="cible.set($any($event.target).value)" maxlength="300"
                       [placeholder]="'Référence du code (PR, branche, commit) — facultatif' | t" [attr.aria-label]="'Référence du code' | t" />
                <textarea class="input" rows="8" spellcheck="false" [attr.maxlength]="maxDiff" [value]="diff()"
                          (input)="diff.set($any($event.target).value)" [placeholder]="'Collez le diff de la PR' | t"
                          [attr.aria-label]="'Diff de la pull request' | t"></textarea>
                <div class="rangee">
                  <span class="meta" [class.trop]="lignes() > maxLignes">{{ lignes() }} / {{ maxLignes }} {{ 'lignes' | t }}</span>
                  <button class="btn" type="button" (click)="verification.set(null)">{{ 'Annuler' | t }}</button>
                  <button class="btn primaire" type="button" (click)="verifier(p, e)" [disabled]="attente() || !diff().trim() || lignes() > maxLignes">
                    @if (attente()) { <span class="spinner"></span> {{ 'Vérification…' | t }} } @else { {{ 'Vérifier la couverture' | t }} }
                  </button>
                </div>
              </div>
            }
            <div class="table-wrap">
              <table>
                <thead><tr>
                  <th>{{ 'Critère' | t }}</th><th>{{ 'Statut' | t }}</th><th>{{ 'Preuve' | t }}</th>
                  <th>{{ 'Test manquant' | t }}</th><th>{{ 'Verdict QA' | t }}</th>
                </tr></thead>
                <tbody>
                  @for (c of e.criteres; track c.id) {
                    <tr>
                      <td><span class="id">{{ c.reference }}</span> <span dir="auto">{{ c.texte }}</span></td>
                      <td><span class="badge" [class]="badge(c.statut).classe">{{ badge(c.statut).icone }} {{ badge(c.statut).libelle | t }}</span></td>
                      <td class="preuve" dir="auto">{{ c.preuve ?? '—' }}</td>
                      <td dir="auto">{{ c.test ?? '—' }}</td>
                      <td class="qa">
                        <select #v class="input" [value]="c.verdictQa ?? ''" [attr.aria-label]="'Verdict QA' | t">
                          <option value="">—</option>
                          @for (o of verdicts; track o.valeur) { <option [value]="o.valeur" [selected]="c.verdictQa === o.valeur">{{ o.libelle | t }}</option> }
                        </select>
                        <input #com class="input" maxlength="2000" [value]="c.commentaireQa ?? ''" [placeholder]="'Commentaire QA' | t" [attr.aria-label]="'Commentaire QA' | t" />
                        <button class="btn petit" type="button" (click)="enregistrerVerdict(p, c, v.value, com.value)">{{ 'Enregistrer' | t }}</button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </section>
        } @empty {
          <div class="vide petit"><b>{{ 'Aucune exigence pour l’instant' | t }}</b></div>
        }
      } } @else {
        <h1 class="titre-page">{{ 'Projets' | t }}</h1>
        <p class="sous-titre">{{ 'Traçabilité de bout en bout : exigence → critère d’acceptation → code → test → verdict QA.' | t }}</p>

        <section class="carte bloc" [attr.aria-label]="'Nouveau projet' | t">
          <div class="rangee">
            <input class="input" [value]="nom()" (input)="nom.set($any($event.target).value)" maxlength="120"
                   [placeholder]="'Nom du projet' | t" [attr.aria-label]="'Nom du projet' | t" />
            <input class="input" [value]="description()" (input)="description.set($any($event.target).value)" maxlength="2000"
                   [placeholder]="'Description (facultatif)' | t" [attr.aria-label]="'Description' | t" />
            <button class="btn primaire" type="button" (click)="creer()" [disabled]="attente() || !nom().trim()"><app-icone nom="nouvelle" />{{ 'Créer le projet' | t }}</button>
          </div>
        </section>

        <div class="liste-projets">
          @for (p of projets(); track p.id) {
            <a class="carte projet" [routerLink]="['/projets', p.id]">
              <b dir="auto">{{ p.nom }}</b>
              @if (p.description) { <span class="meta" dir="auto">{{ p.description }}</span> }
              <div class="jauge"><span [style.width.%]="p.synthese.couverture"></span></div>
              <span class="meta">{{ 'Couverture' | t }} {{ p.synthese.couverture }} % · {{ p.synthese.nbExigences }} {{ 'exigence(s)' | t }} ·
                {{ p.synthese.nbCriteres }} {{ 'critère(s)' | t }} · QA ✔ {{ p.synthese.nbValidesQa }}</span>
            </a>
          } @empty {
            <div class="vide petit"><span class="grand">🧭</span><b>{{ 'Aucun projet pour l’instant' | t }}</b>
              <span>{{ 'Créez un projet, puis ajoutez ses exigences.' | t }}</span></div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .page { display: flex; flex-direction: column; gap: 16px; }
    .bloc, .synthese, .exigence { padding: 16px 18px; display: flex; flex-direction: column; gap: 10px; }
    h2 { font-size: 15px; margin: 0; }
    .rangee { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
    .rangee .input { flex: 1; min-width: 180px; }
    .rangee .ref { flex: 0 0 160px; min-width: 120px; }
    textarea.input { resize: vertical; width: 100%; }
    .info, .meta { font-size: 12.5px; color: var(--texte-3); margin: 0; }
    .meta.trop { color: var(--critique); font-weight: 600; }
    .ligne-couv { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; }
    .jauge { height: 10px; background: var(--surface-2); border-radius: 6px; overflow: hidden; border: 1px solid var(--bord); }
    .jauge span { display: block; height: 100%; background: var(--ok); }
    .compteurs { display: flex; gap: 6px; flex-wrap: wrap; }
    .tete-exigence { display: flex; gap: 10px; align-items: center; }
    .tete-exigence b { flex: 1; }
    .id { font: 600 11.5px var(--mono); color: var(--texte-3); }
    .verif { display: flex; flex-direction: column; gap: 8px; background: var(--surface-2); border-radius: 10px; padding: 12px; }
    .verif .rangee { justify-content: flex-end; }
    .verif .meta { margin-right: auto; }
    .table-wrap { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; font-size: 13px; }
    th { text-align: left; font-size: 11px; text-transform: uppercase; letter-spacing: .05em; color: var(--texte-3); padding: 6px 8px; border-bottom: 1px solid var(--bord); }
    td { padding: 8px; border-bottom: 1px solid var(--bord); vertical-align: top; line-height: 1.4; }
    td.preuve { white-space: pre-line; overflow-wrap: anywhere; max-width: 320px; font-size: 12.5px; }
    td.qa { display: flex; flex-direction: column; gap: 6px; min-width: 190px; }
    .liste-projets { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px; }
    .projet { padding: 14px 16px; display: flex; flex-direction: column; gap: 8px; color: var(--texte); text-decoration: none; }
    .projet:hover { border-color: var(--primaire); }
    .vide { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; text-align: center; color: var(--texte-2); min-height: 200px; }
    .vide.petit { min-height: 120px; grid-column: 1 / -1; }
    .grand { font-size: 30px; }
    .erreur { background: var(--critique-pale); color: var(--critique); border-radius: 10px; padding: 14px; display: flex; flex-direction: column; gap: 10px; align-items: flex-start; }
  `],
})
export class ProjetsComponent {
  private readonly api = inject(ApiService);
  private readonly notif = inject(NotifService);
  private readonly router = inject(Router);

  readonly maxTexte = MAX_TEXTE;
  readonly maxDiff = MAX_DIFF;
  readonly maxLignes = MAX_LIGNES_DIFF;
  readonly badge = badgeStatutProjet;
  readonly verdicts = VERDICTS_QA;

  readonly projets = signal<ProjetResume[]>([]);
  readonly projet = signal<ProjetDetail | null>(null);
  readonly chargement = signal(true);
  readonly attente = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly nom = signal('');
  readonly description = signal('');
  readonly refExigence = signal('');
  readonly titreExigence = signal('');
  readonly texteExigence = signal('');
  readonly verification = signal<number | null>(null);
  readonly diff = signal('');
  readonly cible = signal('');

  constructor() {
    inject(ActivatedRoute).paramMap.pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe((pm) => {
      const id = Number(pm.get('id'));
      this.erreur.set(null);
      this.chargement.set(true);
      this.projet.set(null);
      if (id) {
        this.api.projet(id).subscribe({
          next: (p) => { this.projet.set(p); this.chargement.set(false); },
          error: (e) => { this.erreur.set(messageErreur(e)); this.chargement.set(false); },
        });
      } else {
        this.api.projets().subscribe({
          next: (l) => { this.projets.set(l); this.chargement.set(false); },
          error: (e) => { this.erreur.set(messageErreur(e)); this.chargement.set(false); },
        });
      }
    });
  }

  lignes(): number {
    return this.diff() ? this.diff().split('\n').length : 0;
  }

  creer(): void {
    this.attente.set(true);
    this.api.creerProjet({ nom: this.nom().trim(), description: this.description().trim() || null }).subscribe({
      next: (p) => { this.attente.set(false); this.nom.set(''); this.description.set(''); this.router.navigate(['/projets', p.id]); },
      error: (e) => { this.attente.set(false); this.notif.erreur(messageErreur(e)); },
    });
  }

  supprimer(p: ProjetDetail): void {
    if (!confirm(tr('Supprimer ce projet, ses exigences et ses critères ?'))) {
      return;
    }
    this.api.supprimerProjet(p.id).subscribe({
      next: () => this.router.navigate(['/projets']),
      error: (e) => this.notif.erreur(messageErreur(e)),
    });
  }

  ajouterExigence(p: ProjetDetail): void {
    this.attente.set(true);
    this.api.ajouterExigence(p.id, {
      reference: this.refExigence().trim() || null, titre: this.titreExigence().trim(), texte: this.texteExigence(),
    }).subscribe({
      next: (maj) => {
        this.projet.set(maj);
        this.attente.set(false);
        this.refExigence.set('');
        this.titreExigence.set('');
        this.texteExigence.set('');
      },
      error: (e) => { this.attente.set(false); this.notif.erreur(messageErreur(e)); },
    });
  }

  ouvrirVerification(e: Exigence): void {
    this.verification.set(this.verification() === e.id ? null : e.id);
  }

  verifier(p: ProjetDetail, e: Exigence): void {
    this.attente.set(true);
    this.api.verifierExigence(p.id, e.id, { diff: this.diff(), cible: this.cible().trim() || null }).subscribe({
      next: (maj) => { this.projet.set(maj); this.attente.set(false); this.verification.set(null); this.diff.set(''); },
      error: (err) => { this.attente.set(false); this.notif.erreur(messageErreur(err)); },
    });
  }

  enregistrerVerdict(p: ProjetDetail, c: CritereProjet, verdict: string, commentaire: string): void {
    this.api.verdictQa(p.id, c.id, corpsVerdict(verdict, commentaire)).subscribe({
      next: () => {
        // La synthèse (validés QA) est recalculée par le serveur : on recharge le projet.
        this.api.projet(p.id).subscribe((maj) => this.projet.set(maj));
        this.notif.succes(tr('Verdict QA enregistré.'));
      },
      error: (e) => this.notif.erreur(messageErreur(e)),
    });
  }

  exporter(p: ProjetDetail): void {
    this.api.matriceCsv(p.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `revia-matrice-${p.id}-${new Date().toISOString().slice(0, 10)}.csv`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (e) => this.notif.erreur(messageErreur(e)),
    });
  }
}
