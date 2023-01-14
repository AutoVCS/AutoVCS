package edu.ncsu.csc.autovcs.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.ncsu.csc.autovcs.models.persistent.GitUser;

public interface GitUserRepository extends JpaRepository<GitUser, Long> {

    public GitUser findByNameAndEmail ( String name, String email );

    public GitUser findByName ( String name );
    
    /* If `weakEquivalence=false` is changed to `weakEquivalence=true`, we may have multiple users by the same email;
     *  `ORDER BY id LIMIT 1` ensured we will only get a single GitUser back  */
    public GitUser findTop1ByEmailOrderById ( String email );

    public List<GitUser> findByExcludedTrue ();

    public List<GitUser> findByNameContaining ( String name );

    public List<GitUser> findByEmailContaining ( String email );

}
