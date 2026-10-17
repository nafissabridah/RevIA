import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';

describe('AuthService (session JWT)', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('stocke le jeton et l\'utilisateur après connexion', () => {
    auth.connexion('sarra@revia.local', 'Sarra2026!').subscribe();
    const req = http.expectOne('/api/auth/login');
    expect(req.request.body).toEqual({ email: 'sarra@revia.local', motDePasse: 'Sarra2026!' });
    req.flush({ token: 'jwt', expireDansSecondes: 7200, utilisateur: { id: 1, nom: 'Sarra A.', role: 'DEVELOPPEUR' } });
    expect(auth.jeton).toBe('jwt');
    expect(auth.estConnecte()).toBeTrue();
    expect(auth.estDeveloppeur()).toBeTrue();
    expect(auth.estAdmin()).toBeFalse();
  });

  it('considère un jeton expiré comme absent', () => {
    localStorage.setItem('revia.jeton', 'vieux');
    localStorage.setItem('revia.expiration', String(Date.now() - 1000));
    expect(auth.jeton).toBeNull();
  });

  it('efface la session à la déconnexion', () => {
    localStorage.setItem('revia.jeton', 'jwt');
    localStorage.setItem('revia.expiration', String(Date.now() + 60_000));
    auth.deconnexion(false);
    expect(auth.jeton).toBeNull();
    expect(auth.utilisateur()).toBeNull();
  });
});
