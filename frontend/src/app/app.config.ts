import { ApplicationConfig, LOCALE_ID, importProvidersFrom } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { Injectable } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy, provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { MonacoEditorModule } from 'ngx-monaco-editor-v2';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';

import { routes } from './app.routes';
import { authInterceptor } from './core/http';
import { tr } from './core/i18n';
import { langueCourante } from './core/langue.service';

registerLocaleData(localeFr);

/** Titre de l'onglet dans la langue de l'utilisateur. */
@Injectable({ providedIn: 'root' })
export class TitreTraduit extends TitleStrategy {
  constructor(private readonly titre: Title) {
    super();
  }

  override updateTitle(etat: RouterStateSnapshot): void {
    const t = this.buildTitle(etat);
    if (t) {
      this.titre.setTitle(tr(t));
    }
  }
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes, withComponentInputBinding(), withInMemoryScrolling({ scrollPositionRestoration: 'top' })),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimationsAsync(),
    provideCharts(withDefaultRegisterables()),
    importProvidersFrom(MonacoEditorModule.forRoot({ baseUrl: './assets' })),
    { provide: TitleStrategy, useClass: TitreTraduit },
    // Dates et nombres : format de la langue choisie au chargement de la page
    { provide: LOCALE_ID, useFactory: () => (langueCourante() === 'en' ? 'en-US' : 'fr-FR') },
  ],
};
