# RevIA GitHub App — install RevIA on a repository in one click

With the GitHub App, a RevIA user clicks **Settings › GitHub › Install RevIA on GitHub**, picks the repositories,
and every pull request is reviewed automatically — no workflow file, no secret to copy into the repository.

On each pull request (`opened`, `synchronize`, `reopened`, `ready_for_review`; drafts are skipped) RevIA:

1. lists the changed files (removed, binary, oversized files and non-source extensions are skipped — same
   extension filter as `integrations/revia_ci.py`, at most `GITHUB_APP_MAX_FICHIERS` files, default 20);
2. reviews each file at the head commit with the same engine as the web app (secrets masked before any AI call,
   quota and plan of the linked RevIA account, optional domain pack); the reviews appear in the user's history;
3. posts inline review comments for critical / major findings, only on lines added by the PR, with a one-click
   ```` ```suggestion ```` when the fix is a single line (never posted twice: hidden `<!-- revia:path:line:slug -->`
   marker, same format as the CI action; at most `GITHUB_APP_MAX_INLINE` per run, default 20);
4. posts **one** summary comment (hidden marker `<!-- revia:summary -->`), updated on every new push.

If the account's daily quota is exhausted, the summary comment says so (no crash, no partial spam).
Pull requests from an installation that is not linked to a RevIA account are ignored (logged only).

The CI action (`integrations/github/action.yml`) keeps working; use one or the other on a given repository.

---

## 1. Register the GitHub App

You need one App per RevIA deployment. Replace `<frontend>` with the public URL of the Angular app
(e.g. `revia.vercel.app`) and `<backend>` with the URL of the Spring Boot API (e.g. `revia-api.onrender.com`).

### Option A — from the manifest (fastest)

1. Save this page locally as `create-revia-app.html`, edit the URLs, open it in your browser and click the button
   (to create the App under an organization, replace the form action with
   `https://github.com/organizations/<org>/settings/apps/new`):

   ```html
   <form action="https://github.com/settings/apps/new" method="post">
     <input type="hidden" name="manifest" id="manifest">
     <button type="submit">Create the RevIA GitHub App</button>
   </form>
   <script>
     document.getElementById('manifest').value = JSON.stringify({
       "name": "RevIA",
       "url": "https://<frontend>",
       "description": "AI code review: one summary comment and inline suggestions on every pull request.",
       "hook_attributes": { "url": "https://<backend>/api/github/webhook", "active": true },
       "redirect_url": "https://<frontend>/parametres",
       "setup_url": "https://<frontend>/github/installe",
       "setup_on_update": true,
       "callback_urls": ["https://<frontend>/github/installe"],
       "request_oauth_on_install": false,
       "public": true,
       "default_permissions": {
         "pull_requests": "write",
         "contents": "read",
         "metadata": "read",
         "issues": "write"
       },
       "default_events": ["pull_request"]
     });
   </script>
   ```

   The same manifest as plain JSON:

   ```json
   {
     "name": "RevIA",
     "url": "https://<frontend>",
     "description": "AI code review: one summary comment and inline suggestions on every pull request.",
     "hook_attributes": { "url": "https://<backend>/api/github/webhook", "active": true },
     "redirect_url": "https://<frontend>/parametres",
     "setup_url": "https://<frontend>/github/installe",
     "setup_on_update": true,
     "callback_urls": ["https://<frontend>/github/installe"],
     "request_oauth_on_install": false,
     "public": true,
     "default_permissions": {
       "pull_requests": "write",
       "contents": "read",
       "metadata": "read",
       "issues": "write"
     },
     "default_events": ["pull_request"]
   }
   ```

2. GitHub creates the App and redirects to `https://<frontend>/parametres?code=<code>`. Copy the `code` value from
   the address bar (valid for one hour) and exchange it for the App credentials:

   ```bash
   curl -s -X POST -H "Accept: application/vnd.github+json" https://api.github.com/app-manifests/<code>/conversions
   ```

   The JSON answer contains `id` (→ `GITHUB_APP_ID`), `slug` (→ `GITHUB_APP_SLUG`), `pem`
   (→ `GITHUB_APP_PRIVATE_KEY`), `webhook_secret` (→ `GITHUB_APP_WEBHOOK_SECRET`), `client_id` and `client_secret`
   (optional, see §3). Store them in your secret manager straight away: the private key is shown only once.

### Option B — by hand

GitHub › **Settings › Developer settings › GitHub Apps › New GitHub App** (or the same page of an organization):

| Field | Value |
|---|---|
| GitHub App name | `RevIA` (must be unique on GitHub; the slug is derived from it) |
| Homepage URL | `https://<frontend>` |
| Callback URL | `https://<frontend>/github/installe` (only used with the optional OAuth check, §3) |
| Setup URL | `https://<frontend>/github/installe` — tick **Redirect on update** |
| Webhook | Active; URL `https://<backend>/api/github/webhook`; secret: a long random value (`openssl rand -hex 32`) |
| Repository permissions | **Pull requests: Read & write**, **Contents: Read-only**, **Metadata: Read-only**, **Issues: Read & write** (PR conversation comments) |
| Subscribe to events | **Pull request** (installation events are always delivered to Apps) |
| Where can this App be installed? | *Any account* to offer it to your customers, *Only on this account* for internal use |

Then, on the App page: note the **App ID** and the **public link** `https://github.com/apps/<slug>` (the slug is
the last part), and **Generate a private key** (a `.pem` file is downloaded).

## 2. Configure RevIA (Render)

Render dashboard › service `revia-api` › **Environment** (the keys already exist in `render.yaml` with `sync: false`):

| Variable | Value |
|---|---|
| `GITHUB_APP_ID` | the App ID (number) |
| `GITHUB_APP_PRIVATE_KEY` | the whole content of the `.pem` file, `-----BEGIN RSA PRIVATE KEY-----` … `-----END RSA PRIVATE KEY-----`. Multi-line values are accepted, or a single line with literal `\n`. PKCS#1 and PKCS#8 both work. |
| `GITHUB_APP_WEBHOOK_SECRET` | the webhook secret |
| `GITHUB_APP_SLUG` | the slug (`https://github.com/apps/<slug>`) |
| `GITHUB_API_URL` | optional, default `https://api.github.com` (GitHub Enterprise Server: `https://<host>/api/v3`) |
| `GITHUB_APP_CLIENT_ID`, `GITHUB_APP_CLIENT_SECRET` | optional, see §3 |
| `GITHUB_APP_LANGUE` | optional, language of the PR comments (default `en`; `fr`, `es`, `de`…) |
| `GITHUB_APP_METIER` | optional domain pack applied to the reviews (`banque-paiement`, `sante`…) |
| `GITHUB_APP_MAX_FICHIERS`, `GITHUB_APP_MAX_INLINE` | optional caps per pull request (default 20 / 20) |
| `REVIA_URL_PUBLIQUE` | recommended: `https://<frontend>`, so the summary links to each full report |

The feature stays **off** until the first four variables are all set. When it is on, `GET /api/systeme/info` returns
`githubActif: true` and `githubInstallUrl: https://github.com/apps/<slug>/installations/new`, and the GitHub card
appears in **Settings**. With Docker Compose, put the same variables in `.env` (see `.env.example`).

## 3. Linking an installation to a RevIA account

After the installation GitHub redirects the user to the Setup URL `https://<frontend>/github/installe?installation_id=…`.
The page (sign-in first if needed) calls `POST /api/github/installations {installationId}`; the backend checks with
the App JWT that the installation exists (`GET /app/installations/{id}`) and links it to the signed-in account.
Reviews of that installation's pull requests then count against **that** account's quota and plan.

Ownership proof — so that nobody can link someone else's installation and receive their code in their history:

* **Recommended:** set `GITHUB_APP_CLIENT_ID` / `GITHUB_APP_CLIENT_SECRET` and tick **Request user authorization
  (OAuth) during installation** on the App (`request_oauth_on_install: true` in the manifest). GitHub then adds a
  `code` to the redirect; RevIA exchanges it and requires the installation to be in the user's
  `GET /user/installations`.
* **Without OAuth:** only an installation created less than 30 minutes ago and not yet linked can be linked
  (GitHub redirects right after the installation). An older one: uninstall and reinstall the App.

An installation linked to an account is never re-assigned to another one (409). **Settings › GitHub › Unlink** stops
the reviews (the App stays installed on GitHub until it is uninstalled there); uninstalling the App on GitHub
removes the link automatically (`installation.deleted` webhook).

## 4. Test it

1. Settings › GitHub › **Install RevIA on GitHub**, choose a test repository → you land back on RevIA, the
   installation is listed.
2. Open a pull request that changes a `.java` / `.py` / `.ts`… file. Within a minute you get the summary comment and,
   for critical / major findings on changed lines, inline comments. Push another commit: the summary is updated,
   inline comments are not duplicated.
3. GitHub › App settings › **Advanced** shows each delivery and its response (`202` with
   `{"resultat":"ACCEPTE|IGNORE|DOUBLON|APPLIQUE"}`); **Redeliver** replays one (a redelivery keeps the same
   `X-GitHub-Delivery`, so it is answered `DOUBLON` — trigger a new push instead).
4. Signature check without GitHub (local backend):

   ```bash
   BODY='{"zen":"ok","hook_id":1}'
   SIG="sha256=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$GITHUB_APP_WEBHOOK_SECRET" | sed 's/^.* //')"
   curl -i -X POST http://localhost:8085/api/github/webhook -H "Content-Type: application/json" \
     -H "X-GitHub-Event: ping" -H "X-GitHub-Delivery: test-$(date +%s)" -H "X-Hub-Signature-256: $SIG" -d "$BODY"
   # → 202 {"resultat":"IGNORE"} ; a wrong signature → 401
   ```

The automated tests (`GithubAppApiTest`, `GithubAppUnitTest`) run the whole flow against a local fake GitHub API
with an RSA key generated at runtime: no real GitHub call, no repository code leaves the machine.

## 5. Behaviour and limits

* Webhook `POST /api/github/webhook`: public, authenticated only by `X-Hub-Signature-256` (HMAC-SHA256 of the raw
  body, constant-time compare; otherwise `401`). Each `X-GitHub-Delivery` is processed once (table
  `github_livraison`). The answer is immediate (`202`); reviews run in the background on a bounded pool
  (2 in parallel, 50 queued; beyond that `503`, redeliver later).
* App authentication: RS256 JWT (`iat` = now − 60 s, `exp` = now + 9 min, `iss` = App ID) built with the JDK and
  the jjwt library already used by RevIA; installation tokens are cached until one minute before they expire.
  HTTP timeouts: 5 s connect, 30 s read.
* Files over 2,000 lines or 200,000 characters, binary files and files whose diff GitHub does not return (very
  large diffs) are listed as *skipped* in the summary. Line comments only target lines added by the PR.
* Each reviewed file counts as one review in the user's daily quota (same rule as the web app and the CI action).
* No check run / commit status is created (the CI action remains the way to enforce a blocking quality gate).
* Repository code is sent only to the AI provider configured on the RevIA server, after secret masking.

---

## Note en français

La GitHub App permet d'installer RevIA sur un dépôt en un clic (Paramètres › GitHub › « Installer RevIA sur
GitHub ») : chaque pull request reçoit une synthèse unique (mise à jour à chaque push) et des commentaires en ligne
avec suggestion en un clic, sans fichier de workflow. Pour l'activer : créer l'App sur GitHub (manifeste ci-dessus ou
formulaire, §1 — URL du webhook `https://<backend>/api/github/webhook`, URL de configuration
`https://<frontend>/github/installe`, permissions Pull requests R/W, Contents R, Metadata R, Issues R/W, événement
Pull request), puis renseigner sur Render `GITHUB_APP_ID`, `GITHUB_APP_PRIVATE_KEY`, `GITHUB_APP_WEBHOOK_SECRET` et
`GITHUB_APP_SLUG` (§2). La fonctionnalité reste désactivée tant que ces quatre variables ne sont pas renseignées.
Les revues sont comptées sur le quota du compte RevIA qui a relié l'installation ; quota atteint = un commentaire
le signale. Pour prouver l'accès à l'installation, activer de préférence l'OAuth (`GITHUB_APP_CLIENT_ID` /
`GITHUB_APP_CLIENT_SECRET`, §3) ; sans OAuth, seule une installation de moins de 30 minutes peut être reliée.
