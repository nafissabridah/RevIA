import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { NotifService } from '../core/notif.service';
import { IconeComponent } from './icone.component';
import { blocsDeCode, markdownVersHtml } from './markdown';
import { TPipe } from '../core/i18n';

/** Réponse de l'IA en Markdown, avec boutons « Copier le code » et « Copier tout ». */
@Component({
  selector: 'app-reponse-ia',
  standalone: true,
  imports: [TPipe, IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="md" dir="auto" [innerHTML]="html()"></div>
    @if (actions()) {
      <div class="copies">
        @if (code()) {
          <button class="btn petit" type="button" (click)="copier(code()!, 'Code copié')"><app-icone nom="copier" [taille]="14" />{{ 'Copier le code' | t }}</button>
        }
        <button class="btn petit" type="button" (click)="copier(contenu(), 'Réponse copiée')"><app-icone nom="copier" [taille]="14" />{{ 'Copier tout' | t }}</button>
      </div>
    }
  `,
  styles: [`
    :host { display: block; min-width: 0; }
    .md { font-size: 13.5px; line-height: 1.6; overflow-wrap: anywhere; }
    .md :first-child { margin-top: 0; }
    .md ::ng-deep p { margin: 0 0 10px; }
    .md ::ng-deep h3, .md ::ng-deep h4, .md ::ng-deep h5 { margin: 14px 0 6px; font-size: 14.5px; }
    .md ::ng-deep ul, .md ::ng-deep ol { margin: 0 0 10px; padding-left: 22px; }
    .md ::ng-deep li { margin: 3px 0; }
    .md ::ng-deep code { background: var(--primaire-pale); color: var(--primaire-fonce); border-radius: 5px; padding: 1px 5px; font-size: 12.5px; }
    .md ::ng-deep blockquote { margin: 0 0 10px; padding: 8px 12px; border-left: 3px solid var(--accent-ia); background: var(--surface-2); color: var(--texte-2); border-radius: 0 8px 8px 0; }
    .md ::ng-deep pre.md-code {
      position: relative; background: #111427; color: #e5e7ff; border-radius: 10px; padding: 14px; margin: 0 0 12px;
      overflow-x: auto; font-size: 12.5px; line-height: 1.55;
    }
    .md ::ng-deep pre.md-code code { background: none; color: inherit; padding: 0; font-size: inherit; }
    .md ::ng-deep .md-lang { position: absolute; top: 6px; right: 10px; font-size: 10.5px; color: #7d84ad; text-transform: uppercase; letter-spacing: .08em; }
    .copies { display: flex; gap: 8px; margin-top: 4px; }
  `],
})
export class ReponseIaComponent {
  readonly contenu = input.required<string>();
  readonly actions = input(true);
  readonly html = computed(() => markdownVersHtml(this.contenu()));
  readonly code = computed(() => {
    const blocs = blocsDeCode(this.contenu());
    return blocs.length ? blocs.join('\n\n') : null;
  });
  private readonly notif = inject(NotifService);

  copier(texte: string, message: string): void {
    navigator.clipboard?.writeText(texte).then(() => this.notif.succes(message), () => this.notif.erreur('Copie impossible.'));
  }
}
