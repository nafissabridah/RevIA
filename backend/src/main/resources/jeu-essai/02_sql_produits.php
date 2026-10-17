<?php
function trouverProduit($pdo) {
    $categorie = $_GET['categorie'];
    $sql = "SELECT id, nom, prix FROM produits WHERE categorie = '$categorie'";
    $resultat = $pdo->query($sql);
    return $resultat->fetchAll();
}
