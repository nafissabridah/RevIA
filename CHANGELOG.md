# Changelog

## 1.4.0 – 2026-10-22
### Added
- **Plans and payment (Paddle Billing, merchant of record: VAT handled worldwide)**: Gratuit / Pro / Équipe with daily
  review quotas (`REVIA_OFFRE_*`, defaults 20 / 200 / 1000), Paddle.js overlay checkout, signed webhook
  `POST /api/paddle/webhook` (HMAC-SHA256, timestamp tolerance, idempotent), customer portal. Off until `PADDLE_*` is set:
  pricing buttons then fall back to sign-up / contact. Admin quota override still wins; new "reset to plan" action.
- **Legal pages** `/legal/cgu`, `/legal/confidentialite`, `/legal/mentions-legales`, `/legal/dpa` (FR/EN templates that
  describe how RevIA really processes data, company values in `core/marque.ts` marked `À COMPLÉTER`, "to be validated by a
  lawyer" banner until `valideParJuriste` is set).
- **GDPR**: data export `GET /api/compte/export` and account deletion `DELETE /api/compte` (password required).
- **Inline PR review comments** (GitHub and GitLab): each critical / major finding on its changed line, one-click GitHub
  suggestion for one-line fixes, never posted twice (`REVIA_INLINE`, `REVIA_INLINE_MAX`).
- CI: PostgreSQL job (Flyway + schema validation on the real database), unit tests of the CI script
  (`integrations/test_revia_ci.py`), `permissions: contents: read`.
### Changed
- **Existing users' daily quota**: the old default of 50 becomes the plan quota (20 on Gratuit); only quotas an admin
  changed are kept (migration V3).
- Demo-mode criteria verdicts follow the user's language (English when the UI is in English).
### Fixed (found by running proven tests with a real local LLM)
- A green run that executed **no test** (e.g. the AI returned the source instead of tests) counted as proven; tests that
  catch **no injected bug** (0 % mutation score) were delivered. Both are now rejected.
- Copies of the source in the AI answer are skipped when picking the test file.
- "Bugs found" listed guesses made while writing the tests: only failures observed on the runner are reported now,
  marked "to confirm".
- The HTTP read timeout to the LLM used the review timeout, cutting long tool answers; Ollama tool answers are capped at
  4 096 tokens (small local models loop beyond). CI client timeout `REVIA_TIMEOUT` (600 s), `REVIA_KEEP_REJECTED` to debug.
- Tests: backend 74 → 85, frontend 30 → 34, CI script 6.

## 1.3.0 – 2026-10-22
### Added: what no other AI review/QA tool does
- **Proven tests** (`REVIA_PROVEN_TESTS=true` in `integrations/revia_ci.py`): for each changed source file, RevIA generates
  unit tests, **runs them in your own CI runner**, sends the real failure output back to the AI to repair them (up to
  `REVIA_MAX_FIX` rounds), then **injects bugs into your code (mutation testing)** to prove the tests catch them, and asks
  the AI to kill the surviving mutants. Only green tests are kept; red ones are deleted, never committed. Tests that fail
  because the *code* is wrong become a "🐞 Bugs found while testing" list instead of weakened assertions. The PR comment
  shows the mutation score per file; `REVIA_MIN_MUTATION` can gate the build. Your code is never executed on the RevIA
  server. Defaults for pytest, Jest, Maven/JUnit and Go (`REVIA_TEST_CMD` for anything else).
- **Acceptance-criteria coverage**: `POST /api/assistant/criteres` and a new **"Couverture des critères"** page. RevIA reads
  the user story (the "Acceptance criteria" section of the PR description, bullets or Gherkin scenarios) and checks each
  criterion against the PR diff: ✅ covered (implemented **and** tested) / 🟡 partial / ❌ not covered, with
  `file:line` evidence and the missing test. "Covered" without evidence is downgraded automatically. CI section in the PR
  comment, optional gate `REVIA_MIN_AC_COVERAGE`. Works offline in demo mode (keyword matching).
- New AI tool `reparer-tests` (test repair from real execution output / surviving mutants).
- **AI answers in 15 languages** (fr, en, es, de, pt, it, nl, pl, tr, ar, ru, hi, zh, ja, ko) via `X-RevIA-Langue` and a
  "Langue des réponses de l'IA" setting; right-to-left rendering for Arabic.
- GitHub Action inputs `acceptance-criteria`, `ac-file`, `min-ac-coverage`, `proven-tests`, `test-command`, `min-mutation`;
  the example workflow uploads the proven tests as a build artifact.
### Changed
- **Stronger free models for QA**: with Gemini, the QA tools now use `gemini-flash-latest` (full reasoning) and fall back
  transparently to `gemini-flash-lite-latest` when Flash's small free quota is reached; review and chat stay on
  Flash-Lite (19/20 on the test set, large free quota). Tools get 16 384 output tokens (`REVIA_AI_MAX_TOKENS_OUTILS`) and
  180 s (`REVIA_AI_TIMEOUT_OUTILS`) instead of 4 096 tokens / 30 s, which truncated long test plans. The chat keeps
  minimal reasoning for speed.
- Test generation prompt: precise assertions (a test must fail if an operator or a bound changes), runnable as-is from
  the given execution context, never a test that validates a bug.
- Review / chat timeout 30 s → 60 s (`REVIA_AI_TIMEOUT`).
### Fixed
- OpenRouter default model `qwen/qwen3-coder:free` no longer exists (checked live on 2026-10-22): default is now the
  `openrouter/free` router, which always picks a free model that is online.
- Rate limiting behind Vercel: every visitor shared Vercel's IP (one 10/min login bucket for the whole world).
  `REVIA_IP_HEADER=x-vercel-forwarded-for` (set in `render.yaml`).
- Live streaming was buffered by nginx (`proxy_buffering off`); SSE timeout 120 s → 300 s.
- The backend container ignored SIGTERM (`exec java`), `server.shutdown: graceful`.
### Security
- Production refuses the example JWT secret / admin password of `.env.example` (anyone could forge an admin token);
  `.env.example` secrets are now empty, so docker-compose refuses to start until they are set.
- Dev profile (known demo passwords) listens on 127.0.0.1 only (`REVIA_DEV_ADDRESS` to override).
- HSTS and Permissions-Policy headers (nginx, Vercel); nginx `/assets/` no longer drops the security headers.
- CI script warns when `REVIA_URL` is plain http (the API token would travel unencrypted).
- Tests: backend 64 → 74 (criteria, languages, Flash → Flash-Lite fallback, criteria/repair API), CI script verified
  end-to-end on a real git repo (broken tests repaired, mutants killed, CRLF source restored byte for byte).

## 1.2.0 – 2026-10-21
### Added
- **QA toolbox** (7 new AI tools): test plan & cases from a user story (table + Gherkin + traceability, Xray/TestRail/Zephyr
  columns), E2E UI tests (Playwright, Cypress, Selenium, WebdriverIO, page objects), API tests (REST Assured, pytest,
  supertest, Postman, Karate + k6 smoke test), bug report writer, logs / stack-trace root-cause analysis, test-data
  generator (nominal, boundary, invalid, injection), test review (missing cases, flaky patterns, surviving mutants).
  All work offline in demo mode (rule-based answers, incl. known-exception diagnosis).
- **Free LLM failover chain** `REVIA_AI_FALLBACK` (e.g. `groq,openrouter,mistral,demo`): when a free provider hits its
  quota, rejects the key or is down, the next one answers (streaming included). Per-provider keys `GEMINI_API_KEY`,
  `GROQ_API_KEY`, `OPENROUTER_API_KEY`, `MISTRAL_API_KEY`. Chain shown in Settings and `/api/systeme/info`.
- New free providers: **OpenRouter** (`qwen/qwen3-coder:free`) and **Mistral** (`codestral-latest`).
- **Full English UI** with a FR/EN switch (sidebar, landing page), translated tab titles, dates and API error messages.
- **AI answers in the user's language**: reports, assistant and tools follow the UI language (`X-RevIA-Langue` header),
  no server restart needed. CI script: `REVIA_LANG`.
- **CI pull/merge request comment**: one summary comment (gate status, score per file, top 15 findings) created then
  updated on every push — GitHub (`GITHUB_TOKEN`) and GitLab (`GITLAB_TOKEN`).
- Tests: `FallbackLlmClientTest`, QA tools, i18n (backend 64 tests, frontend 25 tests).
### Fixed
- Groq default model `llama-3.3-70b-versatile` was retired by Groq on 2026-08-16: default is now `openai/gpt-oss-120b`.
- `Probleme.signalerFauxPositif` threw a NullPointerException on a problem not yet attached to a review
  (`IntegrationsTest.sarif` was failing).
- `api-key` silently fell back to `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` whatever the provider (an OpenAI key could be
  sent to Gemini): each provider now reads its own key.
- The streaming client called `abort()` on teardown even after a stream had completed normally; it now aborts only when
  the user leaves before the end. (Chrome still lists completed streamed fetches as `ERR_ABORTED` in its network log:
  a browser reporting artifact, the full response is received.)
- Tools page said "Gemini is working…" whatever the configured model.

## 1.1.0 – 2026-10-18
### Added
- Public landing page `/` (FR/EN): value proposition, live trial without account, CI section, pricing, FAQ.
- `POST /api/essai`: anonymous trial (150 lines, not stored, 3/min/IP, daily global cap).
- Personal API tokens (`/api/jetons`), accepted as `Authorization: Bearer rvia_…`; Flyway migration V2.
- SARIF 2.1.0 export `GET /api/revues/{id}/sarif` and "SARIF" button on the report.
- `integrations/`: CI script, GitHub composite action, workflow example, GitLab CI template.
- English system prompt `prompts/revue-system.en.st` (`REVIA_PROMPT`).
- Unit tests `IntegrationsTest`.
### Fixed
- Refreshing `/assistant` or `/outils` returned 404.
- Sidebar badge always said "Gemini" whatever the provider.
- Login screen no longer says "Mini-projet · Sujet 4".
### Security
- `.env` (contained a real API key) removed from the delivered archive: rotate that key.
