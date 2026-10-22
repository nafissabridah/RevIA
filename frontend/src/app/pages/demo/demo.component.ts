import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { LangueService } from '../../core/langue.service';
import { MARQUE } from '../../core/marque';
import { COMMENTAIRE_PR, RUN_DEMO, SOURCE_PANIER, TESTS_PANIER, nombreDeTests, scoreMutation, scoreRevue } from './demo-donnees';
import { lireMarkdownPr } from './markdown-pr';

/** Ligne du code source qui contredit sa documentation (« à partir de 50 € » mais « > 50 »). */
const LIGNE_BUG = 'if code == "LIVRAISON" and total > 50:';
const LIGNE_DOC = 'à partir de 50 €';
const LIGNES_TESTS_BORNE = ['"LIVRAISON"'];

export interface LigneCode { n: number; texte: string; marque: boolean }

/** Découpe un code en lignes numérotées, en marquant celles qui contiennent l'un des extraits donnés. */
export function lignesCode(code: string, marqueurs: string[]): LigneCode[] {
  return code.replace(/\n$/, '').split('\n').map((texte, i) => ({ n: i + 1, texte, marque: marqueurs.some((m) => texte.includes(m)) }));
}

const NB_TESTS = nombreDeTests(TESTS_PANIER);
const MUTATION = scoreMutation(COMMENTAIRE_PR);
const REVUE = scoreRevue(COMMENTAIRE_PR, RUN_DEMO.fichierSource);

const T = {
  fr: {
    titreOnglet: 'Démo : des tests qui prouvent qu’ils marchent · RevIA',
    description: 'Un vrai run RevIA sur une pull request Python : revue de code, tests générés et exécutés dans la CI, 100 % de score de mutation, avec un modèle IA gratuit. Fichiers et commentaire de PR tels quels.',
    accueil: 'Accueil', connexion: 'Se connecter', compte: 'Créer un compte', ouvrir: 'Ouvrir l’application',
    badge: 'Démo · un vrai run du 22/10/2026, rien de simulé',
    titre1: 'Des tests qui prouvent', titre2: 'qu’ils marchent.',
    sous: 'Une pull request modifie un petit module Python de panier d’achat. RevIA relit le code, écrit les tests, les fait tourner dans la CI, injecte des bugs pour vérifier qu’ils les attrapent, puis poste le résultat sur la PR. Et il a trouvé un bug que la documentation trahissait. Voici exactement ce qui s’est passé.',
    chiffres: [
      [`${NB_TESTS}`, 'tests générés, exécutés et verts'],
      [`${MUTATION ?? '–'} %`, 'score de mutation : chaque bug injecté a été attrapé'],
      ['0 €', 'modèle IA gratuit (Google Gemini Flash)'],
    ],
    voirFichiers: 'Voir les fichiers du run', etapesTitre: 'Ce que fait RevIA, étape par étape',
    etapes: [
      ['Pull request ouverte', 'Le script RevIA démarre dans le job CI de la PR et prend en charge panier.py.'],
      ['Revue de code', `Bugs, sécurité OWASP, bonnes pratiques : score ${REVUE ?? '–'}/100, aucun problème critique, quality gate franchie.`],
      ['Tests générés', `L’IA écrit un fichier unittest à partir du code et de sa documentation : ${NB_TESTS} tests gardés, plus un test de ce que promet la documentation.`],
      ['Exécutés dans VOTRE CI', 'Les tests tournent sur votre runner, pas chez nous : votre code n’est jamais exécuté sur les serveurs de RevIA. Les secrets sont masqués avant tout appel à l’IA.'],
      ['Réparés à partir de la vraie sortie', 'Si un test échoue, RevIA renvoie la vraie sortie d’erreur à l’IA et corrige le test, sur plusieurs tours si besoin. Jamais un test qui n’a pas tourné.'],
      ['Bugs injectés (mutation testing)', 'RevIA modifie volontairement votre code (un * devient /, un > devient >=, un == devient !=…) et relance les tests : chaque bug doit faire échouer au moins un test.'],
      ['Seuls les tests utiles sont gardés', `Un fichier vert qui n’attrape aucun bug injecté est rejeté et supprimé : il ne prouve rien. Ici, ${MUTATION ?? '–'} % des mutants ont été attrapés.`],
      ['Commentaire sur la PR', 'Score, quality gate, tests prouvés, score de mutation et contradictions doc / code observées arrivent directement dans la pull request.'],
    ],
    mutantTitre: 'Un mutant, concrètement', illustratif: 'Exemple illustratif',
    mutantOrig: 'Code d’origine', mutantMut: 'Mutant injecté',
    mutantVerdict: 'test_total_ht_nominal attend 36.5 et obtiendrait 6.83 : le test passe au rouge, le mutant est « tué ».',
    mutantNote: 'Construit avec un vrai opérateur du script CI (* → /) sur une vraie ligne de panier.py ; le détail mutant par mutant de ce run n’est pas reproduit ici.',
    fichiersTitre: 'Les fichiers du run, tels quels',
    fichiersSous: 'Copiés à l’identique : aucune IA n’est appelée quand vous ouvrez cette page.',
    onglets: ['Code testé', 'Tests générés', 'Commentaire de PR'],
    bugTitre: '🐞 Bug probable — à confirmer',
    bugTexte: 'La documentation dit « à partir de 50 € » (50 € inclus), le code teste total > 50 : un panier d’exactement 50 € n’a pas la remise. Lequel a raison ? Seul le métier peut trancher.',
    bugTests: 'Comment RevIA l’a trouvé : en plus des tests gardés, l’IA a écrit un test du comportement DOCUMENTÉ (un panier de 50 € avec LIVRAISON doit passer à 45 €). RevIA l’a exécuté sur le runner de la CI : il a échoué sur une assertion (AssertionError: 50.0 != 45.0). La contradiction est donc observée, pas supposée, et signalée sur la PR.',
    bugRegles: 'Si ce test de la documentation passe, l’IA s’était trompée : rien n’est signalé. S’il est cassé (syntaxe, import…), il ne prouve rien : rien n’est signalé non plus. Dans tous les cas il est supprimé, jamais commité.',
    bugRevia: 'Les tests gardés ne figent volontairement pas cette borne (ils testent 60 €, pas 50 €) : à vous de trancher, corriger le code (>= 50) ou la documentation.',
    testsNote: '60 € testé, pas 50 € : la borne contestée n’est pas figée dans les tests gardés. Le test de la documentation (50 € → 45 €), rouge, a été supprimé.',
    prNote: 'Rendu du Markdown posté par le script CI. Le lien « open » pointait vers l’instance RevIA locale utilisée pour ce run.',
    lienLocal: 'Lien vers l’instance locale du run',
    honneteTitre: 'Ce qu’il faut savoir sur ce run',
    honnete: [
      'Un vrai run, pas un scénario : le code, les tests et le commentaire sont copiés tels quels.',
      'Gemini Flash était surchargé (erreur 503) : RevIA est passé automatiquement sur Gemini Flash-Lite, gratuit lui aussi.',
      `L’IA n’est pas déterministe : un run antérieur sur le même fichier avait obtenu ${RUN_DEMO.scoreRunPrecedent} % et demandé des tours de réparation. Le score de mutation sert justement à le mesurer.`,
      'Aucun critère d’acceptation n’était fourni avec cette PR : la vérification de couverture a été sautée.',
      `L’IA avait indiqué « ligne ${RUN_DEMO.ligneIndiqueeParIa} » : RevIA ne lui fait pas confiance et retrouve lui-même la vraie ligne (25). Le commentaire ci-dessus montre ce format corrigé.`,
      `${MUTATION ?? '–'} % des mutants attrapés et pourtant un bug probable : les deux contrôles sont complémentaires, aucun ne garantit zéro bug.`,
    ],
    pourQuiTitre: 'Pour qui ?',
    pourQui: [
      ['👩‍💻', 'Développeurs', 'Une revue et des tests qui ont vraiment tourné, à chaque PR, sans quitter votre CI. Vous gardez les fichiers générés.'],
      ['🧪', 'QA / testeurs', 'Tests prouvés par mutation, user story → cas de test et Gherkin, tests E2E et d’API, rapports de bug.'],
      ['📐', 'Business analysts', 'Couverture des critères d’acceptation par la PR, flowchart → cas de test, revue des exigences : ambiguïtés et contradictions repérées avant le code.'],
    ],
    commencer: 'Commencer gratuitement',
    ctaTitre: 'Essayez sur votre propre code',
    ctaSous: 'Compte gratuit, installation en CI en 2 minutes, ou un pilote accompagné avec votre équipe.',
    cta: ['Créer un compte gratuit', 'Installer dans ma CI', 'Demander un pilote'],
    pied: 'Démo construite à partir d’un run réel du script CI RevIA.',
  },
  en: {
    titreOnglet: 'Demo: tests that prove they work · RevIA',
    description: 'A real RevIA run on a Python pull request: code review, tests generated and executed in CI, 100% mutation score, with a free AI model. Files and PR comment shown as is.',
    accueil: 'Home', connexion: 'Sign in', compte: 'Create an account', ouvrir: 'Open the app',
    badge: 'Demo · a real run from 2026-10-22, nothing simulated',
    titre1: 'Tests that prove', titre2: 'they work.',
    sous: 'A pull request changes a small Python shopping-cart module. RevIA reviews the code, writes the tests, runs them in CI, injects bugs to check that the tests catch them, then posts the result on the PR. And it found a bug the documentation gave away. Here is exactly what happened.',
    chiffres: [
      [`${NB_TESTS}`, 'tests generated, executed and green'],
      [`${MUTATION ?? '–'}%`, 'mutation score: every injected bug was caught'],
      ['€0', 'free AI model (Google Gemini Flash)'],
    ],
    voirFichiers: 'See the run files', etapesTitre: 'What RevIA does, step by step',
    etapes: [
      ['Pull request opened', 'The RevIA script starts in the PR’s CI job and picks up panier.py.'],
      ['Code review', `Bugs, OWASP security, good practices: score ${REVUE ?? '–'}/100, no critical issue, quality gate passed.`],
      ['Tests generated', `The AI writes a unittest file from the code and its documentation: ${NB_TESTS} kept tests, plus one test of what the documentation promises.`],
      ['Run in YOUR CI', 'Tests run on your runner, not ours: your code is never executed on RevIA servers. Secrets are masked before any AI call.'],
      ['Repaired from the real output', 'When a test fails, RevIA sends the real error output back to the AI and fixes the test, over several rounds if needed. Never a test that has not run.'],
      ['Bugs injected (mutation testing)', 'RevIA deliberately changes your code (a * becomes /, a > becomes >=, a == becomes !=…) and re-runs the tests: every bug must make at least one test fail.'],
      ['Only tests that catch bugs are kept', `A green file that catches no injected bug is rejected and deleted: it proves nothing. Here, ${MUTATION ?? '–'}% of mutants were caught.`],
      ['PR comment', 'Score, quality gate, proven tests, mutation score and observed doc / code contradictions land right in the pull request.'],
    ],
    mutantTitre: 'One mutant, concretely', illustratif: 'Illustrative example',
    mutantOrig: 'Original code', mutantMut: 'Injected mutant',
    mutantVerdict: 'test_total_ht_nominal expects 36.5 and would get 6.83: the test turns red, the mutant is “killed”.',
    mutantNote: 'Built with a real operator of the CI script (* → /) on a real line of panier.py; this run’s per-mutant details are not reproduced here.',
    fichiersTitre: 'The run files, as is',
    fichiersSous: 'Copied verbatim: no AI is called when you open this page.',
    onglets: ['Code under test', 'Generated tests', 'PR comment'],
    bugTitre: '🐞 Probable bug — to be confirmed',
    bugTexte: 'The docstring says “à partir de 50 €” (from €50, inclusive), the code checks total > 50: a cart of exactly €50 gets no discount. Which one is right? Only the business can decide.',
    bugTests: 'How RevIA found it: besides the kept tests, the AI wrote a test of the DOCUMENTED behaviour (a €50 cart with LIVRAISON must become €45). RevIA ran it on the CI runner: it failed on an assertion (AssertionError: 50.0 != 45.0). The contradiction is observed, not assumed, and reported on the PR.',
    bugRegles: 'If that documentation test passes, the AI was wrong: nothing is reported. If it is broken (syntax, import…), it proves nothing: nothing is reported either. Either way it is deleted, never committed.',
    bugRevia: 'The kept tests deliberately do not lock in this bound (they test €60, not €50): you decide whether to fix the code (>= 50) or the documentation.',
    testsNote: '€60 tested, not €50: the disputed bound is not locked into the kept tests. The documentation test (€50 → €45), red, was deleted.',
    prNote: 'Rendering of the Markdown posted by the CI script. The “open” link pointed to the local RevIA instance used for this run.',
    lienLocal: 'Link to the run’s local instance',
    honneteTitre: 'What you should know about this run',
    honnete: [
      'A real run, not a script: the code, tests and comment are copied as is.',
      'Gemini Flash was overloaded (error 503): RevIA switched automatically to Gemini Flash-Lite, also free.',
      `AI is not deterministic: an earlier run on the same file scored ${RUN_DEMO.scoreRunPrecedent}% and needed repair rounds. That is exactly what the mutation score measures.`,
      'No acceptance criteria were provided with this PR: the coverage check was skipped.',
      `The AI said “line ${RUN_DEMO.ligneIndiqueeParIa}”: RevIA does not trust it and locates the real line itself (25). The comment above shows this corrected format.`,
      `${MUTATION ?? '–'}% of mutants caught, and still a probable bug: the two checks complement each other, neither guarantees zero bugs.`,
    ],
    pourQuiTitre: 'Who is it for?',
    pourQui: [
      ['👩‍💻', 'Developers', 'A review and tests that really ran, on every PR, without leaving your CI. You keep the generated files.'],
      ['🧪', 'QA / testers', 'Mutation-proven tests, user story → test cases and Gherkin, E2E and API tests, bug reports.'],
      ['📐', 'Business analysts', 'Acceptance-criteria coverage of the PR, flowchart → test cases, requirement review: ambiguities and contradictions caught before the code.'],
    ],
    commencer: 'Start for free',
    ctaTitre: 'Try it on your own code',
    ctaSous: 'Free account, CI setup in 2 minutes, or a guided pilot with your team.',
    cta: ['Create a free account', 'Install in my CI', 'Ask for a pilot'],
    pied: 'Demo built from a real run of the RevIA CI script.',
  },
};

/** Page publique /demo : un vrai run RevIA (revue, tests prouvés, mutation, commentaire de PR), statique, FR / EN. */
@Component({
  selector: 'app-demo',
  standalone: true,
  imports: [RouterLink, NgTemplateOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ng-template #segs let-contenu>
      @for (s of contenu; track $index) {
        @switch (s.type) {
          @case ('gras') { <strong>{{ s.texte }}</strong> }
          @case ('code') { <code>{{ s.texte }}</code> }
          @case ('lien') { <span class="lien-local" [title]="t().lienLocal + ' : ' + s.url">{{ s.texte }}</span> }
          @default {<ng-container>{{ s.texte }}</ng-container>}
        }
      }
    </ng-template>

    <header class="nav">
      <a class="logo" routerLink="/"><span class="carre">R</span>Rev<span class="ia">IA</span></a>
      <nav><a routerLink="/">{{ t().accueil }}</a></nav>
      <div class="actions">
        <button class="langue" type="button" (click)="langues.basculer()" aria-label="Language">{{ langue() === 'fr' ? 'EN' : 'FR' }}</button>
        @if (connecte()) {
          <a class="btn primaire" routerLink="/revues/nouvelle">{{ t().ouvrir }}</a>
        } @else {
          <a class="lien" routerLink="/connexion">{{ t().connexion }}</a>
          <a class="btn primaire" routerLink="/inscription">{{ t().compte }}</a>
        }
      </div>
    </header>

    <section class="hero">
      <div class="badge-hero">{{ t().badge }}</div>
      <h1>{{ t().titre1 }}<br /><span>{{ t().titre2 }}</span></h1>
      <p class="sous">{{ t().sous }}</p>
      <div class="chiffres">
        @for (c of t().chiffres; track $index) {
          <div class="chiffre" data-test="chiffre"><b>{{ c[0] }}</b><span>{{ c[1] }}</span></div>
        }
      </div>
      <div class="ctas">
        <a class="btn primaire grand" routerLink="/inscription">{{ t().commencer }}</a>
        <a class="btn fantome grand" href="#fichiers" (click)="allerA($event, 'fichiers')">{{ t().voirFichiers }}</a>
      </div>
    </section>

    <section class="bloc" id="etapes">
      <h2>{{ t().etapesTitre }}</h2>
      <ol class="frise">
        @for (e of t().etapes; track $index) {
          <li><span class="num">{{ $index + 1 }}</span><div><h3>{{ e[0] }}</h3><p>{{ e[1] }}</p></div></li>
        }
      </ol>

      <div class="mutant">
        <div class="mutant-tete"><h3>{{ t().mutantTitre }}</h3><span class="etiquette">{{ t().illustratif }}</span></div>
        <div class="mutant-diff">
          <div><span class="leg">{{ t().mutantOrig }} · panier.py, {{ ligneMutant() }}</span><pre class="orig">total += prix <b>*</b> quantite</pre></div>
          <div><span class="leg">{{ t().mutantMut }}</span><pre class="mut">total += prix <b>/</b> quantite</pre></div>
        </div>
        <p class="verdict">✅ {{ t().mutantVerdict }}</p>
        <p class="note">{{ t().mutantNote }}</p>
      </div>
    </section>

    <section class="bloc" id="fichiers">
      <h2>{{ t().fichiersTitre }}</h2>
      <p class="centre">{{ t().fichiersSous }}</p>
      <div class="onglets" role="tablist">
        @for (o of t().onglets; track $index) {
          <button type="button" role="tab" [attr.aria-selected]="onglet() === $index" [class.actif]="onglet() === $index"
                  (click)="onglet.set($index)">{{ o }}</button>
        }
      </div>

      @switch (onglet()) {
        @case (0) {
          <div class="panneau-grille">
            <div class="code" role="tabpanel">
              <div class="code-tete">{{ run.fichierSource }}</div>
              <pre>@for (l of source; track l.n) {<span class="ligne" [class.marque]="l.marque"><span class="n">{{ l.n }}</span>{{ l.texte }}</span>}</pre>
            </div>
            <aside class="bulle" data-test="bug">
              <h3>{{ t().bugTitre }}</h3>
              <p>{{ t().bugTexte }}</p>
              <p>{{ t().bugTests }}</p>
              <p class="note">{{ t().bugRegles }}</p>
              <p>{{ t().bugRevia }}</p>
            </aside>
          </div>
        }
        @case (1) {
          <div class="code" role="tabpanel">
            <div class="code-tete">{{ run.fichierTests }}<span class="vert">✅ {{ nbTests }} / {{ nbTests }}</span></div>
            <pre>@for (l of tests; track l.n) {<span class="ligne" [class.marque]="l.marque"><span class="n">{{ l.n }}</span>{{ l.texte }}</span>}</pre>
            <p class="note pied-code">{{ t().testsNote }}</p>
          </div>
        }
        @case (2) {
          <div class="pr" role="tabpanel" data-test="commentaire-pr">
            @for (b of commentaire; track $index) {
              @switch (b.type) {
                @case ('titre') { <h3 class="pr-titre"><ng-container *ngTemplateOutlet="segs; context: { $implicit: b.contenu }" /></h3> }
                @case ('liste') {
                  <ul class="pr-liste">@for (li of b.elements; track $index) { <li><ng-container *ngTemplateOutlet="segs; context: { $implicit: li }" /></li> }</ul>
                }
                @case ('note') { <p class="pr-note"><ng-container *ngTemplateOutlet="segs; context: { $implicit: b.contenu }" /></p> }
                @case ('tableau') {
                  <div class="defile">
                    <table>
                      <thead><tr>
                        @for (h of b.entetes; track $index) {
                          <th [style.text-align]="b.alignements[$index]"><ng-container *ngTemplateOutlet="segs; context: { $implicit: h }" /></th>
                        }
                      </tr></thead>
                      <tbody>
                        @for (r of b.lignes; track $index) {
                          <tr>
                            @for (c of r; track $index) {
                              <td [style.text-align]="b.alignements[$index]"><ng-container *ngTemplateOutlet="segs; context: { $implicit: c }" /></td>
                            }
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>
                }
                @default { <p><ng-container *ngTemplateOutlet="segs; context: { $implicit: b.contenu }" /></p> }
              }
            }
          </div>
          <p class="note centre-note">{{ t().prNote }}</p>
        }
      }
    </section>

    <section class="bloc honnete">
      <h2>{{ t().honneteTitre }}</h2>
      <ul>@for (h of t().honnete; track $index) { <li>{{ h }}</li> }</ul>
    </section>

    <section class="bloc" id="pour-qui">
      <h2>{{ t().pourQuiTitre }}</h2>
      <div class="grille">
        @for (p of t().pourQui; track p[1]) {
          <div class="carte-f"><span class="ico">{{ p[0] }}</span><h3>{{ p[1] }}</h3><p>{{ p[2] }}</p>
            <a class="lien-carte" routerLink="/inscription">{{ t().commencer }} →</a></div>
        }
      </div>
    </section>

    <section class="bloc final">
      <h2>{{ t().ctaTitre }}</h2>
      <p class="centre">{{ t().ctaSous }}</p>
      <div class="ctas">
        <a class="btn primaire grand" routerLink="/inscription">{{ t().cta[0] }}</a>
        <a class="btn fantome grand" routerLink="/" fragment="ci">{{ t().cta[1] }}</a>
        <a class="btn fantome grand" [href]="'mailto:' + marque.contact + '?subject=RevIA%20pilot'">{{ t().cta[2] }}</a>
      </div>
    </section>

    <footer>
      <span>© {{ annee }} RevIA</span><span>{{ t().pied }}</span>
      <a [href]="'mailto:' + marque.contact">{{ marque.contact }}</a>
      <a routerLink="/legal/cgu">CGU</a><a routerLink="/legal/confidentialite">{{ langue() === 'fr' ? 'Confidentialité' : 'Privacy' }}</a>
    </footer>
  `,
  styles: [`
    :host { display: block; background: var(--nuit); color: #e8eaff; min-height: 100vh; overflow-x: hidden; }
    a { color: inherit; text-decoration: none; }
    .nav { position: sticky; top: 0; z-index: 10; display: flex; align-items: center; gap: 24px; padding: 14px 5vw;
           background: rgba(11, 14, 29, .85); backdrop-filter: blur(10px); border-bottom: 1px solid #1d2142; }
    .logo { display: flex; align-items: center; gap: 8px; font-weight: 800; font-size: 20px; }
    .carre { width: 30px; height: 30px; border-radius: 8px; background: linear-gradient(135deg, #6366f1, #8b5cf6); display: grid; place-items: center; font-size: 15px; }
    .ia { color: var(--accent-ia); margin-left: -8px; }
    nav { display: flex; gap: 22px; margin-left: 20px; color: #b9bedf; font-size: 14px; }
    nav a:hover, .lien:hover { color: #fff; }
    .actions { margin-left: auto; display: flex; align-items: center; gap: 14px; font-size: 14px; }
    .langue { background: transparent; border: 1px solid #2c3160; color: #c9cdf0; border-radius: 7px; padding: 5px 9px; cursor: pointer; font-weight: 700; font-size: 12px; }
    .btn.fantome { background: transparent; color: #e8eaff; border: 1px solid #3a3f73; }
    .btn.fantome:hover { background: #1b1f3d; }
    .btn.grand { height: 46px; padding: 0 22px; font-size: 15px; }
    .hero { text-align: center; padding: 80px 5vw 40px; background: radial-gradient(900px 420px at 50% 0%, #2a2a7a 0%, transparent 65%); }
    .badge-hero { display: inline-block; padding: 6px 14px; border-radius: 99px; border: 1px solid #3a3f73; color: var(--accent-ia); font-size: 12.5px; font-weight: 600; }
    h1 { font-size: clamp(36px, 6vw, 68px); line-height: 1.05; letter-spacing: -.035em; margin: 22px 0 18px; font-weight: 800; }
    h1 span { background: linear-gradient(90deg, #a5b4fc, #c084fc); -webkit-background-clip: text; background-clip: text; color: transparent; }
    .sous { max-width: 760px; margin: 0 auto; color: #b9bedf; font-size: 17px; line-height: 1.6; }
    .chiffres { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; max-width: 820px; margin: 34px auto 0; }
    .chiffre { background: #12162e; border: 1px solid #22274c; border-radius: 14px; padding: 18px 14px; display: flex; flex-direction: column; gap: 6px; }
    .chiffre b { font-size: 38px; font-weight: 800; letter-spacing: -.02em; color: #fff; }
    .chiffre span { color: #a9aed3; font-size: 13.5px; line-height: 1.4; }
    .ctas { display: flex; gap: 12px; justify-content: center; margin-top: 30px; flex-wrap: wrap; }
    .bloc { max-width: 1180px; margin: 0 auto; padding: 80px 5vw 0; }
    .bloc h2 { font-size: clamp(26px, 3.4vw, 38px); letter-spacing: -.02em; margin: 0 0 12px; text-align: center; }
    .centre { text-align: center; color: #9aa0c8; margin: 0 0 26px; }
    .frise { list-style: none; padding: 0; margin: 34px 0 0; display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; counter-reset: e; }
    .frise li { background: #12162e; border: 1px solid #22274c; border-radius: 14px; padding: 18px; display: flex; gap: 12px; align-items: flex-start; }
    .num { flex: none; width: 30px; height: 30px; border-radius: 50%; display: grid; place-items: center; font-weight: 800; font-size: 14px;
           background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #fff; }
    .frise h3 { margin: 3px 0 6px; font-size: 15.5px; } .frise p { margin: 0; color: #a9aed3; line-height: 1.5; font-size: 13.5px; }
    .mutant { margin-top: 22px; background: #10142a; border: 1px solid #2c3160; border-radius: 14px; padding: 20px; }
    .mutant-tete { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; } .mutant h3 { margin: 0; font-size: 17px; }
    .etiquette { font-size: 11.5px; font-weight: 700; text-transform: uppercase; letter-spacing: .06em; color: #fcd34d; border: 1px solid #6b5a1e; border-radius: 99px; padding: 3px 10px; }
    .mutant-diff { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 14px; }
    .leg { display: block; font-size: 12px; color: #9aa0c8; margin-bottom: 6px; }
    .mutant pre { margin: 0; padding: 10px 12px; border-radius: 9px; font: 13.5px/1.5 var(--mono); overflow-x: auto; }
    .orig { background: #182238; color: #c7d2fe; } .mut { background: #3a1d22; color: #fecaca; }
    .mutant pre b { background: rgba(255, 255, 255, .16); border-radius: 3px; padding: 0 3px; }
    .verdict { margin: 14px 0 6px; color: #e8eaff; font-size: 14px; line-height: 1.5; }
    .note { color: #8b91bb; font-size: 12.5px; line-height: 1.5; margin: 6px 0 0; }
    .onglets { display: flex; gap: 6px; justify-content: center; flex-wrap: wrap; margin-bottom: 16px; }
    .onglets button { background: transparent; border: 1px solid #2c3160; color: #c9cdf0; border-radius: 99px; padding: 8px 16px; cursor: pointer; font-size: 13.5px; font-weight: 600; }
    .onglets button.actif { background: #5b5bf6; border-color: #5b5bf6; color: #fff; }
    .panneau-grille { display: grid; grid-template-columns: 1.5fr 1fr; gap: 16px; align-items: start; }
    .code { background: #0f1328; border: 1px solid #262b52; border-radius: 14px; overflow: hidden; min-width: 0; }
    .code-tete { display: flex; justify-content: space-between; gap: 10px; padding: 10px 14px; border-bottom: 1px solid #262b52; font: 12.5px var(--mono); color: #9aa0c8; }
    .vert { color: #86efac; font-weight: 700; }
    .code pre { margin: 0; padding: 12px 0; font: 12.5px/1.6 var(--mono); color: #c7d2fe; overflow-x: auto; max-height: 560px; overflow-y: auto; }
    .ligne { display: block; padding: 0 14px 0 0; white-space: pre; }
    .ligne.marque { background: rgba(245, 158, 11, .16); box-shadow: inset 3px 0 0 #f59e0b; }
    .n { display: inline-block; width: 42px; padding-right: 12px; text-align: right; color: #4b5187; user-select: none; }
    .pied-code { padding: 0 14px 12px; }
    .bulle { background: #2a1f0d; border: 1px solid #6b4e16; border-radius: 14px; padding: 18px; color: #fde9c4; }
    .bulle h3 { margin: 0 0 8px; font-size: 16px; color: #fcd34d; } .bulle p { margin: 0 0 10px; line-height: 1.55; font-size: 14px; }
    .bulle .note { color: #d9c08f; margin: 0; }
    .pr { background: var(--surface); color: var(--texte); border-radius: 14px; padding: 20px 22px; max-width: 880px; margin: 0 auto; }
    .pr-titre { font-size: 18px; margin: 14px 0 10px; } .pr-titre:first-child { margin-top: 0; }
    .pr p { line-height: 1.55; margin: 0 0 12px; font-size: 14px; }
    .pr code { background: #eff1f7; border-radius: 5px; padding: 1px 6px; font-size: 12.5px; }
    .pr-liste { margin: 0 0 12px; padding-left: 20px; line-height: 1.55; font-size: 14px; }
    .pr-liste code { white-space: normal; }
    .pr-note { font-size: 12px !important; color: var(--texte-2); }
    .defile { overflow-x: auto; margin-bottom: 14px; }
    table { border-collapse: collapse; width: 100%; font-size: 13.5px; }
    th, td { border: 1px solid var(--bord); padding: 7px 10px; text-align: left; white-space: nowrap; }
    th { background: var(--surface-2); font-weight: 600; }
    .lien-local { color: var(--primaire); text-decoration: underline dotted; cursor: help; }
    .centre-note { text-align: center; }
    .honnete ul { max-width: 820px; margin: 24px auto 0; padding: 0; list-style: none; }
    .honnete li { padding: 12px 0 12px 28px; border-bottom: 1px solid #22274c; color: #c9cdf0; line-height: 1.55; position: relative; }
    .honnete li::before { content: '•'; position: absolute; left: 8px; color: var(--accent-ia); font-weight: 800; }
    .grille { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-top: 30px; }
    .carte-f { background: #12162e; border: 1px solid #22274c; border-radius: 14px; padding: 22px; display: flex; flex-direction: column; }
    .carte-f h3 { margin: 12px 0 6px; font-size: 17px; } .carte-f p { margin: 0 0 14px; color: #a9aed3; line-height: 1.55; font-size: 14px; flex: 1; }
    .ico { font-size: 26px; }
    .lien-carte { color: var(--accent-ia); font-weight: 600; font-size: 14px; } .lien-carte:hover { color: #fff; }
    .final { text-align: center; }
    footer { margin-top: 90px; padding: 28px 5vw; border-top: 1px solid #1d2142; display: flex; gap: 22px; flex-wrap: wrap; color: #8b91bb; font-size: 13px; }
    footer span:nth-child(2) { flex: 1; }
    @media (max-width: 960px) {
      nav, .actions .lien { display: none; }
      .frise { grid-template-columns: 1fr 1fr; }
      .panneau-grille, .grille { grid-template-columns: 1fr; }
    }
    @media (max-width: 640px) {
      .nav { gap: 12px; padding: 12px 16px; }
      .hero { padding: 56px 16px 30px; }
      .bloc { padding: 60px 16px 0; }
      .chiffres, .frise, .mutant-diff { grid-template-columns: 1fr; }
      .chiffre b { font-size: 32px; }
      .actions .btn { padding: 0 12px; font-size: 13px; }
      .btn.grand { width: 100%; justify-content: center; }
      footer { padding: 24px 16px; }
    }
  `],
})
export class DemoComponent {
  readonly langues = inject(LangueService);
  private readonly auth = inject(AuthService);
  private readonly meta = inject(Meta);
  private readonly titre = inject(Title);

  readonly marque = MARQUE;
  readonly run = RUN_DEMO;
  readonly annee = new Date().getFullYear();
  readonly langue = this.langues.langue;
  readonly t = computed(() => T[this.langue()]);
  readonly connecte = computed(() => this.auth.estConnecte());
  readonly onglet = signal(0);

  readonly nbTests = NB_TESTS;
  readonly source = lignesCode(SOURCE_PANIER, [LIGNE_BUG, LIGNE_DOC]);
  readonly tests = lignesCode(TESTS_PANIER, LIGNES_TESTS_BORNE);
  readonly commentaire = lireMarkdownPr(COMMENTAIRE_PR);
  private readonly numLigneMutant = SOURCE_PANIER.split('\n').findIndex((l) => l.includes('total += prix * quantite')) + 1;
  readonly ligneMutant = computed(() => (this.langue() === 'fr' ? 'ligne ' : 'line ') + this.numLigneMutant);

  constructor() {
    const descriptionAvant = this.meta.getTag('name="description"')?.content ?? null;
    effect(() => {
      const t = this.t();
      this.titre.setTitle(t.titreOnglet);
      this.meta.updateTag({ name: 'description', content: t.description });
    });
    // En quittant la page, on remet la description générique du site
    inject(DestroyRef).onDestroy(() => {
      if (descriptionAvant !== null) {
        this.meta.updateTag({ name: 'description', content: descriptionAvant });
      }
    });
  }

  /** Défilement vers une section sans changer de route (le <base href> casserait un simple #ancre). */
  allerA(ev: Event, id: string): void {
    ev.preventDefault();
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
}
