# RevIA en bref

> Version courte. Tous les détails sont dans [README.md](README.md).
> Équipe : Nafissa Bridah · Rahma Kehrji · Housine Saihi · Aziz Eifa · Mustapha Sifaoui
> Code : https://github.com/nafissabridah/RevIA

## 🎯 Le projet en une phrase

**RevIA relit ton code comme un collègue.** Tu colles un code, l'IA trouve les failles et les bugs, explique pourquoi c'est un problème et propose la correction.

## 👤 Ce que l'utilisateur peut faire

- ✍️ Coller ou importer un code (Java, Python, JS, TS, PHP, C, C#…)
- 🔍 Recevoir un **rapport** : score /100, résumé, problèmes classés par gravité
- 🛠️ Voir la **correction** : avant / après, explication, référence OWASP
- 🚩 Signaler un **faux positif** (le score se recalcule)
- 📜 Consulter son **historique** (recherche, filtres, export CSV)
- 📊 Suivre son **tableau de bord** (progression, problèmes récurrents)
- 🛡️ **Admin** : gérer les utilisateurs, les quotas et le prompt système

## 🧰 Technologies

| Partie | Outils (version) |
|---|---|
| 🖥️ Front-end | Angular 17.3 · TypeScript 5.4 · Angular Material 17.3 · Monaco (éditeur) · ng2-charts 6 |
| ⚙️ Back-end | Spring Boot 3.5 · Java 17 · Spring Security + JWT · Bean Validation · Bucket4j |
| 🤖 IA | Spring AI 1.1.8 + **Google Gemini** |
| 🗄️ Base | PostgreSQL (prod) · H2 (local) · JPA · Flyway |
| 🚀 Déploiement | Docker · Render (API) · Vercel (front) · GitHub Actions (CI) |

## 🤖 Le LLM choisi : Google Gemini

| | |
|---|---|
| **Modèle** | `gemini-flash-lite-latest` |
| **Pourquoi** | **gratuit** (la fiche de l'atelier impose des API gratuites), rapide (≈ 1,5 s), précis sur notre jeu d'essai |
| **Clé** | créée sur aistudio.google.com, rangée dans `.env` sur le serveur, **jamais** sur GitHub ni dans le navigateur |
| **Autres choix possibles** | Groq, Ollama (local), OpenAI, Claude : il suffit de changer `REVIA_AI_PROVIDER` |

ℹ️ Nous avons d'abord testé `gemini-flash-latest`, mais son offre gratuite est limitée à **20 analyses par jour**. Nous sommes donc passés à la version *lite*.

## 🧠 Où intervient le LLM ?

Uniquement pour **analyser le code**, côté serveur (`AnalyseurIA.java`) :

1. ✂️ **Découpage** : le code est coupé en morceaux de 300 lignes
2. 🙈 **Masquage** : mots de passe, clés et e-mails deviennent `***MASQUÉ***` avant l'envoi
3. 📨 **Appel à Gemini** : température 0,2, délai de 30 s, prompt système versionné, réponse forcée en JSON
4. ✅ **Validation** : le JSON est vérifié (lignes existantes, gravités…), avec 1 nouvelle tentative si besoin
5. 📋 **Affichage** du rapport

💡 Sans clé, le mode **Démo** (analyse par règles) permet de tout tester hors ligne.

## 🧪 Tests réalisés

| Test | Résultat |
|---|---|
| Tests back-end (JUnit 5, Mockito, MockMvc) | ✅ 42 / 42 |
| Tests front-end (Jasmine / Karma) | ✅ 14 / 14 |
| **Jeu d'essai avec Gemini** : 20 codes avec un défaut connu | ✅ **19 / 20 détectés (95 %)**, objectif ≥ 85 % |
| **Jeu d'essai avec Gemini** : 3 codes sains | ✅ **0 faux positif**, objectif ≤ 1 |
| Jeu d'essai en mode Démo | ✅ 20 / 20 et 0 faux positif |
| Parcours complet dans le navigateur | ✅ 0 erreur |
| Profil production sur PostgreSQL | ✅ OK |
| CI GitHub Actions (tests + build Docker) | ✅ Verte |
| Erreurs de l'API (délai, quota, clé absente) | ✅ messages clairs, testés |

📄 Détail du test Gemini : [docs/rapport-jeu-essai-gemini.md](docs/rapport-jeu-essai-gemini.md)

## 📐 La présentation est-elle respectée ?

**Oui.** Tout ce qui est prévu dans la présentation est réalisé.

- ✅ Technologies et versions (Angular 17, Spring Boot 3, Java 17, Spring AI, PostgreSQL…)
- ✅ Diagramme de classes : Utilisateur, Développeur, Administrateur, AnalyseurIA, PromptSysteme, Revue, Problème, Correction, Gravité, TableauDeBord
- ✅ Les 11 cas d'utilisation
- ✅ Module IA en 5 étapes (300 lignes, masquage, 0,2 / 30 s, validation JSON + 1 nouvelle tentative)
- ✅ Sécurité : BCrypt, JWT, quota de 50 revues par jour, limitation de débit (Bucket4j)
- ✅ Maquettes et couleurs des écrans (diapos 12 à 14)
- ✅ Messages d'erreur de l'annexe
- ✅ Objectifs de test (≥ 85 % de détection, ≤ 1/3 faux positif), atteints **avec un vrai LLM**

**Petits écarts assumés** :

- La présentation cite « OpenAI ou Claude », qui sont payants. Nous utilisons **Gemini**, gratuit, comme l'exige la fiche. OpenAI et Claude restent disponibles.
- Le score de l'exemple vaut **36** au lieu de 38 : c'est la formule de la présentation qui donne 36.
- La correction est un objet {avant, après, justification}, pour pouvoir afficher le diff.
- Deux diapos se contredisent sur l'administrateur. Le code suit le diagramme de classes.

## 📋 La fiche « AI App Studio » est-elle respectée ?

- ✅ Application web qui appelle une **API d'IA gratuite** (Gemini)
- ✅ Clé dans `.env`, **côté serveur**, jamais sur GitHub
- ✅ Tests avec mocks, sans appeler la vraie API
- ✅ Solution de secours si l'API ne répond pas (messages clairs + mode Démo)
- ✅ Code sur GitHub, CI verte
- ✅ Application démontrable en fonctionnement

## ▶️ Lancer l'application

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start.ps1
```

Ouvrir 👉 **http://localhost:8085**

| Compte | Email | Mot de passe |
|---|---|---|
| Développeur | sarra@revia.local | Sarra2026! |
| Admin | admin@revia.local | Admin2026! |

🔑 Pour utiliser Gemini, créer un fichier `.env` à la racine du projet :

```
REVIA_AI_PROVIDER=gemini
REVIA_AI_API_KEY=<votre clé Gemini>
```
