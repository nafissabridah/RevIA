import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { FluxIaService } from '../../core/flux-ia.service';
import { LangueService } from '../../core/langue.service';
import { LANGAGES } from '../../core/libelles';
import { AssistantReponse, OutilIA } from '../../core/models';
import { TransfertCodeService } from '../../core/transfert-code.service';
import { CodeEditorComponent } from '../../shared/code-editor.component';
import { IconeComponent } from '../../shared/icone.component';
import { ReponseIaComponent } from '../../shared/reponse-ia.component';

interface Textes {
  titre: string;
  description: string;
  bouton: string;
  placeholder: string;
}

interface Outil {
  id: OutilIA;
  icone: string;
  groupe: 'dev' | 'qa';
  fr: Textes;
  en: Textes;
  /** Cadres / formats proposés (le premier est la valeur par défaut, envoyée comme « langage cible »). */
  cadres?: string[];
  /** L'entrée est du texte libre (exigence, logs…) plutôt que du code. */
  texteLibre?: boolean;
}

export const OUTILS: Outil[] = [
  { id: 'tests', icone: '🧪', groupe: 'dev',
    fr: { titre: 'Tests unitaires', description: 'JUnit, pytest ou Jest prêts à copier.', bouton: 'Générer les tests', placeholder: 'Collez la fonction ou la classe à tester' },
    en: { titre: 'Unit tests', description: 'Copy-ready JUnit, pytest or Jest tests.', bouton: 'Generate tests', placeholder: 'Paste the function or class to test' } },
  { id: 'documentation', icone: '📝', groupe: 'dev',
    fr: { titre: 'Documentation', description: 'Javadoc, docstrings, commentaires et README.', bouton: 'Documenter le code', placeholder: 'Collez le code à documenter' },
    en: { titre: 'Documentation', description: 'Javadoc, docstrings, comments and README.', bouton: 'Document the code', placeholder: 'Paste the code to document' } },
  { id: 'explication', icone: '💡', groupe: 'dev',
    fr: { titre: 'Explication de code', description: 'Le code expliqué pas à pas, ligne par ligne.', bouton: 'Expliquer le code', placeholder: 'Collez le code à comprendre' },
    en: { titre: 'Explain code', description: 'The code explained step by step, line by line.', bouton: 'Explain the code', placeholder: 'Paste the code to understand' } },
  { id: 'commit', icone: '✍️', groupe: 'dev',
    fr: { titre: 'Commit & pull request', description: 'Message de commit et description de PR à partir du diff.', bouton: 'Rédiger le message', placeholder: 'Collez le résultat de « git diff »' },
    en: { titre: 'Commit & pull request', description: 'Commit message and PR description from a diff.', bouton: 'Write the message', placeholder: 'Paste the output of "git diff"' },
    texteLibre: true },
  { id: 'refactoring', icone: '🔁', groupe: 'dev',
    fr: { titre: 'Refactoring', description: 'Un code plus lisible et plus simple, même comportement.', bouton: 'Refactorer', placeholder: 'Collez le code à améliorer' },
    en: { titre: 'Refactoring', description: 'Cleaner, simpler code with the same behaviour.', bouton: 'Refactor', placeholder: 'Paste the code to improve' } },
  { id: 'conversion', icone: '🔀', groupe: 'dev',
    fr: { titre: 'Conversion de langage', description: 'Traduire le code dans un autre langage, de façon idiomatique.', bouton: 'Convertir', placeholder: 'Collez le code à convertir' },
    en: { titre: 'Language conversion', description: 'Translate code into another language, idiomatically.', bouton: 'Convert', placeholder: 'Paste the code to convert' } },
  // ----- QA -----
  { id: 'cas-de-test', icone: '📋', groupe: 'qa', texteLibre: true, cadres: ['Markdown + Gherkin', 'Xray (Jira)', 'TestRail', 'Zephyr Scale'],
    fr: { titre: 'Plan & cas de test', description: 'User story → cas de test, Gherkin, traçabilité.', bouton: 'Générer les cas de test', placeholder: 'Collez la user story, l’exigence et ses critères d’acceptation' },
    en: { titre: 'Test plan & cases', description: 'User story → test cases, Gherkin, traceability.', bouton: 'Generate test cases', placeholder: 'Paste the user story, requirement and acceptance criteria' } },
  { id: 'e2e', icone: '🖥️', groupe: 'qa', texteLibre: true, cadres: ['Playwright (TypeScript)', 'Cypress', 'Selenium Java', 'Selenium Python', 'WebdriverIO'],
    fr: { titre: 'Tests E2E (UI)', description: 'Playwright, Cypress ou Selenium avec page objects.', bouton: 'Générer les tests E2E', placeholder: 'Décrivez le parcours, ou collez le HTML / le composant' },
    en: { titre: 'E2E UI tests', description: 'Playwright, Cypress or Selenium with page objects.', bouton: 'Generate E2E tests', placeholder: 'Describe the user journey, or paste the HTML / component' } },
  { id: 'tests-api', icone: '🔌', groupe: 'qa', cadres: ['Auto', 'REST Assured (Java)', 'pytest + requests', 'Jest + supertest', 'Postman', 'Karate', 'k6'],
    fr: { titre: 'Tests d’API', description: 'REST Assured, pytest, Postman + test de charge k6.', bouton: 'Générer les tests d’API', placeholder: 'Collez le contrat OpenAPI, le contrôleur ou la liste des endpoints' },
    en: { titre: 'API tests', description: 'REST Assured, pytest, Postman + k6 load test.', bouton: 'Generate API tests', placeholder: 'Paste the OpenAPI contract, controller or list of endpoints' } },
  { id: 'rapport-bug', icone: '🐞', groupe: 'qa', texteLibre: true,
    fr: { titre: 'Rapport de bug', description: 'Description brute → ticket Jira clair et complet.', bouton: 'Rédiger le rapport', placeholder: 'Décrivez le bug avec vos mots (et collez les logs si vous les avez)' },
    en: { titre: 'Bug report', description: 'Rough notes → a clear, complete Jira ticket.', bouton: 'Write the report', placeholder: 'Describe the bug in your own words (paste logs if you have them)' } },
  { id: 'analyse-logs', icone: '🔎', groupe: 'qa', texteLibre: true,
    fr: { titre: 'Logs & stack trace', description: 'Cause racine, ligne en cause et correctif.', bouton: 'Analyser', placeholder: 'Collez la stack trace, les logs ou le message d’erreur' },
    en: { titre: 'Logs & stack traces', description: 'Root cause, failing line and fix.', bouton: 'Analyse', placeholder: 'Paste the stack trace, logs or error message' } },
  { id: 'donnees-test', icone: '🗃️', groupe: 'qa', cadres: ['JSON', 'CSV', 'SQL INSERT', 'YAML'],
    fr: { titre: 'Données de test', description: 'Jeux nominaux, limites, invalides et injections.', bouton: 'Générer les données', placeholder: 'Collez le schéma : classe, table SQL, JSON Schema ou exemple' },
    en: { titre: 'Test data', description: 'Nominal, boundary, invalid and injection datasets.', bouton: 'Generate data', placeholder: 'Paste the schema: class, SQL table, JSON Schema or example' } },
  { id: 'revue-tests', icone: '🧐', groupe: 'qa',
    fr: { titre: 'Revue de tests', description: 'Cas manquants, tests instables, assertions faibles.', bouton: 'Relire les tests', placeholder: 'Collez vos tests (et le code testé si possible)' },
    en: { titre: 'Test review', description: 'Missing cases, flaky tests, weak assertions.', bouton: 'Review the tests', placeholder: 'Paste your tests (and the code under test if possible)' } },
];

const EXEMPLES: Record<OutilIA, { code: string; langage: string }> = {
  tests: { langage: 'java', code: 'public class Panier {\n    public double total(double prix, int quantite) {\n        if (quantite < 0) {\n            throw new IllegalArgumentException("Quantité négative");\n        }\n        return prix * quantite;\n    }\n}' },
  documentation: { langage: 'python', code: 'def moyenne(notes):\n    if not notes:\n        return 0\n    return sum(notes) / len(notes)\n' },
  explication: { langage: 'javascript', code: 'const actifs = users\n  .filter(u => u.actif)\n  .map(u => u.email.toLowerCase())\n  .sort();\n' },
  commit: { langage: 'auto', code: 'diff --git a/src/Panier.java b/src/Panier.java\n@@ -2,5 +2,8 @@ public class Panier {\n     public double total(double prix, int quantite) {\n+        if (quantite < 0) {\n+            throw new IllegalArgumentException("Quantité négative");\n+        }\n         return prix * quantite;\n     }\n' },
  refactoring: { langage: 'java', code: 'public String statut(int score) {\n    String s = "";\n    if (score >= 80) { s = "bon"; } else { if (score >= 50) { s = "moyen"; } else { s = "faible"; } }\n    return s;\n}' },
  conversion: { langage: 'python', code: 'def compter_mots(texte):\n    compte = {}\n    for mot in texte.lower().split():\n        compte[mot] = compte.get(mot, 0) + 1\n    return compte\n' },
  'cas-de-test': { langage: 'auto', code: 'As a registered customer, I want to reset my password so that I can sign in again.\n\nAcceptance criteria:\n- A reset link is sent to the account e-mail and expires after 30 minutes\n- The new password must have at least 12 characters, one digit and one symbol\n- The link can be used only once\n- After 5 requests in one hour, further requests are refused' },
  e2e: { langage: 'auto', code: '<form data-testid="login-form">\n  <label for="email">E-mail</label>\n  <input id="email" name="email" type="email" required>\n  <label for="password">Password</label>\n  <input id="password" name="password" type="password" required>\n  <button type="submit" data-testid="login-submit">Sign in</button>\n  <p role="alert" data-testid="login-error" hidden>Invalid e-mail or password</p>\n</form>\n\nJourney: a user signs in, lands on /dashboard and sees "Hello <name>".' },
  'tests-api': { langage: 'java', code: '@RestController\n@RequestMapping("/api/orders")\npublic class OrderController {\n    @GetMapping("/{id}")\n    public OrderDto get(@PathVariable Long id) { return service.find(id); }\n\n    @PostMapping\n    @ResponseStatus(HttpStatus.CREATED)\n    public OrderDto create(@Valid @RequestBody CreateOrder req) { return service.create(req); }\n\n    @DeleteMapping("/{id}")\n    @PreAuthorize("hasRole(\'ADMIN\')")\n    public void delete(@PathVariable Long id) { service.delete(id); }\n}' },
  'rapport-bug': { langage: 'auto', code: 'checkout broken on mobile?? when I apply the promo code SUMMER10 the total goes to 0 and I can pay 0€. happens on iphone safari, not sure on android. console says: TypeError: Cannot read properties of undefined (reading \'rate\') at applyDiscount (cart.js:118)' },
  'analyse-logs': { langage: 'auto', code: '2026-10-20 14:02:11 ERROR [http-nio-8080-exec-4] o.a.c.c.C.[dispatcherServlet] Servlet.service() threw exception\njava.lang.NullPointerException: Cannot invoke "com.shop.Customer.getAddress()" because "customer" is null\n    at com.shop.billing.InvoiceService.build(InvoiceService.java:57)\n    at com.shop.billing.InvoiceController.create(InvoiceController.java:31)\n    at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)' },
  'donnees-test': { langage: 'sql', code: 'CREATE TABLE customer (\n  id BIGINT PRIMARY KEY,\n  full_name VARCHAR(120) NOT NULL,\n  email VARCHAR(255) NOT NULL UNIQUE,\n  birth_date DATE,\n  country CHAR(2) NOT NULL,\n  credit_limit DECIMAL(10,2) CHECK (credit_limit >= 0)\n);' },
  'revue-tests': { langage: 'javascript', code: "describe('cart', () => {\n  it('works', async () => {\n    const cart = new Cart();\n    cart.add({ id: 1, price: 10 }, 2);\n    await new Promise(r => setTimeout(r, 1000));\n    expect(cart.total()).toBeTruthy();\n  });\n\n  it('remove', () => {\n    cart.remove(1);\n  });\n});" },
};

/** Boîte à outils IA : tout ce qui accélère le travail du développeur et du testeur (QA), au-delà de la revue. */
@Component({
  selector: 'app-outils',
  standalone: true,
  imports: [FormsModule, RouterLink, CodeEditorComponent, IconeComponent, ReponseIaComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="barre">
      <div class="fil"><a routerLink="/revues/nouvelle">RevIA</a> / <b>{{ l.t('Outils IA', 'AI tools') }}</b></div>
      @if (!outil().texteLibre) {
        <select class="input petit-select" [ngModel]="langage()" (ngModelChange)="langage.set($event)" [attr.aria-label]="l.t('Langage du code', 'Code language')">
          @for (lg of langages; track lg.valeur) { <option [value]="lg.valeur">{{ lg.icone }}  {{ lg.valeur === 'auto' ? l.t('Détection auto', 'Auto-detect') : lg.libelle }}</option> }
        </select>
      }
      @if (outil().id === 'conversion') {
        <select class="input petit-select" [ngModel]="cible()" (ngModelChange)="cible.set($event)" [attr.aria-label]="l.t('Langage cible', 'Target language')">
          @for (lg of cibles; track lg.valeur) { <option [value]="lg.valeur">{{ l.t('Vers', 'To') }} {{ lg.libelle }}</option> }
        </select>
      }
      @if (outil().cadres; as cadres) {
        <select class="input petit-select" [ngModel]="cadre()" (ngModelChange)="cadre.set($event)" [attr.aria-label]="l.t('Cadre de test', 'Test framework')">
          @for (c of cadres; track c) { <option [value]="c">{{ c }}</option> }
        </select>
      }
      <button class="btn" type="button" (click)="exemple()"><app-icone nom="ampoule" />{{ l.t('Exemple', 'Sample') }}</button>
      <button class="btn primaire" type="button" (click)="generer()" [disabled]="attente() || !code().trim()">
        @if (attente()) { <span class="spinner"></span> {{ l.t('Génération…', 'Generating…') }} } @else { <app-icone nom="etoile" />{{ textes().bouton }} }
      </button>
    </header>

    <div class="page">
      @for (g of groupes; track g.id) {
        <h2 class="groupe">{{ l.t(g.fr, g.en) }}</h2>
        <div class="outils" role="tablist" [attr.aria-label]="l.t(g.fr, g.en)">
          @for (o of g.outils; track o.id) {
            <button type="button" role="tab" class="outil" [class.actif]="o.id === outil().id" [attr.aria-selected]="o.id === outil().id"
                    (click)="choisir(o)">
              <span class="ico">{{ o.icone }}</span>
              <span><b>{{ l.t(o.fr, o.en).titre }}</b><small>{{ l.t(o.fr, o.en).description }}</small></span>
            </button>
          }
        </div>
      }

      <div class="grille">
        <section class="editeur-carte" [attr.aria-label]="l.t('Entrée', 'Input')">
          <div class="tete"><span>{{ textes().placeholder }}</span><span class="meta">{{ nbLignes() }} / 500 {{ l.t('lignes', 'lines') }}</span></div>
          <div class="zone">
            <app-code-editor [code]="code()" (codeChange)="code.set($event)" [langage]="outil().texteLibre ? 'plaintext' : langage()" />
          </div>
        </section>

        <section class="carte resultat" [attr.aria-label]="l.t('Résultat', 'Result')" aria-live="polite">
          @if (attente() && !texte()) {
            <div class="vide"><span class="spinner sombre grand"></span><b>{{ moteur() }} {{ l.t('travaille…', 'is working…') }}</b>
              <span>{{ l.t('Masquage des secrets, puis génération.', 'Masking secrets, then generating.') }}</span></div>
          } @else if (attente()) {
            <div class="entete"><span class="badge langage">{{ outil().icone }} {{ textes().titre }}</span><span class="infos">{{ moteur() }} {{ l.t('écrit…', 'is writing…') }}</span></div>
            <app-reponse-ia [contenu]="texte()" [actions]="false" />
          } @else if (erreur()) {
            <div class="erreur" role="alert"><b>{{ erreur() }}</b>
              <button class="btn petit" type="button" (click)="generer()">{{ l.t('Réessayer', 'Retry') }}</button></div>
          } @else if (reponse()) { @if (reponse(); as r) {
            <div class="entete">
              <span class="badge langage">{{ outil().icone }} {{ textes().titre }}</span>
              <span class="infos">{{ r.modele }} · {{ duree(r.dureeMs) }} s
                @if (r.nbMasquages) { · 🔒 {{ r.nbMasquages }} {{ l.t('secret(s) masqué(s)', 'secret(s) masked') }} }</span>
            </div>
            <app-reponse-ia [contenu]="r.contenu" />
          } } @else {
            <div class="vide">
              <span class="grand">{{ outil().icone }}</span>
              <b>{{ textes().titre }}</b>
              <span>{{ textes().description }}</span>
              <span>{{ l.t('Collez votre texte à gauche (ou cliquez sur « Exemple »), puis', 'Paste your input on the left (or click "Sample"), then') }} « {{ textes().bouton }} ».</span>
            </div>
          }
        </section>
      </div>
    </div>
  `,
  styles: [`
    .petit-select { height: 34px; font-size: 13px; padding: 0 8px; max-width: 220px; }
    .page { max-width: none; }
    .groupe { font-size: 12px; text-transform: uppercase; letter-spacing: .06em; color: var(--texte-3); margin: 0 0 8px; font-weight: 700; }
    .outils { display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 10px; margin-bottom: 16px; }
    .outil {
      display: flex; gap: 10px; align-items: flex-start; text-align: left; padding: 12px; border-radius: var(--rayon);
      border: 1px solid var(--bord); background: var(--surface); cursor: pointer; font: 13px var(--police); color: var(--texte);
      box-shadow: var(--ombre); transition: border-color .15s, background .15s;
    }
    .outil:hover { border-color: #c7cbf0; }
    .outil.actif { border-color: var(--primaire); background: var(--primaire-pale); box-shadow: 0 0 0 1px var(--primaire) inset; }
    .ico { font-size: 20px; line-height: 1; }
    .outil b { display: block; font-size: 13px; margin-bottom: 3px; }
    .outil small { display: block; color: var(--texte-2); font-size: 11.5px; line-height: 1.35; }
    .grille { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 18px; align-items: start; }
    .editeur-carte {
      background: #111427; border-radius: var(--rayon); overflow: hidden; border: 1px solid #1f2444;
      display: flex; flex-direction: column; height: calc(100vh - 250px); min-height: 420px;
    }
    .tete { display: flex; justify-content: space-between; padding: 9px 12px; border-bottom: 1px solid #1f2444; color: #a7addb; font-size: 12px; gap: 10px; }
    .meta { color: #7d84ad; white-space: nowrap; }
    .zone { flex: 1; min-height: 0; }
    .resultat { padding: 18px; min-height: 420px; max-height: calc(100vh - 250px); overflow-y: auto; }
    .entete { display: flex; justify-content: space-between; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
    .infos { font-size: 12px; color: var(--texte-3); }
    .vide { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; text-align: center; color: var(--texte-2); min-height: 380px; }
    .vide b { color: var(--texte); font-size: 15px; }
    .grand { font-size: 34px; }
    .erreur { background: var(--critique-pale); color: var(--critique); border-radius: 10px; padding: 14px; display: flex; flex-direction: column; gap: 10px; align-items: flex-start; }
    @media (max-width: 900px) {
      .outils { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      .grille { grid-template-columns: 1fr; }
      .editeur-carte { height: 360px; min-height: 0; }
    }
  `],
})
export class OutilsComponent implements OnInit {
  private readonly flux = inject(FluxIaService);
  private readonly transfert = inject(TransfertCodeService);
  private readonly api = inject(ApiService);
  readonly l = inject(LangueService);

  readonly outils = OUTILS;
  readonly groupes = [
    { id: 'dev', fr: 'Développement', en: 'Development', outils: OUTILS.filter((o) => o.groupe === 'dev') },
    { id: 'qa', fr: 'QA & tests', en: 'QA & testing', outils: OUTILS.filter((o) => o.groupe === 'qa') },
  ];
  readonly langages = LANGAGES;
  readonly cibles = LANGAGES.filter((l) => l.valeur !== 'auto');
  readonly outil = signal<Outil>(OUTILS[0]);
  readonly textes = computed(() => this.l.t(this.outil().fr, this.outil().en));
  readonly code = signal('');
  readonly langage = signal('auto');
  readonly cible = signal('python');
  readonly cadre = signal('');
  readonly attente = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly reponse = signal<AssistantReponse | null>(null);
  readonly texte = signal('');
  /** Nom du modèle configuré côté serveur (et non une étiquette figée). */
  readonly moteur = signal('RevIA');
  readonly nbLignes = computed(() => (this.code() ? this.code().split('\n').length : 0));

  ngOnInit(): void {
    const recu = this.transfert.prendre();
    if (recu) {
      this.code.set(recu.code);
      this.langage.set(recu.langage);
    }
    this.api.systeme().subscribe({ next: (s) => s.modele && this.moteur.set(s.modele), error: () => {} });
  }

  choisir(o: Outil): void {
    if (o.id !== this.outil().id) {
      this.outil.set(o);
      this.cadre.set(o.cadres?.[0] ?? '');
      this.reponse.set(null);
      this.erreur.set(null);
      if (o.id === 'conversion' && this.cible() === this.langage()) {
        this.cible.set(this.langage() === 'python' ? 'java' : 'python');
      }
    }
  }

  exemple(): void {
    const e = EXEMPLES[this.outil().id];
    this.code.set(e.code);
    this.langage.set(e.langage);
    this.reponse.set(null);
    this.erreur.set(null);
  }

  /** Langage cible envoyé à l'API : langage de conversion, ou cadre de test choisi (sauf valeur par défaut « Auto »). */
  private cibleEnvoyee(): string | undefined {
    const o = this.outil();
    if (o.id === 'conversion') {
      return this.cibles.find((l) => l.valeur === this.cible())?.libelle;
    }
    const c = this.cadre();
    return c && c !== 'Auto' && c !== 'Markdown + Gherkin' ? c : undefined;
  }

  generer(): void {
    if (!this.code().trim() || this.attente()) {
      return;
    }
    this.attente.set(true);
    this.erreur.set(null);
    this.reponse.set(null);
    this.texte.set('');
    const langage = this.outil().texteLibre ? 'auto' : this.langage();
    this.flux.outil(this.outil().id, { code: this.code(), langage, cible: this.cibleEnvoyee() }).subscribe((ev) => {
      if (ev.type === 'morceau') {
        this.texte.update((t) => t + ev.texte);
      } else if (ev.type === 'fin') {
        this.reponse.set({ ...ev.info, contenu: this.texte().trim() });
        this.attente.set(false);
      } else {
        this.erreur.set(ev.message);
        this.attente.set(false);
      }
    });
  }

  duree(ms: number): string {
    const s = (ms / 1000).toFixed(1);
    return this.l.langue() === 'fr' ? s.replace('.', ',') : s;
  }
}
