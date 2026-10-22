package com.revia.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.revia.domain.Projet;

/** Toutes les requêtes filtrent sur le propriétaire : un utilisateur ne voit que ses propres projets. */
public interface ProjetRepository extends JpaRepository<Projet, Long> {

    List<Projet> findByProprietaireIdOrderByCreeLeDesc(Long proprietaireId);

    List<Projet> findByProprietaireIdOrderByCreeLeAsc(Long proprietaireId);

    Optional<Projet> findByIdAndProprietaireId(Long id, Long proprietaireId);
}
