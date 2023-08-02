package edu.ncsu.csc.autovcs.services;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import edu.ncsu.csc.autovcs.AutoVCSProperties;
import edu.ncsu.csc.autovcs.forms.PopulateDataForm;
import edu.ncsu.csc.autovcs.models.persistent.DataFetchProgress;
import edu.ncsu.csc.autovcs.models.persistent.GitUser;

@Component
public class PopulateDataService {
	
    @Autowired
    private GitUserService       userService;

    @Autowired
    private GHRepositoryService  repositoryService;

    @Autowired
    private GHCommitService      commitService;

    @Autowired
    private GHPullRequestService prService;
    
    @Autowired
    private DataFetchProgressService progressService;
	
    @Async
	public void populateData(final PopulateDataForm form, DataFetchProgress progress) {
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
                progress.setFailure( "Repository not found" );
                progressService.save(progress);
                return;
            }

            repos = Collections.singletonList( singleRepo );
        }

        else {
            try {
                org = github.getOrganization( form.getOrganisation() );
            }
            catch ( final Exception e ) {
                progress.setFailure( "Organisation requested not found" );
                progressService.save(progress);
                return;
            }

            System.out.println( "Organisation found @ " + Instant.now() );

            try {
                repos = org.listRepositories();
            }
            catch ( final Exception e ) {
            	progress.setFailure( "No repositories found" );
            	progressService.save(progress);
            	return;
            }
        }

        System.out.println( "Initialisation finished" + " @" + Instant.now() );

        /* initialise the repository */

        
        Integer savedReposCount = 0;

        final List<String> reposWithDuplicateUsers = new ArrayList<String>();
        final Map<String, String> unableToCheck = new LinkedHashMap<String, String>();
        
        List<GHRepository> matchingRepos = new ArrayList<GHRepository>();
        
        repos.forEach(matchingRepos::add);
        
        matchingRepos = matchingRepos.stream().filter(e -> e.getName().startsWith( form.getRepository() )).toList();
        
        progress.setHowManyToFetch(matchingRepos.size());
        progress.setHowManyFetched(0);

        for ( final GHRepository repo : matchingRepos ) {

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
            edu.ncsu.csc.autovcs.models.persistent.GHRepository repoToSave = repositoryService
                    .findByNameAndOrganisation( repoName, organisationName );

            /* Create a new repo if there wasn't one found */
            System.out.println( "Initialising repo " + repoName + " @" + Instant.now() );
            if ( null == repoToSave ) {
                repoToSave = new edu.ncsu.csc.autovcs.models.persistent.GHRepository();
                repoToSave.setRepositoryName( repoName );
                repoToSave.setOrganisationName( organisationName );
                repositoryService.save( repoToSave );
            }

            /* Add commits */
            if ( form.getCommit() ) {
                System.out.println( "Fetching commits for " + repoName + " @" + Instant.now() );
                final Collection<edu.ncsu.csc.autovcs.models.persistent.GHCommit> newCommits = getCommitsOnRepo( repo,
                        repoToSave, form.isFetchAllHistory() );
                /*
                 * Due to lazy loading on the part of Spring, we need to fetch
                 * all possible commits first or we get strange issues
                 */
                repositoryService.loadCommits( repoToSave );
                repoToSave.addCommits( newCommits );
            }

            /* Add PRs */
            if ( form.isPr() ) {
                System.out.println( "Fetching PRs for " + repoName + " @" + Instant.now() );
                repoToSave.addPullRequests( getPullRequestsForRepo( repo ) );
            }



            repoToSave.setLastFetchedAt( Instant.now() );

            System.out.println( "Finished; about to save " + repoName + " @" + Instant.now() );
            repositoryService.save( repoToSave );
            
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
            
            savedReposCount++;
            progress.setHowManyFetched(savedReposCount);
            progressService.save(progress);
        }

        Integer queriesAvailableAtEnd = null;

        try {
            queriesAvailableAtEnd = github.getRateLimit().remaining;
        }
        catch ( final Exception e ) {
            // marvelous
        }

        System.out.println( "Queries consumed during fetch: " + ( queriesAvailable - queriesAvailableAtEnd ) );
        
        progress.setIsComplete(true);
        progress.setDuplicates(reposWithDuplicateUsers);
        progress.setUnableToCheckDuplicates(unableToCheck);
        progress.setStatus(reposWithDuplicateUsers.isEmpty() && unableToCheck.isEmpty() ? DataFetchProgress.CompletionStatus.SUCCESS : DataFetchProgress.CompletionStatus.DUPLICATES);
        
        progressService.save(progress);
	}
	

    private boolean checkForDuplicateMembers ( final edu.ncsu.csc.autovcs.models.persistent.GHRepository repoToSave,
            final GHOrganization org ) {

        final String teamAndRepoName = repoToSave.getRepositoryName();

        try {
            final GHTeam team = org.getTeamByName( teamAndRepoName );

            if ( null == team ) {
                throw new RuntimeException( "A matching team could not be found" );
            }

            final Set<GitUser> usersOnTeam = team.getMembers().stream().map( e -> userService.forUser( e ) )
                    .collect( Collectors.toSet() );

            repositoryService.loadCommits( repoToSave );
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
                requestsToSave.add( prService.forPullRequest( fullRequest ) );
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

            /*
             * Grab the most recent commit we have for this repository, so we
             * can figure out the timestamp on it & know how far back to fetch
             */
            final edu.ncsu.csc.autovcs.models.persistent.GHCommit mostRecent = commitService
                    .findMostRecentByRepository( persistentRepo );

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
                        final edu.ncsu.csc.autovcs.models.persistent.GHCommit parsed = commitService.forCommit( commit,
                                branch.getName() );
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
