# RevIA – audit commercial et corrections (v1.1)

Question posée : *pourquoi une équipe ou une école n'achèterait-elle pas RevIA aujourd'hui ?*
Méthode : parcours complet en visiteur puis en utilisateur (navigateur automatisé), lecture du code, comparaison avec
les outils concurrents (CodeRabbit, Greptile, Graphite, GitHub Copilot code review…).

## Freins trouvés → ce qui a été fait

| # | Frein à l'achat | Gravité | Correction (v1.1) |
|---|---|---|---|
| 1 | **Aucune page d'accueil** : un visiteur tombe sur un formulaire de connexion, sans proposition de valeur, sans prix. | Bloquant | Page d'accueil publique `/` (FR/EN) : bénéfices, essai en direct, intégration CI, confidentialité, tarifs, FAQ. |
| 2 | **Impossible d'essayer sans créer de compte.** | Bloquant | Endpoint public `POST /api/essai` (150 lignes max, rien n'est enregistré, 3 essais/min/IP, plafond quotidien global). |
| 3 | Mention « **Mini-projet · Sujet 4** » sur l'écran de connexion : signal « projet étudiant ». | Fort | Remplacée par « Revue de code IA · OWASP · CI » + lien retour vers l'accueil. |
| 4 | **Pas d'intégration aux pull requests / CI** : les équipes achètent un outil de revue pour leurs PR, pas pour coller du code. | Bloquant | Jetons d'API personnels (empreinte SHA-256 seule en base, révocables), script `integrations/revia_ci.py`, GitHub Action composite, modèle GitLab CI, annotations ligne par ligne, résumé, échec sous un score minimal. |
| 5 | **Pas de format standard** pour les résultats. | Fort | Export **SARIF 2.1.0** (`GET /api/revues/{id}/sarif` + bouton « SARIF ») : GitHub code scanning, Azure DevOps, VS Code… |
| 6 | **Rapports uniquement en français.** | Fort (international) | Prompt système anglais `prompts/revue-system.en.st` (variable `REVIA_PROMPT`). Page d'accueil bilingue. |
| 7 | **Bug** : actualiser `/assistant` ou `/outils` renvoie une **erreur 404** (routes absentes du `SpaController`). | Moyen | Routes ajoutées. Vérifié : 200. |
| 8 | Badge « **Gemini** » affiché en dur même quand le fournisseur est Démo/OpenAI/Claude (information trompeuse). | Moyen | Le badge affiche le fournisseur réellement configuré. |
| 9 | **Confidentialité** : le niveau gratuit de Gemini peut utiliser les données envoyées pour améliorer les services de Google ; une entreprise n'enverra pas son code ainsi. | Fort (B2B) | Mise en avant de l'auto-hébergement et d'Ollama local sur la page d'accueil et dans la FAQ. **À faire côté exploitation** : en production commerciale, utiliser une offre payante/contractuelle (OpenAI, Anthropic, Gemini payant) ou Ollama. |
| 10 | Le zip contenait un fichier `.env` avec une **vraie clé d'API**. | Sécurité | `.env` retiré du nouveau zip. **À faire : révoquer cette clé sur aistudio.google.com et en créer une nouvelle.** |

## Vérifications effectuées

- Back-end recompilé (javac sur les bibliothèques du JAR), application relancée, tests manuels de l'API :
  essai public (200, puis 429 au-delà de la limite), création/listage/révocation de jeton, revue via jeton,
  export SARIF, jeton invalide → 401, routes `/assistant` `/outils` → 200.
- Script CI testé sur un dépôt Git : 2 fichiers, gate en échec (code 1) sur le fichier vulnérable, SARIF généré.
- Front-end Angular recompilé (0 erreur), pages vérifiées par captures (bureau + mobile 390 px).
- Nouveaux tests unitaires : `IntegrationsTest` (SARIF, empreinte des jetons). Leurs attentes ont été vérifiées
  à l'exécution, **mais la suite Maven complète n'a pas pu être lancée ici** (Maven Central inaccessible depuis
  l'environnement). Lancez `mvn test` dans `backend/` avant de publier.

## v1.2 : freins levés

| Frein | Correction (v1.2) |
|---|---|
| Application uniquement en français | Interface complète FR/EN (bascule), titres, dates, messages d'erreur ; réponses de l'IA dans la langue choisie |
| Rien pour les testeurs (QA), alors que la revue ne couvre qu'une partie du cycle | 7 outils QA : cas de test + Gherkin, E2E, tests d'API + k6, rapport de bug, stack trace, données de test, revue de tests |
| Quotas gratuits des LLM : une analyse échoue dès que Gemini sature | Relais automatique entre fournisseurs gratuits (Groq, OpenRouter, Mistral) puis mode démo |
| Modèle Groq par défaut retiré par Groq (16/08/2026) : fournisseur cassé | `openai/gpt-oss-120b` |
| Pas de retour visible dans la pull request | Commentaire de synthèse unique sur la PR (GitHub) / MR (GitLab), mis à jour à chaque push |

## Ce qui reste à faire pour vendre (non codé ici)

1. **Paiement** : les boutons de tarifs mènent à l'inscription ou au contact. Ajouter Stripe/Paddle (ou facturation manuelle au début).
2. **Analyse d'un dépôt entier** et commentaires en ligne via une GitHub App (aujourd'hui : annotations, commentaire de synthèse et SARIF via le workflow).
3. SSO (Google/Microsoft), espaces d'équipe/organisation, mentions légales, CGV, politique de confidentialité, DPA (RGPD).
4. Personnaliser `frontend/src/app/core/marque.ts` (adresse de contact, prix) avant la mise en ligne.
5. **Valider la demande** : pilotes gratuits avec 10 à 20 prospects (`sales/RevIA-prospects.xlsx`), mesurer l'usage et la conversion.
6. Mode démo : ses textes restent en français ; en anglais, utilisez un vrai LLM (les rapports de l'IA suivent alors la langue).

## v1.3 : se différencier (audit du 22/10/2026)

Constat : la revue de code IA (CodeRabbit, Qodo, Copilot review, Greptile) et la génération de cas de test (TestCollab,
Qodo, testRigor) sont des marchés saturés. Le frein n°1 des équipes QA en 2026 est la **confiance** : 94 % utilisent l'IA
pour tester, seulement 18 % font confiance à leur couverture ; la demande n°1 (72,8 %, Automation Guild 2026) est
« des tests générés par l'IA qui soient justes, pas hallucinés ». Aucun outil trouvé ne prouve ses tests dans la PR, ni
ne vérifie automatiquement une PR contre les critères d'acceptation de sa user story.

| Différenciateur | Ce que voit le client |
|---|---|
| **Tests prouvés** (génération → exécution dans sa CI → réparation → tests de mutation) | Uniquement des tests verts, un score de mutation par fichier, et les bugs trouvés en testant |
| **Couverture des critères d'acceptation** | « 67 % de la user story couverte : AC-3 non implémenté, AC-2 sans test », avec preuves fichier:ligne |
| 15 langues de réponse de l'IA (dont arabe RTL, turc, espagnol, chinois) | Marchés non anglophones, mal servis par les outils américains |

Arguments de vente : « RevIA ne vous donne jamais un test qu'il n'a pas exécuté » · « votre code ne quitte jamais votre
runner pour être exécuté » · « gratuit avec des LLM gratuits, ou 100 % local avec Ollama ».

Reste à faire (inchangé) : paiement, SSO/organisations, pages légales, GitHub App (commentaires en ligne), pilotes clients.
