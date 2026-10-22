import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { messageErreur } from '../../core/http';
import { LANGUES_IA, LangueIa, LangueService } from '../../core/langue.service';
import { JetonApi, SystemeInfo } from '../../core/models';
import { NotifService } from '../../core/notif.service';
import { TPipe } from '../../core/i18n';

/** Paramètres : modifier son profil, changer son mot de passe, voir le module IA utilisé. */
@Component({
  selector: 'app-parametres',
  standalone: true,
  imports: [TPipe, ReactiveFormsModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="barre"><div class="fil">{{ 'Espace de travail /' | t }} <b>{{ 'Paramètres' | t }}</b></div></header>
    <div class="page">
      <h1 class="titre-page">{{ 'Paramètres' | t }}</h1>
      <p class="sous-titre">{{ 'Votre profil, votre sécurité et le module d\\'IA utilisé par RevIA.' | t }}</p>

      <div class="grille">
        <form class="carte bloc" [formGroup]="profil" (ngSubmit)="enregistrerProfil()">
          <h2>{{ 'Profil' | t }}</h2>
          <div class="champ">
            <label for="nom">{{ 'Nom affiché' | t }}</label>
            <input id="nom" class="input" formControlName="nom" maxlength="80" />
          </div>
          <div class="champ">
            <label>{{ 'E-mail (identifiant)' | t }}</label>
            <input class="input" [value]="auth.utilisateur()?.email" disabled />
          </div>
          <p class="info">{{ 'Compte créé le' | t }} {{ auth.utilisateur()?.creeLe | date: 'dd/MM/yyyy' }} ·
            @if (auth.estDeveloppeur()) { {{ 'quota :' | t }} {{ auth.utilisateur()?.quotaJournalier }} {{ 'revues / jour' | t }} } @else { {{ 'administrateur' | t }} }</p>
          <button class="btn primaire" type="submit" [disabled]="profil.invalid || profil.pristine">{{ 'Enregistrer' | t }}</button>
        </form>

        <form class="carte bloc" [formGroup]="mdp" (ngSubmit)="changerMdp()">
          <h2>{{ 'Mot de passe' | t }}</h2>
          <div class="champ">
            <label for="ancien">{{ 'Mot de passe actuel' | t }}</label>
            <input id="ancien" class="input" type="password" formControlName="ancien" autocomplete="current-password" />
          </div>
          <div class="champ">
            <label for="nouveau">{{ 'Nouveau mot de passe' | t }}</label>
            <input id="nouveau" class="input" type="password" formControlName="nouveau" autocomplete="new-password" />
            <span class="info">{{ '8 caractères minimum, avec au moins une lettre et un chiffre. Stocké haché (BCrypt).' | t }}</span>
          </div>
          <button class="btn primaire" type="submit" [disabled]="mdp.invalid">{{ 'Changer le mot de passe' | t }}</button>
        </form>

        <div class="carte bloc">
          <h2>{{ 'Langue' | t }}</h2>
          <div class="champ">
            <label for="langue-ia">{{ 'Langue des réponses de l'IA' | t }}</label>
            <select id="langue-ia" class="input" (change)="choisirLangueIa($any($event.target).value)">
              <option value="auto" [selected]="langues.langueIa() === 'auto'">{{ "Automatique (langue de l'interface)" | t }}</option>
              @for (lg of languesIa; track lg.code) { <option [value]="lg.code" [selected]="lg.code === langues.langueIa()">{{ lg.nom }}</option> }
            </select>
            <span class="info">{{ "Rapports de revue, assistant et outils sont rédigés dans cette langue ; l'interface reste en français ou en anglais." | t }}</span>
          </div>
        </div>

        @if (auth.estDeveloppeur()) {
          <div class="carte bloc large">
            <h2>{{ 'Intégration CI / API' | t }}</h2>
            <p class="info">{{ 'Un jeton personnel permet à GitHub Actions, GitLab CI ou Jenkins de lancer des revues sur chaque pull request, d\\'échouer sous un score minimal et de publier un rapport SARIF. Il a les mêmes droits que votre compte.' | t }}</p>
            <div class="ligne-jeton">
              <input class="input" name="nomJeton" [value]="nomJeton()" (input)="nomJeton.set($any($event.target).value)"
                     maxlength="80" [placeholder]="'Nom du jeton (ex. GitHub Actions)' | t" (keydown.enter)="creerJeton()" />
              <button class="btn primaire" type="button" (click)="creerJeton()">{{ 'Créer un jeton' | t }}</button>
            </div>
            @if (nouveauJeton(); as v) {
              <div class="jeton-neuf">
                <b>{{ 'Copiez ce jeton maintenant : il ne sera plus affiché.' | t }}</b>
                <div class="copie"><code>{{ v }}</code><button class="btn" type="button" (click)="copierJeton(v)">{{ 'Copier' | t }}</button></div>
                <span class="info">{{ 'Dans GitHub : Settings › Secrets and variables › Actions › secret' | t }} <code>REVIA_TOKEN</code>{{ ', variable' | t }} <code>REVIA_URL</code> = {{ origine }}.</span>
              </div>
            }
            @if (jetons().length) {
              <table class="jetons">
                <thead><tr><th>{{ 'Nom' | t }}</th><th>{{ 'Jeton' | t }}</th><th>{{ 'Créé le' | t }}</th><th>{{ 'Dernier usage' | t }}</th><th></th></tr></thead>
                <tbody>
                  @for (j of jetons(); track j.id) {
                    <tr><td>{{ j.nom }}</td><td><code>{{ j.apercu }}</code></td><td>{{ j.creeLe | date: 'dd/MM/yyyy' }}</td>
                      <td>{{ j.dernierUsage ? (j.dernierUsage | date: ('dd/MM/yyyy HH:mm' | t)) : ('jamais' | t) }}</td>
                      <td><button class="btn danger-texte" type="button" (click)="revoquer(j.id)">{{ 'Révoquer' | t }}</button></td></tr>
                  }
                </tbody>
              </table>
            } @else { <p class="info">{{ 'Aucun jeton pour l\\'instant.' | t }}</p> }
            <details class="exemple">
              <summary>{{ 'Exemple de workflow GitHub Actions' | t }}</summary>
              <pre>{{ workflow }}</pre>
            </details>
          </div>
        }

        @if (systeme(); as s) {
          <div class="carte bloc">
            <h2>{{ 'Module d\\'IA' | t }}</h2>
            <dl>
              <dt>{{ 'Fournisseur' | t }}</dt><dd>{{ nomFournisseur(s.fournisseur) | t }}</dd>
              <dt>{{ 'Modèle' | t }}</dt><dd><code>{{ s.modele }}</code></dd>
              @if ((s.chaine?.length ?? 0) > 1) {
                <dt>{{ 'Relais automatique' | t }}</dt><dd>@for (c of s.chaine; track c; let last = $last) { <code>{{ c }}</code>@if (!last) { → } }
                  <br><small>{{ 'Si un fournisseur gratuit atteint son quota, le suivant répond.' | t }}</small></dd>
              }
              <dt>{{ 'Température' | t }}</dt><dd>{{ s.temperature }} {{ '(réponses stables d\\'une analyse à l\\'autre)' | t }}</dd>
              <dt>{{ 'Délai maximum' | t }}</dt><dd>{{ s.timeoutSec }} {{ 's' | t }}</dd>
              <dt>{{ 'Taille maximale' | t }}</dt><dd>{{ s.maxLignes }} {{ 'lignes par extrait' | t }}</dd>
              <dt>{{ 'État' | t }}</dt><dd>@if (s.disponible) { <span class="badge s-corrigee">{{ '● Disponible' | t }}</span> } @else { <span class="badge s-a_corriger">{{ '● Clé d\\'API absente' | t }}</span> }</dd>
              <dt>{{ 'Version' | t }}</dt><dd>{{ 'RevIA' | t }} {{ s.version }}</dd>
            </dl>
            <p class="info">{{ '🔒 La clé d\\'API ne quitte jamais le serveur ; vos secrets et e-mails sont masqués avant l\\'envoi au modèle.' | t }}</p>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .grille { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 14px; align-items: start; }
    .bloc { padding: 20px; display: flex; flex-direction: column; gap: 14px; }
    h2 { font-size: 15px; }
    .info { font-size: 12px; color: var(--texte-3); margin: 0; }
    .bloc .btn { align-self: flex-start; }
    dl { display: grid; grid-template-columns: 130px 1fr; gap: 8px 12px; margin: 0; font-size: 13px; }
    dt { color: var(--texte-2); }
    dd { margin: 0; }
    .large { grid-column: 1 / -1; }
    .ligne-jeton { display: flex; gap: 10px; }
    .ligne-jeton .input { flex: 1; max-width: 420px; }
    .jeton-neuf { background: var(--ok-pale); border-radius: 10px; padding: 12px 14px; display: flex; flex-direction: column; gap: 8px; font-size: 13px; }
    .copie { display: flex; gap: 10px; align-items: center; }
    .copie code { background: #fff; padding: 6px 10px; border-radius: 6px; font-size: 12.5px; word-break: break-all; flex: 1; }
    .jetons { width: 100%; border-collapse: collapse; font-size: 13px; }
    .jetons th { text-align: left; color: var(--texte-2); font-weight: 600; padding: 6px 8px; border-bottom: 1px solid var(--bord); }
    .jetons td { padding: 8px; border-bottom: 1px solid var(--bord); }
    .danger-texte { color: var(--critique); background: none; border: none; }
    .exemple pre { background: var(--sidebar); color: #c7d2fe; padding: 14px; border-radius: 10px; font: 12px/1.6 var(--mono); overflow: auto; }
    .exemple summary { cursor: pointer; font-size: 13px; color: var(--primaire-fonce); font-weight: 600; }
  `],
})
export class ParametresComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly notif = inject(NotifService);
  private readonly fb = inject(FormBuilder);
  readonly langues = inject(LangueService);
  readonly languesIa = LANGUES_IA;

  readonly systeme = signal<SystemeInfo | null>(null);
  readonly jetons = signal<JetonApi[]>([]);
  readonly nomJeton = signal('GitHub Actions');
  readonly nouveauJeton = signal<string | null>(null);
  readonly origine = location.origin;
  readonly workflow = `name: RevIA review
on: pull_request
permissions: { contents: read, security-events: write }
jobs:
  revia:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }
      - uses: nafissabridah/RevIA/integrations/github@main
        with:
          url: \${{ vars.REVIA_URL }}
          token: \${{ secrets.REVIA_TOKEN }}
          min-score: "70"
      - uses: github/codeql-action/upload-sarif@v3
        if: always()
        with: { sarif_file: revia.sarif, category: revia }`;
  readonly profil = this.fb.nonNullable.group({ nom: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(80)]] });
  readonly mdp = this.fb.nonNullable.group({
    ancien: ['', Validators.required],
    nouveau: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72), Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/)]],
  });

  nomFournisseur(f: string): string {
    const noms: Record<string, string> = {
      demo: 'Démo (analyse par règles, sans LLM)',
      groq: 'Groq (gratuit)',
      gemini: 'Google Gemini (gratuit)',
      openrouter: 'OpenRouter (modèles gratuits)',
      mistral: 'Mistral Codestral',
      ollama: 'Ollama (local, gratuit)',
      openai: 'OpenAI',
      anthropic: 'Claude (Anthropic)',
    };
    return noms[f] ?? f;
  }

  ngOnInit(): void {
    this.profil.reset({ nom: this.auth.utilisateur()?.nom ?? '' });
    this.api.systeme().subscribe((s) => this.systeme.set(s));
    if (this.auth.estDeveloppeur()) {
      this.api.jetons().subscribe((j) => this.jetons.set(j));
    }
  }

  choisirLangueIa(code: string): void {
    this.langues.definirLangueIa(code === 'auto' ? 'auto' : (code as LangueIa));
  }

  creerJeton(): void {
    this.api.creerJeton(this.nomJeton().trim() || 'CI').subscribe({
      next: (r) => {
        this.nouveauJeton.set(r.valeur);
        this.jetons.update((l) => [r.jeton, ...l]);
      },
      error: (e) => this.notif.erreur(messageErreur(e)),
    });
  }

  revoquer(id: number): void {
    this.api.revoquerJeton(id).subscribe({
      next: () => {
        this.jetons.update((l) => l.filter((j) => j.id !== id));
        this.notif.succes('Jeton révoqué : il ne fonctionne plus.');
      },
      error: (e) => this.notif.erreur(messageErreur(e)),
    });
  }

  copierJeton(v: string): void {
    navigator.clipboard?.writeText(v).then(() => this.notif.succes('Jeton copié.'), () => this.notif.erreur('Copie refusée par le navigateur.'));
  }

  enregistrerProfil(): void {
    this.api.modifierProfil(this.profil.getRawValue().nom.trim()).subscribe({
      next: (u) => {
        this.auth.utilisateur.set(u);
        this.profil.reset({ nom: u.nom });
        this.notif.succes('Profil mis à jour.');
      },
      error: (e) => this.notif.erreur(messageErreur(e)),
    });
  }

  changerMdp(): void {
    const { ancien, nouveau } = this.mdp.getRawValue();
    this.api.changerMotDePasse(ancien, nouveau).subscribe({
      next: () => {
        this.mdp.reset();
        this.notif.succes('Mot de passe modifié.');
      },
      error: (e) => this.notif.erreur(messageErreur(e)),
    });
  }
}
