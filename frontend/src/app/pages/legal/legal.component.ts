import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink, RouterLinkActive } from '@angular/router';
import { map } from 'rxjs';
import { TPipe } from '../../core/i18n';
import { LangueService } from '../../core/langue.service';
import { MARQUE } from '../../core/marque';
import { DOCS_LEGAUX, DocLegal, SOUS_TRAITANTS, documentLegal } from './contenu-legal';

/** Pages légales publiques : /legal/cgu, /legal/confidentialite, /legal/mentions-legales, /legal/dpa (FR / EN). */
@Component({
  selector: 'app-legal',
  standalone: true,
  imports: [TPipe, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="legal">
      <header>
        <a class="logo" routerLink="/">Rev<span>IA</span></a>
        <nav>
          @for (d of docs; track d) {
            <a [routerLink]="['/legal', d]" routerLinkActive="actif">{{ libelle(d) | t }}</a>
          }
        </nav>
        <button class="langue" type="button" (click)="langues.basculer()">{{ langues.langue() === 'fr' ? 'EN' : 'FR' }}</button>
      </header>

      @if (!valide) {
        <div class="bandeau" role="note">
          ⚠️ {{ 'Modèle à faire valider par un juriste' | t }} —
          {{ "les informations marquées « À COMPLÉTER » doivent être renseignées avant la mise en ligne." | t }}
        </div>
      }

      <article>
        <h1>{{ document().titre }}</h1>
        <p class="maj">{{ 'Dernière mise à jour :' | t }} {{ miseAJour }}</p>
        <p class="intro">{{ document().intro }}</p>
        @for (s of document().sections; track s.titre) {
          <section>
            <h2>{{ s.titre }}</h2>
            @for (p of s.paragraphes; track $index) { <p>{{ p }}</p> }
          </section>
        }
        @if (document().sousTraitants) {
          <div class="defile">
            <table>
              <thead><tr><th>{{ 'Sous-traitant' | t }}</th><th>{{ 'Rôle' | t }}</th><th>{{ 'Données' | t }}</th><th>{{ 'Localisation' | t }}</th></tr></thead>
              <tbody>
                @for (st of sousTraitants; track st.nom) {
                  <tr><td><b>{{ st.nom }}</b></td><td>{{ st.role[langues.langue()] }}</td><td>{{ st.donnees[langues.langue()] }}</td><td>{{ st.lieu[langues.langue()] }}</td></tr>
                }
              </tbody>
            </table>
          </div>
          <p class="note">{{ "Les fournisseurs d'IA ne sont appelés que s'ils sont configurés sur l'instance ; une instance auto-hébergée avec Ollama n'en utilise aucun." | t }}</p>
        }
      </article>

      <footer>
        <a routerLink="/">{{ "← Retour à l'accueil" | t }}</a>
        <a [href]="'mailto:' + marque.contact">{{ marque.contact }}</a>
      </footer>
    </div>
  `,
  styles: [`
    .legal { min-height: 100vh; background: #f7f8fc; color: #1c2033; font-size: 15px; line-height: 1.65; }
    header { display: flex; align-items: center; gap: 18px; flex-wrap: wrap; padding: 16px 5vw; background: #0b0e1d; }
    .logo { color: #fff; font-weight: 800; font-size: 20px; text-decoration: none; } .logo span { color: #8b8cff; }
    nav { display: flex; gap: 14px; flex-wrap: wrap; flex: 1; }
    nav a { color: #b8bce0; text-decoration: none; font-size: 13.5px; } nav a.actif, nav a:hover { color: #fff; }
    .langue { background: transparent; border: 1px solid #3a3f6b; color: #fff; border-radius: 8px; padding: 4px 10px; cursor: pointer; }
    .bandeau { background: #fff4d6; border-bottom: 1px solid #f0d58a; color: #6b4e00; padding: 10px 5vw; font-size: 13.5px; }
    article { max-width: 860px; margin: 0 auto; padding: 34px 16px 20px; }
    h1 { font-size: 28px; margin: 0 0 4px; } h2 { font-size: 17px; margin: 26px 0 6px; }
    .maj { color: #6b7090; font-size: 13px; margin: 0 0 16px; } .intro { font-weight: 500; }
    p { margin: 0 0 10px; }
    .defile { overflow-x: auto; margin-top: 10px; }
    table { width: 100%; border-collapse: collapse; font-size: 13px; background: #fff; }
    th, td { text-align: left; padding: 8px 10px; border: 1px solid #e3e5f0; vertical-align: top; } th { background: #eef0fa; }
    .note { font-size: 13px; color: #6b7090; margin-top: 10px; }
    footer { max-width: 860px; margin: 0 auto; padding: 20px 16px 40px; display: flex; gap: 20px; flex-wrap: wrap; font-size: 13px; }
    footer a { color: #4b4fd1; }
  `],
})
export class LegalComponent {
  private readonly route = inject(ActivatedRoute);
  readonly langues = inject(LangueService);
  readonly marque = MARQUE;
  readonly docs = DOCS_LEGAUX;
  readonly sousTraitants = SOUS_TRAITANTS;
  readonly valide = MARQUE.legal.valideParJuriste;
  readonly miseAJour = MARQUE.legal.miseAJour;

  private readonly doc = toSignal(
    this.route.paramMap.pipe(map((p) => (DOCS_LEGAUX as string[]).includes(p.get('doc') ?? '') ? (p.get('doc') as DocLegal) : 'cgu')),
    { initialValue: 'cgu' as DocLegal },
  );
  readonly document = computed(() => documentLegal(this.doc(), this.langues.langue()));

  libelle(d: DocLegal): string {
    return { cgu: 'CGU', confidentialite: 'Confidentialité', 'mentions-legales': 'Mentions légales', dpa: 'DPA' }[d];
  }
}
