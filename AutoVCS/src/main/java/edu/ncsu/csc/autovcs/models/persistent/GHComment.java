package edu.ncsu.csc.autovcs.models.persistent;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequestReviewComment;

@Entity
@Table ( name = "GHCommentS" )
public class GHComment extends DomainObject<GHComment> {

    @Id
    @GeneratedValue ( strategy = GenerationType.IDENTITY )
    private Long    id;

    @ManyToOne
    private GitUser commenter;

    @Column ( columnDefinition = "text" )
    private String  comment;

    private Instant timestamp;

    public GHComment () {
    }

    public GHComment ( final GHPullRequestReviewComment comment ) {
        try {
            this.timestamp = comment.getCreatedAt().toInstant();
            this.commenter = GitUser.forUser( comment.getUser() );
            this.comment = comment.getBody();

        }
        catch ( final Exception e ) {
            // a comment without all of these fields is useless, so fail if we
            // don't have
            // any
            throw new RuntimeException( e );
        }
    }

    public GHComment ( final GHIssueComment comment ) {
        try {
            this.timestamp = comment.getCreatedAt().toInstant();
            this.commenter = GitUser.forUser( comment.getUser() );
            this.comment = comment.getBody();
        }
        catch ( final Exception e ) {
            // a comment without all of these fields is useless, so fail if we
            // don't have
            // any
            throw new RuntimeException( e );
        }
    }

    @SuppressWarnings ( "unchecked" )
    public static List<GHComment> getForUser ( final GitUser user ) {
        return (List<GHComment>) getWhere( GHComment.class, eqList( "commenter", user ) );
    }

    public GitUser getCommenter () {
        return commenter;
    }

    public void setCommenter ( final GitUser commenter ) {
        this.commenter = commenter;
    }

    public String getComment () {
        return comment;
    }

    public void setComment ( final String comment ) {
        this.comment = comment;
    }

    public Instant getTimestamp () {
        return timestamp;
    }

    public void setTimestamp ( final Instant timestamp ) {
        this.timestamp = timestamp;
    }

    public void setId ( final Long id ) {
        this.id = id;
    }

    @Override
    public Serializable getId () {
        return id;
    }

    @Override
    protected Serializable getKey () {
        return getId();
    }
}
