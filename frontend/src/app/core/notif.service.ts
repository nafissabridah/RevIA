import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { tr } from './i18n';

@Injectable({ providedIn: 'root' })
export class NotifService {
  private readonly snack = inject(MatSnackBar);

  succes(message: string): void {
    this.snack.open(tr(message), 'OK', { duration: 3000, panelClass: 'snack-ok' });
  }

  erreur(message: string): void {
    this.snack.open(tr(message), tr('Fermer'), { duration: 7000, panelClass: 'snack-erreur' });
  }
}
