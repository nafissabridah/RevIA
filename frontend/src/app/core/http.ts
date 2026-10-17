import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { ErreurApi } from './models';

/** Ajoute le jeton JWT ; sur 401, ferme la session et renvoie vers la connexion. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const jeton = auth.jeton;
  const requete = jeton && req.url.startsWith('/api') ? req.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } }) : req;
  return next(requete).pipe(
    catchError((e: HttpErrorResponse) => {
      if (e.status === 401 && !req.url.includes('/api/auth/')) {
        auth.deconnexion();
      }
      return throwError(() => e);
    }),
  );
};

/** Message lisible pour l'utilisateur : jamais d'erreur technique brute (annexe de la présentation). */
export function messageErreur(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    const corps = e.error as ErreurApi | null;
    if (corps && typeof corps === 'object' && 'message' in corps && corps.message) {
      return corps.message;
    }
    if (e.status === 0) {
      return 'Impossible de joindre le serveur RevIA. Vérifiez votre connexion.';
    }
    if (e.status === 504) {
      return "L'analyse prend plus de temps que prévu. Réessayez dans un instant.";
    }
    if (e.status === 429) {
      return 'Trop de demandes. Réessayez dans un instant.';
    }
  }
  return "Une erreur inattendue s'est produite. Réessayez plus tard.";
}

export const connecteGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.estConnecte()) {
    return router.createUrlTree(['/connexion']);
  }
  if (auth.utilisateur()) {
    return true;
  }
  return auth.rafraichir().pipe(
    map(() => true),
    catchError(() => of(router.createUrlTree(['/connexion']))),
  );
};

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.estAdmin() ? true : inject(Router).createUrlTree(['/revues/nouvelle']);
};

export const developpeurGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.estAdmin() ? inject(Router).createUrlTree(['/admin']) : true;
};

export const inviteGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.estConnecte() ? inject(Router).createUrlTree(['/revues/nouvelle']) : true;
};
