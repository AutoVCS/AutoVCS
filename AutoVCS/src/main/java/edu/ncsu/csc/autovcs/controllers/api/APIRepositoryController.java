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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import edu.ncsu.csc.autovcs.AutoVCSProperties;
import edu.ncsu.csc.autovcs.forms.PopulateDataForm;
import edu.ncsu.csc.autovcs.models.persistent.DataFetchProgress;
import edu.ncsu.csc.autovcs.models.persistent.GitUser;
import edu.ncsu.csc.autovcs.services.DataFetchProgressService;
import edu.ncsu.csc.autovcs.services.GHCommitService;
import edu.ncsu.csc.autovcs.services.GHPullRequestService;
import edu.ncsu.csc.autovcs.services.GHRepositoryService;
import edu.ncsu.csc.autovcs.services.GitUserService;
import edu.ncsu.csc.autovcs.services.PopulateDataService;

@RestController
@SuppressWarnings ( { "rawtypes", "unchecked" } )
public class APIRepositoryController extends APIController {

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
    
    @Autowired
    private PopulateDataService populateService;

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
    	
    	// start tracking progress so we can report to the user
    	DataFetchProgress progress = new DataFetchProgress();
    	
    	progressService.save(progress);
    	
    	populateService.populateData(form, progress);
    	
        return new ResponseEntity(progress.getId(), HttpStatus.OK );
    }
    
    @GetMapping (BASE_PATH + "populateRepositories/{id}/status")
    public ResponseEntity getPopulationStatus(@PathVariable Long id) {
    	return new ResponseEntity(progressService.findById(id), HttpStatus.OK);
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
        final edu.ncsu.csc.autovcs.models.persistent.GHRepository repo = repositoryService
                .findByNameAndOrganisation( repository, organisation );

        return new ResponseEntity(
                commitService.findByRepository( repo ).stream().map( commit -> commit.getBranches() )
                        .flatMap( branches -> branches.stream() ).distinct().collect( Collectors.toSet() ),
                HttpStatus.OK );
    }

    @GetMapping ( BASE_PATH + "repositories/{organisation}/{repository}/members" )
    public ResponseEntity getRepositoryMembers ( @PathVariable final String organisation,
            @PathVariable final String repository ) {
        final edu.ncsu.csc.autovcs.models.persistent.GHRepository repo = repositoryService
                .findByNameAndOrganisation( repository, organisation );

        final Set<GitUser> users = commitService.findByRepository( repo ).stream().map( commit -> commit.getAuthor() )
                .distinct().collect( Collectors.toSet() );

        final List<edu.ncsu.csc.autovcs.models.persistent.GHPullRequest> PRs = prService.findByRepository( repo );

        users.addAll( PRs.stream().map( request -> request.getOpenedBy() ).collect( Collectors.toSet() ) );

        users.addAll( PRs.stream().map( PR -> PR.getPullRequestComments() ).flatMap( comments -> comments.stream() )
                .map( comment -> comment.getCommenter() ).collect( Collectors.toSet() ) );
        
        users.removeIf( user -> user.isExcluded() );
        
        return new ResponseEntity( users, HttpStatus.OK );
    }

    @GetMapping ( BASE_PATH + "repositoryData" )
    public ResponseEntity getRepositories () {
        /*
         * map to a flat type because otherwise we get a stack overflow b/c
         * infinite recursion with repo <-> commit referencing each other.
         */
        final List<edu.ncsu.csc.autovcs.models.persistent.GHRepository> repos = repositoryService.findAll();
        return new ResponseEntity( repos.stream().map( edu.ncsu.csc.autovcs.models.persistent.GHRepository::format )
                .collect( Collectors.toList() ), HttpStatus.OK );
    }


}
