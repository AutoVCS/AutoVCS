package edu.ncsu.csc.autovcs.models.persistent;

import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequestReviewComment;

@Entity
@Table ( name = "GHPullRequests" )
public class GHPullRequest extends DomainObject<GHPullRequest> {

    private static DomainObjectCache<Long, GHPullRequest> cache = new DomainObjectCache<Long, GHPullRequest>(
            GHPullRequest.class );

    @Id
    @GeneratedValue ( strategy = GenerationType.IDENTITY )
    private Long                                          id;

    @ManyToOne
    @NotNull
    private GHRepository                                  repository;

    @OneToMany ( cascade = CascadeType.ALL, fetch = FetchType.EAGER )
    private Set<GHComment>                                pullRequestComments;

    private Instant                                       openedAt;

    private Instant                                       closedAt;

    private int                                           number;

    @ManyToOne
    private GitUser                                       mergedBy;

    @ManyToOne
    private GitUser                                       openedBy;

    @Column ( columnDefinition = "text" )
    private String                                        title;

    @Column ( columnDefinition = "text" )
    private String                                        body;

    private String                                        url;

    /** For Hibernate */
    public GHPullRequest () {
    }

    public GHPullRequest ( final org.kohsuke.github.GHPullRequest request ) {

        repository = GHRepository.forRepository( request.getRepository() );
        this.number = request.getNumber();

        try {
            openedAt = request.getCreatedAt().toInstant();
        }
        catch ( final IOException e ) {
            throw new RuntimeException( e );
        }
        try {
            closedAt = request.getClosedAt().toInstant();
        }
        catch ( final Exception e ) {
            // might not be closed, carry on
        }

        List<GHIssueComment> comments = null;
        try {
            comments = request.listComments().asList();
        }
        catch ( final IOException e ) {
        }

        List<GHPullRequestReviewComment> reviewComments = null;
        try {
            reviewComments = request.listReviewComments().asList();
        }
        catch ( final IOException e ) {
        }

        pullRequestComments = new HashSet<GHComment>();
        if ( null != comments ) {
            pullRequestComments.addAll( comments.stream().map( GHComment::new ).collect( Collectors.toSet() ) );
        }
        if ( null != reviewComments ) {
            pullRequestComments.addAll( reviewComments.stream().map( GHComment::new ).collect( Collectors.toSet() ) );
        }
        try {
            mergedBy = GitUser.forUser( request.getMergedBy() );
        }
        catch ( final Exception e ) {
            // maybe not merged
        }
        try {
            openedBy = GitUser.forUser( request.getUser() );
        }
        catch ( final Exception e ) {
            throw new RuntimeException( e );
        }

        this.title = request.getTitle();

        this.body = request.getBody();

        this.url = request.getHtmlUrl().toString();
    }

    @SuppressWarnings ( "unchecked" )
    public static List<GHPullRequest> getByRepository ( final GHRepository repository ) {
        return (List<GHPullRequest>) getWhere( GHPullRequest.class, eqList( "repository", repository ) );
    }

    @SuppressWarnings ( "unchecked" )
    public static List<GHPullRequest> getOpenedBy ( final GitUser user ) {
        return (List<GHPullRequest>) getWhere( GHPullRequest.class, eqList( "openedBy", user ) );
    }

    @SuppressWarnings ( "unchecked" )
    public static List<GHPullRequest> getMergedBy ( final GitUser user ) {
        return (List<GHPullRequest>) getWhere( GHPullRequest.class, eqList( "mergedBy", user ) );
    }

    public GHRepository getRepository () {
        return repository;
    }

    public void setRepository ( final GHRepository repository ) {
        this.repository = repository;
    }

    public Set<GHComment> getPullRequestComments () {
        return pullRequestComments;
    }

    public void setPullRequestComments ( final Set<GHComment> pullRequestComments ) {
        this.pullRequestComments = pullRequestComments;
    }

    public Instant getOpenedAt () {
        return openedAt;
    }

    public void setOpenedAt ( final Instant openedAt ) {
        this.openedAt = openedAt;
    }

    public Instant getClosedAt () {
        return closedAt;
    }

    public void setClosedAt ( final Instant closedAt ) {
        this.closedAt = closedAt;
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

    public int getNumber () {
        return number;
    }

    public void setNumber ( final int number ) {
        this.number = number;
    }

    public GitUser getMergedBy () {
        return mergedBy;
    }

    public void setMergedBy ( final GitUser mergedBy ) {
        this.mergedBy = mergedBy;
    }

    public GitUser getOpenedBy () {
        return openedBy;
    }

    public void setOpenedBy ( final GitUser openedBy ) {
        this.openedBy = openedBy;
    }

    public String getUrl () {
        return url;
    }

    public String getTitle () {
        return title;
    }

    public String getBody () {
        return body;
    }

    @Override
    public int hashCode () {
        return Objects.hash( number, repository );
    }

    @Override
    public boolean equals ( final Object obj ) {
        if ( this == obj ) {
            return true;
        }
        if ( obj == null ) {
            return false;
        }
        if ( getClass() != obj.getClass() ) {
            return false;
        }
        final GHPullRequest other = (GHPullRequest) obj;
        return number == other.number && Objects.equals( repository, other.repository );
    }

    public DisplayPullRequest format () {
        return new DisplayPullRequest( this );
    }

    public class DisplayPullRequest {

        private final String repository;

        private final int    number;

        private final String title;

        private final String body;

        private final String url;

        private DisplayPullRequest ( final GHPullRequest request ) {
            this.repository = request.getRepository().toString();
            this.number = request.getNumber();
            this.title = request.getTitle();
            this.body = request.getBody();
            this.url = request.getUrl();
        }

        public String getRepository () {
            return repository;
        }

        public Integer getNumber () {
            return number;
        }

        public String getTitle () {
            return title;
        }

        public String getBody () {
            return body;
        }

        public String getUrl () {
            return url;
        }
    }
}
