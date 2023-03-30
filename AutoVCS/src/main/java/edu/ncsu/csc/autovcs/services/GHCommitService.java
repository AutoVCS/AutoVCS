package edu.ncsu.csc.autovcs.services;

import java.util.ArrayList;
import java.util.List;

import javax.transaction.Transactional;

import org.kohsuke.github.GHCommit.File;
import org.kohsuke.github.GHCommit.GHAuthor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import edu.ncsu.csc.autovcs.models.persistent.GHCommit;
import edu.ncsu.csc.autovcs.models.persistent.GHRepository;
import edu.ncsu.csc.autovcs.models.persistent.GitUser;
import edu.ncsu.csc.autovcs.repositories.GHCommentRepository;
import edu.ncsu.csc.autovcs.repositories.GHCommitRepository;

/**
 * Provides database access to Git Commits, and allows building
 * a slightly simplified representation of a Git Commit from the 
 * information made available by the GitHub API.  Allows building
 * a {@link edu.ncsu.csc.autovcs.models.persistent.GHCommit} from
 * a {@link org.kohsuke.github.GHCommit}.  Also provides the ability
 * to query the database for commits associated with a repository
 * or for a user. 
 * @author Kai Presler-Marshall
 *
 */
@Component
@Transactional
public class GHCommitService extends Service<GHCommit, Long> {

	/** Provides access to the DB for CRUD tasks */
    @Autowired
    private GHCommitRepository  repository;

    /** Provides ability to look up users to associate with created comments */
    @Autowired
    private GitUserService      userService;

    @Override
    protected JpaRepository<GHCommit, Long> getRepository () {
        return repository;
    }

    /**
     * Finds all commits authored by a given user
     * @param author
     * @return List of commits found, potentially empty
     */
    public List<GHCommit> findByUser ( final GitUser author ) {
        return repository.findByAuthor( author );
    }

    /**
     * Find all commits associated with a given repository
     * @param repository
     * @return List of commits found, potentially empty
     */
    public List<GHCommit> findByRepository ( final GHRepository repository ) {
        return this.repository.findByRepository( repository );
    }

    /**
     * Finds the most recent (single) commit associated with a given repository
     * @param repository 
     * @return Commit found, if any
     */
    public GHCommit findMostRecentByRepository ( final GHRepository repository ) {
        return this.repository.findFirstByRepositoryOrderByCommitDateDesc( repository );

    }

    /**
     * Overridden save method to ensure we have at least one of the (author, committer)
     * for a commit stored 
     */
    @Override
    public void save ( final GHCommit commit ) {
        if ( null == commit.getAuthor() && null == commit.getCommitter() ) {
            throw new RuntimeException( "Must have an author or committer present" );
        }

        super.save( commit );

    }

    /**
     * Parses a {@link org.kohsuke.github.GHCommit} into a 
     * {@link edu.ncsu.csc.autovcs.models.persistent.GHCommit} for further
     * use by AutoVCS.
     * @param apiCommit Commit from the GitHub API
     * @return Parsed commit
     */
    public GHCommit forCommit ( final org.kohsuke.github.GHCommit apiCommit ) {
        return forCommit( apiCommit, null );
    }

    /**
     * Parses a {@link org.kohsuke.github.GHCommit} into a 
     * {@link edu.ncsu.csc.autovcs.models.persistent.GHCommit} for further
     * use by AutoVCS.  See the details in {@link edu.ncsu.csc.autovcs.models.persistent.GHCommit}
     * for what information is extracted from the GitHub API.
     * @param apiCommit Commit from the GitHub API
     * @param branchName Branch this commit was associated with
     * @return Parsed commit
     */
    public GHCommit forCommit ( final org.kohsuke.github.GHCommit apiCommit, final String branchName ) {

        final GHCommit commit = new GHCommit();

        try {
            final GHAuthor author = (GHAuthor) apiCommit.getCommitShortInfo().getAuthor();
            final GHAuthor committer = (GHAuthor) apiCommit.getCommitShortInfo().getCommitter();

            commit.setAuthor( userService.forUser( author ) );
            commit.setCommitter( userService.forUser( committer ) );
            commit.setCommitMessage( apiCommit.getCommitShortInfo().getMessage() );
            commit.setSha1( apiCommit.getSHA1() );

            final List<File> filesOnCommit = apiCommit.getFiles();

            commit.setFiles( filesOnCommit );

            /* to get a high-level summary for the commit, just sum up the changes on each individual file */
            commit.setLinesAdded(
                    filesOnCommit.stream().map( file -> file.getLinesAdded() ).reduce( 0, ( a, b ) -> a + b ) );
            commit.setLinesRemoved(
                    filesOnCommit.stream().map( file -> file.getLinesDeleted() ).reduce( 0, ( a, b ) -> a + b ) );

            commit.setLinesChanged(
                    filesOnCommit.stream().map( file -> file.getLinesChanged() ).reduce( 0, ( a, b ) -> a + b ) );

            final List<File> filesModified = new ArrayList<File>( filesOnCommit );

            filesModified
                    .removeIf( e -> e.getLinesAdded() == 0 && e.getLinesDeleted() == 0 && e.getLinesChanged() == 0 );

            commit.setFilesChanged( filesModified.size() );

        }
        catch ( final Exception e ) {
            throw new RuntimeException( e );
        }
        try {
            commit.setCommitDate( apiCommit.getCommitShortInfo().getAuthor().getDate().toInstant() );
        }
        catch ( final Exception e ) {
        }
        try {
            commit.setMergeCommit( 2 == apiCommit.getParents().size() );
        }
        catch ( final Exception e ) {
            // first commit will have no parents, carry on
        }

        /* If not a merge commit, label the parents */
        try {
            if ( apiCommit.getParents().size() == 1 ) {
                commit.setParent( apiCommit.getParentSHA1s().get( 0 ) );
            }
        }
        catch ( final Exception e ) {
            // initial commit will have no parents, carry on
        }

        commit.setUrl( apiCommit.getHtmlUrl().toString() );

        commit.addBranch( branchName );

        return commit;
    }

}
