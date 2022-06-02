package edu.ncsu.csc.autovcs.controllers.api;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitQueryBuilder;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GitHub;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import edu.ncsu.csc.autovcs.AutoVCSProperties;
import edu.ncsu.csc.autovcs.forms.PopulateDataForm;
import edu.ncsu.csc.autovcs.models.persistent.GitUser;

@RestController
@SuppressWarnings ( { "rawtypes", "unchecked" } )
public class APIRepositoryController extends APIController {

    @GetMapping ( BASE_PATH + "rateLimits" )
    public ResponseEntity getRateLimits () {

        try {
            final GitHub github = AutoVCSProperties.getGH();
            return new ResponseEntity( github.getRateLimit(), HttpStatus.OK );
        }
        catch ( final IOException e ) {
            return new ResponseEntity( HttpStatus.INTERNAL_SERVER_ERROR );
        }
    }

    /**
     * Fetches data from Github to parse and save in the database
     *
     * @param organisation
     *            Github organisation to find repos in
     * @param prefix
     *            Prefix of the repository name to match against
     * @return
     */
    @PostMapping ( BASE_PATH + "populateRepositories/" )
    public ResponseEntity populateRepositories ( @RequestBody final PopulateDataForm form ) {
        System.out.println( "Initialisation starting @" + Instant.now() );
        final GitHub github = AutoVCSProperties.getGH();

        System.out.println( "Connected to GitHub @ " + Instant.now() );

        Integer queriesAvailable = null;

        try {
            queriesAvailable = github.getRateLimit().remaining;
        }
        catch ( final Exception e ) {
            // marvelous
        }

        Iterable<GHRepository> repos = null;
        GHOrganization org = null;

        if ( form.isUser() ) {
            GHRepository singleRepo = null;
            try {
                singleRepo = github.getRepository( form.getOrganisation() + "/" + form.getRepository() );
            }
            catch ( final IOException e ) {
                return new ResponseEntity( errorResponse( "Repository not found" ), HttpStatus.NOT_FOUND );
            }

            repos = Collections.singletonList( singleRepo );
        }

        else {
            try {
                org = github.getOrganization( form.getOrganisation() );
            }
            catch ( final Exception e ) {
                return new ResponseEntity( errorResponse( "Organisation requested not found" ), HttpStatus.NOT_FOUND );
            }

            System.out.println( "Organisation found @ " + Instant.now() );

            try {
                repos = org.listRepositories();
            }
            catch ( final Exception e ) {
                return new ResponseEntity( errorResponse( "No repositories found" ), HttpStatus.BAD_REQUEST );
            }
        }

        System.out.println( "Initialisation finished" + " @" + Instant.now() );

        /* initialise the repository */

        int matchingRepos = 0;

        final List<String> reposWithDuplicateUsers = new ArrayList<String>();
        final Map<String, String> unableToCheck = new LinkedHashMap<String, String>();

        for ( final GHRepository repo : repos ) {
            if ( !repo.getName().startsWith( form.getRepository() ) ) {
                continue;
            }

            String organisationName;
            String repoName;

            try {
                organisationName = repo.getOwner().getLogin();
            }
            catch ( final IOException e ) {
                throw new RuntimeException( e );
            }

            repoName = repo.getName();

            /*
             * Look up the existing repository that we have, if any. This is to
             * avoid creating duplicates if we call this method again to get
             * updates
             */
            edu.ncsu.csc.autovcs.models.persistent.GHRepository repoToSave = edu.ncsu.csc.autovcs.models.persistent.GHRepository
                    .getByNameAndOrganisation( repoName, organisationName );

            /* Create a new repo if there wasn't one found */
            System.out.println( "Initialising repo " + repoName + " @" + Instant.now() );
            if ( null == repoToSave ) {
                repoToSave = new edu.ncsu.csc.autovcs.models.persistent.GHRepository();
                repoToSave.setRepositoryName( repoName );
                repoToSave.setOrganisationName( organisationName );
                repoToSave.save();
            }

            /* Add commits */
            if ( form.getCommit() ) {
                System.out.println( "Fetching commits for " + repoName + " @" + Instant.now() );
                repoToSave.addCommits( getCommitsOnRepo( repo, repoToSave, form.isFetchAllHistory() ) );
            }

            /* Add PRs */
            if ( form.isPr() ) {
                System.out.println( "Fetching PRs for " + repoName + " @" + Instant.now() );
                repoToSave.addPullRequests( getPullRequestsForRepo( repo ) );
            }

            if ( !form.isUser() && form.isCheckDuplicates() ) {
                System.out.println( "Checking for duplicate users on " + repoName );
                try {
                    if ( checkForDuplicateMembers( repoToSave, org ) ) {
                        reposWithDuplicateUsers.add( repoToSave.getRepositoryName() );
                    }
                }
                catch ( final RuntimeException re ) {
                    unableToCheck.put( repoToSave.getRepositoryName(), re.getMessage() );
                }
            }

            repoToSave.setLastFetchedAt( Instant.now() );

            System.out.println( "Finished; about to save " + repoName + " @" + Instant.now() );
            repoToSave.save();
            matchingRepos++;
        }

        Integer queriesAvailableAtEnd = null;

        try {
            queriesAvailableAtEnd = github.getRateLimit().remaining;
        }
        catch ( final Exception e ) {
            // marvelous
        }

        System.out.println( "Queries consumed during fetch: " + ( queriesAvailable - queriesAvailableAtEnd ) );

        return new ResponseEntity(
                new RepositoryFetchInformation( matchingRepos, reposWithDuplicateUsers, unableToCheck ),
                HttpStatus.OK );
    }

    private boolean checkForDuplicateMembers ( final edu.ncsu.csc.autovcs.models.persistent.GHRepository repoToSave,
            final GHOrganization org ) {

        final String teamAndRepoName = repoToSave.getRepositoryName();

        try {
            final GHTeam team = org.getTeamByName( teamAndRepoName );

            if ( null == team ) {
                throw new RuntimeException( "A matching team could not be found" );
            }

            final Set<GitUser> usersOnTeam = team.getMembers().stream().map( e -> GitUser.forUser( e ) )
                    .collect( Collectors.toSet() );

            final Set<GitUser> usersOnRepo = repoToSave.getCommits().stream().map( e -> e.getAuthor() )
                    .collect( Collectors.toSet() );

            usersOnRepo.removeIf( e -> e.isExcluded() );

            usersOnRepo.removeAll( usersOnTeam );

            /*
             * If we still have users present on the repo who weren't present on
             * the team (ie, set is not empty) we have duplicate users
             */
            return !usersOnRepo.isEmpty();

        }
        catch ( final Exception e ) {
            throw new RuntimeException( "Error occurred while checking for duplicate members on repository "
                    + repoToSave.getRepositoryName() + ": " + e.getMessage() );
        }

    }

    private List<edu.ncsu.csc.autovcs.models.persistent.GHPullRequest> getPullRequestsForRepo (
            final GHRepository repo ) {
        final List<edu.ncsu.csc.autovcs.models.persistent.GHPullRequest> requestsToSave = new ArrayList<edu.ncsu.csc.autovcs.models.persistent.GHPullRequest>();
        try {
            final List<GHPullRequest> requests = repo.getPullRequests( GHIssueState.ALL );
            for ( final GHPullRequest request : requests ) {
                final GHPullRequest fullRequest = repo.getPullRequest( request.getNumber() );
                requestsToSave.add( new edu.ncsu.csc.autovcs.models.persistent.GHPullRequest( fullRequest ) );
            }

        }
        catch ( final Exception e ) {
            throw new RuntimeException( e );
        }

        return requestsToSave;
    }

    private Collection<edu.ncsu.csc.autovcs.models.persistent.GHCommit> getCommitsOnRepo ( final GHRepository repo,
            final edu.ncsu.csc.autovcs.models.persistent.GHRepository persistentRepo, final Boolean includeAll ) {
        final Map<String, edu.ncsu.csc.autovcs.models.persistent.GHCommit> allCommitsForRepo = new HashMap<String, edu.ncsu.csc.autovcs.models.persistent.GHCommit>();
        try {

            /* Grab the most recent commit we have for this repository */
            final edu.ncsu.csc.autovcs.models.persistent.GHCommit mostRecent = edu.ncsu.csc.autovcs.models.persistent.GHCommit
                    .getMostRecentByRepository( persistentRepo );

            /* Handle the scenario if we have no commits */
            final Instant mostRecentCommitDate = null == mostRecent ? Instant.ofEpochMilli( 0L )
                    : mostRecent.getCommitDate();

            /* GH API uses old APIs...have to convert */
            final Date converted = Date.from( mostRecentCommitDate );

            /*
             * Retrieve each branch in the repository, and for each, go find the
             * commits on it
             */
            repo.getBranches().values().forEach( branch -> {
                final String head = branch.getSHA1();

                final GHCommitQueryBuilder commitBuilder = repo.queryCommits().from( head );

                /*
                 * GH API behaves oddly -- it seems to filter on _day_ not
                 * _timestamp_, so this doesn't work quite as well as we'd want.
                 * Still, it's better than nothing.
                 */
                final List<GHCommit> allForBranch = includeAll ? commitBuilder.list().asList()
                        : commitBuilder.since( converted ).list().asList();

                /*
                 * Commits on this branch might already have been "discovered"
                 * via another branch before. This is to update the commit
                 * previously located (if any) to indicate it is also on this
                 * newly-found branch
                 */
                allForBranch.stream().forEach( commit -> {
                    final String commitHash = commit.getSHA1();

                    if ( allCommitsForRepo.containsKey( commitHash ) ) {
                        allCommitsForRepo.get( commitHash ).addBranch( branch.getName() );
                    }
                    else {
                        final edu.ncsu.csc.autovcs.models.persistent.GHCommit parsed = new edu.ncsu.csc.autovcs.models.persistent.GHCommit(
                                commit, branch.getName() );
                        allCommitsForRepo.put( commitHash, parsed );
                    }
                } );
            } );
        }
        catch ( final Exception e1 ) {
            throw new RuntimeException( e1 );
        }
        return allCommitsForRepo.values();
    }

    /**
     * Retrieves all unique branches for the {organisation, repository}
     * combination requested.
     *
     * @param organisation
     *            The Github organisation the Repository is associated with
     * @param repository
     *            The specific repository to search
     * @return
     */
    @GetMapping ( BASE_PATH + "repositories/{organisation}/{repository}/branches" )
    public ResponseEntity getRepositoryBranches ( @PathVariable final String organisation,
            @PathVariable final String repository ) {
        final edu.ncsu.csc.autovcs.models.persistent.GHRepository repo = edu.ncsu.csc.autovcs.models.persistent.GHRepository
                .getByNameAndOrganisation( repository, organisation );

        return new ResponseEntity( edu.ncsu.csc.autovcs.models.persistent.GHCommit.getByRepository( repo ).stream()
                .map( commit -> commit.getBranches() ).flatMap( branches -> branches.stream() ).distinct()
                .collect( Collectors.toSet() ), HttpStatus.OK );
    }

    @GetMapping ( BASE_PATH + "repositories/{organisation}/{repository}/members" )
    public ResponseEntity getRepositoryMembers ( @PathVariable final String organisation,
            @PathVariable final String repository ) {
        final edu.ncsu.csc.autovcs.models.persistent.GHRepository repo = edu.ncsu.csc.autovcs.models.persistent.GHRepository
                .getByNameAndOrganisation( repository, organisation );

        final Set<GitUser> users = edu.ncsu.csc.autovcs.models.persistent.GHCommit.getByRepository( repo ).stream()
                .map( commit -> commit.getAuthor() ).distinct().collect( Collectors.toSet() );

        final List<edu.ncsu.csc.autovcs.models.persistent.GHPullRequest> PRs = edu.ncsu.csc.autovcs.models.persistent.GHPullRequest
                .getByRepository( repo );

        users.addAll( PRs.stream().map( request -> request.getOpenedBy() ).collect( Collectors.toSet() ) );

        users.addAll( PRs.stream().map( PR -> PR.getPullRequestComments() ).flatMap( comments -> comments.stream() )
                .map( comment -> comment.getCommenter() ).collect( Collectors.toSet() ) );

        return new ResponseEntity( users, HttpStatus.OK );
    }

    @GetMapping ( BASE_PATH + "repositoryData" )
    public ResponseEntity getRepositories () {
        /*
         * map to a flat type because otherwise we get a stack overflow b/c
         * infinite recursion with repo <-> commit referencing each other.
         */
        final List<edu.ncsu.csc.autovcs.models.persistent.GHRepository> repos = edu.ncsu.csc.autovcs.models.persistent.GHRepository
                .getAll();
        return new ResponseEntity( repos.stream().map( edu.ncsu.csc.autovcs.models.persistent.GHRepository::format )
                .collect( Collectors.toList() ), HttpStatus.OK );
    }

    public class RepositoryFetchInformation {
        private final Integer             howManyFetched;

        private final List<String>        reposWithDuplicateUsers;

        private final Map<String, String> unableToCheckDuplicateUsers;

        public RepositoryFetchInformation ( final Integer howManyFetched, final List<String> reposWithDuplicateUsers,
                final Map<String, String> unableToCheckDuplicateUsers ) {
            this.howManyFetched = howManyFetched;
            this.reposWithDuplicateUsers = reposWithDuplicateUsers;
            this.unableToCheckDuplicateUsers = unableToCheckDuplicateUsers;
        }

        public Integer getHowManyFetched () {
            return howManyFetched;
        }

        public List<String> getReposWithDuplicateUsers () {
            return reposWithDuplicateUsers;
        }

        public Map<String, String> getUnableToCheckDuplicateUsers () {
            return unableToCheckDuplicateUsers;
        }

    }
}
