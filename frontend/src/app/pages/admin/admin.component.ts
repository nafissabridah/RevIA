import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { messageErreur } from '../../core/http';
import { PromptSysteme, SystemeInfo, Utilisateur } from '../../core/models';
import { NotifService } from '../../core/notif.service';
import { TPipe } from '../../core/i18n';

/** Gérer utilisateurs & quotas (administrateur) + versions du prompt système. */
@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [TPipe, FormsModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="barre"><div class="fil">{{ 'Administration /' | t }} <b>{{ 'Utilisateurs & quotas' | t }}</b></div></header>
    <div class="page">
      <h1 class="titre-page">{{ 'Utilisateurs & quotas' | t }}</h1>
      <p class="sous-titre">{{ 'Définissez le nombre de revues par jour de chaque développeur, bloquez un compte, gérez le prompt système.' | t }}</p>

      @if (systeme(); as s) {
        <div class="carte etat" [class.ko]="!s.disponible">
          <b>{{ 'Module IA :' | t }} {{ s.fournisseur }} / {{ s.modele }}</b>
          <span>{{ s.disponible ? ('opérationnel' | t) : ("clé d'API absente : les analyses sont refusées (définir REVIA_AI_API_KEY)" | t) }} {{ '· température' | t }} {{ s.temperature }} {{ '· délai' | t }} {{ s.timeoutSec }} {{ 's · quota par défaut' | t }} {{ s.quotaParDefaut }}{{ '/jour' | t }}</span>
        </div>
      }

      <div class="carte">
        <div class="defile">
          <table>
            <thead><tr><th>{{ 'Utilisateur' | t }}</th><th>{{ 'Rôle' | t }}</th><th>{{ 'Inscrit le' | t }}</th><th>{{ 'Revues' | t }}</th><th>{{ 'Aujourd\\'hui' | t }}</th><th>{{ 'Quota / jour' | t }}</th><th>{{ 'Compte' | t }}</th></tr></thead>
            <tbody>
              @for (u of utilisateurs(); track u.id) {
                <tr [class.bloque]="u.bloque">
                  <td><b>{{ u.nom }}</b><br /><span class="mail">{{ u.email }}</span></td>
                  <td>{{ u.role === 'ADMIN' ? 'Administrateur' : 'Développeur' }}</td>
                  <td>{{ u.creeLe | date: 'dd/MM/yyyy' }}</td>
                  <td>{{ u.nbRevues }}</td>
                  <td>{{ u.revuesDuJour ?? '—' }}</td>
                  <td>
                    @if (u.role === 'DEVELOPPEUR') {
                      <div class="quota">
                        <input class="input" type="number" min="0" max="10000" [(ngModel)]="quotas[u.id]" [attr.aria-label]="('Quota de ' | t) + u.nom" />
                        <button class="btn petit" type="button" (click)="definirQuota(u)" [disabled]="quotas[u.id] === u.quotaJournalier">{{ 'OK' | t }}</button>
                      </div>
                    } @else { — }
                  </td>
                  <td>
                    @if (u.id !== auth.utilisateur()?.id) {
                      <button class="btn petit" [class.danger]="!u.bloque" type="button" (click)="bloquer(u)">{{ u.bloque ? ('Débloquer' | t) : ('Bloquer' | t) }}</button>
                    } @else { <span class="mail">{{ 'vous' | t }}</span> }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>

      <h2 class="sous-section">{{ 'Prompt système' | t }}</h2>
      <div class="prompts">
        <div class="carte versions">
          @for (p of prompts(); track p.id) {
            <button type="button" class="version" [class.selection]="p.id === promptVu()?.id" (click)="promptVu.set(p)">
              <b>{{ p.version }}</b> @if (p.actif) { <span class="badge s-corrigee">{{ 'actif' | t }}</span> }
              <span class="mail">{{ p.creeLe | date: ('dd/MM/yyyy HH:mm' | t) }}</span>
            </button>
          }
          <button type="button" class="btn petit" (click)="nouveau()">{{ '+ Nouvelle version' | t }}</button>
        </div>
        <div class="carte editeur-prompt">
          @if (edition()) {
            <div class="champ"><label for="v">{{ 'Version' | t }}</label><input id="v" class="input" [(ngModel)]="nouvelleVersion" [placeholder]="'v2' | t" maxlength="20" /></div>
            <textarea class="input" rows="18" [(ngModel)]="nouveauContenu" [attr.aria-label]="'Contenu du prompt' | t"></textarea>
            <p class="mail">{{ 'Variables disponibles :' | t }} {{ '{' }}{{ 'langage' | t }}{{ '}' }} {{ 'et' | t }} {{ '{' }}{{ 'mode' | t }}{{ '}' }}{{ '. La nouvelle version devient active immédiatement.' | t }}</p>
            <div class="actions">
              <button class="btn" type="button" (click)="edition.set(false)">{{ 'Annuler' | t }}</button>
              <button class="btn primaire" type="button" (click)="publier()">{{ 'Publier et activer' | t }}</button>
            </div>
          } @else if (promptVu()) {
            @if (promptVu(); as p) {
              <pre>{{ p.contenu }}</pre>
              @if (!p.actif) { <button class="btn" type="button" (click)="activer(p)">{{ 'Activer cette version' | t }}</button> }
            }
          }
        </div>
      </div>
    </div>
  `,
  styles: [`
    .etat { padding: 12px 16px; margin-bottom: 14px; display: flex; flex-direction: column; gap: 3px; border-left: 4px solid var(--ok); font-size: 13px; }
    .etat.ko { border-left-color: var(--critique); }
    .etat span { color: var(--texte-2); }
    .defile { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; min-width: 820px; }
    th { text-align: left; font-size: 11px; letter-spacing: .08em; text-transform: uppercase; color: var(--texte-3); padding: 10px 14px; border-bottom: 1px solid var(--bord); }
    td { padding: 10px 14px; border-bottom: 1px solid var(--bord); font-size: 13px; vertical-align: middle; }
    tr.bloque td { background: #fff7f7; }
    .mail { color: var(--texte-3); font-size: 12px; }
    .quota { display: flex; gap: 6px; }
    .quota .input { width: 86px; height: 30px; }
    .sous-section { font-size: 16px; margin: 24px 0 10px; }
    .prompts { display: grid; grid-template-columns: 240px 1fr; gap: 14px; align-items: start; }
    .versions { padding: 10px; display: flex; flex-direction: column; gap: 6px; }
    .version { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; text-align: left; padding: 8px 10px; border-radius: 8px; border: 1px solid transparent; background: none; cursor: pointer; font: 13px var(--police); }
    .version.selection { border-color: var(--primaire); background: var(--primaire-pale); }
    .editeur-prompt { padding: 14px; display: flex; flex-direction: column; gap: 10px; min-width: 0; }
    pre { white-space: pre-wrap; font-size: 12.5px; margin: 0; background: var(--surface-2); padding: 12px; border-radius: 8px; max-height: 480px; overflow: auto; }
    .actions { display: flex; gap: 8px; justify-content: flex-end; }
    @media (max-width: 900px) { .prompts { grid-template-columns: 1fr; } }
  `],
})
export class AdminComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly notif = inject(NotifService);

  readonly utilisateurs = signal<Utilisateur[]>([]);
  readonly prompts = signal<PromptSysteme[]>([]);
  readonly promptVu = signal<PromptSysteme | null>(null);
  readonly systeme = signal<SystemeInfo | null>(null);
  readonly edition = signal(false);
  quotas: Record<number, number | null> = {};
  nouvelleVersion = '';
  nouveauContenu = '';

  ngOnInit(): void {
    this.chargerUtilisateurs();
    this.chargerPrompts();
    this.api.systeme().subscribe((s) => this.systeme.set(s));
  }

  private chargerUtilisateurs(): void {
    this.api.utilisateurs().subscribe({
      next: (us) => {
        this.quotas = Object.fromEntries(us.map((u) => [u.id, u.quotaJournalier]));
        this.utilisateurs.set(us);
      },
      error: (e) => this.notif.erreur(messageErreur(e)),
    });
  }

  private chargerPrompts(): void {
    this.api.prompts().subscribe((ps) => {
      this.prompts.set(ps);
      this.promptVu.set(ps.find((p) => p.actif) ?? ps[0] ?? null);
    });
  }

  definirQuota(u: Utilisateur): void {
    const q = Number(this.quotas[u.id]);
    this.api.definirQuota(u.id, q).subscribe({
      next: (n) => {
        this.remplacer(n);
        this.notif.succes(`Quota de ${n.nom} : ${n.quotaJournalier} revues / jour.`);
      },
      error: (e) => this.notif.erreur(messageErreur(e)),
    });
  }

  bloquer(u: Utilisateur): void {
    if (!u.bloque && !confirm(`Bloquer le compte de ${u.nom} ? Il sera déconnecté immédiatement.`)) {
      return;
    }
    this.api.bloquer(u.id, !u.bloque).subscribe({
      next: (n) => {
        this.remplacer(n);
        this.notif.succes(n.bloque ? 'Compte bloqué.' : 'Compte débloqué.');
      },
      error: (e) => this.notif.erreur(messageErreur(e)),
    });
  }

  nouveau(): void {
    const actif = this.prompts().find((p) => p.actif);
    this.nouvelleVersion = `v${this.prompts().length + 1}`;
    this.nouveauContenu = actif?.contenu ?? '';
    this.edition.set(true);
  }

  publier(): void {
    this.api.publierPrompt(this.nouvelleVersion.trim(), this.nouveauContenu).subscribe({
      next: () => {
        this.edition.set(false);
        this.notif.succes('Nouvelle version du prompt publiée et activée.');
        this.chargerPrompts();
      },
      error: (e) => this.notif.erreur(messageErreur(e)),
    });
  }

  activer(p: PromptSysteme): void {
    this.api.activerPrompt(p.id).subscribe({
      next: () => {
        this.notif.succes(`Prompt ${p.version} activé.`);
        this.chargerPrompts();
      },
      error: (e) => this.notif.erreur(messageErreur(e)),
    });
  }

  private remplacer(n: Utilisateur): void {
    this.utilisateurs.update((us) => us.map((u) => (u.id === n.id ? n : u)));
    this.quotas[n.id] = n.quotaJournalier;
  }
}
