import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { messageErreur } from '../../core/http';
import { tr } from '../../core/i18n';
import { LangueService } from '../../core/langue.service';
import { AnalyseFlowchart, FlowchartRequete } from '../../core/models';
import { NotifService } from '../../core/notif.service';
import { telechargerCsv, versCsv } from '../../shared/csv';
import { IconeComponent } from '../../shared/icone.component';
import { ReponseIaComponent } from '../../shared/reponse-ia.component';
import { SelecteurMetierComponent } from '../../shared/selecteur-metier.component';
import { EXEMPLE_BPMN, EXEMPLE_MERMAID, formatFichier, lignesCasDeTest, MAX_IMAGE, MAX_SOURCE, PROBLEMES } from './flowchart-outils';

type Format = 'auto' | 'mermaid' | 'bpmn' | 'drawio';

/**
 * Flowchart → tests : un diagramme (Mermaid, BPMN 2.0, draw.io, ou une image transcrite par l'IA) devient la liste
 * de ses chemins, de ses défauts (branche manquante, impasse, boucle…) et un tableau de cas de test, calculés
 * sans IA côté serveur ; l'IA ne fait qu'enrichir ces cas (données, résultats attendus, Gherkin).
 */
@Component({
  selector: 'app-flowchart',
  standalone: true,
  imports: [RouterLink, IconeComponent, ReponseIaComponent, SelecteurMetierComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="barre">
      <div class="fil"><a routerLink="/revues/nouvelle">RevIA</a> / <b>{{ l.t('Flowchart → tests', 'Flowchart → tests') }}</b></div>
      <app-selecteur-metier [metier]="metier()" (metierChange)="metier.set($event)" />
      <label class="case"><input type="checkbox" [checked]="enrichir()" (change)="enrichir.set($any($event.target).checked)" />
        {{ l.t('Enrichir avec l’IA', 'Enrich with AI') }}</label>
      <button class="btn primaire" type="button" (click)="analyser()" [disabled]="attente() || !valide()">
        @if (attente()) { <span class="spinner"></span> {{ l.t('Analyse…', 'Analysing…') }} } @else { <app-icone nom="etoile" />{{ l.t('Analyser le diagramme', 'Analyse the diagram') }} }
      </button>
    </header>

    <div class="page">
      <h1 class="titre-page">{{ l.t('Flowchart → cas de test', 'Flowchart → test cases') }}</h1>
      <p class="sous-titre">{{ l.t('Chaque chemin du diagramme devient un cas de test, chaque décision chiffrée ses valeurs limites. Les chemins sont calculés sans IA : l’IA ne les invente jamais, elle les enrichit.', 'Every path of the diagram becomes a test case, every numeric decision its boundary values. Paths are computed without AI: the AI never invents them, it only enriches them.') }}</p>

      <div class="grille">
        <section class="carte bloc" [attr.aria-label]="l.t('Diagramme', 'Diagram')">
          <div class="outils-entree">
            <button class="btn petit" type="button" (click)="exemple('mermaid')"><app-icone nom="ampoule" [taille]="14" />{{ l.t('Exemple Mermaid (sinistre)', 'Mermaid sample (insurance claim)') }}</button>
            <button class="btn petit" type="button" (click)="exemple('bpmn')"><app-icone nom="ampoule" [taille]="14" />{{ l.t('Exemple BPMN (paiement)', 'BPMN sample (payment)') }}</button>
            <button class="btn petit" type="button" (click)="fichier.click()"><app-icone nom="import" [taille]="14" />{{ l.t('Importer un fichier', 'Import a file') }}</button>
            <input #fichier type="file" hidden accept=".png,.jpg,.jpeg,.webp,.bpmn,.drawio,.dio,.xml,.mmd,.mermaid,.md,.txt"
                   (change)="importer($any($event.target))" />
          </div>
          @if (image()) {
            <div class="apercu">
              <img [src]="image()" [alt]="l.t('Diagramme importé', 'Imported diagram')" />
              <div class="apercu-infos">
                <b>{{ nomImage() }}</b>
                <span>{{ l.t('L’IA transcrit l’image en Mermaid (fournisseurs multimodaux comme Gemini), puis la même analyse sans IA s’applique. Vous pourrez corriger la transcription.', 'The AI transcribes the image into Mermaid (multimodal providers such as Gemini), then the same AI-free analysis runs. You can correct the transcription.') }}</span>
                <button class="btn petit" type="button" (click)="retirerImage()">{{ l.t('Retirer l’image', 'Remove image') }}</button>
              </div>
            </div>
          } @else {
            <div class="champ">
              <label for="source">{{ l.t('Mermaid, BPMN 2.0 (XML) ou draw.io', 'Mermaid, BPMN 2.0 (XML) or draw.io') }}
                <span class="meta" [class.trop]="source().length > maxSource">{{ source().length }} / {{ maxSource }}</span></label>
              <textarea id="source" class="input code" rows="22" spellcheck="false" [value]="source()"
                        (input)="source.set($any($event.target).value)"
                        [placeholder]="l.t('flowchart TD\\n  A([Début]) --> B{Montant > 1000 ?}\\n  B -->|Oui| C[Expertise]\\n  B -->|Non| D([Règlement])', 'flowchart TD\\n  A([Start]) --> B{Amount > 1000?}\\n  B -->|Yes| C[Expert review]\\n  B -->|No| D([Payment])')"></textarea>
            </div>
            <div class="ligne-format">
              <label for="format">{{ l.t('Format', 'Format') }}</label>
              <select id="format" class="input petit-select" [value]="format()" (change)="format.set($any($event.target).value)">
                <option value="auto">{{ l.t('Détection automatique', 'Auto-detect') }}</option>
                <option value="mermaid">Mermaid</option>
                <option value="bpmn">BPMN 2.0</option>
                <option value="drawio">draw.io</option>
              </select>
              <span class="info">{{ l.t('Le XML avec DOCTYPE ou entités est refusé (sécurité).', 'XML with DOCTYPE or entities is rejected (security).') }}</span>
            </div>
          }
        </section>

        <section class="carte resultat" [attr.aria-label]="l.t('Résultat', 'Result')" aria-live="polite">
          @if (attente()) {
            <div class="vide"><span class="spinner sombre grand"></span><b>{{ l.t('Analyse des chemins…', 'Analysing paths…') }}</b>
              <span>{{ enrichir() ? l.t('Calcul sans IA, puis enrichissement par l’IA.', 'AI-free computation, then AI enrichment.') : l.t('Calcul sans IA.', 'AI-free computation.') }}</span></div>
          } @else if (erreur()) {
            <div class="erreur" role="alert"><b>{{ erreur() }}</b>
              <button class="btn petit" type="button" (click)="analyser()">{{ l.t('Réessayer', 'Retry') }}</button></div>
          } @else if (resultat()) { @if (resultat(); as r) {
            <div class="synthese">
              <span class="badge langage">{{ r.format }}</span>
              <span class="badge neutre">{{ r.noeuds.length }} {{ l.t('nœud(s)', 'node(s)') }}</span>
              <span class="badge neutre">{{ r.chemins.length }}{{ r.cheminsTronques ? '+' : '' }} {{ l.t('chemin(s)', 'path(s)') }}</span>
              <span class="badge" [class]="nbMajeurs() ? 'g-majeure' : 's-corrigee'">{{ r.problemes.length }} {{ l.t('problème(s)', 'issue(s)') }}</span>
              <span class="badge neutre">{{ r.casDeTest.length }} {{ l.t('cas de test', 'test case(s)') }}</span>
              <span class="infos">{{ r.fournisseur }} · {{ duree(r.dureeMs) }} s @if (r.nbMasquages) { · 🔒 {{ r.nbMasquages }} }</span>
            </div>
            @if (r.avertissement) { <p class="avertissement" role="status">ℹ️ {{ trad(r.avertissement) }}</p> }
            @for (m of r.remarques; track $index) { <p class="remarque">{{ m }}</p> }

            <h2 class="sous">{{ l.t('Problèmes du diagramme', 'Diagram issues') }}</h2>
            @if (!r.problemes.length) { <p class="ok">✅ {{ l.t('Aucun défaut de structure détecté.', 'No structural defect found.') }}</p> }
            <ul class="problemes">
              @for (p of r.problemes; track $index) {
                <li [class]="'g-' + p.gravite">
                  <span class="type">{{ probleme(p.type).icone }} {{ l.t(probleme(p.type).fr, probleme(p.type).en) }}</span>
                  <span dir="auto">{{ p.message }}</span>
                </li>
              }
            </ul>

            <h2 class="sous">{{ l.t('Chemins', 'Paths') }} ({{ r.chemins.length }})</h2>
            <ol class="chemins">
              @for (c of r.chemins; track c.id) {
                <li [class.incomplet]="!c.complet">
                  <span class="id">{{ c.id }}</span>
                  <span dir="auto">{{ c.etapes.join(' → ') }}</span>
                  @if (!c.complet) { <span class="badge g-majeure">{{ l.t('bloqué', 'blocked') }}</span> }
                </li>
              }
            </ol>

            <div class="tete-tableau">
              <h2 class="sous">{{ l.t('Cas de test', 'Test cases') }} ({{ r.casDeTest.length }})</h2>
              <button class="btn petit" type="button" (click)="exporter()" [disabled]="!r.casDeTest.length"><app-icone nom="export" [taille]="14" />{{ l.t('Exporter en CSV', 'Export to CSV') }}</button>
            </div>
            <div class="table-defile">
              <table class="cas">
                <thead><tr><th>ID</th><th>{{ l.t('Titre', 'Title') }}</th><th>{{ l.t('Étapes', 'Steps') }}</th><th>{{ l.t('Données', 'Data') }}</th><th>{{ l.t('Résultat attendu', 'Expected result') }}</th></tr></thead>
                <tbody>
                  @for (c of r.casDeTest; track c.id) {
                    <tr [class.limite]="c.type === 'limite'">
                      <td class="id">{{ c.id }}</td>
                      <td dir="auto">{{ c.titre }}</td>
                      <td dir="auto"><ol>@for (e of c.etapes; track $index) { <li>{{ e }}</li> }</ol></td>
                      <td dir="auto">{{ c.donnees ?? '—' }}</td>
                      <td dir="auto">{{ c.attendu }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>

            @if (r.enrichissement) {
              <h2 class="sous">{{ l.t('Enrichissement par l’IA', 'AI enrichment') }} · {{ r.modele }}</h2>
              <app-reponse-ia [contenu]="r.enrichissement" />
            }

            <div class="tete-tableau">
              <h2 class="sous">{{ r.format === 'image' ? l.t('Transcription Mermaid (à vérifier)', 'Mermaid transcription (check it)') : 'Mermaid' }}</h2>
              <span class="actions">
                <button class="btn petit" type="button" (click)="copier(r.mermaid)"><app-icone nom="copier" [taille]="14" />{{ l.t('Copier', 'Copy') }}</button>
                @if (r.format !== 'mermaid') {
                  <button class="btn petit" type="button" (click)="corriger(r.mermaid)">{{ l.t('Modifier dans l’éditeur', 'Edit in the editor') }}</button>
                }
              </span>
            </div>
            <pre class="mermaid">{{ r.mermaid }}</pre>
          } } @else {
            <div class="vide">
              <span class="grand">🔀</span>
              <b>{{ l.t('Du diagramme aux cas de test', 'From diagram to test cases') }}</b>
              <span>{{ l.t('Collez un Mermaid, un BPMN ou un draw.io, importez un fichier ou une image (ou cliquez sur un exemple), puis « Analyser le diagramme ».', 'Paste Mermaid, BPMN or draw.io, import a file or an image (or click a sample), then "Analyse the diagram".') }}</span>
            </div>
          }
        </section>
      </div>
    </div>
  `,
  styles: [`
    .page { max-width: none; }
    .case { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; color: var(--texte-2); white-space: nowrap; }
    .petit-select { height: 34px; font-size: 13px; padding: 0 8px; max-width: 220px; }
    .grille { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.25fr); gap: 18px; align-items: start; }
    .bloc { padding: 18px; display: flex; flex-direction: column; gap: 12px; }
    .outils-entree { display: flex; gap: 8px; flex-wrap: wrap; }
    .champ label { display: flex; justify-content: space-between; gap: 10px; }
    .meta { color: var(--texte-3); font-weight: 400; white-space: nowrap; }
    .meta.trop { color: var(--critique); font-weight: 600; }
    textarea.code { resize: vertical; width: 100%; font: 12.5px/1.5 var(--mono); }
    .ligne-format { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; font-size: 13px; }
    .info { font-size: 12px; color: var(--texte-3); }
    .apercu { display: flex; flex-direction: column; gap: 10px; }
    .apercu img { max-width: 100%; max-height: 420px; object-fit: contain; border: 1px solid var(--bord); border-radius: 10px; background: #fff; }
    .apercu-infos { display: flex; flex-direction: column; gap: 6px; align-items: flex-start; font-size: 13px; color: var(--texte-2); }
    .resultat { padding: 18px; min-height: 420px; max-height: calc(100vh - 150px); overflow-y: auto; }
    .vide { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; text-align: center; color: var(--texte-2); min-height: 380px; }
    .vide b { color: var(--texte); font-size: 15px; }
    .grand { font-size: 34px; }
    .erreur { background: var(--critique-pale); color: var(--critique); border-radius: 10px; padding: 14px; display: flex; flex-direction: column; gap: 10px; align-items: flex-start; }
    .synthese { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; padding-bottom: 12px; border-bottom: 1px solid var(--bord); }
    .infos { font-size: 12px; color: var(--texte-3); margin-left: auto; }
    .avertissement { background: var(--info-pale); color: var(--texte); border-radius: 8px; padding: 8px 12px; font-size: 13px; margin: 12px 0 0; }
    .remarque { font-size: 12.5px; color: var(--texte-2); margin: 8px 0 0; }
    .ok { font-size: 13px; margin: 0; }
    .sous { font-size: 12px; text-transform: uppercase; letter-spacing: .06em; color: var(--texte-3); font-weight: 700; margin: 18px 0 8px; }
    .problemes { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
    .problemes li { border-radius: 8px; padding: 8px 10px; font-size: 13px; display: flex; flex-direction: column; gap: 2px; color: var(--texte) !important; }
    .problemes li.g-info { background: var(--info-pale); }
    .problemes .type { font-weight: 700; font-size: 12px; }
    .chemins { margin: 0; padding-left: 0; list-style: none; display: flex; flex-direction: column; gap: 4px; font-size: 13px; }
    .chemins li { display: flex; gap: 8px; align-items: baseline; }
    .chemins li.incomplet { color: var(--majeure); }
    .id { font: 600 11.5px var(--mono); color: var(--texte-3); white-space: nowrap; }
    .tete-tableau { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
    .actions { display: flex; gap: 6px; }
    .table-defile { overflow-x: auto; }
    table.cas { width: 100%; border-collapse: collapse; font-size: 12.5px; }
    .cas th { text-align: left; font-size: 11px; text-transform: uppercase; letter-spacing: .04em; color: var(--texte-3); border-bottom: 1px solid var(--bord); padding: 6px; }
    .cas td { border-bottom: 1px solid var(--bord); padding: 6px; vertical-align: top; }
    .cas td ol { margin: 0; padding-left: 16px; }
    .cas tr.limite td { background: var(--mineure-pale); }
    pre.mermaid { background: #111427; color: #e5e7ff; border-radius: 10px; padding: 14px; font-size: 12px; line-height: 1.5; overflow-x: auto; margin: 0; }
    @media (max-width: 1000px) {
      .grille { grid-template-columns: 1fr; }
      .resultat { max-height: none; }
    }
  `],
})
export class FlowchartComponent {
  private readonly api = inject(ApiService);
  private readonly notif = inject(NotifService);
  readonly l = inject(LangueService);

  readonly maxSource = MAX_SOURCE;
  readonly source = signal('');
  readonly format = signal<Format>('auto');
  readonly image = signal<string | null>(null);
  readonly nomImage = signal('');
  readonly metier = signal('');
  readonly enrichir = signal(true);
  readonly attente = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly resultat = signal<AnalyseFlowchart | null>(null);
  readonly valide = computed(() => !!this.image() || (!!this.source().trim() && this.source().length <= MAX_SOURCE));
  readonly nbMajeurs = computed(() => (this.resultat()?.problemes ?? []).filter((p) => p.gravite === 'majeure').length);

  probleme(type: string): { fr: string; en: string; icone: string } {
    return PROBLEMES[type] ?? { fr: type, en: type, icone: '•' };
  }

  trad(texte: string): string {
    return tr(texte);
  }

  exemple(type: 'mermaid' | 'bpmn'): void {
    this.retirerImage();
    this.source.set(type === 'mermaid' ? EXEMPLE_MERMAID : EXEMPLE_BPMN);
    this.format.set(type);
    this.metier.set(type === 'mermaid' ? 'assurance' : 'banque-paiement');
    this.resultat.set(null);
    this.erreur.set(null);
  }

  importer(champ: HTMLInputElement): void {
    const f = champ.files?.[0];
    champ.value = '';
    if (!f) {
      return;
    }
    const type = formatFichier(f.name);
    if (type === null) {
      this.notif.erreur(this.l.t('Fichier non pris en charge : image (PNG, JPEG, WebP), .bpmn, .drawio, .xml ou .mmd.',
        'Unsupported file: image (PNG, JPEG, WebP), .bpmn, .drawio, .xml or .mmd.'));
      return;
    }
    const lecteur = new FileReader();
    if (type === 'image') {
      if (f.size > MAX_IMAGE) {
        this.notif.erreur(this.l.t('Image trop lourde (4 Mo maximum).', 'Image too large (4 MB maximum).'));
        return;
      }
      lecteur.onload = () => {
        this.image.set(String(lecteur.result));
        this.nomImage.set(f.name);
        this.resultat.set(null);
      };
      lecteur.readAsDataURL(f);
      return;
    }
    lecteur.onload = () => {
      const texte = String(lecteur.result);
      this.retirerImage();
      this.source.set(texte);
      this.format.set(type === 'auto' || formatFichier(f.name, texte) === 'auto' ? 'auto' : (formatFichier(f.name, texte) as Format));
      this.resultat.set(null);
    };
    lecteur.readAsText(f);
  }

  retirerImage(): void {
    this.image.set(null);
    this.nomImage.set('');
  }

  /** Transcription d'image ou export BPMN / draw.io : repris dans l'éditeur pour être corrigé puis réanalysé. */
  corriger(mermaid: string): void {
    this.retirerImage();
    this.source.set(mermaid);
    this.format.set('mermaid');
  }

  analyser(): void {
    if (!this.valide() || this.attente()) {
      return;
    }
    const req: FlowchartRequete = { metier: this.metier() || undefined, enrichir: this.enrichir() };
    if (this.image()) {
      req.image = this.image()!;
    } else {
      req.source = this.source();
      req.format = this.format();
    }
    this.attente.set(true);
    this.erreur.set(null);
    this.resultat.set(null);
    this.api.flowchart(req).subscribe({
      next: (r) => {
        this.resultat.set(r);
        this.attente.set(false);
      },
      error: (e) => {
        this.erreur.set(tr(messageErreur(e)));
        this.attente.set(false);
      },
    });
  }

  exporter(): void {
    const r = this.resultat();
    if (r) {
      telechargerCsv(`revia-cas-de-test-${new Date().toISOString().slice(0, 10)}.csv`,
        versCsv(lignesCasDeTest(r.casDeTest, this.l.langue() === 'en')));
    }
  }

  copier(texte: string): void {
    navigator.clipboard?.writeText(texte).then(
      () => this.notif.succes(this.l.t('Copié', 'Copied')),
      () => this.notif.erreur(this.l.t('Copie impossible.', 'Copy failed.')),
    );
  }

  duree(ms: number): string {
    const s = (ms / 1000).toFixed(1);
    return this.l.langue() === 'fr' ? s.replace('.', ',') : s;
  }
}
