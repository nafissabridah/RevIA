package com.revia.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.revia.domain.Probleme;

public interface ProblemeRepository extends JpaRepository<Probleme, Long> {

    /** Le problème n'est renvoyé que s'il appartient à une revue de l'utilisateur. */
    @Query("select p from Probleme p join fetch p.revue r left join fetch p.correction where p.id = :id and r.auteur.id = :auteurId")
    Optional<Probleme> findPourAuteur(@Param("id") Long id, @Param("auteurId") Long auteurId);
}
