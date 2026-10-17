function afficherCommentaire(commentaire) {
  const zone = document.getElementById('commentaires');
  zone.innerHTML += '<p class="auteur">' + commentaire.auteur + '</p><p>' + commentaire.texte + '</p>';
}
