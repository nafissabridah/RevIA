import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { couleurScore } from '../core/libelles';

/** Anneau de score sur 100 (comme dans la maquette « 38 »). */
@Component({
  selector: 'app-score-ring',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg [attr.width]="taille()" [attr.height]="taille()" viewBox="0 0 100 100" role="img" [attr.aria-label]="'Score ' + score() + ' sur 100'">
      <circle cx="50" cy="50" r="42" fill="none" stroke="var(--bord)" stroke-width="9" />
      <circle cx="50" cy="50" r="42" fill="none" [attr.stroke]="couleur()" stroke-width="9" stroke-linecap="round"
              [attr.stroke-dasharray]="circonference" [attr.stroke-dashoffset]="decalage()" transform="rotate(-90 50 50)" />
      <text x="50" y="50" text-anchor="middle" dominant-baseline="central" class="valeur" [attr.fill]="couleur()">{{ score() }}</text>
      <text x="50" y="72" text-anchor="middle" class="sur">/ 100</text>
    </svg>
  `,
  styles: [`
    :host { display: inline-flex; }
    .valeur { font-size: 30px; font-weight: 800; font-family: var(--police); }
    .sur { font-size: 10px; fill: var(--texte-2); font-family: var(--police); }
    circle { transition: stroke-dashoffset .6s ease; }
  `],
})
export class ScoreRingComponent {
  readonly score = input.required<number>();
  readonly taille = input(96);
  readonly circonference = 2 * Math.PI * 42;
  readonly couleur = computed(() => couleurScore(this.score()));
  readonly decalage = computed(() => this.circonference * (1 - Math.max(0, Math.min(100, this.score())) / 100));
}
