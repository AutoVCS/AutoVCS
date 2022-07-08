package edu.ncsu.csc.autovcs.models.persistent;

import java.io.Serializable;
import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

@Entity
public class GHComment extends DomainObject {

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

    public GHComment ( final GitUser commenter, final String comment, final Instant timestamp ) {
        setCommenter( commenter );
        setComment( comment );
        setTimestamp( timestamp );
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

}
