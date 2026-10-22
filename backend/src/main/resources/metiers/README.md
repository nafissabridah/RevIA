# Packs métier RevIA

Un **pack métier** est un fichier YAML qui décrit les règles, les pièges classiques, les réglementations et le
vocabulaire d'un domaine (finance, assurance, santé…). Il s'écrit et se modifie **sans programmer**.

## À quoi il sert

Dans RevIA, le sélecteur **« Métier »** (page Outils, pages d'analyse métier BA, page Flowchart → tests) permet de
choisir un pack. Son contenu est alors ajouté aux consignes envoyées à l'IA (taille limitée automatiquement) :
la rédaction et la revue d'exigences, les cas de test, la revue de code et l'analyse de flowchart appliquent ainsi
les règles du domaine et citent leur identifiant (ex. `FIN-03`).

## Packs fournis

| Fichier | Domaine | Préfixe des règles |
|---|---|---|
| `generique.yml` | Tous domaines | `GEN` |
| `finance.yml` | Finance et marchés | `FIN` |
| `assurance.yml` | Assurance | `ASS` |
| `banque-paiement.yml` | Banque et paiements | `PAY` |
| `sante.yml` | Santé | `SAN` |
| `ecommerce.yml` | E-commerce | `ECO` |
| `rh.yml` | RH et paie | `RH` |
| `secteur-public.yml` | Secteur public | `PUB` |

## Format d'un fichier

```yaml
id: "finance"                    # identique au nom du fichier, sans .yml (minuscules, chiffres, tirets)
nom:
  fr: "Finance et marchés"       # nom affiché en français
  en: "Finance & markets"        # nom affiché en anglais
description: "Une ou deux phrases en français."
description_en: "The same in English."
glossaire:                       # terme : définition
  "Date de valeur": "Date à laquelle un mouvement produit ses effets financiers."
regles:                          # règles de gestion du domaine
  - id: "FIN-01"                 # PRÉFIXE-NN, unique dans tous les packs
    texte: "La règle, formulée de façon vérifiable."
    pourquoi: "Pourquoi elle compte."
    exemple_bug: "Un bug typique quand on l'oublie."
pieges:                          # défauts classiques à tester
  - titre: "Montants en virgule flottante"
    description: "Le défaut et son mécanisme."
    test: "Le test concret qui l'attrape."
reglementations:                 # rappels, pas des avis juridiques
  - nom: "MiFID II"
    rappel: "Un rappel d'une ligne."
questions_ba:                    # questions qu'un analyste métier senior pose dans ce domaine
  - "Quelle convention d'arrondi s'applique ?"
```

Seules ces clés sont acceptées : une clé mal orthographiée (`regle` au lieu de `regles`) est refusée.

### Règles d'écriture

- **Identifiants de règles** : un préfixe de 2 à 4 majuscules propre au pack, un tiret, deux chiffres (`ECO-07`).
  Un identifiant ne doit jamais être réutilisé, même dans un autre pack.
- **Guillemets** : mettez chaque texte entre guillemets doubles `"…"`. À l'intérieur, utilisez plutôt les
  guillemets français « … » ; un guillemet double doit s'écrire `\"`.
- **Indentation** : deux espaces, jamais de tabulation. Chaque élément de liste commence par `- `.
- **Textes courts** : une ou deux phrases par champ (environ 350 caractères au plus).
- **Pas de données réelles** : aucun vrai numéro de carte, IBAN, e-mail ou clé d'API dans les exemples ;
  décrivez-les (« un numéro de carte de test »).

## Ajouter un pack

1. Copiez `generique.yml` et renommez la copie, par exemple `telecom.yml`.
2. Changez `id` (`"telecom"`), le nom, la description, puis remplacez les règles, pièges, réglementations,
   questions et termes par ceux du domaine, avec un nouveau préfixe (`TEL-01`, `TEL-02`…).
3. Redémarrez l'application : le pack apparaît dans le sélecteur « Métier ».

Deux emplacements possibles :
- **dans ce dossier** (`backend/src/main/resources/metiers/`) : le pack est livré avec l'application
  (il faut reconstruire le JAR) ;
- **dans un dossier externe**, sans reconstruire : indiquez son chemin dans la variable d'environnement
  `REVIA_METIERS_DOSSIER` (ex. `REVIA_METIERS_DOSSIER=/srv/revia/metiers`), déposez-y vos fichiers `.yml`
  et redémarrez. Les packs fournis restent disponibles ; un identifiant de pack ou de règle ne peut pas être
  utilisé deux fois.

Si un fichier est invalide (YAML mal formé, champ obligatoire vide, clé inconnue, identifiant en double),
**l'application refuse de démarrer** et le message d'erreur indique le fichier et le champ en cause
(ex. `Pack métier invalide (metiers/telecom.yml) : regles[2].pourquoi est obligatoire.`). Corrigez puis relancez.

## Avertissement

Les réglementations citées sont des **rappels** destinés à orienter l'analyse et les tests. Elles ne constituent
pas un avis juridique : faites valider les exigences réglementaires par vos équipes conformité ou juridique.
