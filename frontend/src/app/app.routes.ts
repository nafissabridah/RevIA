import { Routes } from '@angular/router';
import { adminGuard, connecteGuard, developpeurGuard, inviteGuard } from './core/http';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'RevIA · Revue de code IA, sécurité OWASP et quality gate CI',
    loadComponent: () => import('./pages/accueil/accueil.component').then((m) => m.AccueilComponent),
  },
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
        path: 'assistant',
        canActivate: [developpeurGuard],
        title: 'Assistant IA · RevIA',
        loadComponent: () => import('./pages/assistant/assistant.component').then((m) => m.AssistantComponent),
      },
      {
        path: 'outils',
        canActivate: [developpeurGuard],
        title: 'Outils IA · RevIA',
        loadComponent: () => import('./pages/outils/outils.component').then((m) => m.OutilsComponent),
      },
      {
        path: 'criteres',
        canActivate: [developpeurGuard],
        title: 'Couverture des critères · RevIA',
        loadComponent: () => import('./pages/criteres/criteres.component').then((m) => m.CriteresComponent),
      },
      {
        path: 'projets',
        canActivate: [developpeurGuard],
        title: 'Projets · RevIA',
        loadComponent: () => import('./pages/projets/projets.component').then((m) => m.ProjetsComponent),
      },
      {
        path: 'projets/:id',
        canActivate: [developpeurGuard],
        title: 'Projet · RevIA',
        loadComponent: () => import('./pages/projets/projets.component').then((m) => m.ProjetsComponent),
      },
      {
        path: 'flowchart',
        canActivate: [developpeurGuard],
        title: 'Flowchart → tests · RevIA',
        loadComponent: () => import('./pages/flowchart/flowchart.component').then((m) => m.FlowchartComponent),
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
  {
    path: 'legal/:doc',
    title: 'Informations légales · RevIA',
    loadComponent: () => import('./pages/legal/legal.component').then((m) => m.LegalComponent),
  },
  {
    // URL de configuration de la GitHub App : relie l'installation au compte (connexion demandée si besoin)
    path: 'github/installe',
    title: 'GitHub · RevIA',
    loadComponent: () => import('./pages/github/github-installe.component').then((m) => m.GithubInstalleComponent),
  },
  { path: 'legal', pathMatch: 'full', redirectTo: 'legal/cgu' },
  {
    path: 'demo',
    title: 'Démo : des tests qui prouvent qu’ils marchent · RevIA',
    loadComponent: () => import('./pages/demo/demo.component').then((m) => m.DemoComponent),
  },
  { path: 'tarifs', redirectTo: '' },
  { path: '**', redirectTo: '' },
];
