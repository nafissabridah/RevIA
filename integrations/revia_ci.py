#!/usr/bin/env python3
"""
RevIA CI - reviews the files changed in a pull/merge request and fails the build below a quality gate.
Optionally: checks the PR against its acceptance criteria, and generates *proven* tests (run in this pipeline,
repaired until green, then mutation-checked) - the AI never hands you a test it has not executed.

Works on GitHub Actions, GitLab CI, Jenkins, Azure DevOps, Bitbucket... (Python 3.8+, standard library only).
The generated tests run here, on your CI runner: your code is never executed on the RevIA server.

Environment variables:
  REVIA_URL        Base URL of your RevIA server (e.g. https://revia.example.com)       [required]
  REVIA_TOKEN      Personal API token created in RevIA > Settings > CI integration     [required]
  REVIA_MIN_SCORE  Fail if any file scores below this value (default 70)
  REVIA_FAIL_ON_CRITICAL  "true" (default) fails if a critical issue is found
  REVIA_BASE       Git ref to diff against (default: origin/$GITHUB_BASE_REF, $CI_MERGE_REQUEST_TARGET_BRANCH_NAME or HEAD~1)
  REVIA_FILES      Optional explicit space-separated file list (skips git diff)
  REVIA_SARIF      Output SARIF path (default revia.sarif)
  REVIA_LANG       Report language: en (default), fr, es, de, pt, it, nl, pl, tr, ar, ru, hi, zh, ja, ko
  REVIA_COMMENT    "true" (default) posts / updates one summary comment on the pull / merge request:
                   GitHub needs GITHUB_TOKEN (permissions: pull-requests: write),
                   GitLab needs GITLAB_TOKEN (project access token with the "api" scope).

  Acceptance criteria (does the PR do what the user story asks?):
  REVIA_AC         "auto" (default): read the criteria from REVIA_AC_FILE, else the PR / MR description;
                   "false" disables the check
  REVIA_AC_FILE    File holding the user story / acceptance criteria (bullets or Gherkin scenarios)
  REVIA_MIN_AC_COVERAGE  Fail if criteria coverage (%) is below this value (default 0 = report only)

  Proven tests (generated, executed, repaired, mutation-checked):
  REVIA_PROVEN_TESTS     "true" to enable (default "false")
  REVIA_TEST_CMD         Command that runs ONE test file; {test} = test file path, {class} = test class name.
                         Defaults: Python "python -m pytest -q {test}", JS/TS "npx jest {test}",
                         Java "mvn -q -B test -Dtest={class} -Dsurefire.failIfNoSpecifiedTests=false",
                         Go "go test ./{dir}/"
  REVIA_MAX_FIX          Repair rounds when the generated tests fail (default 3)
  REVIA_MUTANTS          Mutants injected per file to prove the tests catch bugs (default 8, 0 = skip)
  REVIA_MIN_MUTATION     Fail if a proven test file kills fewer mutants (%) than this (default 0 = report only)
  REVIA_PROVEN_MAX_FILES Source files handled per run (default 5)
Exit code: 0 = gate passed, 1 = gate failed, 2 = configuration / server error.
"""
import json
import os
import re
import shlex
import subprocess
import sys
import urllib.error
import urllib.request

EXTENSIONS = {".java", ".py", ".js", ".jsx", ".ts", ".tsx", ".php", ".c", ".h", ".cpp", ".cc", ".hpp", ".cs",
              ".go", ".rb", ".kt", ".swift", ".rs", ".sql"}
MAX_LINES = 2000
VERSION = "revia-ci/1.3"


def env(name, default=None):
    v = os.environ.get(name, "").strip()
    return v if v else default


def base_ref():
    base = env("REVIA_BASE")
    if not base:
        if env("GITHUB_BASE_REF"):
            base = "origin/" + env("GITHUB_BASE_REF")
        elif env("CI_MERGE_REQUEST_TARGET_BRANCH_NAME"):
            base = "origin/" + env("CI_MERGE_REQUEST_TARGET_BRANCH_NAME")
        else:
            base = "HEAD~1"
    return base


def git_diff(options, paths=()):
    """git diff <options> <base>...HEAD -- <paths> (falls back to <base> when there is no merge base)."""
    base = base_ref()
    try:
        return subprocess.check_output(["git", "diff"] + options + [base + "...HEAD", "--"] + list(paths),
                                       text=True, errors="replace")
    except subprocess.CalledProcessError:
        return subprocess.check_output(["git", "diff"] + options + [base, "--"] + list(paths),
                                       text=True, errors="replace")


def changed_files():
    explicit = env("REVIA_FILES")
    if explicit:
        return explicit.split()
    out = git_diff(["--name-only", "--diff-filter=ACMR"])
    return [f for f in out.splitlines() if f.strip()]


MARKER = "<!-- revia-review -->"
SEVERITY_ICON = {"critique": "🔴", "majeure": "🟠", "mineure": "🟡", "info": "🔵"}
SEVERITY_ORDER = {"critique": 0, "majeure": 1, "mineure": 2, "info": 3}


def call(method, url, token, body=None, timeout=120):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={
        "Authorization": "Bearer " + token, "Content-Type": "application/json", "Accept": "application/json",
        "X-RevIA-Langue": env("REVIA_LANG", "en"), "User-Agent": VERSION})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8"))


def http_json(method, url, headers, body=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    h = {"Content-Type": "application/json", "Accept": "application/json", "User-Agent": VERSION}
    h.update(headers)
    with urllib.request.urlopen(urllib.request.Request(url, data=data, method=method, headers=h), timeout=30) as r:
        raw = r.read().decode("utf-8")
        return json.loads(raw) if raw else None


def github_event():
    try:
        with open(env("GITHUB_EVENT_PATH"), encoding="utf-8") as fh:
            return json.load(fh)
    except (TypeError, OSError, ValueError):
        return {}


def upsert_comment(body):
    """Posts the summary on the PR / MR, or updates RevIA's previous comment (one comment per PR, no spam)."""
    if env("REVIA_COMMENT", "true").lower() != "true":
        return
    body = MARKER + "\n" + body
    try:
        if env("GITHUB_TOKEN") and env("GITHUB_REPOSITORY") and env("GITHUB_EVENT_PATH"):
            number = (github_event().get("pull_request") or {}).get("number")
            if not number:
                return
            api = "%s/repos/%s/issues" % (env("GITHUB_API_URL", "https://api.github.com"), env("GITHUB_REPOSITORY"))
            h = {"Authorization": "Bearer " + env("GITHUB_TOKEN"), "Accept": "application/vnd.github+json"}
            existing = [c for c in http_json("GET", "%s/%s/comments?per_page=100" % (api, number), h) or []
                        if MARKER in (c.get("body") or "")]
            if existing:
                http_json("PATCH", "%s/comments/%s" % (api, existing[0]["id"]), h, {"body": body})
            else:
                http_json("POST", "%s/%s/comments" % (api, number), h, {"body": body})
            print("RevIA: summary comment published on pull request #%s." % number)
        elif env("GITLAB_TOKEN") and env("CI_MERGE_REQUEST_IID") and env("CI_PROJECT_ID"):
            api = "%s/projects/%s/merge_requests/%s/notes" % (env("CI_API_V4_URL", "https://gitlab.com/api/v4"),
                                                              env("CI_PROJECT_ID"), env("CI_MERGE_REQUEST_IID"))
            h = {"PRIVATE-TOKEN": env("GITLAB_TOKEN")}
            existing = [n for n in http_json("GET", api + "?per_page=100", h) or [] if MARKER in (n.get("body") or "")]
            if existing:
                http_json("PUT", "%s/%s" % (api, existing[0]["id"]), h, {"body": body})
            else:
                http_json("POST", api, h, {"body": body})
            print("RevIA: summary note published on merge request !%s." % env("CI_MERGE_REQUEST_IID"))
    except (urllib.error.URLError, OSError, ValueError) as e:
        # The comment is a convenience: never fail the build because of it.
        print("::warning::RevIA could not publish the PR comment: %s" % e)


def md_escape(text):
    return "".join("\\" + ch if ch in "\\`*_[]<>|" else ch for ch in str(text)).replace("\n", " ")


def summary_markdown(rows, findings, min_score, fail_critical, failed):
    status = "❌ **Quality gate failed**" if failed else "✅ **Quality gate passed**"
    lines = ["## 🛡️ RevIA code review", "", status + " — score >= %d%s." % (
        min_score, " and no critical issue" if fail_critical else ""), "",
             "| File | Score | Critical | Gate | Report |", "|---|---|---|---|---|"]
    for f, s, c, g, l in rows:
        lines.append("| `%s` | %s | %s | %s | %s |" % (f, s, c, g, "[open](%s)" % l if l else ""))
    if findings:
        findings.sort(key=lambda x: (SEVERITY_ORDER.get(x[0], 9), x[1], x[2]))
        lines += ["", "### Top findings", ""]
        for sev, path, line, title, fix in findings[:15]:
            lines.append("- %s **%s** — `%s:%s`%s" % (SEVERITY_ICON.get(sev, "•"), md_escape(title), path, line,
                                                       " · fix suggested" if fix else ""))
        if len(findings) > 15:
            lines.append("- … and %d more (see the reports above or the Security tab)." % (len(findings) - 15))
    return "\n".join(lines)


# --------------------------------------------------------------------------------------------------------------
# Acceptance criteria coverage
# --------------------------------------------------------------------------------------------------------------
AC_ICON = {"couvert": "✅ covered", "partiel": "🟡 partial", "non_couvert": "❌ not covered"}
TEST_HINT = re.compile(r"(^|/)(tests?|spec|__tests__)(/|$)|(_test|\.test|\.spec|Test|Tests|_spec)\.[a-z]+$")
SKIP_DIFF = re.compile(r"(package-lock\.json|yarn\.lock|pnpm-lock\.yaml|\.min\.(js|css)|\.svg|\.png|\.jpe?g|\.lock)$")


def acceptance_criteria_text():
    if env("REVIA_AC_FILE"):
        with open(env("REVIA_AC_FILE"), encoding="utf-8", errors="replace") as fh:
            return fh.read()
    body = (github_event().get("pull_request") or {}).get("body")
    if body:
        return body
    return env("CI_MERGE_REQUEST_DESCRIPTION", "")


def pr_diff(max_lines=3800):
    """Diff of code + test files only (lock files, images and minified assets are left out)."""
    files = [f for f in changed_files() if not SKIP_DIFF.search(f)]
    if not files:
        return ""
    out = git_diff(["-U3", "--diff-filter=ACMR"], files)
    lines = out.splitlines()
    if len(lines) > max_lines:
        lines = lines[:max_lines] + ["... (diff truncated by RevIA CI)"]
    return "\n".join(lines)


def check_acceptance_criteria(base_url, token):
    """Returns (markdown, failed) or (None, False) when there is nothing to check."""
    if env("REVIA_AC", "auto").lower() == "false":
        return None, False
    criteria = (acceptance_criteria_text() or "").strip()
    if not criteria:
        print("RevIA: no acceptance criteria found (PR description or REVIA_AC_FILE): check skipped.")
        return None, False
    diff = pr_diff()
    if not diff.strip():
        return None, False
    try:
        r = call("POST", base_url + "/api/assistant/criteres", token, {"criteres": criteria[:20000], "diff": diff},
                 timeout=300)
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")[:300]
        if e.code == 400:
            print("RevIA: acceptance criteria not checked: %s" % detail)
            return None, False
        print("::warning::RevIA acceptance-criteria check failed: HTTP %s %s" % (e.code, detail))
        return None, False
    except urllib.error.URLError as e:
        print("::warning::RevIA acceptance-criteria check failed: %s" % e.reason)
        return None, False
    min_cov = int(env("REVIA_MIN_AC_COVERAGE", "0"))
    failed = r.get("couverture", 0) < min_cov
    lines = ["## 🎯 Acceptance criteria", "",
             "%s **%d%% of the user story covered** — %d covered · %d partial · %d not covered%s." % (
                 "❌" if failed else "✅" if r.get("nbNonCouverts", 0) == 0 else "⚠️", r.get("couverture", 0),
                 r.get("nbCouverts", 0), r.get("nbPartiels", 0), r.get("nbNonCouverts", 0),
                 " (gate: >= %d%%)" % min_cov if min_cov else ""), "",
             md_escape(r.get("resume", "")), "",
             "| ID | Criterion | Status | Evidence | Missing test |", "|---|---|---|---|---|"]
    for c in r.get("criteres", []):
        lines.append("| %s | %s | %s | %s | %s |" % (
            c.get("id"), md_escape(c.get("texte", ""))[:160], AC_ICON.get(c.get("statut"), c.get("statut")),
            "<br>".join(md_escape(p)[:140] for p in c.get("preuves") or []) or md_escape(c.get("explication", ""))[:160],
            md_escape(c.get("testManquant") or "")[:160]))
        if c.get("statut") == "non_couvert":
            print("::warning title=RevIA acceptance criteria::%s not covered: %s" % (c.get("id"), c.get("texte", "")))
    return "\n".join(lines), failed


# --------------------------------------------------------------------------------------------------------------
# Proven tests: generate -> run -> repair -> mutation-check
# --------------------------------------------------------------------------------------------------------------
CODE_BLOCK = re.compile(r"```[\w+#.-]*[ \t]*\n(.*?)\n```", re.S)
BUGS_SECTION = re.compile(r"(?is)(?:#+\s*|\*\*)\s*(?:bugs? probables?[^\n]*|likely bugs?[^\n]*|probable bugs?[^\n]*)\n(.*?)(?:\n#+\s|\Z)")
DEFAULT_CMD = {".py": "python -m pytest -q {test}", ".js": "npx jest {test}", ".jsx": "npx jest {test}",
               ".ts": "npx jest {test}", ".tsx": "npx jest {test}",
               ".java": "mvn -q -B test -Dtest={class} -Dsurefire.failIfNoSpecifiedTests=false",
               ".go": "go test ./{dir}/"}
# Mutation operators: (regex, replacement). Applied one at a time, on one line at a time.
MUTATIONS = [(r"==", "!="), (r"!=", "=="), (r" <= ", " < "), (r" >= ", " > "), (r" < ", " <= "), (r" > ", " >= "),
             (r"&&", "||"), (r"\|\|", "&&"), (r"\band\b", "or"), (r"\bor\b", "and"), (r" \+ ", " - "), (r" - ", " + "),
             (r" \* ", " / "), (r"\bTrue\b", "False"), (r"\bFalse\b", "True"), (r"\btrue\b", "false"),
             (r"\bfalse\b", "true"), (r"\+= 1\b", "-= 1"), (r"\+\+", "--")]
COMMENT = re.compile(r"^\s*(#|//|/?\*|import\b|from\b|package\b|using\b|@)")


def test_path(src):
    """Where the generated test file goes, following each ecosystem's convention."""
    d, name = os.path.split(src)
    stem, ext = os.path.splitext(name)
    if ext == ".py":
        return os.path.join("tests", "test_revia_%s.py" % stem)
    if ext in (".js", ".jsx", ".ts", ".tsx"):
        return os.path.join(d, "%s.revia.test%s" % (stem, ext))
    if ext == ".java":
        return src.replace("src/main/java", "src/test/java", 1)[:-len(name)] + stem + "ReviaTest.java" \
            if "src/main/java" in src else os.path.join(d, stem + "ReviaTest.java")
    if ext == ".go":
        return os.path.join(d, "%s_revia_test.go" % stem)
    return None


def quote(arg):
    """Shell quoting for the runner's shell (cmd.exe on Windows runners does not understand single quotes)."""
    if os.name != "nt":
        return shlex.quote(arg)
    return '"%s"' % arg if re.search(r"[\s&|<>^()]", arg) else arg


def run_tests(cmd_template, tpath, timeout=300):
    stem = os.path.splitext(os.path.basename(tpath))[0]
    cmd = cmd_template.replace("{test}", quote(tpath)).replace("{class}", stem) \
        .replace("{dir}", os.path.dirname(tpath) or ".")
    try:
        p = subprocess.run(cmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                           errors="replace", timeout=timeout)
        return p.returncode == 0, p.stdout
    except subprocess.TimeoutExpired as e:
        return False, "TIMEOUT after %ss\n%s" % (timeout, (e.stdout or "")[-4000:] if isinstance(e.stdout, str) else "")


def first_block(markdown):
    m = CODE_BLOCK.search(markdown or "")
    return m.group(1) if m else None


def likely_bugs(markdown):
    m = BUGS_SECTION.search(markdown or "")
    if not m:
        return []
    items = [re.sub(r"^\s*[-*\d.)]+\s*", "", l).strip() for l in m.group(1).splitlines()]
    return [i for i in items if i and not re.match(r"(?i)^(aucun|none|n/a|-)\.?$", i)][:5]


def tail(text, n=120):
    lines = (text or "").splitlines()
    return "\n".join(lines[-n:])


def mutants(code, limit):
    """Deterministic list of (line_no, original, mutated) spread over the file."""
    found = []
    for i, line in enumerate(code.split("\n")):
        if COMMENT.match(line) or not line.strip():
            continue
        for pattern, repl in MUTATIONS:
            m = re.search(pattern, line)
            # Skip matches inside a string literal: such a mutant changes nothing the tests can see
            if m and line[:m.start()].count('"') % 2 == 0 and line[:m.start()].count("'") % 2 == 0:
                found.append((i, line, line[:m.start()] + m.expand(repl) + line[m.end():]))
                break
    if limit <= 0 or not found:
        return []
    step = max(1, len(found) // limit)
    return found[::step][:limit]


def repair(base_url, token, path, code, tpath, tests, problem):
    text = "Code under test (%s):\n```\n%s\n```\n\nTest file (%s):\n```\n%s\n```\n\n%s" % (
        path, code, tpath, tests, problem)
    r = call("POST", base_url + "/api/assistant/outils/reparer-tests", token,
             {"code": text, "langage": "auto"}, timeout=300)
    return r.get("contenu", "")


def prove_tests(base_url, token, files):
    """Returns (markdown, failed)."""
    max_fix = int(env("REVIA_MAX_FIX", "3"))
    n_mut = int(env("REVIA_MUTANTS", "8"))
    min_mut = int(env("REVIA_MIN_MUTATION", "0"))
    results, failed = [], False
    candidates = [f for f in files if not TEST_HINT.search(f) and test_path(f)]
    for path in candidates[:int(env("REVIA_PROVEN_MAX_FILES", "5"))]:
        ext = os.path.splitext(path)[1].lower()
        cmd = env("REVIA_TEST_CMD") or DEFAULT_CMD.get(ext)
        tpath = test_path(path).replace(os.sep, "/")  # forward slashes work in every test runner, Windows included
        with open(path, encoding="utf-8", errors="replace", newline="") as fh:
            code = fh.read()  # newline="": the mutated lines keep the file's own line endings
        if not cmd or not code.strip():
            results.append((path, "-", "⏭️ skipped (set REVIA_TEST_CMD)", "-", []))
            continue
        if code.count("\n") > 300:
            results.append((path, "-", "⏭️ skipped (> 300 lines)", "-", []))
            continue
        bugs, ok, out, tests = [], False, "", None
        try:
            ctx = "tested file: %s ; test file: %s ; command: %s" % (path, tpath, cmd)
            answer = call("POST", base_url + "/api/assistant/outils/tests", token,
                          {"code": code, "langage": "auto", "cible": ctx[:500]}, timeout=300).get("contenu", "")
            tests = first_block(answer)
            bugs += likely_bugs(answer)
            for attempt in range(max_fix + 1):
                if not tests:
                    break
                os.makedirs(os.path.dirname(tpath) or ".", exist_ok=True)
                with open(tpath, "w", encoding="utf-8") as fh:
                    fh.write(tests + "\n")
                ok, out = run_tests(cmd, tpath)
                if ok or attempt == max_fix:
                    break
                print("RevIA: generated tests for %s fail, repair round %d/%d." % (path, attempt + 1, max_fix))
                answer = repair(base_url, token, path, code, tpath, tests,
                                "Execution output (`%s`, failed):\n```\n%s\n```" % (cmd, tail(out)))
                tests = first_block(answer) or tests
                bugs += likely_bugs(answer)
        except (urllib.error.URLError, OSError, ValueError) as e:
            print("::warning::RevIA proven tests for %s: %s" % (path, e))
        if not ok:
            # Never leave a red or unverified test in the repository.
            if tests is not None and os.path.isfile(tpath):
                os.remove(tpath)
            results.append((path, "-", "❌ rejected (could not make them pass)", "-", dedupe(bugs)))
            continue

        score, survivors = mutation_score(path, code, cmd, tpath, n_mut)
        if survivors and max_fix > 0:
            # One round to kill the surviving mutants, kept only if the suite stays green and scores better.
            try:
                desc = "\n".join("- line %d: `%s` -> `%s`" % (i + 1, a.strip(), b.strip()) for i, a, b in survivors)
                answer = repair(base_url, token, path, code, tpath, tests,
                                "The tests pass, but these surviving mutants were NOT detected:\n" + desc)
                better = first_block(answer)
                if better:
                    with open(tpath, "w", encoding="utf-8") as fh:
                        fh.write(better + "\n")
                    green, _ = run_tests(cmd, tpath)
                    s2, surv2 = mutation_score(path, code, cmd, tpath, n_mut) if green else (-1, survivors)
                    if green and s2 > score:
                        tests, score, survivors = better, s2, surv2
                        bugs += likely_bugs(answer)
                    else:
                        with open(tpath, "w", encoding="utf-8") as fh:
                            fh.write(tests + "\n")
            except (urllib.error.URLError, OSError, ValueError) as e:
                print("::warning::RevIA mutation round for %s: %s" % (path, e))
        bad = score >= 0 and score < min_mut
        failed = failed or bad
        results.append((path, tpath, "❌ weak tests" if bad else "✅ proven (green)",
                        "-" if score < 0 else "%d%%" % score, dedupe(bugs)))

    lines = ["## 🧪 RevIA proven tests", "",
             "Every test file below was **generated, executed in this pipeline and passes**. "
             "The mutation score is the share of bugs RevIA injected into your code that the tests caught.", "",
             "| Source | Generated tests | Status | Mutation score |", "|---|---|---|---|"]
    all_bugs = []
    for src, tp, status, score, bugs in results:
        lines.append("| `%s` | %s | %s | %s |" % (src, "`%s`" % tp if tp != "-" else "-", status, score))
        all_bugs += ["`%s`: %s" % (src, md_escape(b)) for b in bugs]
    if all_bugs:
        lines += ["", "### 🐞 Bugs found while testing", ""] + ["- " + b for b in all_bugs[:10]]
    lines += ["", "<sub>Commit the generated files to keep them (e.g. upload them as a build artifact).</sub>"]
    return "\n".join(lines), failed


def dedupe(items):
    seen, out = set(), []
    for i in items:
        if i.lower() not in seen:
            seen.add(i.lower())
            out.append(i)
    return out


def mutation_score(path, code, cmd, tpath, limit):
    """Injects each mutant, runs the tests, restores the file. Returns (score %, surviving mutants)."""
    muts = mutants(code, limit)
    if not muts:
        return -1, []
    lines = code.split("\n")
    with open(path, "rb") as fh:
        original_bytes = fh.read()  # restored byte for byte, whatever happens
    killed, survivors = 0, []
    try:
        for i, original, mutated in muts:
            lines[i] = mutated
            with open(path, "w", encoding="utf-8", newline="") as fh:
                fh.write("\n".join(lines))
            green, _ = run_tests(cmd, tpath, timeout=120)
            if green:
                survivors.append((i, original, mutated))
            else:
                killed += 1
            lines[i] = original
    finally:
        with open(path, "wb") as fh:
            fh.write(original_bytes)
    return round(killed * 100 / len(muts)), survivors


# --------------------------------------------------------------------------------------------------------------
def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(errors="replace")  # emojis on legacy Windows consoles
    base_url, token = env("REVIA_URL"), env("REVIA_TOKEN")
    if not base_url or not token:
        print("::error::REVIA_URL and REVIA_TOKEN must be set (create a token in RevIA > Settings).")
        return 2
    base_url = base_url.rstrip("/")
    if base_url.startswith("http://") and not re.match(r"http://(localhost|127\.0\.0\.1|\[::1\])(:|/|$)", base_url):
        print("::warning::REVIA_URL uses plain http: your API token travels unencrypted. Use https.")
    min_score = int(env("REVIA_MIN_SCORE", "70"))
    fail_critical = env("REVIA_FAIL_ON_CRITICAL", "true").lower() == "true"
    files = [f for f in changed_files() if os.path.splitext(f)[1].lower() in EXTENSIONS and os.path.isfile(f)]
    if not files:
        print("RevIA: no reviewable source file changed.")

    rows, runs, findings, failed = [], [], [], False
    for path in files:
        with open(path, encoding="utf-8", errors="replace") as fh:
            code = fh.read()
        if not code.strip():
            continue
        if code.count("\n") + 1 > MAX_LINES:
            rows.append((path, "-", "-", "skipped (> %d lines)" % MAX_LINES, ""))
            continue
        try:
            review = call("POST", base_url + "/api/revues", token, {"titre": path, "code": code, "mode": "complet"})
            sarif = call("GET", "%s/api/revues/%s/sarif" % (base_url, review["id"]), token)
        except urllib.error.HTTPError as e:
            detail = e.read().decode("utf-8", "replace")[:300]
            print("::error::RevIA server error on %s: HTTP %s %s" % (path, e.code, detail))
            return 2
        except urllib.error.URLError as e:
            print("::error::Cannot reach RevIA at %s: %s" % (base_url, e.reason))
            return 2
        runs.extend(sarif.get("runs", []))
        crit = review.get("parGravite", {}).get("critique", 0)
        score = review.get("score", 0)
        bad = score < min_score or (fail_critical and crit > 0)
        failed = failed or bad
        link = "%s/revues/%s" % (base_url, review["id"])
        rows.append((path, score, crit, "FAIL" if bad else "ok", link))
        for p in review.get("problemes", []):
            if p.get("fauxPositif"):
                continue
            level = {"critique": "error", "majeure": "warning"}.get(p.get("gravite"), "notice")
            msg = (p.get("titre", "") + ": " + p.get("explication", "")).replace("\n", " ")
            print("::%s file=%s,line=%s,title=RevIA %s::%s" % (level, path, p.get("ligne", 1), p.get("gravite"), msg))
            findings.append((p.get("gravite"), path, p.get("ligne", 1), p.get("titre", ""), bool(p.get("correction"))))

    with open(env("REVIA_SARIF", "revia.sarif"), "w", encoding="utf-8") as fh:
        json.dump({"$schema": "https://json.schemastore.org/sarif-2.1.0.json", "version": "2.1.0", "runs": runs}, fh, indent=2)

    sections = [summary_markdown(rows, findings, min_score, fail_critical, failed)] if files else []
    ac_md, ac_failed = check_acceptance_criteria(base_url, token)
    if ac_md:
        sections.append(ac_md)
        failed = failed or ac_failed
    if env("REVIA_PROVEN_TESTS", "false").lower() == "true" and files:
        pt_md, pt_failed = prove_tests(base_url, token, files)
        sections.append(pt_md)
        failed = failed or pt_failed
    if not sections:
        return 0
    summary = "\n\n".join(sections) + "\n\n<sub>Generated by RevIA · secrets are masked before any AI call.</sub>"
    print(summary)
    if env("GITHUB_STEP_SUMMARY"):
        with open(env("GITHUB_STEP_SUMMARY"), "a", encoding="utf-8") as fh:
            fh.write(summary + "\n")
    upsert_comment(summary)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
