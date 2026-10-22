import { Injectable, signal } from '@angular/core';

/** Passe le code de l'écran de revue vers l'assistant ou les outils IA (sans le renvoyer au serveur). */
@Injectable({ providedIn: 'root' })
export class TransfertCodeService {
  private readonly valeur = signal<{ code: string; langage: string } | null>(null);

  deposer(code: string, langage: string): void {
    this.valeur.set({ code, langage });
  }

  /** Récupère le code déposé (une seule fois). */
  prendre(): { code: string; langage: string } | null {
    const v = this.valeur();
    this.valeur.set(null);
    return v;
  }
}
