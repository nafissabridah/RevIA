import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CATEGORIES, GRAVITES, STATUTS } from '../core/libelles';
import { Categorie, Gravite, Statut } from '../core/models';

@Component({
  selector: 'app-gravite',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge gravite" [class]="'g-' + valeur()"><span class="point"></span>{{ libelles[valeur()] }}</span>`,
})
export class GraviteBadgeComponent {
  readonly valeur = input.required<Gravite>();
  readonly libelles = GRAVITES;
}

@Component({
  selector: 'app-categorie',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge neutre">{{ libelles[valeur()] }}</span>`,
})
export class CategorieBadgeComponent {
  readonly valeur = input.required<Categorie>();
  readonly libelles = CATEGORIES;
}

@Component({
  selector: 'app-statut',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge statut" [class]="'s-' + valeur()">@if (valeur() === 'corrigee') {✓ }{{ libelles[valeur()] }}</span>`,
})
export class StatutBadgeComponent {
  readonly valeur = input.required<Statut>();
  readonly libelles = STATUTS;
}
