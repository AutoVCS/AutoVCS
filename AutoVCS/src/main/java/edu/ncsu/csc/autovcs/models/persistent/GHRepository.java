package edu.ncsu.csc.autovcs.models.persistent;

import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.criterion.Criterion;

@Entity
@Table ( name = "GHRepositories" )
@SuppressWarnings ( "unchecked" )
public class GHRepository extends DomainObject<GHRepository> {

    private static DomainObjectCache<String, GHRepository> cache = new DomainObjectCache<String, GHRepository>(
            GHRepository.class );

    @Id
    @GeneratedValue ( strategy = GenerationType.IDENTITY )
    private Long                                           id;

    @NotNull
    private String                                         repositoryName;

    @NotNull
    private String                                         organisationName;

    // mappedBy indicates that this side is the
    // inverse side, and that the mapping is defined by the attribute repository
    // at the other side of the association.
    @OneToMany ( mappedBy = "repository", cascade = CascadeType.ALL, fetch = FetchType.LAZY )
    private Set<GHCommit>                                  commits;

    // mappedBy indicates that this side is the
    // inverse side, and that the mapping is defined by the attribute repository
    // at the other side of the association.
    @OneToMany ( mappedBy = "repository", cascade = CascadeType.ALL, fetch = FetchType.LAZY )
    private Set<GHPullRequest>                             pullRequests;

    private Instant                                        lastFetchedAt;

    public GHRepository () {
        this.commits = new HashSet<GHCommit>();
        this.pullRequests = new HashSet<GHPullRequest>();
    }

    @Override
    public Serializable getId () {
        return id;
    }

    public String getRepositoryName () {
        return repositoryName;
    }

    public GHRepository setRepositoryName ( final String repositoryName ) {
        this.repositoryName = repositoryName;
        return this;
    }

    public String getOrganisationName () {
        return organisationName;
    }

    public GHRepository setOrganisationName ( final String organisationName ) {
        this.organisationName = organisationName;
        return this;
    }

    public void setCommits ( final Set<GHCommit> commits ) {
        this.commits = commits;
    }

    public void addCommits ( final Collection<GHCommit> commits ) {
        this.commits = new HashSet<GHCommit>( GHCommit.getByRepository( this ) );
        commits.forEach( commit -> addCommit( commit ) );
    }

    public void addCommit ( final GHCommit commit ) {
        if ( this.commits.contains( commit ) ) {
            return;
        }
        this.commits.add( commit );
        commit.setRepository( this );
    }

    public Set<GHCommit> getCommits () {
        if ( null == commits || commits.isEmpty() ) {
            this.commits = new HashSet<GHCommit>( GHCommit.getByRepository( this ) );
        }
        return commits;
    }

    public void addPullRequests ( final Collection<GHPullRequest> requests ) {
        requests.forEach( request -> addPullRequest( request ) );
    }

    public void addPullRequest ( final GHPullRequest request ) {
        if ( this.pullRequests.contains( request ) ) {
            return;
        }
        this.pullRequests.add( request );
        request.setRepository( this );
    }

    public static List<GHRepository> getAll () {
        return (List<GHRepository>) getAll( GHRepository.class );
    }

    public static GHRepository getByNameAndOrganisation ( final String repoName, final String organisationName ) {
        final String key = String.format( "%s:%s", organisationName, repoName );

        final GHRepository repo = cache.get( key );

        if ( null != repo ) {
            return repo;
        }

        final List<Criterion> criteria = new ArrayList<Criterion>();
        criteria.add( eq( "repositoryName", repoName ) );
        criteria.add( eq( "organisationName", organisationName ) );

        final List<GHRepository> matching = (List<GHRepository>) getWhere( GHRepository.class, criteria );
        return matching.isEmpty() ? null : matching.get( 0 );
    }

    public static GHRepository forRepository ( final org.kohsuke.github.GHRepository repo ) {
        String organisationName;
        String repoName;

        try {
            organisationName = repo.getOwner().getLogin();
        }
        catch ( final IOException e ) {
            throw new RuntimeException( e );
        }

        repoName = repo.getName();

        final GHRepository found = getByNameAndOrganisation( repoName, organisationName );
        if ( null == found ) {
            return new GHRepository().setOrganisationName( organisationName ).setRepositoryName( repoName );
        }
        return found;
    }

    public GithubRepository format () {
        return new GithubRepository( this );
    }

    /**
     * Flat version of this class, with no recursive references, for JSON
     * serialisation
     *
     * @author Kai
     */
    public class GithubRepository {
        private final String name;
        private final String organisation;
        private final String display;

        public GithubRepository ( final edu.ncsu.csc.autovcs.models.persistent.GHRepository repo ) {
            this.name = repo.getRepositoryName();
            this.organisation = repo.getOrganisationName();
            this.display = String.format( "%s -- %s", this.organisation, this.name );
        }

        public String getName () {
            return name;
        }

        public String getOrganisation () {
            return organisation;
        }

        public String getDisplay () {
            return display;
        }
    }

    @Override
    protected Serializable getKey () {
        return String.format( "%s:%s", organisationName, repositoryName );
    }

    public void setPullRequests ( final Set<GHPullRequest> pullRequests ) {
        this.pullRequests = pullRequests;
    }

    public Instant getLastFetchedAt () {
        return lastFetchedAt;
    }

    public void setLastFetchedAt ( final Instant lastFetchedAt ) {
        this.lastFetchedAt = lastFetchedAt;
    }
}
