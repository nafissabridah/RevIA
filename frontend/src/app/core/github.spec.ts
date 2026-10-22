import { installationEnAttente, lireInstallation, memoriserInstallation, oublierInstallation } from './github';

describe('GitHub App : installation en attente', () => {
  const params = (v: Record<string, string>) => ({ get: (n: string) => v[n] ?? null });

  afterEach(() => oublierInstallation());

  it('lit installation_id et le code OAuth de l’URL de configuration', () => {
    expect(lireInstallation(params({ installation_id: '123456', setup_action: 'install', code: 'abc' })))
      .toEqual({ installationId: 123456, code: 'abc' });
    expect(lireInstallation(params({ installation_id: '42' }))).toEqual({ installationId: 42, code: null });
  });

  it('refuse un identifiant absent, nul ou non numérique', () => {
    expect(lireInstallation(params({}))).toBeNull();
    expect(lireInstallation(params({ installation_id: '0' }))).toBeNull();
    expect(lireInstallation(params({ installation_id: '12abc' }))).toBeNull();
    expect(lireInstallation(params({ installation_id: '-5' }))).toBeNull();
  });

  it('garde la demande le temps de la connexion, puis l’oublie', () => {
    memoriserInstallation({ installationId: 77, code: null });
    expect(installationEnAttente()).toEqual({ installationId: 77, code: null });
    oublierInstallation();
    expect(installationEnAttente()).toBeNull();
  });
});
