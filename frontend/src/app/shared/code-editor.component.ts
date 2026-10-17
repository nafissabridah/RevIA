import { ChangeDetectionStrategy, Component, NgZone, OnDestroy, effect, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MonacoEditorModule } from 'ngx-monaco-editor-v2';
import { MONACO_LANG } from '../core/libelles';
import { Probleme } from '../core/models';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
declare const monaco: any;

const SEVERITE: Record<string, number> = { critique: 8, majeure: 4, mineure: 2, info: 1 };
const COULEUR: Record<string, string> = { critique: '#ef4444', majeure: '#f97316', mineure: '#eab308', info: '#60a5fa' };

/**
 * Éditeur Monaco (le moteur de VS Code) : coloration, numéros de ligne, et marqueurs dans la marge
 * pour chaque ligne à problème, colorés selon la gravité.
 */
@Component({
  selector: 'app-code-editor',
  standalone: true,
  imports: [FormsModule, MonacoEditorModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!pret()) {
      <div class="chargement">Chargement de l'éditeur…</div>
    }
    <ngx-monaco-editor class="editeur" [options]="options()" [ngModel]="code()" (ngModelChange)="code.set($event)"
                       (onInit)="initialiser($event)" />
  `,
  styles: [`
    :host { display: block; position: relative; height: 100%; min-height: 320px; }
    .editeur { height: 100%; min-height: 320px; display: block; }
    .chargement { position: absolute; inset: 0; display: grid; place-items: center; color: #8b93b8; font-size: 13px; }
    :host ::ng-deep .editor-container { height: 100% !important; }
  `],
})
export class CodeEditorComponent implements OnDestroy {
  readonly code = model<string>('');
  readonly langage = input<string>('plaintext');
  readonly problemes = input<Probleme[]>([]);
  readonly lectureSeule = input(false);
  readonly ligneActive = input<number | null>(null);
  readonly ligneCliquee = output<number>();
  readonly curseur = output<{ ligne: number; colonne: number }>();

  readonly pret = signal(false);
  readonly options = signal<Record<string, unknown>>({});
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private editeur: any = null;
  private decorations: string[] = [];
  private readonly zone = inject(NgZone);

  constructor() {
    effect(() => {
      this.options.set({
        theme: 'revia-sombre',
        language: MONACO_LANG[this.langage()] ?? 'plaintext',
        readOnly: this.lectureSeule(),
        automaticLayout: true,
        minimap: { enabled: false },
        glyphMargin: true,
        fontSize: 13,
        fontFamily: "'JetBrains Mono', Consolas, 'Courier New', monospace",
        scrollBeyondLastLine: false,
        renderLineHighlight: 'all',
        tabSize: 4,
        padding: { top: 10 },
      });
    }, { allowSignalWrites: true });
    effect(() => {
      const pbs = this.problemes();
      this.appliquerMarqueurs(pbs);
    });
    effect(() => {
      const l = this.ligneActive();
      if (l && this.editeur) {
        this.editeur.revealLineInCenter(l);
        this.editeur.setPosition({ lineNumber: l, column: 1 });
      }
    });
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  initialiser(editeur: any): void {
    this.editeur = editeur;
    if (typeof monaco !== 'undefined') {
      monaco.editor.defineTheme('revia-sombre', {
        base: 'vs-dark',
        inherit: true,
        rules: [
          { token: 'keyword', foreground: 'c4b5fd' },
          { token: 'string', foreground: 'fde68a' },
          { token: 'type', foreground: 'f9a8d4' },
          { token: 'comment', foreground: '6b7280', fontStyle: 'italic' },
        ],
        colors: {
          'editor.background': '#111427',
          'editorGutter.background': '#111427',
          'editorLineNumber.foreground': '#4b5275',
          'editor.lineHighlightBackground': '#1a1e38',
        },
      });
      monaco.editor.setTheme('revia-sombre');
    }
    editeur.onMouseDown((e: { target: { position?: { lineNumber: number } } }) => {
      const l = e.target.position?.lineNumber;
      if (l) {
        this.zone.run(() => this.ligneCliquee.emit(l));
      }
    });
    editeur.onDidChangeCursorPosition((e: { position: { lineNumber: number; column: number } }) => {
      this.zone.run(() => this.curseur.emit({ ligne: e.position.lineNumber, colonne: e.position.column }));
    });
    this.pret.set(true);
    this.appliquerMarqueurs(this.problemes());
  }

  private appliquerMarqueurs(pbs: Probleme[]): void {
    if (!this.editeur || typeof monaco === 'undefined') {
      return;
    }
    const modele = this.editeur.getModel();
    if (!modele) {
      return;
    }
    const actifs = pbs.filter((p) => !p.fauxPositif && p.ligne <= modele.getLineCount());
    this.decorations = this.editeur.deltaDecorations(this.decorations, actifs.map((p) => ({
      range: new monaco.Range(p.ligne, 1, p.ligne, 1),
      options: {
        isWholeLine: true,
        className: `ligne-${p.gravite}`,
        glyphMarginClassName: `glyphe-${p.gravite}`,
        glyphMarginHoverMessage: { value: `**${p.titre}** — ${p.explication}` },
        overviewRuler: { color: COULEUR[p.gravite], position: 4 },
      },
    })));
    monaco.editor.setModelMarkers(modele, 'revia', actifs.map((p) => ({
      startLineNumber: p.ligne,
      endLineNumber: p.ligne,
      startColumn: modele.getLineFirstNonWhitespaceColumn(p.ligne) || 1,
      endColumn: modele.getLineMaxColumn(p.ligne),
      message: `${p.titre} : ${p.explication}`,
      severity: SEVERITE[p.gravite] ?? 1,
      source: 'RevIA',
    })));
  }

  ngOnDestroy(): void {
    this.editeur = null;
  }
}
