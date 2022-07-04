package edu.ncsu.csc.autovcs.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.ncsu.csc.autovcs.models.persistent.GHRepository;

public interface GHRepositoryRepository extends JpaRepository<GHRepository, Long> {

    // TODO: do we capitalise the second word?
    public GHRepository findByRepositoryNameAndOrganisationName ( String repositoryName, String organisationName );

}
