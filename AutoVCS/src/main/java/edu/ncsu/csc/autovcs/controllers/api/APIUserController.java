package edu.ncsu.csc.autovcs.controllers.api;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import edu.ncsu.csc.autovcs.models.persistent.GHComment;
import edu.ncsu.csc.autovcs.models.persistent.GHCommit;
import edu.ncsu.csc.autovcs.models.persistent.GHPullRequest;
import edu.ncsu.csc.autovcs.models.persistent.GitUser;
import edu.ncsu.csc.autovcs.services.GHCommentService;
import edu.ncsu.csc.autovcs.services.GHCommitService;
import edu.ncsu.csc.autovcs.services.GHPullRequestService;
import edu.ncsu.csc.autovcs.services.GitUserService;

@RestController
@SuppressWarnings ( { "rawtypes" } )
public class APIUserController extends APIController {

    @Autowired
    private GitUserService       userService;

    @Autowired
    private GHCommentService     commentService;

    @Autowired
    private GHPullRequestService prService;

    @Autowired
    private GHCommitService      commitService;

    @PostMapping ( BASE_PATH + "users/remap" )
    public ResponseEntity remapUsers ( @RequestBody final Map<Long, Long> usersMap ) {
    	
    	Map<String, Integer> resp = new HashMap<String, Integer>();
    	
    	resp.put("NUM_USERS", usersMap.size());
    	
    	// feels kinda silly vs just an `Integer`, but req'd:
    	// https://docs.oracle.com/javase/specs/jls/se10/html/jls-15.html#jls-15.27.2
    	AtomicInteger commits = new AtomicInteger();

        usersMap.forEach( ( oldUserId, newUserId ) -> {
            final GitUser oldUser = userService.findById( oldUserId );

            final GitUser newUser = userService.findById( newUserId );

            // remap commits
            final List<GHCommit> commitsByOldUser = commitService.findByUser( oldUser );
            commitsByOldUser.forEach( commit -> commit.setAuthor( newUser ) );

            commits.addAndGet(commitsByOldUser.size());
            
            commitService.saveAll( commitsByOldUser );

            // remap PR information
            final List<GHComment> comments = commentService.findForUser( oldUser );

            comments.forEach( comment -> comment.setCommenter( newUser ) );

            commentService.saveAll( comments );

            final List<GHPullRequest> openedByOldUser = prService.findOpenedBy( oldUser );
            openedByOldUser.forEach( request -> request.setOpenedBy( newUser ) );

            final List<GHPullRequest> closedByOldUser = prService.findMergedBy( oldUser );
            closedByOldUser.forEach( request -> request.setMergedBy( newUser ) );

            // TODO: We have this above -- is this really needed again?
            // :raised_eyebrow:
            final List<GHComment> commentsByOldUser = commentService.findForUser( oldUser );
            commentsByOldUser.forEach( comment -> comment.setCommenter( newUser ) );

            prService.saveAll( openedByOldUser );
            prService.saveAll( closedByOldUser );

            commentService.saveAll( commentsByOldUser );
            
        } );
        
        resp.put("NUM_COMMITS", commits.get());

        return new ResponseEntity<Map>( resp, HttpStatus.OK );
    }

    @PostMapping ( BASE_PATH + "users" )
    public ResponseEntity createUser ( @RequestBody final GitUser user ) {

        try {
            userService.save( user );
            return new ResponseEntity( HttpStatus.CREATED );
        }
        catch ( final Exception e ) {
            return new ResponseEntity( HttpStatus.BAD_REQUEST );
        }

    }

    @GetMapping ( BASE_PATH + "users" )
    public List<GitUser> getUsers () {
        return userService.findAll();
    }

    @GetMapping ( BASE_PATH + "users/excluded" )
    public List<GitUser> getExcludedUsers () {
        return userService.findExcluded();
    }

    @PostMapping ( BASE_PATH + "users/{id}/include" )
    public ResponseEntity includeUser ( @PathVariable final Long id ) {
        final GitUser user = userService.findById( id );
        if ( null == user ) {
            return new ResponseEntity( HttpStatus.NOT_FOUND );
        }
        user.setExcluded( false );
        userService.save( user );
        return new ResponseEntity( HttpStatus.OK );
    }

    @PostMapping ( BASE_PATH + "users/{id}/exclude" )
    public ResponseEntity excludeUser ( @PathVariable final Long id ) {
        final GitUser user = userService.findById( id );
        if ( null == user ) {
            return new ResponseEntity( HttpStatus.NOT_FOUND );
        }
        user.setExcluded( true );
        userService.save( user );
        return new ResponseEntity( HttpStatus.OK );
    }

    @PostMapping ( BASE_PATH + "users/{name}/{type}/exclude" )
    public ResponseEntity excludeMultipleUsers ( @PathVariable final String name, @PathVariable final String type ) {
        final Set<GitUser> users = new HashSet<GitUser>();

        if ( "name".equals( type ) ) {
            users.addAll( userService.findByNameContaining( name ) );
        }
        else if ( "email".equals( type ) ) {
            users.addAll( userService.findByEmailContaining( name ) );
        }
        else if ( "both".equals( type ) ) {
            users.addAll( userService.findByNameContaining( name ) );
            users.addAll( userService.findByEmailContaining( name ) );
        }
        else {
            return new ResponseEntity( HttpStatus.BAD_REQUEST );
        }
        if ( users.isEmpty() ) {
            return new ResponseEntity( HttpStatus.NOT_FOUND );
        }
        users.forEach( user -> {
            user.setExcluded( true );
        } );
        userService.saveAll( users );
        return new ResponseEntity( HttpStatus.OK );

    }

    @GetMapping ( BASE_PATH + "users/{name}/{type}/exclude" )
    public Set<GitUser> excludeMultipleUsersSearch ( @PathVariable final String name,
            @PathVariable final String type ) {
        final Set<GitUser> users = new HashSet<GitUser>();

        if ( "name".equals( type ) ) {
            users.addAll( userService.findByNameContaining( name ) );
        }
        else if ( "email".equals( type ) ) {
            users.addAll( userService.findByEmailContaining( name ) );
        }
        else if ( "both".equals( type ) ) {
            users.addAll( userService.findByNameContaining( name ) );
            users.addAll( userService.findByEmailContaining( name ) );
        }

        return users;

    }
}
