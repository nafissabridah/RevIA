import { Routes } from '@angular/router';
import { adminGuard, connecteGuard, developpeurGuard, inviteGuard } from './core/http';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  {
    path: 'connexion',
    canActivate: [inviteGuard],
    title: 'Connexion · RevIA',
    loadComponent: () => import('./pages/auth/auth.component').then((m) => m.AuthComponent),
    data: { mode: 'connexion' },
  },
  {
    path: 'inscription',
    canActivate: [inviteGuard],
    title: 'Inscription · RevIA',
    loadComponent: () => import('./pages/auth/auth.component').then((m) => m.AuthComponent),
    data: { mode: 'inscription' },
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [connecteGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'revues/nouvelle' },
      {
        path: 'revues/nouvelle',
        canActivate: [developpeurGuard],
        title: 'Nouvelle revue · RevIA',
        loadComponent: () => import('./pages/revue/revue.component').then((m) => m.RevueComponent),
      },
      {
        path: 'revues/:id',
        canActivate: [developpeurGuard],
        title: 'Rapport de revue · RevIA',
        loadComponent: () => import('./pages/revue/revue.component').then((m) => m.RevueComponent),
      },
      {
        path: 'revues/:id/problemes/:index',
        canActivate: [developpeurGuard],
        title: 'Correction · RevIA',
        loadComponent: () => import('./pages/correction/correction.component').then((m) => m.CorrectionComponent),
      },
      {
        path: 'historique',
        canActivate: [developpeurGuard],
        title: 'Historique · RevIA',
        loadComponent: () => import('./pages/historique/historique.component').then((m) => m.HistoriqueComponent),
      },
      {
        path: 'tableau-de-bord',
        canActivate: [developpeurGuard],
        title: 'Tableau de bord · RevIA',
        loadComponent: () => import('./pages/tableau-de-bord/tableau-de-bord.component').then((m) => m.TableauDeBordComponent),
      },
      {
        path: 'parametres',
        title: 'Paramètres · RevIA',
        loadComponent: () => import('./pages/parametres/parametres.component').then((m) => m.ParametresComponent),
      },
      {
        path: 'admin',
        canActivate: [adminGuard],
        title: 'Administration · RevIA',
        loadComponent: () => import('./pages/admin/admin.component').then((m) => m.AdminComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
