import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { messageErreur } from '../../core/http';
import { libelleLangage } from '../../core/libelles';
import { Categorie, Probleme, RevueDetail } from '../../core/models';
import { NotifService } from '../../core/notif.service';
import { CategorieBadgeComponent, GraviteBadgeComponent } from '../../shared/badges';
import { LigneDiff, desindenter, diffLignes } from '../../shared/diff';
import { IconeComponent } from '../../shared/icone.component';
import { TPipe } from '../../core/i18n';

const REFLEXES: Record<Categorie, string> = {
  securite: "Toute donnée venant de l'extérieur (formulaire, URL, fichier) doit être considérée comme non fiable : validez-la, échappez-la, et passez-la en paramètre plutôt que de la concaténer.",
  bug: 'Pensez aux cas limites : collection vide, valeur absente (null), premier et dernier indice. Un test unitaire par cas limite évite la régression.',
  bonne_pratique: "Un code lisible est un code maintenable : nommez explicitement, gérez les erreurs au bon niveau et rendez l'absence de valeur visible dans les signatures.",
  performance: "Ne chargez que ce dont vous avez besoin : colonnes utiles, pagination, et mesurez avant d'optimiser.",
};

/** La correction expliquée (maquette diapo 13) : le pourquoi, le diff avant / après, le retour utilisateur. */
@Component({
  selector: 'app-correction',
  standalone: true,
  imports: [TPipe, RouterLink, GraviteBadgeComponent, CategorieBadgeComponent, IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="barre">
      <div class="fil">
        <a routerLink="/historique">{{ 'Revues' | t }}</a> /
        @if (revue(); as r) { <a [routerLink]="['/revues', r.id]">{{ r.titre }}</a> / }
        <b>{{ 'Problème' | t }} {{ position() }} {{ 'sur' | t }} {{ revue()?.problemes?.length ?? 0 }}</b>
      </div>
      <button class="btn" type="button" [disabled]="position() <= 1" (click)="aller(position() - 1)">{{ '← Précédent' | t }}</button>
      <button class="btn" type="button" [disabled]="position() >= (revue()?.problemes?.length ?? 0)" (click)="aller(position() + 1)">{{ 'Suivant →' | t }}</button>
    </header>

    @if (erreur()) { <div class="page"><div class="carte vide">{{ erreur() | t }}</div></div> }

    @if (revue(); as r) {
      <div class="page grille">
        <nav class="carte liste" [attr.aria-label]="'Problèmes détectés' | t">
          <div class="sur-titre">{{ r.problemes.length }} {{ 'problèmes détectés' | t }}</div>
          @for (p of r.problemes; track p.id; let i = $index) {
            <a [routerLink]="['/revues', r.id, 'problemes', i + 1]" [class.actif]="i + 1 === position()" [class.fp]="p.fauxPositif">
              <i [class]="'d-' + p.gravite"></i><span class="t">{{ p.titre }}</span><span class="l">{{ 'L' | t }}{{ p.ligne }}</span>
            </a>
          }
        </nav>

        @if (probleme(); as p) {
          <section class="detail">
            <div class="carte bloc">
              <div class="badges">
                <app-gravite [valeur]="p.gravite" />
                <app-categorie [valeur]="p.categorie" />
                @if (p.owasp) { <span class="badge neutre">{{ 'OWASP' | t }} {{ p.owasp }}</span> }
                <span class="badge neutre">{{ 'Ligne' | t }} {{ p.ligne }}</span>
                @if (p.fauxPositif) { <span class="badge neutre">{{ 'Signalé comme faux positif' | t }}</span> }
              </div>
              <h1>{{ p.titre }}</h1>

              <!-- 1. Le pourquoi -->
              <div class="deux">
                <div class="encadre">
                  <div class="sur-titre">{{ 'Problème' | t }}</div>
                  <p>{{ p.explication }}</p>
                </div>
                <div class="encadre">
                  <div class="sur-titre">{{ 'La correction' | t }}</div>
                  <p>{{ p.correction?.justification || ('Aucune correction automatique proposée pour ce problème.' | t) }}</p>
                </div>
              </div>

              <!-- 2. Diff avant / après -->
              @if (p.correction) {
                <div class="diff" [attr.aria-label]="'Différences avant / après' | t">
                  <div class="col">
                    <div class="col-tete">{{ 'Avant ·' | t }} {{ r.titre }}</div>
                    @for (d of gauche(); track $index) {
                      <div class="l" [class.moins]="d.type === 'retire'"><span class="n">{{ d.avant !== null ? p.ligne + d.avant - 1 : '' }}</span><span class="s">{{ d.type === 'retire' ? '−' : ' ' }}</span><code>{{ d.texte }}</code></div>
                    }
                  </div>
                  <div class="col">
                    <div class="col-tete">{{ 'Après · correction proposée par RevIA' | t }}</div>
                    @for (d of droite(); track $index) {
                      <div class="l" [class.plus]="d.type === 'ajoute'"><span class="n">{{ d.apres !== null ? p.ligne + d.apres - 1 : '' }}</span><span class="s">{{ d.type === 'ajoute' ? '+' : ' ' }}</span><code>{{ d.texte }}</code></div>
                    }
                  </div>
                </div>
              }

              <!-- 3. Retour utilisateur -->
              <div class="actions">
                @if (p.correction) {
                  <button class="btn primaire" type="button" (click)="copier(p)"><app-icone nom="copier" />{{ p.correction.copiee ? ('Copier à nouveau' | t) : ('Copier la correction' | t) }}</button>
                }
                <button class="btn" type="button" (click)="corrige(p)" [class.coche]="p.corrige"><app-icone nom="check" />{{ p.corrige ? ('Corrigé' | t) : ('Marquer comme corrigé' | t) }}</button>
                <span class="espace"></span>
                <span class="utile">{{ 'Utile ?' | t }}</span>
                <button class="btn petit" type="button" [class.choisi]="p.utile === true" (click)="utile(p, true)">{{ '👍 Oui' | t }}</button>
                <button class="btn petit" type="button" [class.choisi]="p.utile === false" (click)="utile(p, false)">{{ '👎 Non' | t }}</button>
                <button class="btn petit" type="button" [class.choisi]="p.fauxPositif" (click)="fauxPositif(p)">🏳 {{ p.fauxPositif ? ('Annuler faux positif' | t) : ('Faux positif' | t) }}</button>
              </div>
            </div>

            <div class="carte reflexe">
              <span class="ampoule">💡</span>
              <div>
                <div class="sur-titre">{{ 'Bon réflexe pour la suite' | t }}</div>
                <p>{{ reflexe() | t }}
                  @if (p.occurrencesPrecedentes > 0) {
                    {{ 'RevIA a déjà détecté ce même motif' | t }} <b>{{ p.occurrencesPrecedentes }} {{ 'fois' | t }}</b> {{ 'dans vos revues précédentes.' | t }}
                  }
                </p>
              </div>
            </div>

            <div class="carte contexte">
              <div class="sur-titre">{{ 'Contexte envoyé au modèle' | t }}</div>
              <p>{{ 'Extrait de' | t }} {{ r.nbLignes }} {{ 'lignes · langage :' | t }} {{ libelleLangage(r.langage) | t }} {{ '· secrets masqués avant envoi (' | t }}{{ r.nbMasquages }} {{ 'valeur(s) remplacée(s) par' | t }} <code>***MASQUÉ***</code>{{ ') · réponse validée par schéma JSON · fournisseur' | t }} {{ r.fournisseur }} / {{ r.modele }} {{ '· prompt' | t }} {{ r.promptVersion }}</p>
            </div>
          </section>
        }
      </div>
    }
  `,
  styles: [`
    .grille { display: grid; grid-template-columns: 260px minmax(0, 1fr); gap: 18px; align-items: start; max-width: none; }
    .liste { padding: 14px 10px; position: sticky; top: 76px; }
    .liste .sur-titre { padding: 0 8px 10px; }
    .liste a { display: flex; align-items: center; gap: 8px; padding: 8px; border-radius: 8px; color: var(--texte); font-size: 13px; text-decoration: none; }
    .liste a:hover { background: var(--surface-2); }
    .liste a.actif { background: var(--primaire-pale); color: var(--primaire-fonce); font-weight: 600; }
    .liste a.fp .t { text-decoration: line-through; color: var(--texte-3); }
    .liste i { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; }
    .d-critique { background: var(--critique); } .d-majeure { background: var(--majeure); } .d-mineure { background: var(--mineure); } .d-info { background: var(--info); }
    .t { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .l { font-size: 11px; color: var(--texte-3); }
    .detail { display: flex; flex-direction: column; gap: 12px; min-width: 0; }
    .bloc { padding: 18px 20px; }
    .badges { display: flex; gap: 6px; flex-wrap: wrap; }
    h1 { font-size: 19px; margin: 12px 0 14px; }
    .deux { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .encadre { border: 1px solid var(--bord); border-radius: 10px; padding: 12px 14px; background: var(--surface-2); }
    .encadre p { margin: 6px 0 0; font-size: 13px; color: var(--texte-2); line-height: 1.55; }
    .diff { display: grid; grid-template-columns: 1fr 1fr; margin-top: 14px; border-radius: 10px; overflow: hidden; background: #111427; border: 1px solid #1f2444; }
    .col { min-width: 0; overflow-x: auto; padding-bottom: 6px; }
    .col + .col { border-left: 1px solid #1f2444; }
    .col-tete { color: #8b91bb; font-size: 11.5px; padding: 8px 12px; border-bottom: 1px solid #1f2444; }
    .diff .l { display: flex; font: 12px/1.7 var(--mono); color: #d7dafa; white-space: pre; }
    .diff .n { width: 38px; text-align: right; color: #4b5275; padding-right: 8px; flex-shrink: 0; }
    .diff .s { width: 14px; flex-shrink: 0; color: #8b91bb; }
    .diff code { padding-right: 12px; }
    .moins { background: rgba(229, 72, 77, .18); } .moins .s { color: #f87171; }
    .plus { background: rgba(34, 197, 94, .16); } .plus .s { color: #4ade80; }
    .actions { display: flex; gap: 8px; align-items: center; margin-top: 14px; flex-wrap: wrap; }
    .espace { flex: 1; }
    .utile { color: var(--texte-3); font-size: 12.5px; }
    .choisi, .coche { border-color: var(--primaire); color: var(--primaire-fonce); background: var(--primaire-pale); }
    .reflexe { display: flex; gap: 14px; padding: 16px 18px; align-items: flex-start; }
    .ampoule { font-size: 22px; }
    .reflexe p, .contexte p { margin: 6px 0 0; font-size: 13px; color: var(--texte-2); line-height: 1.55; }
    .contexte { padding: 16px 18px; }
    @media (max-width: 1000px) {
      .grille { grid-template-columns: 1fr; }
      .liste { position: static; }
      .deux, .diff { grid-template-columns: 1fr; }
      .col + .col { border-left: none; border-top: 1px solid #1f2444; }
    }
  `],
})
export class CorrectionComponent {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly notif = inject(NotifService);

  readonly id = input.required<string>();
  readonly index = input.required<string>();

  readonly revue = signal<RevueDetail | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly libelleLangage = libelleLangage;

  readonly position = computed(() => Number(this.index()) || 1);
  readonly probleme = computed(() => this.revue()?.problemes[this.position() - 1] ?? null);
  private readonly diff = computed<LigneDiff[]>(() => {
    const c = this.probleme()?.correction;
    return c ? diffLignes(desindenter(c.codeAvant), desindenter(c.codeApres)) : [];
  });
  readonly gauche = computed(() => this.diff().filter((d) => d.type !== 'ajoute'));
  readonly droite = computed(() => this.diff().filter((d) => d.type !== 'retire'));
  readonly reflexe = computed(() => REFLEXES[this.probleme()?.categorie ?? 'bug']);

  constructor() {
    effect(() => {
      const id = Number(this.id());
      if (this.revue()?.id !== id) {
        this.api.revue(id).subscribe({
          next: (r) => this.revue.set(r),
          error: (e) => this.erreur.set(messageErreur(e)),
        });
      }
    }, { allowSignalWrites: true });
  }

  aller(n: number): void {
    this.router.navigate(['/revues', this.id(), 'problemes', n]);
  }

  copier(p: Probleme): void {
    this.api.copier(p.id).subscribe({
      next: async ({ code }) => {
        try {
          await navigator.clipboard.writeText(code);
          this.notif.succes('Correction copiée dans le presse-papiers.');
        } catch {
          this.notif.erreur("Le navigateur a refusé l'accès au presse-papiers.");
        }
        this.remplacer({ ...p, correction: p.correction ? { ...p.correction, copiee: true } : null });
      },
      error: (e) => this.notif.erreur(messageErreur(e)),
    });
  }

  corrige(p: Probleme): void {
    this.api.corrige(p.id, !p.corrige).subscribe({
      next: (n) => {
        this.remplacer(n);
        this.notif.succes(n.corrige ? 'Problème marqué comme corrigé.' : 'Problème rouvert.');
      },
      error: (e) => this.notif.erreur(messageErreur(e)),
    });
  }

  utile(p: Probleme, valeur: boolean): void {
    this.api.utile(p.id, valeur).subscribe({
      next: (n) => {
        this.remplacer(n);
        this.notif.succes('Merci pour votre retour !');
      },
      error: (e) => this.notif.erreur(messageErreur(e)),
    });
  }

  fauxPositif(p: Probleme): void {
    this.api.fauxPositif(p.id, !p.fauxPositif).subscribe({
      next: () => {
        this.api.revue(Number(this.id())).subscribe((r) => this.revue.set(r));
        this.notif.succes(p.fauxPositif ? 'Signalement retiré.' : 'Faux positif signalé : il ne compte plus dans le score.');
      },
      error: (e) => this.notif.erreur(messageErreur(e)),
    });
  }

  private remplacer(n: Probleme): void {
    const r = this.revue();
    if (r) {
      this.revue.set({ ...r, problemes: r.problemes.map((x) => (x.id === n.id ? n : x)) });
    }
  }
}
