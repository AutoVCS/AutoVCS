package edu.ncsu.csc.autovcs.models.persistent;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.validation.constraints.NotNull;

@Entity
public class GHRepository extends DomainObject {

    @Id
    @GeneratedValue ( strategy = GenerationType.IDENTITY )
    private Long               id;

    @NotNull
    private String             repositoryName;

    @NotNull
    private String             organisationName;

    // mappedBy indicates that this side is the
    // inverse side, and that the mapping is defined by the attribute repository
    // at the other side of the association.
    @OneToMany ( mappedBy = "repository", cascade = CascadeType.ALL, fetch = FetchType.LAZY )
    private Set<GHCommit>      commits;

    // mappedBy indicates that this side is the
    // inverse side, and that the mapping is defined by the attribute repository
    // at the other side of the association.
    @OneToMany ( mappedBy = "repository", cascade = CascadeType.ALL, fetch = FetchType.LAZY )
    private Set<GHPullRequest> pullRequests;

    private Instant            lastFetchedAt;

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

    /**
     * UNSAFE METHOD! Due to lazy loading, you _must_ call
     * GHRepository.loadCommits(this) to load in all commits from the DB before
     * using this method
     *
     * @param commits
     */
    public void addCommits ( final Collection<GHCommit> commits ) {
        commits.forEach( commit -> addCommit( commit ) );
    }

    public void addCommit ( final GHCommit commit ) {
        if ( this.commits.contains( commit ) ) {
            return;
        }
        this.commits.add( commit );
        commit.setRepository( this );
    }

    /**
     * UNSAFE METHOD! Due to lazy loading, you _must_ call
     * GHRepository.loadCommits(this) to load in all commits from the DB before
     * using this method
     *
     * @return commits
     */
    public Set<GHCommit> getCommits () {
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
