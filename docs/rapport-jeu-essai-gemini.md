# Jeu d'essai IA avec un vrai LLM : Google Gemini (gratuit)

Exécuté le 17/10/2026 avec `backend/target/revia.jar` à travers l'API RevIA : masquage → Gemini `gemini-flash-lite-latest` (prompt système final) → validation JSON. Température 0,2, délai 30 s.

Critère : un problème signalé à ± 2 lignes du défaut attendu.

**Résultat : 19 / 20 défauts détectés (95 %, objectif ≥ 85 %) · 0 / 3 faux positif (objectif ≤ 1) · 0 erreur · 1,5 s par analyse en moyenne.**

Seul raté : `10_ressource_journal.c`. Gemini signale bien la fuite du fichier, mais à la ligne 8 (le `return`), pas à la ligne 4 (le `fopen`).

## Historique des essais

| Essai | Modèle | Prompt | Détectés | Faux positifs | Remarque |
|---|---|---|---|---|---|
| 1 | gemini-flash-latest | v1 | 14 analyses réussies sur 14 | — | quota gratuit de 20 requêtes par jour atteint (9 refus correctement affichés) |
| 2 | gemini-flash-lite-latest | v1 | 17 / 20 | 0 / 3 | secrets masqués non signalés |
| 3 | gemini-flash-lite-latest | v1 + règle « ***MASQUÉ*** = secret en dur » | 18 / 20 | 0 / 3 | |
| 4 (final) | gemini-flash-lite-latest | final (+ consigne « français correct ») | **19 / 20** | **0 / 3** | |

## Détail de l'essai final

| Extrait | Résultat | Problèmes trouvés par Gemini | Durée |
|---|---|---|---|
| 01_UserService.java | ✅ détecté (attendu : Injection SQL L8) | Secret en dur dans le code (L3, critique), Injection SQL (L8, critique), Journalisation de données sensibles (L12, majeure), Dépassement d'indice de tableau (L20, critique) | 4 s |
| 02_sql_produits.php | ✅ détecté (attendu : Injection SQL L4) | Injection SQL (L4, critique) | 1 s |
| 03_sql_commandes.py | ✅ détecté (attendu : Injection SQL L6) | Injection SQL (L6, critique) | 1 s |
| 04_sql_auth.py | ✅ détecté (attendu : Injection SQL L3) | Injection SQL (L3, critique) | 2 s |
| 05_secret_Mailer.java | ✅ détecté (attendu : Mot de passe en dur L2) | Secret en dur dans le code (L2, critique), Message vide et destinataire manquant (L6, majeure) | 2 s |
| 06_secret_paiement.js | ✅ détecté (attendu : Secret en dur dans le code L3) | Secret en dur dans le code (L3, critique) | 1 s |
| 07_secret_meteo.py | ✅ détecté (attendu : Secret en dur dans le code L3) | Secret en dur dans le code (L3, critique) | 1 s |
| 08_ressource_RapportDao.java | ✅ détecté (attendu : Ressources non fermées L3) | Absence de fermeture des ressources JDBC (L3, majeure) | 2 s |
| 09_ressource_Import.java | ✅ détecté (attendu : Ressources non fermées L3) | Absence de fermeture des ressources (L3, majeure) | 3 s |
| 10_ressource_journal.c | ❌ manqué (attendu : Ressources non fermées L4) | Fuite de descripteur de fichier (L8, majeure) | 1 s |
| 11_bornes_notes.c | ✅ détecté (attendu : Dépassement d'indice dans la boucle L7) | Dépassement de tampon en lecture (L7, majeure) | 1 s |
| 12_bornes_Panier.java | ✅ détecté (attendu : Dépassement d'indice dans la boucle L6) | Depassement d'indice de tableau (L6, majeure) | 1 s |
| 13_bornes_inverse.py | ✅ détecté (attendu : Dépassement d'indice dans la boucle L3) | Erreur d'indice hors limites (L3, critique) | 2 s |
| 14_bornes_majuscules.c | ✅ détecté (attendu : Dépassement d'indice dans la boucle L5) | Depassement de tampon en lecture et ecriture (L5, majeure) | 1 s |
| 15_xss_commentaires.js | ✅ détecté (attendu : Faille XSS L3) | Injection de code intersite (L3, critique) | 1 s |
| 16_xss_recherche.ts | ✅ détecté (attendu : Faille XSS L3) | Injection de code intersite (L3, critique) | 1 s |
| 17_xss_bonjour.php | ✅ détecté (attendu : Faille XSS L3) | Faille XSS Reflechie (L3, critique) | 1 s |
| 18_null_Profil.java | ✅ détecté (attendu : Déréférencement possible de null L6) | Risque de pointeur nul (L6, majeure) | 1 s |
| 19_division_stats.js | ✅ détecté (attendu : Division par zéro possible L6) | Division par zéro potentielle (L6, majeure) | 1 s |
| 20_comparaison_Role.java | ✅ détecté (attendu : Comparaison de chaînes avec == L4) | Comparaison de chaînes avec l'opérateur d'égalité (L4, majeure), Utilisation de constante littérale à gauche (L4, mineure) | 2 s |
| 21_sain_ClientRepository.java | ✅ aucun problème grave (attendu : sain L-) | — | 1 s |
| 22_sain_panier.js | ✅ aucun problème grave (attendu : sain L-) | — | 1 s |
| 23_sain_notes.py | ✅ aucun problème grave (attendu : sain L-) | — | 1 s |
