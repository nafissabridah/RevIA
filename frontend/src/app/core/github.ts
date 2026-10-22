/**
 * Installation GitHub en attente de liaison : GitHub redirige vers /github/installe?installation_id=…[&code=…]
 * après l'installation de l'App. Si l'utilisateur n'est pas connecté, la demande est gardée le temps de la connexion
 * (sessionStorage : propre à l'onglet, effacée à sa fermeture).
 */
export interface InstallationEnAttente {
  installationId: number;
  code: string | null;
}

const CLE = 'revia.github.installation';

/** Lit installation_id (entier positif) et code depuis les paramètres de l'URL de configuration. */
export function lireInstallation(params: { get(nom: string): string | null }): InstallationEnAttente | null {
  const brut = (params.get('installation_id') ?? '').trim();
  if (!/^\d{1,18}$/.test(brut) || Number(brut) <= 0) {
    return null;
  }
  const code = (params.get('code') ?? '').trim();
  return { installationId: Number(brut), code: code && code.length <= 200 ? code : null };
}

export function memoriserInstallation(i: InstallationEnAttente): void {
  try {
    sessionStorage.setItem(CLE, JSON.stringify(i));
  } catch {
    // stockage indisponible (navigation privée stricte) : l'utilisateur relancera l'installation
  }
}

export function installationEnAttente(): InstallationEnAttente | null {
  try {
    const v = JSON.parse(sessionStorage.getItem(CLE) ?? 'null');
    return v && typeof v.installationId === 'number' && v.installationId > 0
      ? { installationId: v.installationId, code: typeof v.code === 'string' ? v.code : null }
      : null;
  } catch {
    return null;
  }
}

export function oublierInstallation(): void {
  try {
    sessionStorage.removeItem(CLE);
  } catch {
    // rien à faire
  }
}
