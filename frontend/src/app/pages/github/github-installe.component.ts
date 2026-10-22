import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { installationEnAttente, lireInstallation, memoriserInstallation, oublierInstallation } from '../../core/github';
import { messageErreur } from '../../core/http';
import { TPipe } from '../../core/i18n';
import { NotifService } from '../../core/notif.service';

/**
 * URL de configuration (« Setup URL ») de la GitHub App : GitHub y renvoie l'utilisateur après l'installation avec
 * installation_id. L'installation est reliée au compte connecté (connexion demandée d'abord si besoin), puis
 * retour aux paramètres.
 */
@Component({
  selector: 'app-github-installe',
  standalone: true,
  imports: [TPipe, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ecran">
      <div class="carte boite">
        <h1>{{ 'GitHub' | t }}</h1>
        @if (erreur(); as e) {
          <p class="message-erreur">{{ 'Liaison GitHub impossible' | t }} : {{ e | t }}</p>
          <a class="btn primaire" routerLink="/parametres">{{ 'Retour aux paramètres' | t }}</a>
        } @else {
          <p>{{ 'Connexion de GitHub à RevIA…' | t }}</p>
        }
      </div>
    </div>
  `,
  styles: [`
    .ecran { min-height: 100vh; display: grid; place-items: center; padding: 24px; background: var(--fond, #f6f7fb); }
    .boite { max-width: 460px; width: 100%; padding: 28px; display: flex; flex-direction: column; gap: 14px; }
    h1 { font-size: 20px; margin: 0; }
    p { margin: 0; font-size: 14px; }
    .btn { align-self: flex-start; }
    .message-erreur { background: var(--critique-pale); color: var(--critique); border-radius: 9px; padding: 10px 12px; font-size: 13px; }
  `],
})
export class GithubInstalleComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly notif = inject(NotifService);
  readonly erreur = signal<string | null>(null);

  ngOnInit(): void {
    const demande = lireInstallation(this.route.snapshot.queryParamMap) ?? installationEnAttente();
    if (!demande) {
      this.erreur.set("Lien d'installation GitHub invalide.");
      return;
    }
    if (!this.auth.estConnecte()) {
      // Connexion d'abord : la page de connexion revient ici une fois l'utilisateur connecté
      memoriserInstallation(demande);
      this.notif.succes('Connectez-vous pour relier votre installation GitHub.');
      this.router.navigate(['/connexion']);
      return;
    }
    memoriserInstallation(demande);
    this.api.lierGithub(demande.installationId, demande.code).subscribe({
      next: () => {
        oublierInstallation();
        this.notif.succes('Installation GitHub reliée : vos pull requests seront revues automatiquement.');
        this.router.navigate(['/parametres']);
      },
      error: (e) => {
        if (e?.status !== 401) {
          oublierInstallation();
        }
        this.erreur.set(messageErreur(e));
      },
    });
  }
}
