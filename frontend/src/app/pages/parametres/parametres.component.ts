import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { messageErreur } from '../../core/http';
import { LANGUES_IA, LangueIa, LangueService } from '../../core/langue.service';
import { MARQUE } from '../../core/marque';
import { Abonnement, InstallationGithub, JetonApi, Plan, SystemeInfo } from '../../core/models';
import { NotifService } from '../../core/notif.service';
import { TPipe, tr } from '../../core/i18n';
import { PaddleService } from '../../core/paddle.service';

/** Paramètres : modifier son profil, changer son mot de passe, voir le module IA utilisé. */
@Component({
  selector: 'app-parametres',
  standalone: true,
  imports: [TPipe, ReactiveFormsModule, DatePipe, RouterLink],
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
            @if (auth.estDeveloppeur()) { {{ 'offre' | t }} {{ nomOffre(auth.utilisateur()?.plan) | t }} · {{ 'quota :' | t }} {{ auth.utilisateur()?.quotaJournalier }} {{ 'revues / jour' | t }} } @else { {{ 'administrateur' | t }} }</p>
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
          <div class="carte bloc">
            <h2>{{ 'Abonnement' | t }}</h2>
            <dl>
              <dt>{{ 'Offre' | t }}</dt><dd><b>{{ nomOffre(abonnement()?.plan ?? auth.utilisateur()?.plan) | t }}</b></dd>
              <dt>{{ 'Quota' | t }}</dt><dd>{{ auth.utilisateur()?.quotaJournalier }} {{ 'revues / jour' | t }}
                @if (auth.utilisateur()?.quotaPersonnalise) { <br><small>{{ "Quota fixé par l'administrateur (prioritaire sur l'offre)." | t }}</small> }</dd>
              @if (abonnement()?.statut; as st) {
                <dt>{{ 'Statut' | t }}</dt><dd>{{ statutAbonnement(st) | t }}</dd>
              }
              @if (abonnement()?.finPeriode; as fin) {
                <dt>{{ abonnement()?.abonnementEnCours ? ('Prochaine échéance' | t) : ('Fin de période' | t) }}</dt><dd>{{ fin | date: 'dd/MM/yyyy' }}</dd>
              }
            </dl>
            @if (paddle.enAttente()) { <p class="info">{{ 'Paiement reçu : activation de votre offre en cours…' | t }}</p> }
            @if (paddle.actif()) {
              @if (!abonnement()?.abonnementEnCours) {
                <div class="actions">
                  @if (paddle.prixDisponible('PRO')) { <button class="btn primaire" type="button" (click)="souscrire('PRO')">{{ 'Passer à Pro' | t }} · {{ marque.prix.pro }} {{ marque.prix.devise }}</button> }
                  @if (paddle.prixDisponible('EQUIPE')) { <button class="btn" type="button" (click)="souscrire('EQUIPE')">{{ 'Passer à Équipe' | t }} · {{ marque.prix.equipe }} {{ marque.prix.devise }}</button> }
                </div>
                <p class="info">{{ 'Paiement sécurisé par Paddle (marchand officiel) : TVA calculée selon votre pays, facture envoyée par e-mail.' | t }}</p>
              } @else if (abonnement()?.portailDisponible) {
                <div class="actions">
                  <button class="btn" type="button" (click)="ouvrirPortail()">{{ 'Gérer mon abonnement' | t }}</button>
                </div>
                <p class="info">{{ "Moyen de paiement, factures, changement d'offre ou résiliation (portail Paddle)." | t }}</p>
              } @else {
                <p class="info">{{ 'Pour modifier ou résilier, utilisez le lien de votre reçu Paddle ou écrivez-nous :' | t }} <a [href]="'mailto:' + marque.contact">{{ marque.contact }}</a></p>
              }
            } @else {
              <p class="info">{{ "Le paiement en ligne n'est pas encore activé. Pour passer à Pro ou Équipe, écrivez-nous :" | t }}
                <a [href]="'mailto:' + marque.contact + '?subject=RevIA%20Pro'">{{ marque.contact }}</a></p>
            }
          </div>

          @if (systeme()?.githubActif && systeme()?.githubInstallUrl) {
            <div class="carte bloc large">
              <h2>{{ 'GitHub' | t }}</h2>
              <p class="info">{{ 'Installez RevIA sur vos dépôts en un clic : chaque pull request est revue automatiquement (synthèse et commentaires en ligne), sans fichier de workflow. Les revues comptent sur votre quota.' | t }}</p>
              <a class="btn primaire" [href]="systeme()?.githubInstallUrl">{{ 'Installer RevIA sur GitHub' | t }}</a>
              @if (installationsGithub().length) {
                <table class="jetons">
                  <thead><tr><th>{{ 'Compte GitHub' | t }}</th><th>{{ 'Type' | t }}</th><th>{{ 'Reliée le' | t }}</th><th></th></tr></thead>
                  <tbody>
                    @for (i of installationsGithub(); track i.installationId) {
                      <tr><td><b>{{ i.compte }}</b></td><td>{{ (i.typeCompte === 'Organization' ? 'Organisation' : 'Utilisateur') | t }}</td>
                        <td>{{ i.lieLe | date: 'dd/MM/yyyy' }}</td>
                        <td><button class="btn danger-texte" type="button" (click)="delierGithub(i.installationId)">{{ 'Délier' | t }}</button></td></tr>
                    }
                  </tbody>
                </table>
              } @else { <p class="info">{{ "Aucune installation reliée pour l'instant." | t }}</p> }
            </div>
          }

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

        <div class="carte bloc">
          <h2>{{ 'Mes données (RGPD)' | t }}</h2>
          <p class="info">{{ 'Téléchargez toutes les données de votre compte (profil, abonnement, revues, problèmes, jetons) au format JSON.' | t }}</p>
          <button class="btn" type="button" (click)="exporter()">{{ 'Exporter mes données' | t }}</button>
          @if (auth.estDeveloppeur()) {
            <h3>{{ 'Supprimer mon compte' | t }}</h3>
            <p class="info">{{ "Suppression définitive du compte, de l'historique des revues et des jetons d'API. Un abonnement Paddle en cours n'est pas résilié automatiquement." | t }}</p>
            <div class="champ">
              <label for="mdp-suppression">{{ 'Mot de passe actuel' | t }}</label>
              <input id="mdp-suppression" class="input" type="password" autocomplete="current-password"
                     [value]="mdpSuppression()" (input)="mdpSuppression.set($any($event.target).value)" />
            </div>
            <button class="btn danger" type="button" [disabled]="!mdpSuppression()" (click)="supprimerCompte()">{{ 'Supprimer mon compte' | t }}</button>
          }
          <p class="info"><a routerLink="/legal/confidentialite">{{ 'Politique de confidentialité' | t }}</a> · <a routerLink="/legal/cgu">{{ 'CGU' | t }}</a> · <a routerLink="/legal/dpa">{{ 'DPA' | t }}</a></p>
        </div>

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
    h3 { font-size: 13.5px; margin: 6px 0 0; }
    .actions { display: flex; gap: 10px; flex-wrap: wrap; }
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
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly paddle = inject(PaddleService);
  readonly marque = MARQUE;
  readonly langues = inject(LangueService);
  readonly abonnement = signal<Abonnement | null>(null);
  readonly mdpSuppression = signal('');
  readonly languesIa = LANGUES_IA;

  readonly systeme = signal<SystemeInfo | null>(null);
  readonly jetons = signal<JetonApi[]>([]);
  readonly installationsGithub = signal<InstallationGithub[]>([]);
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
    this.api.systeme().subscribe((s) => {
      this.systeme.set(s);
      if (s.githubActif && this.auth.estDeveloppeur()) {
        this.api.installationsGithub().subscribe({ next: (l) => this.installationsGithub.set(l), error: () => this.installationsGithub.set([]) });
      }
    });
    if (this.auth.estDeveloppeur()) {
      this.api.jetons().subscribe((j) => this.jetons.set(j));
      this.chargerAbonnement();
      // Arrivée depuis « Passer à Pro / Équipe » (accueil → inscription) : ouvrir directement le paiement
      const offre = this.route.snapshot.queryParamMap.get('offre');
      this.paddle.charger().subscribe(() => {
        const plan: Plan | null = offre === 'pro' ? 'PRO' : offre === 'equipe' ? 'EQUIPE' : null;
        if (plan && this.paddle.prixDisponible(plan) && !this.abonnement()?.abonnementEnCours) {
          this.souscrire(plan);
        }
      });
    }
  }

  private chargerAbonnement(): void {
    this.api.abonnement().subscribe({ next: (a) => this.abonnement.set(a), error: () => this.abonnement.set(null) });
  }

  nomOffre(p?: Plan | null): string {
    return p === 'PRO' ? 'Pro' : p === 'EQUIPE' ? 'Équipe' : 'Gratuit';
  }

  statutAbonnement(s: string): string {
    const libelles: Record<string, string> = {
      active: 'Actif', trialing: 'Période d\'essai', past_due: 'Paiement en retard', paused: 'En pause', canceled: 'Résilié',
    };
    return libelles[s] ?? s;
  }

  souscrire(plan: Plan): void {
    this.paddle.ouvrirCheckout(plan).catch(() => this.notif.erreur('Le paiement n\'a pas pu s\'ouvrir. Réessayez ou écrivez-nous.'));
  }

  ouvrirPortail(): void {
    this.api.portail().subscribe({
      next: (p) => window.open(p.url, '_blank', 'noopener'),
      error: (e) => this.notif.erreur(messageErreur(e)),
    });
  }

  exporter(): void {
    this.api.exporterCompte().subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'revia-export.json';
        a.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      },
      error: (e) => this.notif.erreur(messageErreur(e)),
    });
  }

  supprimerCompte(): void {
    if (!confirm(tr('Supprimer définitivement votre compte et toutes vos revues ? Cette action est irréversible.'))) {
      return;
    }
    this.api.supprimerCompte(this.mdpSuppression()).subscribe({
      next: (r) => {
        if (r.avertissement) {
          alert(tr(r.avertissement));
        }
        this.auth.deconnexion(false);
        this.notif.succes('Compte supprimé.');
        this.router.navigate(['/']);
      },
      error: (e) => this.notif.erreur(messageErreur(e)),
    });
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

  delierGithub(id: number): void {
    if (!confirm(tr("Délier cette installation ? RevIA ne revoit plus ses pull requests ; l'application reste installée sur GitHub (Settings › Applications pour la désinstaller)."))) {
      return;
    }
    this.api.delierGithub(id).subscribe({
      next: () => {
        this.installationsGithub.update((l) => l.filter((i) => i.installationId !== id));
        this.notif.succes('Installation GitHub déliée.');
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
