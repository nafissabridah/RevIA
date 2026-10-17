function totalPanier(articles) {
  if (!Array.isArray(articles) || articles.length === 0) {
    return 0;
  }
  return articles.reduce((somme, a) => somme + a.prix * a.quantite, 0);
}

function afficherTotal(element, total) {
  element.textContent = `Total : ${total.toFixed(2)} €`;
}

module.exports = { totalPanier, afficherTotal };
