# Lancement de RevIA — mise en ligne, paiement, pilotes

Guide pas à pas pour passer du code (v1.6+) à de vrais utilisateurs. Tout ce qui demande **ton compte** (Render,
Paddle, Google, GitHub) est ici : aucune de ces étapes ne peut être faite par le code.

---

## 1. Mise en ligne (≈ 30 min, ≈ 7 $/mois)

1. **Nouvelle clé Gemini** (l'ancienne a été collée dans une conversation) : <https://aistudio.google.com/apikey> →
   *Create API key* → supprime l'ancienne (celle qui finit par `Dtfg`). Mets la nouvelle **uniquement** dans
   `RevIA/.env` (local) et dans les variables d'environnement de Render (prod). Jamais dans le code ni dans un chat.
2. **Clés de relais gratuites** (recommandé : si Gemini est saturé, RevIA passe au suivant) :
   OpenRouter <https://openrouter.ai/keys>, Mistral <https://console.mistral.ai>, Groq <https://console.groq.com/keys>
   (Groq a refusé l'inscription depuis le réseau de l'entreprise : réessaie depuis ton téléphone en 4G).
   `REVIA_AI_FALLBACK=openrouter,mistral,groq,demo`.
3. **Render** (backend + PostgreSQL) : <https://dashboard.render.com> → *New* → *Blueprint* → dépôt
   `nafissabridah/RevIA`, branche `release/v1.3` (ou `main` après fusion). `render.yaml` crée tout.
   Passe le service web en **Starter** (le plan gratuit s'endort après 15 min : la première revue d'un client
   prendrait ~1 min). Renseigne les variables marquées `sync: false` : `GEMINI_API_KEY`, `REVIA_JWT_SECRET`
   (64 caractères aléatoires), `REVIA_ADMIN_PASSWORD`, `REVIA_CORS_ORIGINS` (URL Vercel).
4. **Vercel** (interface) : <https://vercel.com/new> → même dépôt, dossier `frontend`. `vercel.json` relaie `/api`
   vers Render : remplace l'URL du backend dedans si Render t'en donne une autre.
5. Vérifie : page d'accueil, inscription, une revue, `/demo`, `/legal/mentions-legales`.

## 2. Paiement — Paddle (≈ 1 h + délai de validation Paddle)

1. Crée le compte vendeur : <https://www.paddle.com> → *Get started*. Paddle vérifie chaque vendeur (identité,
   site en ligne, pages légales, prix visibles) : **mets le site en ligne avant** (étape 1).
   ⚠️ Vérifie dans le formulaire que Paddle accepte un vendeur **particulier résidant en Tunisie** et le mode de
   versement (virement / Payoneer). S'il refuse, alternatives « marchand officiel » : Lemon Squeezy, Gumroad — ou crée
   une structure (auto-entrepreneur / SUARL) et remplis `core/marque.ts` avec ses informations.
2. Dans Paddle : *Catalog → Products* : « RevIA Pro » (9 €/mois) et « RevIA Équipe » (19 €/mois) → copie les deux
   `price_id` (`pri_…`).
3. *Developer tools → Authentication* : une *client-side token* (`live_…`) et une clé API.
   *Notifications* : URL `https://<backend>/api/paddle/webhook`, événements `subscription.*` et `transaction.completed`
   → copie le *secret key* de la notification.
4. Sur Render : `PADDLE_ENV=production`, `PADDLE_CLIENT_TOKEN`, `PADDLE_API_KEY`, `PADDLE_WEBHOOK_SECRET`,
   `PADDLE_PRICE_PRO`, `PADDLE_PRICE_EQUIPE`. Teste d'abord tout en **sandbox** (<https://sandbox-vendors.paddle.com>,
   carte de test `4242 4242 4242 4242`).

## 3. Pages légales

`frontend/src/app/core/marque.ts` est rempli pour un **projet personnel** (éditeur : Saleh, personne physique, droit
tunisien, INPDP + autorités de l'UE pour les utilisateurs européens, hébergeurs Render et Vercel).
À faire par toi :
- ajouter une **adresse postale** de contact si un juriste ou Paddle l'exige (aujourd'hui : « Tunisie ») ;
- faire relire les 4 pages par un juriste (idéalement un avocat tunisien qui connaît le RGPD : tes clients européens
  le demanderont), puis passer `valideParJuriste: true` (le bandeau « à faire valider » disparaît) ;
- si Render est créé dans une autre région qu'Oregon, corriger `SOUS_TRAITANTS` dans `pages/legal/contenu-legal.ts`.

## 4. Offre pilote (3 à 5 équipes)

**Pourquoi** : 5 équipes qui l'utilisent chaque semaine valent plus que 100 inscrits. Elles donnent les témoignages,
les chiffres et les bugs qui feront vendre les suivantes.

**L'offre** : plan Équipe **gratuit 3 mois** + installation faite avec eux (30 min en visio) + une ligne directe
(WhatsApp / e-mail). En échange : un point de 20 min toutes les 2 semaines, le droit de citer des chiffres anonymisés,
et un témoignage si elles sont satisfaites.

**Profil idéal** :
- **ESN / sociétés de nearshore en Tunisie** qui livrent des projets à des banques et assureurs français : elles ont
  des équipes QA, des cahiers de recette, des exigences de traçabilité — exactement les packs *finance* /
  *assurance* et l'écran Projets. C'est la cible n° 1 (proches, nombreuses, sous pression sur la qualité).
- Startups (fintech, insurtech, e-commerce) de 5 à 30 développeurs sans QA dédiée : les *tests prouvés* remplacent
  une partie du QA manuel.
- Équipes QA / BA qui écrivent encore les cas de test à la main depuis des user stories ou des flowcharts.

**Ce qu'on mesure** (à noter à chaque point) : PR revues / semaine, tests prouvés ajoutés, score de mutation moyen,
bugs trouvés avant la recette, critères d'acceptation couverts, temps gagné estimé par l'équipe.

## 5. Messages prêts à envoyer

### LinkedIn — FR (message direct, < 600 caractères)
> Bonjour {Prénom}, je développe RevIA, un outil qui génère des tests unitaires **et prouve qu'ils marchent** : il
> les exécute dans votre CI, les répare, puis injecte des bugs dans le code pour vérifier que les tests les
> attrapent (score de mutation). Il vérifie aussi chaque PR contre les critères d'acceptation et sort la matrice de
> traçabilité exigence → test → verdict QA. Je cherche 5 équipes pilotes : plan Équipe offert 3 mois et installation
> faite avec vous. Ça vous intéresse d'en parler 15 min ? Démo : {URL}/demo

### LinkedIn — EN
> Hi {Name}, I'm building RevIA: it writes unit tests **and proves they work** — it runs them in your CI, repairs
> them from the real output, then injects bugs into your code to check the tests catch them (mutation score). It
> also checks every PR against the acceptance criteria and exports the requirement → test → QA verdict traceability
> matrix. I'm looking for 5 pilot teams: Team plan free for 3 months, set up together. Open to a 15-min call?
> Demo: {URL}/demo

### E-mail — responsable QA / CTO d'une ESN (FR)
Objet : Vos tests unitaires générés par l'IA attrapent-ils vraiment les bugs ?

> Bonjour {Prénom},
>
> Les outils d'IA écrivent des tests, mais personne ne vérifie qu'ils détectent quelque chose. RevIA le prouve :
> chaque test est exécuté dans votre pipeline, réparé à partir de la vraie sortie, puis confronté à des bugs injectés
> volontairement. Seuls les tests qui en attrapent sont gardés. Sur notre exemple réel (modèle gratuit Gemini) :
> 9 tests verts, 100 % des bugs injectés détectés.
>
> Pour vos projets banque / assurance, RevIA vérifie aussi chaque PR contre les critères d'acceptation, transforme
> les flowcharts (BPMN, draw.io, Mermaid) en cas de test et produit la matrice de traçabilité pour la recette.
> Le code n'est jamais exécuté sur nos serveurs, et peut rester chez vous (modèle local Ollama).
>
> Je cherche 5 équipes pilotes (plan Équipe offert 3 mois). 15 minutes cette semaine ?
>
> Saleh — {URL}/demo

### Post de lancement (LinkedIn / dev.to / Reddit r/QualityAssurance, r/softwaretesting)
> J'ai demandé à une IA gratuite d'écrire des tests pour un panier d'achat. Puis j'ai **injecté des bugs** dans le
> code pour voir si ses tests les attrapaient. Première version : 86 %. Après réparation automatique : 100 %.
> Et un bug caché (la doc dit « à partir de 50 € », le code fait `> 50`) que les tests ne devaient surtout pas
> « valider »… Le détail, le code et les tests générés : {URL}/demo
> RevIA est gratuit pour commencer, open source, fonctionne avec des modèles gratuits ou en local.

### Show HN (EN, titre)
`Show HN: RevIA – AI-generated unit tests that are proven by mutation testing in your own CI`

## 6. Croissance

- **Lien dans chaque commentaire de PR** (« Reviewed by RevIA ») : chaque PR d'un client est vue par toute son équipe.
- **Gratuit pour l'open source** (dépôts publics) : visibilité, étoiles GitHub, contributions.
- **GitHub App** (installation en 1 clic) : voir `docs/GITHUB-APP.md`.
- **Prochaines intégrations demandées par les équipes BA / QA** : import Jira / Azure DevOps des user stories,
  export Xray / TestRail des cas de test.
- **Prix** : garde 9 € / 19 € pendant les pilotes ; après 10 clients payants, teste 19 € / 49 € (les équipes QA en
  entreprise comparent avec des outils à 30–60 $ par utilisateur).
