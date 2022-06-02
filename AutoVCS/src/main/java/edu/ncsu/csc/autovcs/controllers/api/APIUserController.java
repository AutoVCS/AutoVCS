package edu.ncsu.csc.autovcs.controllers.api;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import edu.ncsu.csc.autovcs.models.persistent.DomainObject;
import edu.ncsu.csc.autovcs.models.persistent.GHComment;
import edu.ncsu.csc.autovcs.models.persistent.GHCommit;
import edu.ncsu.csc.autovcs.models.persistent.GHPullRequest;
import edu.ncsu.csc.autovcs.models.persistent.GitUser;

@RestController
@SuppressWarnings ( { "rawtypes" } )
public class APIUserController extends APIController {

    @PostMapping ( BASE_PATH + "users/remap" )
    public ResponseEntity remapUsers ( @RequestBody final Map<Long, Long> usersMap ) {

        usersMap.forEach( ( oldUserId, newUserId ) -> {
            final GitUser oldUser = GitUser.getById( oldUserId );

            final GitUser newUser = GitUser.getById( newUserId );

            // remap commits
            final List<GHCommit> commitsByOldUser = GHCommit.getForUser( oldUser );
            commitsByOldUser.forEach( commit -> commit.setAuthor( newUser ) );

            DomainObject.saveAll( commitsByOldUser );

            // remap PR information
            final List<GHComment> comments = GHComment.getForUser( oldUser );

            comments.forEach( comment -> comment.setCommenter( newUser ) );

            DomainObject.saveAll( comments );

            final List<GHPullRequest> openedByOldUser = GHPullRequest.getOpenedBy( oldUser );
            openedByOldUser.forEach( request -> request.setOpenedBy( newUser ) );

            final List<GHPullRequest> closedByOldUser = GHPullRequest.getMergedBy( oldUser );
            closedByOldUser.forEach( request -> request.setMergedBy( newUser ) );

            final List<GHComment> commentsByOldUser = GHComment.getForUser( oldUser );
            commentsByOldUser.forEach( comment -> comment.setCommenter( newUser ) );

            DomainObject.saveAll( openedByOldUser );
            DomainObject.saveAll( closedByOldUser );

            DomainObject.saveAll( commentsByOldUser );
        } );

        return new ResponseEntity( HttpStatus.OK );
    }

    @PostMapping ( BASE_PATH + "users" )
    public ResponseEntity createUser ( @RequestBody final GitUser user ) {

        try {
            user.save();
            return new ResponseEntity( HttpStatus.CREATED );
        }
        catch ( final Exception e ) {
            return new ResponseEntity( HttpStatus.BAD_REQUEST );
        }

    }

    @GetMapping ( BASE_PATH + "users" )
    public List<GitUser> getUsers () {
        return GitUser.getAll();
    }

    @GetMapping ( BASE_PATH + "users/excluded" )
    public List<GitUser> getExcludedUsers () {
        return GitUser.getExcluded();
    }

    @PostMapping ( BASE_PATH + "users/{id}/include" )
    public ResponseEntity includeUser ( @PathVariable final Long id ) {
        final GitUser user = GitUser.getById( id );
        if ( null == user ) {
            return new ResponseEntity( HttpStatus.NOT_FOUND );
        }
        user.setExcluded( false );
        user.save();
        return new ResponseEntity( HttpStatus.OK );
    }

    @PostMapping ( BASE_PATH + "users/{id}/exclude" )
    public ResponseEntity excludeUser ( @PathVariable final Long id ) {
        final GitUser user = GitUser.getById( id );
        if ( null == user ) {
            return new ResponseEntity( HttpStatus.NOT_FOUND );
        }
        user.setExcluded( true );
        user.save();
        return new ResponseEntity( HttpStatus.OK );
    }

    @PostMapping ( BASE_PATH + "users/{name}/{type}/exclude" )
    public ResponseEntity excludeMultipleUsers ( @PathVariable final String name, @PathVariable final String type ) {
        final Set<GitUser> users = new HashSet<GitUser>();

        if ( "name".equals( type ) ) {
            users.addAll( GitUser.getByNameContaining( name ) );
        }
        else if ( "email".equals( type ) ) {
            users.addAll( GitUser.getByEmailContaining( name ) );
        }
        else if ( "both".equals( type ) ) {
            users.addAll( GitUser.getByNameContaining( name ) );
            users.addAll( GitUser.getByEmailContaining( name ) );
        }
        else {
            return new ResponseEntity( HttpStatus.BAD_REQUEST );
        }
        if ( users.isEmpty() ) {
            return new ResponseEntity( HttpStatus.NOT_FOUND );
        }
        users.forEach( user -> {
            user.setExcluded( true );
            user.save();
        } );
        return new ResponseEntity( HttpStatus.OK );

    }

    @GetMapping ( BASE_PATH + "users/{name}/{type}/exclude" )
    public Set<GitUser> excludeMultipleUsersSearch ( @PathVariable final String name,
            @PathVariable final String type ) {
        final Set<GitUser> users = new HashSet<GitUser>();

        if ( "name".equals( type ) ) {
            users.addAll( GitUser.getByNameContaining( name ) );
        }
        else if ( "email".equals( type ) ) {
            users.addAll( GitUser.getByEmailContaining( name ) );
        }
        else if ( "both".equals( type ) ) {
            users.addAll( GitUser.getByNameContaining( name ) );
            users.addAll( GitUser.getByEmailContaining( name ) );
        }

        return users;

    }
}
