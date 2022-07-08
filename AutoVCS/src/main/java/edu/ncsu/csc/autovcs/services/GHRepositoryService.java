package edu.ncsu.csc.autovcs.services;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import edu.ncsu.csc.autovcs.models.persistent.GHCommit;
import edu.ncsu.csc.autovcs.models.persistent.GHRepository;
import edu.ncsu.csc.autovcs.repositories.GHRepositoryRepository;

@Component
@Transactional
public class GHRepositoryService extends Service<GHRepository, Long> {

    @Autowired
    private GHRepositoryRepository repository;

    @Autowired
    private GHCommitService        commitService;

    @Override
    protected JpaRepository<GHRepository, Long> getRepository () {
        return repository;
    }

    public GHRepository findByNameAndOrganisation ( final String repoName, final String organisationName ) {

        return repository.findByRepositoryNameAndOrganisationName( repoName, organisationName );
    }

    public GHRepository forRepository ( final org.kohsuke.github.GHRepository repo ) {
        String organisationName;
        String repoName;

        try {
            organisationName = repo.getOwner().getLogin();
        }
        catch ( final IOException e ) {
            throw new RuntimeException( e );
        }

        repoName = repo.getName();

        final GHRepository found = findByNameAndOrganisation( repoName, organisationName );
        if ( null == found ) {
            return new GHRepository().setOrganisationName( organisationName ).setRepositoryName( repoName );
        }
        return found;
    }

    @Transactional
    public void loadCommits ( final GHRepository repository ) {

        final Set<GHCommit> existingCommits = repository.getCommits();

        if ( null == existingCommits || existingCommits.isEmpty() ) {
            repository.setCommits( new HashSet<GHCommit>( commitService.findByRepository( repository ) ) );
        }

    }

}
