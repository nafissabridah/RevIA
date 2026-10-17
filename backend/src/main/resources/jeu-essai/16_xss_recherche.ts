export function afficherRecherche(terme: string): void {
  const resultat = document.querySelector('#resultat') as HTMLElement;
  resultat.innerHTML = `Résultats pour : <b>${terme}</b>`;
}
