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

@Component
@Transactional
public class GHCommentService extends Service<GHComment, Long> {

    @Autowired
    private GHCommentRepository repository;

    @Autowired
    private GitUserService      userService;

    @Override
    protected JpaRepository<GHComment, Long> getRepository () {
        return repository;
    }

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

    public GHComment forComment ( final GHIssueComment comment ) {
        try {

            final GitUser commenter = userService.forUser( comment.getUser() );
            final String commentContent = comment.getBody();
            final Instant timestamp = comment.getCreatedAt().toInstant();
            return new GHComment( commenter, commentContent, timestamp );
        }
        catch ( final Exception e ) {
            // a comment without all of these fields is useless, so fail if we
            // don't have
            // any
            throw new RuntimeException( e );
        }
    }

    public List<GHComment> findForUser ( final GitUser user ) {
        return repository.findByCommenter( user );
    }

}
