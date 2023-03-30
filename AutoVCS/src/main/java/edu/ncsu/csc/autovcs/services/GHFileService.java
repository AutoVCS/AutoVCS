package edu.ncsu.csc.autovcs.services;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import edu.ncsu.csc.autovcs.models.persistent.GHCommit;
import edu.ncsu.csc.autovcs.models.persistent.GHFile;
import edu.ncsu.csc.autovcs.repositories.GHFileRepository;

/**
 * Provides database access to the GHFile model, which stores
 * information about files added/changed/deleted on commits.
 * @author Kai Presler-Marshall
 *
 */
@Component
@Transactional
public class GHFileService extends Service<GHFile, Long> {

	/**	 * Provides access to the DB for CRUD tasks */
    @Autowired
    private GHFileRepository repository;

    @Override
    protected JpaRepository<GHFile, Long> getRepository () {
        return repository;
    }

    /**
     * Finds all files associated with a given {@link edu.ncsu.csc.autovcs.models.persistent.GHCommit} commit.
     * @param commit
     * @return List of files associated with this commit, potentially empty
     */
    public List<GHFile> findByCommit ( final GHCommit commit ) {
        return repository.findByAssociatedCommit( commit );
    }

}
