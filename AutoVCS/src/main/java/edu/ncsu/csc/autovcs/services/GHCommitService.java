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
import edu.ncsu.csc.autovcs.repositories.GHCommitRepository;

@Component
@Transactional
public class GHCommitService extends Service<GHCommit, Long> {

    @Autowired
    private GHCommitRepository repository;

    @Autowired
    private GitUserService     userService;

    @Override
    protected JpaRepository<GHCommit, Long> getRepository () {
        return repository;
    }

    public List<GHCommit> findByUser ( final GitUser author ) {
        return repository.findByAuthor( author );
    }

    public List<GHCommit> findByRepository ( final GHRepository repository ) {
        return this.repository.findByRepository( repository );
    }

    public GHCommit findMostRecentByRepository ( final GHRepository repository ) {
        return this.repository.findFirstByRepositoryOrderByCommitDateDesc( repository );

    }

    @Override
    public void save ( final GHCommit commit ) {
        if ( null == commit.getAuthor() && null == commit.getCommitter() ) {
            throw new RuntimeException( "Must have an author or committer present" );
        }

        super.save( commit );

    }

    public GHCommit forCommit ( final org.kohsuke.github.GHCommit c ) {
        return forCommit( c, null );
    }

    public GHCommit forCommit ( final org.kohsuke.github.GHCommit c, final String branchName ) {

        final GHCommit commit = new GHCommit();

        try {
            final GHAuthor author = (GHAuthor) c.getCommitShortInfo().getAuthor();
            final GHAuthor committer = (GHAuthor) c.getCommitShortInfo().getCommitter();

            commit.setAuthor( userService.forUser( author ) );
            commit.setCommitter( userService.forUser( committer ) );
            commit.setCommitMessage( c.getCommitShortInfo().getMessage() );
            commit.setSha1( c.getSHA1() );

            final List<File> filesOnCommit = c.getFiles();

            commit.setFiles( filesOnCommit );

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
            commit.setCommitDate( c.getCommitShortInfo().getAuthor().getDate().toInstant() );
        }
        catch ( final Exception e ) {
        }
        try {
            commit.setMergeCommit( 2 == c.getParents().size() );
        }
        catch ( final Exception e ) {
            // first commit will have no parents, carry on
        }

        /* If not a merge commit, label the parents */
        try {
            if ( c.getParents().size() == 1 ) {
                commit.setParent( c.getParentSHA1s().get( 0 ) );
            }
        }
        catch ( final Exception e ) {
            // initial commit will have no parents, carry on
        }

        commit.setUrl( c.getHtmlUrl().toString() );

        commit.addBranch( branchName );

        return commit;
    }

}
