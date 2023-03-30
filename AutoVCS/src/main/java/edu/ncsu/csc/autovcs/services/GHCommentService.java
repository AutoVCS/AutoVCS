package edu.ncsu.csc.autovcs.services;

import java.time.Instant;
import java.util.List;

import javax.transaction.Transactional;

import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequestReviewComment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import edu.ncsu.csc.autovcs.models.persistent.GHComment;
import edu.ncsu.csc.autovcs.models.persistent.GitUser;
import edu.ncsu.csc.autovcs.repositories.GHCommentRepository;

/**
 * Provides database access to GitHub Comments, and allows building {@link edu.ncsu.csc.autovcs.models.persistent.GHComment}
 * from the {@link org.kohsuke.github.GHPullRequestReviewComment} and {@link org.kohsuke.github.GHIssueComment} from the GitHub API.
 * @author Kai Presler-Marshall
 *
 */
@Component
@Transactional
public class GHCommentService extends Service<GHComment, Long> {

	/** Provides access to the DB for CRUD tasks */
    @Autowired
    private GHCommentRepository repository;

    /** Provides ability to look up users to associate with created comments */
    @Autowired
    private GitUserService      userService;

    @Override
    protected JpaRepository<GHComment, Long> getRepository () {
        return repository;
    }
    

    /**
     * Converts a {@link org.kohsuke.github.GHPullRequestReviewComment} to a GHComment
     * that can be stored in the database.  Requires the comment to contain the 
     * user who left the comment, the content of the comment, and timestamp the comment
     * was created at.  A comment missing any of these fields will result in an exception.
     * @param comment GHPullRequestReviewComment to parse
     * @return Parsed GHComment object
     */
    public GHComment forComment ( final GHPullRequestReviewComment comment ) {
        try {

            final GitUser commenter = userService.forUser( comment.getUser() );
            final String commentContent = comment.getBody();
            final Instant timestamp = comment.getCreatedAt().toInstant();
            return new GHComment( commenter, commentContent, timestamp );

        }
        catch ( final Exception e ) {
            // a comment without all of these fields is useless, so fail if we
            // don't have any
            throw new RuntimeException( e );
        }
    }

    /**
     * Converts a {@link org.kohsuke.github.GHIssueComment} to a GHComment
     * that can be stored in the database.  Requires the comment to contain the 
     * user who left the comment, the content of the comment, and timestamp the comment
     * was created at.  A comment missing any of these fields will result in an exception.
     * @param comment GHIssueComment to parse
     * @return Parsed GHComment object
     */
    public GHComment forComment ( final GHIssueComment comment ) {
        try {

            final GitUser commenter = userService.forUser( comment.getUser() );
            final String commentContent = comment.getBody();
            final Instant timestamp = comment.getCreatedAt().toInstant();
            return new GHComment( commenter, commentContent, timestamp );
        }
        catch ( final Exception e ) {
            // a comment without all of these fields is useless, so fail if we
            // don't have any
            throw new RuntimeException( e );
        }
    }

    /**
     * Retrieves all GHComments made by a given {@link edu.ncsu.csc.autovcs.models.persistent.GitUser}
     * @param user User whose comments we want
     * @return List of matching comments, or empty list if none found
     */
    public List<GHComment> findForUser ( final GitUser user ) {
        return repository.findByCommenter( user );
    }

}
