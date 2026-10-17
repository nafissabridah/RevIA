import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../core/auth.service';
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
        <a class="logo" routerLink="/">
          <span class="carre">R</span><span class="nom">Rev<span class="ia">IA</span></span>
        </a>

        <nav>
          @if (auth.estDeveloppeur()) {
            <div class="section">Espace de travail</div>
            <a routerLink="/revues/nouvelle" routerLinkActive="actif"><app-icone nom="nouvelle" />Nouvelle revue</a>
            <a routerLink="/historique" routerLinkActive="actif"><app-icone nom="historique" />Historique
              <span class="compteur">{{ auth.utilisateur()?.nbRevues ?? 0 }}</span></a>
            <a routerLink="/tableau-de-bord" routerLinkActive="actif"><app-icone nom="tableau" />Tableau de bord</a>
            <a routerLink="/parametres" routerLinkActive="actif"><app-icone nom="parametres" />Paramètres</a>
          }
          @if (auth.estAdmin()) {
            <div class="section">Administration</div>
            <a routerLink="/admin" routerLinkActive="actif"><app-icone nom="utilisateurs" />Utilisateurs &amp; quotas</a>
            <a routerLink="/parametres" routerLinkActive="actif"><app-icone nom="parametres" />Paramètres</a>
          }
        </nav>

        <div class="bas">
          @if (auth.estDeveloppeur()) {
            <div class="quota" [class.plein]="pourcentage() >= 100">
              <div class="q-titre">Quota du jour</div>
              <div class="jauge"><span [style.width.%]="pourcentage()"></span></div>
              <div class="q-texte">{{ auth.utilisateur()?.revuesDuJour ?? 0 }} / {{ auth.utilisateur()?.quotaJournalier ?? 0 }} revues · réinitialisé à minuit</div>
            </div>
          }
          <div class="profil">
            <span class="avatar">{{ initiales(auth.utilisateur()?.nom) }}</span>
            <div class="qui">
              <div class="n">{{ auth.utilisateur()?.nom }}</div>
              <div class="r">{{ auth.estAdmin() ? 'Administrateur' : 'Développeur' }}</div>
            </div>
            <button class="sortir" type="button" (click)="auth.deconnexion()" title="Se déconnecter" aria-label="Se déconnecter">
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
    .compteur { margin-left: auto; font-size: 11px; background: #262b52; color: #b9bff0; border-radius: 6px; padding: 1px 7px; }
    .bas { margin-top: auto; display: flex; flex-direction: column; gap: 12px; }
    .quota { background: var(--sidebar-2); border: 1px solid #262b52; border-radius: 10px; padding: 12px; }
    .q-titre { color: #fff; font-weight: 600; font-size: 12.5px; margin-bottom: 8px; }
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
  private readonly router = inject(Router);
  readonly menuOuvert = signal(false);
  readonly initiales = initiales;
  readonly pourcentage = computed(() => {
    const u = this.auth.utilisateur();
    if (!u?.quotaJournalier) return 0;
    return Math.min(100, Math.round(((u.revuesDuJour ?? 0) / u.quotaJournalier) * 100));
  });

  ngOnInit(): void {
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => this.menuOuvert.set(false));
  }
}
