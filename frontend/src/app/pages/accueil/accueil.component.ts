import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { messageErreur } from '../../core/http';
import { LangueService } from '../../core/langue.service';
import { MARQUE } from '../../core/marque';
import { Plan, RevueDetail } from '../../core/models';
import { PaddleService } from '../../core/paddle.service';
import { ScoreRingComponent } from '../../shared/score-ring.component';


const EXEMPLE = `public class UserService {
    private static final String DB_PASSWORD = "admin123";

    public User find(Connection cn, String id) throws SQLException {
        Statement st = cn.createStatement();
        ResultSet rs = st.executeQuery("SELECT * FROM users WHERE id=" + id);
        if (rs.next()) return new User(rs.getString("name"));
        return null;
    }
}`;

const T = {
  fr: {
    nav: ['Fonctionnalités', 'CI / Pull requests', 'Tarifs', 'FAQ', 'Démo'], demo: 'Voir un vrai run : tests prouvés, 100 % de mutation →', connexion: 'Se connecter', essai: 'Essai gratuit',
    ouvrir: "Ouvrir l'application",
    badge: 'Revue de code IA · OWASP Top 10 · auto-hébergeable',
    titre1: 'Bloquez les failles', titre2: 'avant le merge.',
    sous: "RevIA relit chaque fichier comme un développeur senior : injections, secrets en dur, bugs, mauvaises pratiques. Il explique le risque, propose la correction et bloque la pull request sous votre seuil qualité.",
    cta1: 'Tester maintenant · sans compte', cta2: 'Créer un compte gratuit',
    preuves: ['12 langages', 'Secrets masqués avant l’IA', 'Export SARIF (GitHub, GitLab, Azure)', 'Votre LLM ou 100 % local'],
    bacTitre: 'Essayez sur votre code', bacSous: 'Collez jusqu’à 150 lignes. Rien n’est enregistré.',
    analyser: 'Analyser', analyse: 'Analyse en cours…', exemple: 'Recharger l’exemple',
    vide: 'Aucun problème détecté. 🎉', ligne: 'ligne', correction: 'Correction proposée', avant: 'Avant', apres: 'Après',
    inscrireApres: 'Gardez l’historique, le tableau de bord et branchez votre CI :', inscrire: 'Créer un compte gratuit',
    fTitre: 'Revue de code, QA et tests : tout au même endroit',
    f: [
      ['🛡️', 'Sécurité OWASP', 'Chaque faille est rattachée au Top 10 OWASP 2021 et classée par gravité : critique, majeure, mineure.'],
      ['🛠️', 'Correction expliquée', 'Diff avant / après prêt à copier, avec le pourquoi du changement. Idéal pour former les juniors.'],
      ['🔒', 'Secrets masqués', 'Mots de passe, clés d’API et e-mails sont remplacés avant tout envoi au modèle.'],
      ['🚦', 'Quality gate en CI', 'GitHub Actions, GitLab CI, Jenkins : la PR échoue sous le score choisi ou si une faille critique apparaît.'],
      ['📊', 'Suivi de progression', 'Historique, export CSV, problèmes récurrents par développeur et score moyen dans le temps.'],
      ['🏢', 'Vos données chez vous', 'Auto-hébergement Docker, LLM au choix (OpenAI, Claude, Gemini, Groq) ou Ollama 100 % local.'],
      ['📋', 'Boîte à outils QA', 'User story → cas de test et Gherkin, tests E2E Playwright / Cypress, tests d’API + k6, données de test, rapports de bug.'],
      ['🔎', 'Débogage accéléré', 'Collez une stack trace : cause racine, ligne en cause, correctif et test de non-régression en quelques secondes.'],
      ['⚡', 'IA gratuite et sans coupure', 'Gemini, Groq, OpenRouter, Mistral : si un fournisseur gratuit atteint son quota, le suivant prend le relais.'],
      ['🧬', 'Tests prouvés en CI', 'Chaque test généré est exécuté, réparé puis vérifié par mutation — jamais un test qui n’a pas tourné.'],
      ['🎯', 'Couverture des critères d’acceptation', 'La PR remplit-elle la user story ? Verdict par critère avec preuves.'],
    ],
    ciTitre: 'Branché sur vos pull requests en 2 minutes',
    ciSous: 'Créez un jeton dans Paramètres, ajoutez ce workflow : les problèmes apparaissent en annotations sur la PR et dans l’onglet Security de GitHub (SARIF).',
    ciPoints: ['Annotations ligne par ligne sur la PR', 'Résumé avec score par fichier', 'Échec du job sous le seuil', 'SARIF pour GitHub code scanning'],
    privTitre: 'Pensé pour les équipes qui ne peuvent pas envoyer leur code n’importe où',
    priv: "Installez RevIA sur votre serveur avec Docker et choisissez où part le code : un modèle local Ollama (rien ne sort de votre réseau), ou le fournisseur cloud de votre choix avec votre propre clé et votre propre contrat. Les secrets sont masqués dans tous les cas.",
    tTitre: 'Des tarifs simples', tSous: 'Commencez gratuitement. Passez à l’équipe quand la CI devient indispensable.',
    plans: [
      { nom: 'Gratuit', code: 'GRATUIT', prix: '0', unite: '', points: ['1 développeur', '{q} revues / jour', 'Rapport + corrections', 'Historique et tableau de bord'], cta: 'Commencer' },
      { nom: 'Pro', code: 'PRO', prix: 'pro', unite: '/ dev / mois', points: ['Tout le plan Gratuit', '{q} revues / jour', 'Jetons d’API et CI', 'Export SARIF', 'Assistant, outils dev et QA'], cta: 'Essayer Pro', ctaPaye: 'Passer à Pro', vedette: true },
      { nom: 'Équipe', code: 'EQUIPE', prix: 'equipe', unite: '/ dev / mois', points: ['Tout le plan Pro', '{q} revues / jour', 'Administration et quotas', 'Prompt système versionné', 'Support prioritaire'], cta: 'Démarrer l’équipe', ctaPaye: 'Passer à Équipe' },
      { nom: 'Auto-hébergé', prix: 'Sur devis', unite: '', points: ['Installation sur vos serveurs', 'LLM local ou privé', 'Contrat et SLA', 'Accompagnement à la mise en place'], cta: 'Nous contacter' },
    ],
    faqTitre: 'Questions fréquentes',
    faq: [
      ['Mon code est-il conservé ?', 'L’essai sans compte n’enregistre rien. Avec un compte, la revue est stockée dans votre historique et vous pouvez la supprimer. En auto-hébergement, tout reste sur votre serveur.'],
      ['Quels langages ?', 'Java, Python, JavaScript, TypeScript, PHP, C, C++, C#, Go, Kotlin, Ruby, SQL… La détection du langage est automatique.'],
      ['Quel modèle d’IA ?', 'Par défaut, des modèles gratuits et puissants (Google Gemini, gpt-oss-120b sur Groq, Qwen3 Coder sur OpenRouter, Codestral) enchaînés automatiquement en cas de quota. Ou le vôtre : OpenAI, Anthropic Claude, Ollama en local. Un mode à règles sans IA fonctionne aussi hors ligne.'],
      ['RevIA remplace-t-il la relecture humaine ?', 'Non. Il traite la partie répétitive (failles connues, oublis, mauvaises pratiques) pour que le relecteur se concentre sur la conception et le métier.'],
      ['Puis-je l’utiliser en CI ?', 'Oui : jeton d’API personnel, script prêt pour GitHub Actions et GitLab CI, commentaire de synthèse sur la PR / MR, export SARIF standard.'],
      ['Est-ce utile pour les testeurs (QA) ?', 'Oui : les outils QA transforment une user story en plan de test et en scénarios Gherkin, écrivent les tests E2E et d’API, génèrent les données de test, relisent vos tests existants et rédigent les rapports de bug.'],
    ],
    pied: 'Une IA ne remplace pas le relecteur humain : elle l’aide à se concentrer sur l’essentiel.',
    legal: ['CGU', 'Confidentialité', 'Mentions légales', 'DPA'],
    taxes: 'Paiement sécurisé par Paddle, marchand officiel : TVA et taxes calculées selon votre pays.',
    paiementKo: 'Le paiement n’a pas pu s’ouvrir. Réessayez ou écrivez-nous.',
  },
  en: {
    nav: ['Features', 'CI / Pull requests', 'Pricing', 'FAQ', 'Demo'], demo: 'See a real run: proven tests, 100% mutation score →', connexion: 'Sign in', essai: 'Free trial',
    ouvrir: 'Open the app',
    badge: 'AI code review · OWASP Top 10 · self-hostable',
    titre1: 'Stop vulnerabilities', titre2: 'before the merge.',
    sous: 'RevIA reviews every file like a senior developer: injections, hard-coded secrets, bugs and bad practices. It explains the risk, proposes the fix and blocks the pull request below your quality gate.',
    cta1: 'Try it now · no account', cta2: 'Create a free account',
    preuves: ['12 languages', 'Secrets masked before the AI', 'SARIF export (GitHub, GitLab, Azure)', 'Your LLM or 100% local'],
    bacTitre: 'Try it on your code', bacSous: 'Paste up to 150 lines. Nothing is stored.',
    analyser: 'Analyse', analyse: 'Analysing…', exemple: 'Reload the sample',
    vide: 'No issue found. 🎉', ligne: 'line', correction: 'Suggested fix', avant: 'Before', apres: 'After',
    inscrireApres: 'Keep your history and dashboard, and plug RevIA into your CI:', inscrire: 'Create a free account',
    fTitre: 'Code review, QA and testing in one place',
    f: [
      ['🛡️', 'OWASP security', 'Every flaw is mapped to the OWASP Top 10 2021 and ranked by severity: critical, major, minor.'],
      ['🛠️', 'Explained fixes', 'Copy-ready before / after diff with the reason for the change. Great for mentoring juniors.'],
      ['🔒', 'Secrets masked', 'Passwords, API keys and e-mails are replaced before anything is sent to the model.'],
      ['🚦', 'CI quality gate', 'GitHub Actions, GitLab CI, Jenkins: the PR fails below your score or when a critical flaw appears.'],
      ['📊', 'Progress tracking', 'History, CSV export, recurring issues per developer and average score over time.'],
      ['🏢', 'Your data stays yours', 'Self-host with Docker, pick your LLM (OpenAI, Claude, Gemini, Groq) or run Ollama 100% locally.'],
      ['📋', 'QA toolbox', 'User story → test cases and Gherkin, Playwright / Cypress E2E tests, API tests + k6, test data, bug reports.'],
      ['🔎', 'Faster debugging', 'Paste a stack trace: root cause, failing line, fix and regression test in seconds.'],
      ['⚡', 'Free AI, no downtime', 'Gemini, Groq, OpenRouter, Mistral: when a free provider hits its quota, the next one takes over.'],
      ['🧬', 'Tests proven in CI', 'Every generated test is run, repaired, then checked by mutation testing — never a test that has not run.'],
      ['🎯', 'Acceptance-criteria coverage', 'Does the PR fulfil the user story? A verdict per criterion, with evidence.'],
    ],
    ciTitre: 'Plugged into your pull requests in 2 minutes',
    ciSous: 'Create a token in Settings and add this workflow: issues show up as PR annotations and in GitHub’s Security tab (SARIF).',
    ciPoints: ['Line-level annotations on the PR', 'Summary with a score per file', 'Job fails below the gate', 'SARIF for GitHub code scanning'],
    privTitre: 'Built for teams that cannot send their code just anywhere',
    priv: 'Install RevIA on your own server with Docker and choose where code goes: a local Ollama model (nothing leaves your network), or the cloud provider of your choice with your own key and contract. Secrets are masked either way.',
    tTitre: 'Simple pricing', tSous: 'Start free. Move to Team when CI becomes a must.',
    plans: [
      { nom: 'Free', code: 'GRATUIT', prix: '0', unite: '', points: ['1 developer', '{q} reviews / day', 'Report + fixes', 'History and dashboard'], cta: 'Get started' },
      { nom: 'Pro', code: 'PRO', prix: 'pro', unite: '/ dev / month', points: ['Everything in Free', '{q} reviews / day', 'API tokens and CI', 'SARIF export', 'AI assistant, dev and QA tools'], cta: 'Try Pro', ctaPaye: 'Upgrade to Pro', vedette: true },
      { nom: 'Team', code: 'EQUIPE', prix: 'equipe', unite: '/ dev / month', points: ['Everything in Pro', '{q} reviews / day', 'Admin and quotas', 'Versioned system prompt', 'Priority support'], cta: 'Start a team', ctaPaye: 'Upgrade to Team' },
      { nom: 'Self-hosted', prix: 'Custom', unite: '', points: ['Installed on your servers', 'Local or private LLM', 'Contract and SLA', 'Onboarding support'], cta: 'Contact us' },
    ],
    faqTitre: 'FAQ',
    faq: [
      ['Is my code stored?', 'The no-account trial stores nothing. With an account, reviews are kept in your history and you can delete them. Self-hosted, everything stays on your server.'],
      ['Which languages?', 'Java, Python, JavaScript, TypeScript, PHP, C, C++, C#, Go, Kotlin, Ruby, SQL… Language detection is automatic.'],
      ['Which AI model?', 'By default, free and powerful models (Google Gemini, gpt-oss-120b on Groq, Qwen3 Coder on OpenRouter, Codestral) chained automatically when a quota is hit. Or bring your own: OpenAI, Anthropic Claude, a local Ollama model. A rule-based mode also works fully offline.'],
      ['Does RevIA replace human review?', 'No. It handles the repetitive part (known flaws, oversights, bad practices) so reviewers can focus on design and business logic.'],
      ['Can I use it in CI?', 'Yes: personal API token, ready-made script for GitHub Actions and GitLab CI, summary comment on the PR / MR, standard SARIF export.'],
      ['Is it useful for testers (QA)?', 'Yes: the QA tools turn a user story into a test plan and Gherkin scenarios, write E2E and API tests, generate test data, review your existing tests and write bug reports.'],
    ],
    pied: 'AI does not replace human reviewers: it helps them focus on what matters.',
    legal: ['Terms', 'Privacy', 'Legal notice', 'DPA'],
    taxes: 'Secure payment by Paddle, merchant of record: VAT and sales tax calculated for your country.',
    paiementKo: 'Checkout could not open. Try again or e-mail us.',
  },
};

const WORKFLOW = `# .github/workflows/revia-review.yml
on: pull_request
permissions: { contents: read, security-events: write }
jobs:
  revia:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }
      - uses: nafissabridah/RevIA/integrations/github@main
        with:
          url: \${{ vars.REVIA_URL }}
          token: \${{ secrets.REVIA_TOKEN }}
          min-score: "70"`;

/** Page d'accueil publique : proposition de valeur, essai sans compte, CI, tarifs, FAQ (FR / EN). */
@Component({
  selector: 'app-accueil',
  standalone: true,
  imports: [FormsModule, RouterLink, ScoreRingComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="nav">
      <a class="logo" routerLink="/"><span class="carre">R</span>Rev<span class="ia">IA</span></a>
      <nav>
        <a href="#fonctionnalites">{{ t().nav[0] }}</a><a href="#ci">{{ t().nav[1] }}</a>
        <a href="#tarifs">{{ t().nav[2] }}</a><a href="#faq">{{ t().nav[3] }}</a><a routerLink="/demo">{{ t().nav[4] }}</a>
      </nav>
      <div class="actions">
        <button class="langue" (click)="basculerLangue()" [attr.aria-label]="'Language'">{{ langue() === 'fr' ? 'EN' : 'FR' }}</button>
        @if (connecte()) {
          <a class="btn primaire" routerLink="/revues/nouvelle">{{ t().ouvrir }}</a>
        } @else {
          <a class="lien" routerLink="/connexion">{{ t().connexion }}</a>
          <a class="btn primaire" href="#essai">{{ t().essai }}</a>
        }
      </div>
    </header>

    <section class="hero">
      <div class="badge-hero">{{ t().badge }}</div>
      <h1>{{ t().titre1 }}<br /><span>{{ t().titre2 }}</span></h1>
      <p class="sous">{{ t().sous }}</p>
      <div class="ctas">
        <a class="btn primaire grand" href="#essai">{{ t().cta1 }}</a>
        <a class="btn fantome grand" routerLink="/inscription">{{ t().cta2 }}</a>
      </div>
      <a class="lien-demo" routerLink="/demo">{{ t().demo }}</a>
      <ul class="preuves">@for (p of t().preuves; track p) { <li>✓ {{ p }}</li> }</ul>
    </section>

    <section class="bac" id="essai">
      <div class="bac-entete">
        <div><h2>{{ t().bacTitre }}</h2><p>{{ t().bacSous }}</p></div>
        <div class="bac-actions">
          <button class="btn fantome" (click)="code.set(exemple)">{{ t().exemple }}</button>
          <button class="btn primaire" (click)="analyser()" [disabled]="envoi() || !code().trim()" data-test="analyser">
            @if (envoi()) { <span class="spinner"></span> {{ t().analyse }} } @else { ▶ {{ t().analyser }} }
          </button>
        </div>
      </div>
      <div class="bac-corps">
        <textarea [ngModel]="code()" (ngModelChange)="code.set($event)" spellcheck="false" aria-label="Code"></textarea>
        <div class="resultat">
          @if (erreur()) { <div class="message-erreur" role="alert">{{ erreur() }}</div> }
          @if (resultat(); as r) {
            <div class="res-entete">
              <app-score-ring [score]="r.score" [taille]="84" />
              <div><b>{{ r.titre }}</b><span class="meta">{{ r.langage }} · {{ r.nbLignes }} {{ t().ligne }}s · {{ r.dureeMs }} ms</span>
                <p class="resume">{{ r.resume }}</p></div>
            </div>
            @if (!r.problemes.length) { <p class="vide">{{ t().vide }}</p> }
            @for (p of r.problemes; track $index) {
              <article class="pb" [class]="'pb g-' + p.gravite">
                <div class="pb-tete"><span class="grav">{{ p.gravite }}</span><b>{{ p.titre }}</b><span class="l">{{ t().ligne }} {{ p.ligne }}</span></div>
                <p>{{ p.explication }}</p>
                @if (p.owasp) { <span class="owasp">{{ p.owasp }}</span> }
                @if (p.correction; as c) {
                  <details><summary>{{ t().correction }}</summary>
                    <div class="diff"><pre class="moins">{{ c.codeAvant }}</pre><pre class="plus">{{ c.codeApres }}</pre></div>
                  </details>
                }
              </article>
            }
            <div class="apres-essai">{{ t().inscrireApres }} <a class="btn primaire" routerLink="/inscription">{{ t().inscrire }}</a></div>
          } @else if (!erreur()) {
            <div class="attente"><span class="gros">⌘</span><span>{{ t().bacSous }}</span></div>
          }
        </div>
      </div>
    </section>

    <section class="bloc" id="fonctionnalites">
      <h2>{{ t().fTitre }}</h2>
      <div class="grille">
        @for (f of t().f; track f[1]) { <div class="carte-f"><span class="ico">{{ f[0] }}</span><h3>{{ f[1] }}</h3><p>{{ f[2] }}</p></div> }
      </div>
    </section>

    <section class="bloc ci" id="ci">
      <div class="ci-texte">
        <h2>{{ t().ciTitre }}</h2><p>{{ t().ciSous }}</p>
        <ul>@for (p of t().ciPoints; track p) { <li>✓ {{ p }}</li> }</ul>
      </div>
      <pre class="yaml">{{ workflow }}</pre>
    </section>

    <section class="bloc prive">
      <h2>{{ t().privTitre }}</h2><p>{{ t().priv }}</p>
    </section>

    <section class="bloc" id="tarifs">
      <h2>{{ t().tTitre }}</h2><p class="centre">{{ t().tSous }}</p>
      <div class="plans">
        @for (p of t().plans; track p.nom) {
          <div class="plan" [class.vedette]="p.vedette">
            <h3>{{ p.nom }}</h3>
            <div class="prix">{{ prix(p.prix) }}<small>{{ p.unite }}</small></div>
            <ul>@for (x of p.points; track x) { <li>✓ {{ point(x, p.code) }}</li> }</ul>
            @if ($last) {
              <a class="btn fantome" [href]="'mailto:' + marque.contact + '?subject=RevIA%20self-hosted'">{{ p.cta }}</a>
            } @else if (p.ctaPaye && paddle.prixDisponible($any(p.code))) {
              <button class="btn" [class.primaire]="p.vedette" [class.fantome]="!p.vedette" type="button" (click)="souscrire($any(p.code))">{{ p.ctaPaye }}</button>
            } @else {
              <a class="btn" [class.primaire]="p.vedette" [class.fantome]="!p.vedette" routerLink="/inscription">{{ p.cta }}</a>
            }
          </div>
        }
      </div>
      @if (paddle.actif()) { <p class="centre taxes">{{ t().taxes }}</p> }
      @if (erreurPaiement()) { <p class="centre taxes" role="alert">{{ erreurPaiement() }}</p> }
    </section>

    <section class="bloc" id="faq">
      <h2>{{ t().faqTitre }}</h2>
      <div class="faq">@for (q of t().faq; track q[0]) { <details><summary>{{ q[0] }}</summary><p>{{ q[1] }}</p></details> }</div>
    </section>

    <footer>
      <span>© {{ annee }} RevIA</span><span>{{ t().pied }}</span>
      <a [href]="marque.depot" target="_blank" rel="noopener">GitHub</a><a [href]="'mailto:' + marque.contact">{{ marque.contact }}</a>
      <a routerLink="/legal/cgu">{{ t().legal[0] }}</a><a routerLink="/legal/confidentialite">{{ t().legal[1] }}</a>
      <a routerLink="/legal/mentions-legales">{{ t().legal[2] }}</a><a routerLink="/legal/dpa">{{ t().legal[3] }}</a>
    </footer>
  `,
  styles: [`
    :host { display: block; background: var(--nuit); color: #e8eaff; min-height: 100vh; }
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
    .hero { text-align: center; padding: 90px 5vw 50px;
            background: radial-gradient(900px 420px at 50% 0%, #2a2a7a 0%, transparent 65%); }
    .badge-hero { display: inline-block; padding: 6px 14px; border-radius: 99px; border: 1px solid #3a3f73; color: var(--accent-ia); font-size: 12.5px; font-weight: 600; letter-spacing: .02em; }
    h1 { font-size: clamp(38px, 6vw, 72px); line-height: 1.04; letter-spacing: -.035em; margin: 22px 0 18px; font-weight: 800; }
    h1 span { background: linear-gradient(90deg, #a5b4fc, #c084fc); -webkit-background-clip: text; background-clip: text; color: transparent; }
    .sous { max-width: 720px; margin: 0 auto; color: #b9bedf; font-size: 18px; line-height: 1.6; }
    .ctas { display: flex; gap: 12px; justify-content: center; margin-top: 30px; flex-wrap: wrap; }
    .lien-demo { display: inline-block; margin-top: 18px; color: var(--accent-ia); font-weight: 600; font-size: 14.5px; }
    .lien-demo:hover { color: #fff; }
    .preuves { list-style: none; padding: 0; margin: 30px 0 0; display: flex; gap: 22px; justify-content: center; flex-wrap: wrap; color: #9aa0c8; font-size: 13.5px; }
    .bac { margin: 10px auto 0; max-width: 1180px; padding: 0 5vw; }
    .bac-entete { display: flex; justify-content: space-between; align-items: end; gap: 16px; margin-bottom: 14px; flex-wrap: wrap; }
    .bac h2 { font-size: 24px; margin: 0; } .bac-entete p { margin: 4px 0 0; color: #9aa0c8; }
    .bac-actions { display: flex; gap: 10px; }
    .bac-corps { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    textarea { min-height: 440px; resize: vertical; background: #10142a; color: #e5e7ff; border: 1px solid #262b52; border-radius: 14px; padding: 16px; font: 13px/1.6 var(--mono); outline: none; }
    textarea:focus { border-color: #5b5bf6; }
    .resultat { background: var(--surface); color: var(--texte); border-radius: 14px; padding: 18px; max-height: 620px; overflow: auto; }
    .attente { height: 100%; min-height: 400px; display: grid; place-items: center; align-content: center; gap: 8px; color: var(--texte-3); }
    .gros { font-size: 40px; }
    .res-entete { display: flex; gap: 14px; align-items: center; margin-bottom: 12px; }
    .meta { display: block; color: var(--texte-2); font-size: 12px; margin-top: 2px; }
    .resume { margin: 6px 0 0; font-size: 13px; color: var(--texte-2); line-height: 1.5; }
    .pb { border: 1px solid var(--bord); border-left: 4px solid var(--info); border-radius: 10px; padding: 10px 12px; margin-top: 10px; font-size: 13px; }
    .pb p { margin: 6px 0; line-height: 1.5; color: #3b4063; }
    .g-critique { border-left-color: var(--critique); } .g-majeure { border-left-color: var(--majeure); } .g-mineure { border-left-color: var(--mineure); }
    .pb-tete { display: flex; gap: 8px; align-items: center; }
    .grav { text-transform: uppercase; font-size: 10.5px; font-weight: 800; padding: 2px 7px; border-radius: 6px; background: var(--info-pale); color: var(--info); }
    .g-critique .grav { background: var(--critique-pale); color: var(--critique); } .g-majeure .grav { background: var(--majeure-pale); color: var(--majeure); }
    .g-mineure .grav { background: var(--mineure-pale); color: var(--mineure); }
    .l { margin-left: auto; color: var(--texte-3); font-size: 12px; white-space: nowrap; }
    .owasp { display: inline-block; font-size: 11.5px; background: var(--primaire-pale); color: var(--primaire-fonce); padding: 2px 8px; border-radius: 6px; }
    details summary { cursor: pointer; font-weight: 600; margin-top: 6px; color: var(--primaire-fonce); }
    .diff pre { margin: 6px 0 0; padding: 8px 10px; border-radius: 8px; font: 12px/1.5 var(--mono); white-space: pre-wrap; }
    .moins { background: #fdecec; color: #8a1c1f; } .plus { background: #e6f7ec; color: #14532d; }
    .vide { color: var(--ok); font-weight: 600; }
    .apres-essai { margin-top: 16px; padding: 12px; border-radius: 10px; background: var(--primaire-pale); display: flex; gap: 12px; align-items: center; justify-content: space-between; flex-wrap: wrap; font-size: 13px; }
    .message-erreur { background: var(--critique-pale); color: var(--critique); border-radius: 9px; padding: 10px 12px; font-size: 13px; }
    .bloc { max-width: 1180px; margin: 0 auto; padding: 90px 5vw 0; }
    .bloc h2 { font-size: clamp(26px, 3.4vw, 38px); letter-spacing: -.02em; margin: 0 0 12px; text-align: center; }
    .centre { text-align: center; color: #9aa0c8; margin: 0 0 30px; }
    .grille { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-top: 34px; }
    .carte-f { background: #12162e; border: 1px solid #22274c; border-radius: 14px; padding: 22px; }
    .carte-f h3 { margin: 12px 0 6px; font-size: 17px; } .carte-f p { margin: 0; color: #a9aed3; line-height: 1.55; font-size: 14px; }
    .ico { font-size: 26px; }
    .ci { display: grid; grid-template-columns: 1fr 1.1fr; gap: 40px; align-items: center; }
    .ci h2 { text-align: left; } .ci p { color: #b9bedf; line-height: 1.6; }
    .ci ul { list-style: none; padding: 0; color: #c9cdf0; line-height: 2; }
    .yaml { background: #0f1328; border: 1px solid #262b52; border-radius: 14px; padding: 18px; font: 12.5px/1.6 var(--mono); color: #c7d2fe; overflow: auto; margin: 0; }
    .prive { text-align: center; } .prive p { max-width: 780px; margin: 0 auto; color: #b9bedf; line-height: 1.7; font-size: 16px; }
    .plans { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
    .plan { background: #12162e; border: 1px solid #22274c; border-radius: 16px; padding: 24px; display: flex; flex-direction: column; }
    .plan.vedette { border-color: #6366f1; box-shadow: 0 0 0 1px #6366f1, 0 20px 50px rgba(99, 102, 241, .25); }
    .plan h3 { margin: 0; font-size: 16px; color: #c9cdf0; }
    .prix { font-size: 34px; font-weight: 800; margin: 10px 0 14px; } .prix small { font-size: 13px; font-weight: 500; color: #9aa0c8; margin-left: 6px; }
    .plan ul { list-style: none; padding: 0; margin: 0 0 20px; color: #b9bedf; line-height: 2; font-size: 14px; flex: 1; }
    .plan .btn { justify-content: center; }
    .taxes { font-size: 12.5px; color: #8b91bb; margin-top: 14px; }
    .faq { max-width: 780px; margin: 30px auto 0; }
    .faq details { border-bottom: 1px solid #22274c; padding: 16px 0; }
    .faq summary { color: #fff; font-size: 16px; } .faq p { color: #b9bedf; line-height: 1.6; margin: 10px 0 0; }
    footer { margin-top: 90px; padding: 28px 5vw; border-top: 1px solid #1d2142; display: flex; gap: 22px; flex-wrap: wrap; color: #8b91bb; font-size: 13px; }
    footer span:nth-child(2) { flex: 1; }
    @media (max-width: 960px) {
      nav { display: none; }
      .bac-corps, .ci, .grille, .plans { grid-template-columns: 1fr; }
      textarea { min-height: 280px; }
      .attente { min-height: 140px; }
      .actions .lien { display: none; }
    }
  `],
})
export class AccueilComponent {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly paddle = inject(PaddleService);
  readonly erreurPaiement = signal('');

  readonly marque = MARQUE;
  readonly workflow = WORKFLOW;
  readonly exemple = EXEMPLE;
  readonly annee = new Date().getFullYear();
  private readonly langues = inject(LangueService);
  readonly langue = this.langues.langue;
  readonly t = computed(() => T[this.langue()]);
  readonly connecte = computed(() => this.auth.estConnecte());
  readonly code = signal(EXEMPLE);
  readonly envoi = signal(false);
  readonly erreur = signal('');
  readonly resultat = signal<RevueDetail | null>(null);

  constructor() {
    this.paddle.charger().subscribe();
  }

  /** « {q} revues / jour » → quota réel de l'offre (configuré côté serveur). */
  point(x: string, code?: string): string {
    return x.replace('{q}', this.paddle.quotas()[(code ?? 'GRATUIT') as Plan].toLocaleString(this.langue() === 'fr' ? 'fr-FR' : 'en-US'));
  }

  /** Passer à Pro / Équipe : inscription d'abord si besoin, puis paiement Paddle depuis Paramètres. */
  souscrire(plan: Plan): void {
    this.erreurPaiement.set('');
    if (!this.auth.estConnecte()) {
      this.router.navigate(['/inscription'], { queryParams: { offre: plan.toLowerCase() } });
      return;
    }
    if (!this.auth.estDeveloppeur()) {
      this.router.navigate(['/parametres']);
      return;
    }
    this.paddle.ouvrirCheckout(plan).catch(() => this.erreurPaiement.set(this.t().paiementKo));
  }

  basculerLangue(): void {
    this.langues.basculer();
  }

  prix(p: string): string {
    if (p === 'pro' || p === 'equipe') {
      return MARQUE.prix[p] + ' ' + MARQUE.prix.devise;
    }
    return p === '0' ? '0 ' + MARQUE.prix.devise : p;
  }

  analyser(): void {
    this.envoi.set(true);
    this.erreur.set('');
    this.http.post<RevueDetail>('/api/essai', { code: this.code() }).subscribe({
      next: (r) => { this.resultat.set(r); this.envoi.set(false); },
      error: (e) => { this.erreur.set(messageErreur(e)); this.resultat.set(null); this.envoi.set(false); },
    });
  }
}
