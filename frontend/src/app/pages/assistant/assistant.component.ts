import { DecimalPipe } from '@angular/common';
import { AfterViewChecked, ChangeDetectionStrategy, Component, ElementRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FluxIaService } from '../../core/flux-ia.service';
import { LANGAGES } from '../../core/libelles';
import { MessageChat } from '../../core/models';
import { TransfertCodeService } from '../../core/transfert-code.service';
import { IconeComponent } from '../../shared/icone.component';
import { ReponseIaComponent } from '../../shared/reponse-ia.component';
import { TPipe, tr } from '../../core/i18n';

const CLE = 'revia.chat';
const SUGGESTIONS = [
  "Explique-moi l'erreur NullPointerException et comment l'éviter",
  'Comment se protéger des injections SQL en Java ?',
  "Quelle est la différence entre == et equals() ?",
  'Écris une expression régulière qui valide une adresse e-mail',
];

interface Bulle extends MessageChat {
  meta?: string;
  enCours?: boolean;
}

/** Assistant IA (chatbot) : questions libres sur le code, avec un extrait joint en option. */
@Component({
  selector: 'app-assistant',
  standalone: true,
  imports: [TPipe, FormsModule, RouterLink, DecimalPipe, IconeComponent, ReponseIaComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="barre">
      <div class="fil"><a routerLink="/revues/nouvelle">{{ 'RevIA' | t }}</a> / <b>{{ 'Assistant IA' | t }}</b></div>
      <button class="btn" type="button" (click)="codeOuvert.set(!codeOuvert())">
        <app-icone nom="import" />{{ codeOuvert() ? ('Masquer le code joint' | t) : ('Joindre du code' | t) }}
      </button>
      <button class="btn" type="button" (click)="nouvelle()" [disabled]="!messages().length || attente()">
        <app-icone nom="nouvelle" />{{ 'Nouvelle conversation' | t }}
      </button>
    </header>

    <div class="page grille" [class.avec-code]="codeOuvert()">
      <section class="carte chat" [attr.aria-label]="'Conversation' | t">
        <div class="fil-messages" #fil>
          @if (!messages().length) {
            <div class="accueil">
              <div class="logo-ia">✦</div>
              <h2>{{ 'Bonjour, je suis l\\'assistant RevIA' | t }}</h2>
              <p>{{ 'Posez une question sur votre code, une erreur ou une bonne pratique. Je réponds en français, avec des exemples.' | t }}</p>
              <div class="suggestions">
                @for (s of suggestions; track s) {
                  <button type="button" class="suggestion" (click)="envoyer(tr(s))">{{ s | t }}</button>
                }
              </div>
            </div>
          }
          @for (m of messages(); track $index) {
            <div class="message" [class.moi]="m.role === 'utilisateur'">
              @if (m.role === 'assistant') { <span class="avatar-ia">✦</span> }
              <div class="bulle">
                @if (m.role === 'assistant' && m.enCours && !m.contenu) {
                  <div class="ecrit"><i></i><i></i><i></i></div>
                } @else if (m.role === 'assistant') {
                  <app-reponse-ia [contenu]="m.contenu" [actions]="!m.enCours" />
                  @if (m.meta) { <div class="meta">{{ m.meta }}</div> }
                } @else {
                  <div class="texte">{{ m.contenu }}</div>
                }
              </div>
            </div>
          }
          @if (erreur()) {
            <div class="erreur" role="alert">{{ erreur() | t }} <button class="btn petit" type="button" (click)="reessayer()">{{ 'Réessayer' | t }}</button></div>
          }
        </div>

        <form class="saisie" (ngSubmit)="envoyer(question())">
          <textarea class="input" rows="2" [ngModel]="question()" (ngModelChange)="question.set($event)" name="question"
                    [placeholder]="'Écrivez votre question… (Entrée pour envoyer, Maj+Entrée pour aller à la ligne)' | t" maxlength="8000"
                    (keydown.enter)="toucheEntree($event)" [attr.aria-label]="'Votre question' | t"></textarea>
          <button class="btn primaire envoyer" type="submit" [disabled]="!question().trim() || attente()" [attr.aria-label]="'Envoyer' | t">
            <app-icone nom="envoyer" />
          </button>
        </form>
        <p class="confiance"><app-icone nom="bouclier" [taille]="13" /> {{ 'Mots de passe, clés et e-mails sont masqués avant l\\'envoi à l\\'IA.' | t }}</p>
      </section>

      @if (codeOuvert()) {
        <aside class="carte code" [attr.aria-label]="'Code joint' | t">
          <div class="tete">
            <b>{{ 'Code joint' | t }}</b>
            <select class="input petit-select" [ngModel]="langage()" (ngModelChange)="langage.set($event)" [attr.aria-label]="'Langage' | t">
              @for (l of langages; track l.valeur) { <option [value]="l.valeur">{{ l.libelle | t }}</option> }
            </select>
          </div>
          <textarea class="input zone-code" [ngModel]="code()" (ngModelChange)="code.set($event)" spellcheck="false"
                    [placeholder]="'Collez ici le code dont vous voulez parler (optionnel)' | t" [attr.aria-label]="'Code joint' | t"></textarea>
          <span class="aide">{{ code() ? (code().split('\\n').length | number) + (' ligne(s) jointe(s) à chaque question' | t) : ("L'assistant verra ce code avec vos questions." | t) }}</span>
        </aside>
      }
    </div>
  `,
  styles: [`
    .grille { display: grid; grid-template-columns: minmax(0, 1fr); gap: 18px; max-width: 1100px; }
    .grille.avec-code { grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr); max-width: 1440px; }
    .chat { display: flex; flex-direction: column; height: calc(100vh - 110px); min-height: 480px; }
    .fil-messages { flex: 1; overflow-y: auto; padding: 22px; display: flex; flex-direction: column; gap: 16px; }
    .accueil { margin: auto; text-align: center; max-width: 560px; }
    .logo-ia {
      width: 52px; height: 52px; margin: 0 auto 12px; border-radius: 14px; display: grid; place-items: center; font-size: 24px; color: #fff;
      background: linear-gradient(135deg, #6d6af8, #4338ca);
    }
    .accueil h2 { font-size: 19px; margin-bottom: 6px; }
    .accueil p { color: var(--texte-2); margin: 0 0 18px; }
    .suggestions { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .suggestion {
      text-align: left; border: 1px solid var(--bord); background: var(--surface-2); border-radius: 10px; padding: 10px 12px;
      font: 13px var(--police); color: var(--texte); cursor: pointer;
    }
    .suggestion:hover { border-color: var(--primaire); background: var(--primaire-pale); }
    .message { display: flex; gap: 10px; align-items: flex-start; }
    .message.moi { justify-content: flex-end; }
    .avatar-ia {
      width: 28px; height: 28px; border-radius: 8px; flex-shrink: 0; display: grid; place-items: center; color: #fff; font-size: 13px;
      background: linear-gradient(135deg, #6d6af8, #4338ca);
    }
    .bulle { max-width: 82%; min-width: 0; background: var(--surface-2); border: 1px solid var(--bord); border-radius: 4px 14px 14px 14px; padding: 12px 14px; }
    .moi .bulle { background: var(--primaire); color: #fff; border-color: var(--primaire); border-radius: 14px 4px 14px 14px; }
    .texte { white-space: pre-wrap; overflow-wrap: anywhere; font-size: 13.5px; line-height: 1.55; }
    .meta { margin-top: 8px; font-size: 11.5px; color: var(--texte-3); }
    .ecrit { display: flex; gap: 5px; padding: 4px 2px; }
    .ecrit i { width: 7px; height: 7px; border-radius: 50%; background: var(--texte-3); animation: rebond 1s infinite ease-in-out; }
    .ecrit i:nth-child(2) { animation-delay: .15s; } .ecrit i:nth-child(3) { animation-delay: .3s; }
    @keyframes rebond { 0%, 80%, 100% { opacity: .3; transform: translateY(0); } 40% { opacity: 1; transform: translateY(-4px); } }
    .erreur { background: var(--critique-pale); color: var(--critique); border-radius: 10px; padding: 10px 12px; font-size: 13px; display: flex; gap: 10px; align-items: center; justify-content: space-between; }
    .saisie { display: flex; gap: 10px; padding: 14px 16px 6px; border-top: 1px solid var(--bord); }
    .saisie textarea { flex: 1; resize: none; font-family: var(--police); font-size: 14px; }
    .envoyer { height: auto; width: 46px; padding: 0; }
    .confiance { margin: 0; padding: 0 16px 12px; font-size: 11.5px; color: var(--texte-3); display: flex; gap: 6px; align-items: center; }
    .code { display: flex; flex-direction: column; gap: 10px; padding: 14px; height: calc(100vh - 110px); min-height: 480px; }
    .tete { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
    .petit-select { height: 32px; font-size: 13px; }
    .zone-code { flex: 1; resize: none; background: #111427; color: #e5e7ff; border-color: #1f2444; }
    .aide { font-size: 12px; color: var(--texte-3); }
    @media (max-width: 900px) {
      .grille.avec-code { grid-template-columns: 1fr; }
      .suggestions { grid-template-columns: 1fr; }
      .code { height: 360px; min-height: 0; }
    }
  `],
})
export class AssistantComponent implements OnInit, AfterViewChecked {
  private readonly flux = inject(FluxIaService);
  private readonly transfert = inject(TransfertCodeService);
  private readonly fil = viewChild<ElementRef<HTMLElement>>('fil');

  readonly suggestions = SUGGESTIONS;
  readonly tr = tr;
  readonly langages = LANGAGES;
  readonly messages = signal<Bulle[]>([]);
  readonly question = signal('');
  readonly attente = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly codeOuvert = signal(false);
  readonly code = signal('');
  readonly langage = signal('auto');
  private defiler = false;

  ngOnInit(): void {
    try {
      const sauve = sessionStorage.getItem(CLE);
      if (sauve) {
        this.messages.set(JSON.parse(sauve) as Bulle[]);
      }
    } catch {
      /* stockage indisponible : conversation vide */
    }
    const recu = this.transfert.prendre();
    if (recu) {
      this.code.set(recu.code);
      this.langage.set(recu.langage);
      this.codeOuvert.set(true);
    }
    this.defiler = true;
  }

  ngAfterViewChecked(): void {
    const el = this.fil()?.nativeElement;
    if (this.defiler && el) {
      el.scrollTop = el.scrollHeight;
      this.defiler = false;
    }
  }

  toucheEntree(e: Event): void {
    const k = e as KeyboardEvent;
    if (!k.shiftKey) {
      k.preventDefault();
      this.envoyer(this.question());
    }
  }

  envoyer(texte: string): void {
    const q = texte.trim();
    if (!q || this.attente()) {
      return;
    }
    this.messages.update((m) => [...m, { role: 'utilisateur', contenu: q }]);
    this.question.set('');
    this.demander();
  }

  reessayer(): void {
    this.demander();
  }

  nouvelle(): void {
    this.messages.set([]);
    this.erreur.set(null);
    this.sauver();
  }

  private demander(): void {
    this.attente.set(true);
    this.erreur.set(null);
    this.defiler = true;
    const historique = this.messages().map(({ role, contenu }) => ({ role, contenu }));
    const code = this.codeOuvert() ? this.code() : '';
    this.messages.update((m) => [...m, { role: 'assistant', contenu: '', enCours: true }]);
    const majDernier = (f: (b: Bulle) => Bulle) => this.messages.update((m) => [...m.slice(0, -1), f(m[m.length - 1])]);
    this.flux.discuter(historique, code, this.langage()).subscribe((ev) => {
      if (ev.type === 'morceau') {
        majDernier((b) => ({ ...b, contenu: b.contenu + ev.texte }));
      } else if (ev.type === 'fin') {
        const secrets = ev.info.nbMasquages ? ` · ${ev.info.nbMasquages} secret(s) masqué(s)` : '';
        majDernier((b) => ({
          role: 'assistant', contenu: b.contenu.trim(),
          meta: `${ev.info.modele} · ${(ev.info.dureeMs / 1000).toFixed(1).replace('.', ',')} s${secrets}`,
        }));
        this.attente.set(false);
        this.sauver();
      } else {
        // Réponse incomplète : on la retire pour pouvoir réessayer proprement
        this.messages.update((m) => m.slice(0, -1));
        this.erreur.set(ev.message);
        this.attente.set(false);
      }
      this.defiler = true;
    });
  }

  private sauver(): void {
    try {
      sessionStorage.setItem(CLE, JSON.stringify(this.messages()));
    } catch {
      /* ignoré */
    }
  }
}
