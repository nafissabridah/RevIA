import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { messageErreur } from '../../core/http';
import { SystemeInfo } from '../../core/models';
import { NotifService } from '../../core/notif.service';

/** Paramètres : modifier son profil, changer son mot de passe, voir le module IA utilisé. */
@Component({
  selector: 'app-parametres',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="barre"><div class="fil">Espace de travail / <b>Paramètres</b></div></header>
    <div class="page">
      <h1 class="titre-page">Paramètres</h1>
      <p class="sous-titre">Votre profil, votre sécurité et le module d'IA utilisé par RevIA.</p>

      <div class="grille">
        <form class="carte bloc" [formGroup]="profil" (ngSubmit)="enregistrerProfil()">
          <h2>Profil</h2>
          <div class="champ">
            <label for="nom">Nom affiché</label>
            <input id="nom" class="input" formControlName="nom" maxlength="80" />
          </div>
          <div class="champ">
            <label>E-mail (identifiant)</label>
            <input class="input" [value]="auth.utilisateur()?.email" disabled />
          </div>
          <p class="info">Compte créé le {{ auth.utilisateur()?.creeLe | date: 'dd/MM/yyyy' }} ·
            @if (auth.estDeveloppeur()) { quota : {{ auth.utilisateur()?.quotaJournalier }} revues / jour } @else { administrateur }</p>
          <button class="btn primaire" type="submit" [disabled]="profil.invalid || profil.pristine">Enregistrer</button>
        </form>

        <form class="carte bloc" [formGroup]="mdp" (ngSubmit)="changerMdp()">
          <h2>Mot de passe</h2>
          <div class="champ">
            <label for="ancien">Mot de passe actuel</label>
            <input id="ancien" class="input" type="password" formControlName="ancien" autocomplete="current-password" />
          </div>
          <div class="champ">
            <label for="nouveau">Nouveau mot de passe</label>
            <input id="nouveau" class="input" type="password" formControlName="nouveau" autocomplete="new-password" />
            <span class="info">8 caractères minimum, avec au moins une lettre et un chiffre. Stocké haché (BCrypt).</span>
          </div>
          <button class="btn primaire" type="submit" [disabled]="mdp.invalid">Changer le mot de passe</button>
        </form>

        @if (systeme(); as s) {
          <div class="carte bloc">
            <h2>Module d'IA</h2>
            <dl>
              <dt>Fournisseur</dt><dd>{{ nomFournisseur(s.fournisseur) }}</dd>
              <dt>Modèle</dt><dd><code>{{ s.modele }}</code></dd>
              <dt>Température</dt><dd>{{ s.temperature }} (réponses stables d'une analyse à l'autre)</dd>
              <dt>Délai maximum</dt><dd>{{ s.timeoutSec }} s</dd>
              <dt>Taille maximale</dt><dd>{{ s.maxLignes }} lignes par extrait</dd>
              <dt>État</dt><dd>@if (s.disponible) { <span class="badge s-corrigee">● Disponible</span> } @else { <span class="badge s-a_corriger">● Clé d'API absente</span> }</dd>
              <dt>Version</dt><dd>RevIA {{ s.version }}</dd>
            </dl>
            <p class="info">🔒 La clé d'API ne quitte jamais le serveur ; vos secrets et e-mails sont masqués avant l'envoi au modèle.</p>
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
  `],
})
export class ParametresComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly notif = inject(NotifService);
  private readonly fb = inject(FormBuilder);

  readonly systeme = signal<SystemeInfo | null>(null);
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
      ollama: 'Ollama (local, gratuit)',
      openai: 'OpenAI',
      anthropic: 'Claude (Anthropic)',
    };
    return noms[f] ?? f;
  }

  ngOnInit(): void {
    this.profil.reset({ nom: this.auth.utilisateur()?.nom ?? '' });
    this.api.systeme().subscribe((s) => this.systeme.set(s));
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
