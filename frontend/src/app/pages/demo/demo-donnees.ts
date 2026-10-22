/**
 * Données de la page publique /demo : copie VERBATIM d'un vrai run du script CI (integrations/revia_ci.py)
 * du 2026-10-22, avec le modèle gratuit Gemini (gemini-flash-latest, repli automatique sur
 * gemini-flash-lite-latest sur erreur 503). Aucun appel à l'IA au chargement de la page, aucun chiffre inventé :
 * les nombres affichés sont lus dans ces textes.
 *
 * Run affiché : le 5e run Gemini de la journée (fichier scratchpad gemini-run5.txt), le premier avec la détection des
 * contradictions documentation / code. La ligne de log précédant le commentaire et le code de sortie ont été retirés.
 */

export const RUN_DEMO = {
  date: '2026-10-22',
  /** Le numéro de ligne donné par l'IA dans la capture (avant que RevIA ne localise lui-même la ligne). */
  ligneIndiqueeParIa: 18,
  modele: 'gemini-flash-latest',
  modeleRepli: 'gemini-flash-lite-latest',
  fichierSource: 'panier.py',
  fichierTests: 'tests/test_revia_panier.py',
  /** Score de mutation du run précédent sur le même fichier (il avait fallu des tours de réparation). */
  scoreRunPrecedent: 86,
};

/** Code testé, tel quel. */
export const SOURCE_PANIER = `"""Panier d'achat : total, TVA et code promo."""

TVA = 0.20


def total_ht(articles):
    """Somme prix x quantité. Une quantité négative est refusée."""
    total = 0.0
    for prix, quantite in articles:
        if quantite < 0:
            raise ValueError("quantité négative")
        total += prix * quantite
    return round(total, 2)


def total_ttc(articles):
    """Total TTC arrondi au centime."""
    return round(total_ht(articles) * (1 + TVA), 2)


def appliquer_code(total, code):
    """BIENVENUE10 : -10 % ; LIVRAISON : -5 € à partir de 50 € d'achat (jamais en dessous de 0)."""
    if code == "BIENVENUE10":
        return round(total * 0.9, 2)
    if code == "LIVRAISON" and total > 50:
        return max(0.0, round(total - 5, 2))
    return total
`;

/** Fichier de tests GARDÉ par RevIA au run 5 : exécuté dans la CI, vert, puis vérifié par mutation. Tel quel. */
export const TESTS_PANIER = `import unittest
from panier import total_ht, total_ttc, appliquer_code


class TestRevIaPanier(unittest.TestCase):

    def test_total_ht_nominal(self):
        articles = [(10.0, 2), (5.5, 3)]
        # (10 * 2) + (5.5 * 3) = 20 + 16.5 = 36.5
        self.assertEqual(total_ht(articles), 36.5)

    def test_total_ht_empty(self):
        self.assertEqual(total_ht([]), 0.0)

    def test_total_ht_negative_quantity_raises(self):
        articles = [(10.0, -1)]
        with self.assertRaises(ValueError) as ctx:
            total_ht(articles)
        self.assertEqual(str(ctx.exception), "quantité négative")

    def test_total_ht_zero_quantity(self):
        # Mutant killer: detects if quantity 0 incorrectly raises ValueError (<= 0 instead of < 0)
        articles = [(10.0, 0)]
        self.assertEqual(total_ht(articles), 0.0)

    def test_total_ttc_nominal(self):
        articles = [(10.0, 2)]  # HT: 20.0
        # TTC: 20.0 * 1.20 = 24.0
        self.assertEqual(total_ttc(articles), 24.0)

    def test_appliquer_code_bienvenue10(self):
        total = 100.0
        # 100 * 0.9 = 90.0
        self.assertEqual(appliquer_code(total, "BIENVENUE10"), 90.0)

    def test_appliquer_code_livraison_above_threshold(self):
        total = 60.0
        # 60 - 5 = 55.0
        self.assertEqual(appliquer_code(total, "LIVRAISON"), 55.0)

    def test_appliquer_code_unknown(self):
        total = 50.0
        self.assertEqual(appliquer_code(total, "UNKNOWN"), 50.0)

    def test_appliquer_code_livraison_floor_zero(self):
        total = 3.0
        # Max floor at 0.0 (though threshold condition prevents reaching here currently)
        self.assertEqual(appliquer_code(total, "LIVRAISON"), 3.0)


if __name__ == "__main__":
    unittest.main()
`;

/**
 * Commentaire posté sur la pull request par le script CI au run 5 (Markdown). Une seule différence avec la capture :
 * l'IA avait écrit « **Line 18** » ; le script localise désormais lui-même la vraie ligne (« line 25 », celle du
 * « total > 50 ») et la ligne est reproduite ici dans ce format corrigé.
 */
export const COMMENTAIRE_PR = `## 🛡️ RevIA code review

✅ **Quality gate passed** — score >= 70 and no critical issue.

| File | Score | Critical | Gate | Report |
|---|---|---|---|---|
| \`panier.py\` | 100 | 0 | ok | [open](http://127.0.0.1:8096/revues/129) |

## 🧪 RevIA proven tests

Every test file below was **generated, executed in this pipeline and passes**. The mutation score is the share of bugs RevIA injected into your code that the tests caught.

| Source | Generated tests | Status | Mutation score |
|---|---|---|---|
| \`panier.py\` | \`tests/test_revia_panier.py\` | ✅ proven (green) | 100% |

### 📄 Code that contradicts its own documentation (observed on this runner, to confirm)

RevIA wrote a test of what the documentation promises and it **failed on your code** (that test was then deleted):

- \`panier.py\`: line 25: The docstring specifies \`-5 € à partir de 50 € d'achat\`, but the implementation uses \`total > 50\`, excluding an exact total of 50.0 €. — observed: \`AssertionError: 50.0 != 45.0\`

<sub>Commit the generated files to keep them (e.g. upload them as a build artifact).</sub>

<sub>Generated by RevIA · secrets are masked before any AI call.</sub>`;

/** Nombre de méthodes de test du fichier généré (compté, pas recopié). */
export function nombreDeTests(tests: string): number {
  return (tests.match(/^\s*def test_\w+\(/gm) ?? []).length;
}

/** Score de mutation (en %) lu dans la ligne « proven » du commentaire de PR, ou null s'il est absent. */
export function scoreMutation(commentaire: string): number | null {
  const m = /proven \(green\)\s*\|\s*(\d+)\s*%/.exec(commentaire);
  return m ? Number(m[1]) : null;
}

/** Score de revue lu dans la ligne du fichier source du tableau « code review », ou null. */
export function scoreRevue(commentaire: string, fichier: string): number | null {
  const echappe = fichier.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const m = new RegExp('^\\|\\s*`' + echappe + '`\\s*\\|\\s*(\\d+)\\s*\\|', 'm').exec(commentaire);
  return m ? Number(m[1]) : null;
}
