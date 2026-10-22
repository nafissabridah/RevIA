# RevIA – AI code review and QA toolbox, with a CI quality gate

RevIA reviews code like a senior developer and helps QA engineers ship tests faster:
OWASP-mapped vulnerabilities, hard-coded secrets, bugs and bad practices, each with a plain-language explanation and a
ready-to-apply fix — plus 13 AI tools for developers and testers. Runs on **free LLMs** with automatic failover,
or 100% locally.

## Only in RevIA

**1. Proven tests — the AI never hands you a test it has not run.** On every pull request, RevIA generates unit tests
for the changed files, **executes them in your CI**, repairs them from the real error output, then **injects bugs into
your code (mutation testing)** to prove the tests actually catch them. You get only green tests, a mutation score per
file, and — when a test fails because the *code* is wrong — a "🐞 bugs found while testing" list instead of a weakened
assertion. Your code runs on your runner, never on the RevIA server.

**2. Acceptance-criteria coverage — does the PR do what the user story asks?** RevIA reads the "Acceptance criteria"
of the PR description (bullets or Gherkin) and checks each one against the diff: ✅ implemented *and* tested, 🟡 partial,
❌ missing, with `file:line` evidence and the test to add. Optional gate: `REVIA_MIN_AC_COVERAGE`.

Why it matters: in 2026 almost every team uses AI for testing, but only 18 % trust their coverage
([Alt.QA, via quashbugs](https://quashbugs.com/blog/ai-statistics)) — the top ask of testers is AI tests that are
*accurate, not hallucinated* ([Automation Guild 2026](https://testguild.com/?p=1042832)). RevIA proves it, in the PR.

```yaml
- uses: nafissabridah/RevIA/integrations/github@main
  with:
    url: ${{ vars.REVIA_URL }}
    token: ${{ secrets.REVIA_TOKEN }}
    proven-tests: "true"        # generate → run → repair → mutation-check
    min-ac-coverage: "80"       # fail if < 80 % of the user story is implemented and tested
```

## What you get

| For developers | For QA / testers | For teams |
|---|---|---|
| Code review with score /100, OWASP Top 10 mapping, before/after fix | User story → test plan, test cases, Gherkin, traceability (Xray / TestRail / Zephyr columns) | CI quality gate (GitHub Actions, GitLab CI, Jenkins…) |
| Unit tests (JUnit, pytest, Jest…) | E2E UI tests: Playwright, Cypress, Selenium, WebdriverIO (page objects, no fixed sleeps) | One summary comment per PR / MR, updated on each push |
| Docs, explanations, refactoring, language conversion | API tests: REST Assured, pytest, supertest, Postman, Karate + k6 smoke load test | SARIF 2.1.0 → GitHub code scanning, Azure DevOps, VS Code |
| Commit message & PR description from a diff | Bug report writer (Jira-ready) · logs / stack-trace root cause | History, CSV export, dashboard, recurring issues |
| AI chat assistant (streamed) | Test-data generator · review of existing tests (flaky, weak assertions, mutants) | Admin: users, daily quotas, versioned system prompt |

Secrets, API keys and e-mails are **masked before anything is sent to the model**. The UI is in **English and French**;
AI reports and answers are written in **15 languages** (English, French, Spanish, German, Portuguese, Italian, Dutch,
Polish, Turkish, Arabic — right-to-left —, Russian, Hindi, Chinese, Japanese, Korean).

## Free and powerful LLMs, without downtime

| `REVIA_AI_PROVIDER` | Default model | Free key |
|---|---|---|
| `gemini` (default in prod) | review/chat `gemini-flash-lite-latest` · QA tools `gemini-flash-latest` (falls back to Flash-Lite on quota) | aistudio.google.com |
| `groq` | `openai/gpt-oss-120b` | console.groq.com |
| `openrouter` | `openrouter/free` (router: always an online free model) | openrouter.ai/keys |
| `mistral` | `codestral-latest` | console.mistral.ai |
| `ollama` | `qwen2.5-coder:1.5b` (any local model) | none, 100% local |
| `openai` / `anthropic` | `gpt-4o-mini` / `claude-sonnet-5-5` | paid |
| `demo` | rule-based engine | none, offline |

Set `REVIA_AI_FALLBACK=groq,openrouter,mistral,demo` and the per-provider keys (`GEMINI_API_KEY`, `GROQ_API_KEY`,
`OPENROUTER_API_KEY`, `MISTRAL_API_KEY`): when a free tier hits its quota, the next provider answers automatically.
Providers without a key are skipped. Free-tier limits change often — check each provider before relying on it, and
remember that some free tiers may use submitted data for training: for confidential code use Ollama or a paid contract.

## Run

```bash
java -jar backend/target/revia.jar       # API + Angular app → http://localhost:8085
# rebuild everything (Windows): powershell -ExecutionPolicy Bypass -File scripts\build.ps1
# production with PostgreSQL:   cp .env.example .env && docker compose up -d --build   → http://localhost:8080
```

Default profile = dev: H2 database, demo data, rule-based provider (no API key needed).
Demo account (dev profile only): `sarra@revia.local` / `Sarra2026!` · admin: `admin@revia.local` / `Admin2026!`.

## Use it on pull requests

1. In RevIA › Settings › *CI / API integration*, create a token.
2. Add it as repository secret `REVIA_TOKEN` and variable `REVIA_URL`.
3. Copy `integrations/github/revia-review.yml` to `.github/workflows/` (it needs `pull-requests: write` for the comment).

GitLab: `integrations/gitlab/.gitlab-ci.revia.yml` (+ `GITLAB_TOKEN` for the merge-request note).
Options: `REVIA_MIN_SCORE` (70), `REVIA_FAIL_ON_CRITICAL` (true), `REVIA_LANG` (en, fr, es, de, pt, it, nl, pl, tr,
ar, ru, hi, zh, ja, ko), `REVIA_COMMENT` (true), `REVIA_AC` / `REVIA_AC_FILE` / `REVIA_MIN_AC_COVERAGE` (acceptance
criteria), `REVIA_PROVEN_TESTS` / `REVIA_TEST_CMD` / `REVIA_MAX_FIX` / `REVIA_MUTANTS` / `REVIA_MIN_MUTATION` (proven
tests) — all documented at the top of `integrations/revia_ci.py`.

Production behind Vercel: set `REVIA_IP_HEADER=x-vercel-forwarded-for` (already in `render.yaml`) so rate limits apply
per visitor. Production refuses the example secrets of `.env.example`.

## Quality

Backend 64 tests (JUnit 5, Mockito, MockMvc) · frontend 25 tests (Karma/Jasmine) · AI test set: 20/20 known defects
found and 0/3 false positives in demo mode, 19/20 with Gemini ([report](docs/rapport-jeu-essai-gemini.md)).

See [CHANGELOG.md](CHANGELOG.md) and [docs/AUDIT-commercial.md](docs/AUDIT-commercial.md) (buying blockers and what
remains: payments, SSO, organisations, legal pages).
