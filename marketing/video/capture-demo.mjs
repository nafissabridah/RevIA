/**
 * Capture des scènes de la vidéo de démonstration RevIA (Full HD 1920×1080, une légende par scène).
 *
 * Module Playwright : `export default async (page) => …`. Variables d'environnement :
 *   REVIA_URL   (défaut http://localhost:8085)   application lancée avec les données de démo
 *   DEMO_LANG   fr | en                          langue de l'interface et des légendes
 *   DEMO_OUT    dossier de sortie des PNG        (défaut ./scenes)
 * En anglais, lancez RevIA avec un vrai LLM (ex. REVIA_AI_PROVIDER=gemini) : le mode démo répond en français.
 * Puis : node build-video.mjs  (assemble les PNG en MP4 H.264 avec fondus).
 */
import fs from 'node:fs';
import path from 'node:path';

const B = process.env.REVIA_URL || 'http://localhost:8085';
const L = process.env.DEMO_LANG === 'en' ? 'en' : 'fr';
const OUT = process.env.DEMO_OUT || path.resolve('scenes');
const t = (fr, en) => (L === 'en' ? en : fr);

const LEGENDES = [
  ['RevIA', t('Revue de code IA et boîte à outils QA, avec quality gate CI', 'AI code review and QA toolbox, with a CI quality gate')],
  [t('Essai gratuit, sans compte', 'Free trial, no account'), t('Collez du code : failles, bugs et corrections en quelques secondes. Rien n’est enregistré.', 'Paste code: flaws, bugs and fixes in seconds. Nothing is stored.')],
  [t('Tout au même endroit', 'Everything in one place'), t('Sécurité OWASP, corrections expliquées, outils QA, IA gratuite avec relais automatique.', 'OWASP security, explained fixes, QA tools, free AI with automatic failover.')],
  [t('Connexion', 'Sign in'), t('Compte gratuit : 50 revues par jour.', 'Free account: 50 reviews per day.')],
  [t('Nouvelle revue', 'New review'), t('Collez ou importez un fichier : 12 langages, jusqu’à 2 000 lignes.', 'Paste or import a file: 12 languages, up to 2,000 lines.')],
  [t('Le rapport', 'The report'), t('Score sur 100, résumé rédigé, problèmes classés par gravité et marqués dans l’éditeur.', 'Score out of 100, written summary, issues ranked by severity and marked in the editor.')],
  [t('La correction expliquée', 'The explained fix'), t('Le pourquoi, la référence OWASP et le diff avant / après prêt à copier.', 'The why, the OWASP reference and a copy-ready before / after diff.')],
  [t('Historique', 'History'), t('Recherche, filtres, export CSV : chaque revue est conservée.', 'Search, filters, CSV export: every review is kept.')],
  [t('Tableau de bord', 'Dashboard'), t('Score moyen, progression sur 30 jours, problèmes récurrents.', 'Average score, 30-day trend, recurring issues.')],
  [t('Outils dev : tests unitaires', 'Dev tools: unit tests'), t('JUnit, pytest ou Jest générés à partir de votre code.', 'JUnit, pytest or Jest generated from your code.')],
  [t('QA : plan et cas de test', 'QA: test plan and cases'), t('User story → cas de test, scénarios Gherkin, traçabilité (Xray, TestRail, Zephyr).', 'User story → test cases, Gherkin scenarios, traceability (Xray, TestRail, Zephyr).')],
  [t('QA : tests end-to-end', 'QA: end-to-end tests'), t('Playwright, Cypress ou Selenium, avec page objects et sélecteurs robustes.', 'Playwright, Cypress or Selenium, with page objects and robust selectors.')],
  [t('QA : tests d’API', 'QA: API tests'), t('REST Assured, pytest, Postman… et un test de charge k6 prêt à lancer.', 'REST Assured, pytest, Postman… and a ready-to-run k6 load test.')],
  [t('QA : logs et stack trace', 'QA: logs and stack traces'), t('Cause racine, ligne en cause, correctif et test de non-régression.', 'Root cause, failing line, fix and regression test.')],
  [t('QA : rapport de bug', 'QA: bug report'), t('Une description brute devient un ticket Jira clair et complet.', 'Rough notes become a clear, complete Jira ticket.')],
  [t('Assistant IA', 'AI assistant'), t('Questions sur le code, les erreurs ou les tests, réponses en direct.', 'Questions about code, errors or tests, answered live.')],
  [t('Intégration CI et IA gratuite', 'CI integration and free AI'), t('Jeton pour GitHub / GitLab, commentaire sur la PR, SARIF. Gemini, Groq, OpenRouter, Mistral ou Ollama local.', 'Token for GitHub / GitLab, PR comment, SARIF. Gemini, Groq, OpenRouter, Mistral or local Ollama.')],
  [t('Administration', 'Administration'), t('Utilisateurs, quotas journaliers et prompt système versionné.', 'Users, daily quotas and a versioned system prompt.')],
  [t('Français et anglais', 'English and French'), t('Interface et réponses de l’IA dans la langue de chaque utilisateur.', 'UI and AI answers in each user’s language.')],
  [t('Commencez gratuitement', 'Start free'), t('Auto-hébergeable avec Docker. Vos secrets sont masqués avant tout envoi à l’IA.', 'Self-host with Docker. Your secrets are masked before anything reaches the AI.')],
];

async function legende(page, i) {
  const [titre, sous] = LEGENDES[i];
  await page.evaluate(({ titre, sous, n, total }) => {
    document.getElementById('revia-legende')?.remove();
    const d = document.createElement('div');
    d.id = 'revia-legende';
    d.innerHTML = `<span class="n">${n} / ${total}</span><div><b></b><span class="s"></span></div>`;
    d.querySelector('b').textContent = titre;
    d.querySelector('.s').textContent = sous;
    const st = document.createElement('style');
    st.textContent = `#revia-legende{position:fixed;left:50%;bottom:28px;transform:translateX(-50%);z-index:99999;display:flex;gap:18px;
      align-items:center;max-width:1500px;padding:16px 26px;border-radius:16px;background:rgba(15,18,34,.92);color:#fff;
      box-shadow:0 12px 40px rgba(0,0,0,.35);font-family:Inter,'Segoe UI',system-ui,sans-serif;border:1px solid rgba(165,180,252,.35)}
      #revia-legende .n{background:#5b5bf6;border-radius:999px;padding:6px 12px;font-weight:700;font-size:15px;white-space:nowrap}
      #revia-legende b{display:block;font-size:26px;letter-spacing:-.01em}
      #revia-legende .s{display:block;font-size:18px;color:#c9cdf0;margin-top:3px}`;
    d.appendChild(st);
    document.body.appendChild(d);
  }, { titre, sous, n: i + 1, total: LEGENDES.length });
  await page.waitForTimeout(250);
  await page.screenshot({ path: path.join(OUT, `scene-${String(i + 1).padStart(2, '0')}.png`) });
  await page.evaluate(() => document.getElementById('revia-legende')?.remove());
}

async function attendreFin(page) {
  await page.waitForFunction(() => document.querySelector('.resultat app-reponse-ia') && !document.querySelector('.resultat .spinner'),
    null, { timeout: 120000 });
  await page.waitForTimeout(400);
}

async function outil(page, nom, i, cadre) {
  await page.locator('.outil', { hasText: nom }).click();
  if (cadre) {
    await page.locator('header select').last().selectOption(cadre);
  }
  await page.locator('header .btn', { hasText: t('Exemple', 'Sample') }).click();
  await page.locator('header .btn.primaire').click();
  await attendreFin(page);
  await legende(page, i);
}

async function connexion(page, email, mdp) {
  await page.goto(B + '/connexion');
  await page.waitForSelector('#email');
  await page.fill('#email', email);
  await page.fill('#mdp', mdp);
  return page;
}

export default async function run(page) {
  fs.mkdirSync(OUT, { recursive: true });
  await page.setViewportSize({ width: 1920, height: 1080 });
  await page.evaluate((l) => localStorage.setItem('revia.langue', l), L);

  // 1-3. Page d'accueil : promesse, essai sans compte, fonctionnalités
  await page.goto(B + '/');
  await page.waitForSelector('section.hero');
  await page.waitForTimeout(600);
  await legende(page, 0);
  await page.locator('#essai').scrollIntoViewIfNeeded();
  await page.locator('#essai button.primaire, #essai .btn.primaire').first().click();
  await page.waitForFunction(() => document.querySelector('#essai')?.innerText.match(/\/ ?100|ligne|line/i), null, { timeout: 60000 });
  await page.waitForTimeout(800);
  await page.evaluate(() => document.querySelector('#essai').scrollIntoView({ block: 'start' }));
  await page.waitForTimeout(400);
  await legende(page, 1);
  await page.evaluate(() => document.querySelector('#fonctionnalites').scrollIntoView({ block: 'start' }));
  await page.waitForTimeout(500);
  await legende(page, 2);

  // 4. Connexion
  await connexion(page, 'sarra@revia.local', 'Sarra2026!');
  await legende(page, 3);
  await page.locator('button[type=submit]').click();
  await page.waitForURL('**/revues/nouvelle', { timeout: 30000 });

  // 5-7. Revue : code, rapport, correction
  await page.locator('button', { hasText: t('Essayer avec un exemple', 'Try with a sample') }).click();
  await page.waitForTimeout(1500); // Monaco
  await legende(page, 4);
  await page.locator('header button.primaire').first().click();
  await page.waitForSelector('text=' + t('Voir la correction', 'See the fix'), { timeout: 60000 });
  await page.waitForTimeout(1200);
  await legende(page, 5);
  await page.locator('a, button', { hasText: t('Voir la correction', 'See the fix') }).first().click();
  await page.waitForSelector('.diff', { timeout: 30000 });
  await page.waitForTimeout(1000);
  await legende(page, 6);

  // 8-9. Historique et tableau de bord
  await page.goto(B + '/historique');
  await page.waitForSelector('table tbody tr');
  await page.waitForTimeout(600);
  await legende(page, 7);
  await page.goto(B + '/tableau-de-bord');
  await page.waitForSelector('canvas');
  await page.waitForTimeout(1500); // animation des graphiques
  await legende(page, 8);

  // 10-15. Outils dev et QA
  await page.goto(B + '/outils');
  await page.waitForSelector('.outil');
  await outil(page, t('Tests unitaires', 'Unit tests'), 9);
  await outil(page, t('Plan & cas de test', 'Test plan & cases'), 10);
  await outil(page, t('Tests E2E (UI)', 'E2E UI tests'), 11);
  await outil(page, t('Tests d’API', 'API tests'), 12);
  await outil(page, t('Logs & stack trace', 'Logs & stack traces'), 13);
  await outil(page, t('Rapport de bug', 'Bug report'), 14);

  // 16. Assistant
  await page.goto(B + '/assistant');
  await page.locator('.suggestion').nth(1).click();
  await page.waitForFunction(() => document.querySelectorAll('app-reponse-ia').length > 0 && !document.querySelector('.spinner'),
    null, { timeout: 120000 });
  await page.waitForTimeout(800);
  await legende(page, 15);

  // 17. Paramètres : jeton CI + module d'IA
  await page.goto(B + '/parametres');
  await page.waitForSelector('dl');
  const jeton = page.locator('input[placeholder*="GitHub"], input[aria-label*="GitHub"]').first();
  if (await jeton.count()) {
    await page.locator('button', { hasText: t('Créer un jeton', 'Create a token') }).click();
    await page.waitForTimeout(800);
  }
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight * 0.35));
  await page.waitForTimeout(500);
  await legende(page, 16);

  // 18. Administration
  await page.locator('button.sortir').click();
  await page.waitForURL('**/connexion');
  await connexion(page, 'admin@revia.local', 'Admin2026!');
  await page.locator('button[type=submit]').click();
  await page.waitForURL('**/admin', { timeout: 30000 });
  await page.waitForSelector('table tbody tr');
  await page.waitForTimeout(600);
  await legende(page, 17);

  // 19. Bascule de langue (l'autre langue, pour la montrer)
  await page.locator('button.langue').click();
  await page.waitForTimeout(600);
  await legende(page, 18);
  await page.locator('button.langue').click();

  // 20. Tarifs
  await page.goto(B + '/');
  await page.waitForSelector('#tarifs');
  await page.evaluate(() => document.querySelector('#tarifs').scrollIntoView({ block: 'start' }));
  await page.waitForTimeout(600);
  await legende(page, 19);
  return { scenes: LEGENDES.length, out: OUT, lang: L };
}
