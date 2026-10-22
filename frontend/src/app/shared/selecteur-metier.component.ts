import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { LangueService } from '../core/langue.service';
import { Metier } from '../core/models';

/**
 * Sélecteur « Métier » (packs finance, assurance, santé…) : les règles, pièges et réglementations du pack
 * choisi sont ajoutés au prompt de l'IA côté serveur. Valeur vide = aucun pack.
 */
@Component({
  selector: 'app-selecteur-metier',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <select class="input petit-select" [value]="metier()" (change)="metierChange.emit($any($event.target).value)"
            [attr.aria-label]="l.t('Métier', 'Industry')" [title]="titre()">
      <option value="">{{ l.t('Métier : aucun', 'Industry: none') }}</option>
      @for (m of metiers(); track m.id) {
        <option [value]="m.id" [selected]="m.id === metier()">{{ l.t('Métier', 'Industry') }} : {{ l.t(m.nomFr, m.nomEn) }}</option>
      }
    </select>
  `,
  styles: [`.petit-select { height: 34px; font-size: 13px; padding: 0 8px; max-width: 260px; }`],
})
export class SelecteurMetierComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly l = inject(LangueService);
  readonly metier = input('');
  readonly metierChange = output<string>();
  readonly metiers = signal<Metier[]>([]);

  ngOnInit(): void {
    this.api.metiers().subscribe({ next: (m) => this.metiers.set(m), error: () => this.metiers.set([]) });
  }

  /** Infobulle : description et contenu du pack choisi. */
  titre(): string {
    const m = this.metiers().find((x) => x.id === this.metier());
    if (!m) {
      return this.l.t('Ajoute les règles métier d’un secteur au prompt de l’IA', 'Adds an industry’s business rules to the AI prompt');
    }
    return this.l.t(
      `${m.description} — ${m.nbRegles} règles, ${m.nbPieges} pièges, ${m.nbReglementations} réglementations (rappels, pas un avis juridique)`,
      `${m.descriptionEn} — ${m.nbRegles} rules, ${m.nbPieges} pitfalls, ${m.nbReglementations} regulations (reminders, not legal advice)`,
    );
  }
}
