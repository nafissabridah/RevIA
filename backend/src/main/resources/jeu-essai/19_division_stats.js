function moyennePanier(articles) {
  let total = 0;
  for (const a of articles) {
    total += a.prix * a.quantite;
  }
  return total / articles.length;
}

module.exports = { moyennePanier };
