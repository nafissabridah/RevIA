"""Tests of revia_ci.py helpers (standard library only): python -m unittest integrations/test_revia_ci.py"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import revia_ci as ci  # noqa: E402


class TestBlocks(unittest.TestCase):
    SRC = "def f():\n    return 1\n"

    def test_ignores_a_copy_of_the_source(self):
        md = "```python\n%s```\nTests:\n```python\nimport unittest\nclass T(unittest.TestCase):\n" \
             "    def test_f(self):\n        self.assertEqual(f(), 1)\n```" % self.SRC
        self.assertIn("def test_f", ci.test_block(md, self.SRC))
        self.assertIsNone(ci.test_block("```python\n%s```" % self.SRC, self.SRC))

    def test_bugs_section(self):
        md = "```python\nx\n```\n### Bugs probables détectés\n- ligne 3 : borne incluse\n- aucun\n"
        self.assertEqual(ci.likely_bugs(md), ["ligne 3 : borne incluse"])


class TestRuns(unittest.TestCase):
    def ran_nothing(self, out):
        return bool(ci.NO_TESTS_RAN.search(out)) and not ci.SOME_TESTS_RAN.search(out)

    def test_green_run_without_tests_is_not_a_proof(self):
        self.assertTrue(self.ran_nothing("Ran 0 tests in 0.000s\n\nOK"))
        self.assertTrue(self.ran_nothing("no tests ran in 0.01s"))
        self.assertFalse(self.ran_nothing("Ran 4 tests in 0.002s\n\nOK"))
        self.assertFalse(self.ran_nothing("Tests run: 0, Failures: 0\nTests run: 6, Failures: 0"))


class TestMutants(unittest.TestCase):
    def test_spread_and_skip_strings_and_comments(self):
        code = "# a == b\nx = 'a == b'\nif a == b:\n    y = c + d\nreturn True\n"
        muts = ci.mutants(code, 10)
        self.assertEqual([m[0] for m in muts], [2, 3, 4])
        self.assertEqual(muts[0][2], "if a != b:")
        self.assertEqual(muts[2][2], "return False")
        self.assertEqual(ci.mutants(code, 0), [])

    def test_same_size_mutant_is_not_run_from_stale_bytecode(self):
        # Seen in a real run: "a - b" -> "a + b" written in the same second kept the old .pyc
        import shutil
        import tempfile
        d = tempfile.mkdtemp()
        try:
            src = os.path.join(d, "calc.py")
            code = "def diff(a, b):\n    return a - b\n"
            with open(src, "w", newline="") as fh:
                fh.write(code)
            with open(os.path.join(d, "test_calc.py"), "w") as fh:
                fh.write("import unittest\nfrom calc import diff\n\n\nclass T(unittest.TestCase):\n"
                         "    def test_diff(self):\n        self.assertEqual(diff(3, 1), 2)\n")
            cmd = 'cd %s && "%s" -m unittest test_calc' % (ci.quote(d), sys.executable)
            self.assertTrue(ci.run_tests(cmd, "test_calc.py")[0])
            score, _ = ci.mutation_score(src, code, cmd, "test_calc.py", 5)
            self.assertEqual(score, 100)
            self.assertFalse(os.path.isdir(os.path.join(d, "__pycache__")))
        finally:
            shutil.rmtree(d, ignore_errors=True)


class TestDocumentation(unittest.TestCase):
    """The code contradicts its doc only if RevIA's test of the DOCUMENTED behaviour really fails on the runner."""
    SRC = ('def livraison(total):\n    """-5 EUR a partir de 50 EUR d\'achat."""\n'
           '    return total - 5 if total > 50 else total\n')

    def run_doc(self, assertion):
        import shutil
        import tempfile
        d = tempfile.mkdtemp()
        old = os.getcwd()
        try:
            os.chdir(d)
            with open("panier.py", "w") as fh:
                fh.write(self.SRC)
            answer = ("Tests.\n```python\nimport unittest\n```\n\n### Contradictions documentation / code\n"
                      "- ligne 3 : la doc dit a partir de 50 (>= 50), le code fait > 50\n\n```python\n"
                      "import unittest\nfrom panier import livraison\n\n\nclass T(unittest.TestCase):\n"
                      "    def test_doc(self):\n        %s\n```\n" % assertion)
            main, doc = ci.split_doc_section(answer)
            self.assertNotIn("Contradictions", main)
            cmd = '"%s" -m unittest {test}' % sys.executable
            found = ci.check_documentation(cmd, "panier.py", self.SRC, "tests/test_revia_panier.py", doc)
            self.assertFalse(os.path.exists("tests/test_revia_panier_doc.py"))  # never left behind
            return found
        finally:
            os.chdir(old)
            shutil.rmtree(d, ignore_errors=True)

    def test_confirmed_when_the_doc_test_fails(self):
        found = self.run_doc("self.assertEqual(livraison(50), 45)")
        self.assertEqual(len(found), 1)
        self.assertIn("a partir de 50", found[0][0])
        self.assertIn("50 != 45", found[0][1])

    def test_line_found_from_the_quoted_code_not_from_the_ai(self):
        claim = "**Line 18**: the docstring says `a partir de 50`, but the code uses `total > 50`."
        self.assertEqual(ci.locate(claim, self.SRC),
                         "line 3: the docstring says `a partir de 50`, but the code uses `total > 50`.")
        self.assertEqual(ci.locate("ligne 9 : rien de cité", self.SRC), "rien de cité")

    def test_ai_wrong_or_broken_test_reports_nothing(self):
        self.assertEqual(self.run_doc("self.assertEqual(livraison(60), 55)"), [])  # doc respected: AI was wrong
        self.assertEqual(self.run_doc("self.assertEqual(livraisonn(50), 45)"), [])  # NameError: proves nothing
        self.assertEqual(ci.check_documentation("x", "a.py", "", "tests/t.py", ""), [])


class TestInline(unittest.TestCase):
    def test_hunks_to_changed_lines(self):
        diff = "@@ -1,0 +1,3 @@\n+a\n+b\n+c\n@@ -10 +12 @@\n-x\n+y\n"
        lines = set()
        for m in ci.HUNK.finditer(diff):
            start, count = int(m.group(1)), int(m.group(2) or 1)
            lines.update(range(start, start + count))
        self.assertEqual(lines, {1, 2, 3, 12})

    def test_one_line_fix_becomes_a_github_suggestion(self):
        src = ["def f(total):", "    if total >= 100:", "        return 1"]
        p = {"ligne": 2, "gravite": "critique", "titre": "Borne", "explication": "e",
             "correction": {"codeAvant": "if total >= 100:", "codeApres": "if total > 100:"}}
        body = ci.inline_body("a.py", src, p)
        self.assertIn("```suggestion\n    if total > 100:\n```", body)
        self.assertIn(ci.inline_marker("a.py", 2, "Borne"), body)


if __name__ == "__main__":
    unittest.main()
