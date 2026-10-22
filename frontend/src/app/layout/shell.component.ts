import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { LangueService } from '../core/langue.service';
import { initiales } from '../core/libelles';
import { IconeComponent } from '../shared/icone.component';

/** Mise en page des maquettes : barre latérale bleu nuit + zone de travail claire. */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cadre" [class.menu-ouvert]="menuOuvert()">
      <aside class="laterale" aria-label="Navigation principale">
        <a class="logo" routerLink="/revues/nouvelle">
          <span class="carre">R</span><span class="nom">Rev<span class="ia">IA</span></span>
        </a>

        <nav>
          @if (auth.estDeveloppeur()) {
            <div class="section">{{ l.t('Espace de travail', 'Workspace') }}</div>
            <a routerLink="/revues/nouvelle" routerLinkActive="actif"><app-icone nom="nouvelle" />{{ l.t('Nouvelle revue', 'New review') }}</a>
            <a routerLink="/historique" routerLinkActive="actif"><app-icone nom="historique" />{{ l.t('Historique', 'History') }}
              <span class="compteur">{{ auth.utilisateur()?.nbRevues ?? 0 }}</span></a>
            <a routerLink="/tableau-de-bord" routerLinkActive="actif"><app-icone nom="tableau" />{{ l.t('Tableau de bord', 'Dashboard') }}</a>
            <div class="section">{{ l.t('IA pour dev & QA', 'AI for dev & QA') }}</div>
            <a routerLink="/assistant" routerLinkActive="actif"><app-icone nom="chat" />{{ l.t('Assistant IA', 'AI assistant') }}@if (fournisseur()) {<span class="nouveau">{{ fournisseur() }}</span>}</a>
            <a routerLink="/outils" routerLinkActive="actif"><app-icone nom="outils" />{{ l.t('Outils dev & QA', 'Dev & QA tools') }}</a>
            <a routerLink="/criteres" routerLinkActive="actif"><app-icone nom="criteres" />{{ l.t('Couverture des critères', 'Acceptance coverage') }}</a>
            <div class="section">{{ l.t('Compte', 'Account') }}</div>
            <a routerLink="/parametres" routerLinkActive="actif"><app-icone nom="parametres" />{{ l.t('Paramètres', 'Settings') }}</a>
          }
          @if (auth.estAdmin()) {
            <div class="section">Administration</div>
            <a routerLink="/admin" routerLinkActive="actif"><app-icone nom="utilisateurs" />{{ l.t('Utilisateurs & quotas', 'Users & quotas') }}</a>
            <a routerLink="/parametres" routerLinkActive="actif"><app-icone nom="parametres" />Paramètres</a>
          }
        </nav>

        <div class="bas">
          @if (auth.estDeveloppeur()) {
            <div class="quota" [class.plein]="pourcentage() >= 100">
              <div class="q-titre">{{ l.t('Quota du jour', 'Daily quota') }}
                <a class="q-offre" routerLink="/parametres" [title]="l.t('Offre et abonnement', 'Plan and subscription')">{{ nomOffre() }}</a></div>
              <div class="jauge"><span [style.width.%]="pourcentage()"></span></div>
              <div class="q-texte">{{ auth.utilisateur()?.revuesDuJour ?? 0 }} / {{ auth.utilisateur()?.quotaJournalier ?? 0 }} {{ l.t('revues · réinitialisé à minuit', 'reviews · resets at midnight') }}</div>
            </div>
          }
          <div class="profil">
            <span class="avatar">{{ initiales(auth.utilisateur()?.nom) }}</span>
            <div class="qui">
              <div class="n">{{ auth.utilisateur()?.nom }}</div>
              <div class="r">{{ auth.estAdmin() ? l.t('Administrateur', 'Administrator') : l.t('Développeur', 'Developer') }}</div>
            </div>
            <button class="langue" type="button" (click)="l.basculer()" [title]="l.t('Switch to English', 'Passer en français')"
                    [attr.aria-label]="l.t('Switch to English', 'Passer en français')">{{ l.langue() === 'fr' ? 'EN' : 'FR' }}</button>
            <button class="sortir" type="button" (click)="auth.deconnexion()" [title]="l.t('Se déconnecter', 'Sign out')" [attr.aria-label]="l.t('Se déconnecter', 'Sign out')">
              <app-icone nom="deconnexion" />
            </button>
          </div>
        </div>
      </aside>

      <button class="burger" type="button" (click)="menuOuvert.set(!menuOuvert())" aria-label="Menu"><app-icone nom="menu" [taille]="20" /></button>
      <div class="voile" (click)="menuOuvert.set(false)"></div>

      <main class="contenu">
        <router-outlet />
      </main>
    </div>
  `,
  styles: [`
    .cadre { display: grid; grid-template-columns: 232px 1fr; min-height: 100vh; }
    .laterale {
      background: var(--sidebar); color: var(--sidebar-texte); display: flex; flex-direction: column;
      padding: 18px 14px; position: sticky; top: 0; height: 100vh; z-index: 20;
    }
    .logo { display: flex; align-items: center; gap: 10px; padding: 4px 8px 22px; text-decoration: none; }
    .carre {
      width: 30px; height: 30px; border-radius: 8px; display: grid; place-items: center; color: #fff; font-weight: 800;
      background: linear-gradient(135deg, #6d6af8, #4338ca);
    }
    .nom { color: #fff; font-weight: 800; font-size: 18px; }
    .ia { color: var(--accent-ia); }
    .section { font-size: 10.5px; letter-spacing: .14em; text-transform: uppercase; color: #6c7298; margin: 14px 10px 8px; }
    nav a {
      display: flex; align-items: center; gap: 10px; padding: 9px 10px; border-radius: 9px; color: var(--sidebar-texte);
      font-size: 13.5px; margin-bottom: 2px; text-decoration: none;
    }
    nav a:hover { background: var(--sidebar-2); }
    nav a.actif { background: var(--sidebar-2); color: #fff; box-shadow: inset 0 0 0 1px #2c3160; }
    .nouveau { margin-left: auto; font-size: 10px; font-weight: 700; background: linear-gradient(135deg, #6d6af8, #4338ca); color: #fff; border-radius: 6px; padding: 1px 6px; }
    .compteur { margin-left: auto; font-size: 11px; background: #262b52; color: #b9bff0; border-radius: 6px; padding: 1px 7px; }
    .bas { margin-top: auto; display: flex; flex-direction: column; gap: 12px; }
    .quota { background: var(--sidebar-2); border: 1px solid #262b52; border-radius: 10px; padding: 12px; }
    .q-titre { color: #fff; font-weight: 600; font-size: 12.5px; margin-bottom: 8px; display: flex; justify-content: space-between; gap: 8px; }
    .q-offre { font-size: 10.5px; font-weight: 700; letter-spacing: .04em; text-transform: uppercase; color: #c7d2fe; background: #2b2f63; border-radius: 6px; padding: 1px 7px; text-decoration: none; }
    .jauge { height: 5px; background: #2a2f58; border-radius: 4px; overflow: hidden; }
    .jauge span { display: block; height: 100%; background: linear-gradient(90deg, #6d6af8, #a5b4fc); border-radius: 4px; transition: width .4s; }
    .plein .jauge span { background: var(--critique); }
    .q-texte { font-size: 11.5px; color: #9aa0c8; margin-top: 7px; }
    .profil { display: flex; align-items: center; gap: 10px; padding: 4px; }
    .avatar {
      width: 32px; height: 32px; border-radius: 50%; background: #f59e0b; color: #fff; display: grid; place-items: center;
      font-weight: 700; font-size: 12px; flex-shrink: 0;
    }
    .qui { flex: 1; min-width: 0; }
    .n { color: #fff; font-weight: 600; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .r { color: #8b91bb; font-size: 11.5px; }
    .langue { background: transparent; border: 1px solid #2c3160; color: #c9cdf0; border-radius: 7px; padding: 4px 7px; cursor: pointer; font-weight: 700; font-size: 11px; }
    .langue:hover { border-color: #5b5bf6; color: #fff; }
    .sortir { background: none; border: none; color: #8b91bb; cursor: pointer; padding: 6px; border-radius: 8px; }
    .sortir:hover { background: var(--sidebar-2); color: #fff; }
    .contenu { min-width: 0; }
    .burger, .voile { display: none; }

    @media (max-width: 900px) {
      .cadre { grid-template-columns: 1fr; }
      .laterale { position: fixed; left: 0; top: 0; width: 248px; transform: translateX(-100%); transition: transform .2s; }
      .menu-ouvert .laterale { transform: none; }
      .burger {
        display: grid; place-items: center; position: fixed; right: 14px; bottom: 14px; z-index: 30; width: 46px; height: 46px;
        border-radius: 50%; border: none; background: var(--primaire); color: #fff; box-shadow: var(--ombre);
      }
      .menu-ouvert .voile { display: block; position: fixed; inset: 0; background: rgba(0, 0, 0, .35); z-index: 15; }
    }
  `],
})
export class ShellComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly l = inject(LangueService);
  private readonly router = inject(Router);
  private readonly api = inject(ApiService);
  readonly menuOuvert = signal(false);
  /** Fournisseur réellement configuré (et non une étiquette figée). */
  readonly fournisseur = signal('');
  private static readonly NOMS: Record<string, string> = {
    demo: 'Démo', gemini: 'Gemini', groq: 'Groq', openrouter: 'OpenRouter', mistral: 'Mistral', ollama: 'Local', openai: 'OpenAI', anthropic: 'Claude',
  };
  readonly initiales = initiales;
  /** Offre affichée sur la carte de quota (GRATUIT / PRO / EQUIPE). */
  readonly nomOffre = computed(() => {
    const p = this.auth.utilisateur()?.plan ?? 'GRATUIT';
    return p === 'PRO' ? 'Pro' : p === 'EQUIPE' ? this.l.t('Équipe', 'Team') : this.l.t('Gratuit', 'Free');
  });

  readonly pourcentage = computed(() => {
    const u = this.auth.utilisateur();
    if (!u?.quotaJournalier) return 0;
    return Math.min(100, Math.round(((u.revuesDuJour ?? 0) / u.quotaJournalier) * 100));
  });

  ngOnInit(): void {
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => this.menuOuvert.set(false));
    this.api.systeme().subscribe({ next: (s) => this.fournisseur.set(ShellComponent.NOMS[s.fournisseur] ?? s.fournisseur), error: () => {} });
  }
}
