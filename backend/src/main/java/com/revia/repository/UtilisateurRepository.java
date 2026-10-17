package com.revia.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.revia.domain.Administrateur;
import com.revia.domain.Utilisateur;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select count(a) from Administrateur a")
    long countAdministrateurs();

    @Query("select a from Administrateur a order by a.id")
    List<Administrateur> findAdministrateurs();
}
