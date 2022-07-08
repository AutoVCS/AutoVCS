package edu.ncsu.csc.autovcs.services;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequestReviewComment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import edu.ncsu.csc.autovcs.models.persistent.GHComment;
import edu.ncsu.csc.autovcs.models.persistent.GHPullRequest;
import edu.ncsu.csc.autovcs.models.persistent.GHRepository;
import edu.ncsu.csc.autovcs.models.persistent.GitUser;
import edu.ncsu.csc.autovcs.repositories.GHPullRequestRepository;

@Component
@Transactional
public class GHPullRequestService extends Service<GHPullRequest, Long> {

    @Autowired
    private GHPullRequestRepository repository;

    @Autowired
    private GHRepositoryService     repositoryService;

    @Autowired
    private GitUserService          userService;

    @Autowired
    private GHCommentService        commentService;

    @Override
    protected JpaRepository<GHPullRequest, Long> getRepository () {
        return repository;
    }

    public GHPullRequest forPullRequest ( final org.kohsuke.github.GHPullRequest request ) {

        final GHRepository repository = repositoryService.forRepository( request.getRepository() );
        final int number = request.getNumber();

        Instant openedAt = null, closedAt = null;

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

        final Set<GHComment> pullRequestComments = new HashSet<GHComment>();
        if ( null != comments ) {
            pullRequestComments
                    .addAll( comments.stream().map( commentService::forComment ).collect( Collectors.toSet() ) );
        }
        if ( null != reviewComments ) {
            pullRequestComments
                    .addAll( reviewComments.stream().map( commentService::forComment ).collect( Collectors.toSet() ) );
        }
        GitUser mergedBy = null, openedBy = null;
        try {
            mergedBy = userService.forUser( request.getMergedBy() );
        }
        catch ( final Exception e ) {
            // maybe not merged
        }
        try {
            openedBy = userService.forUser( request.getUser() );
        }
        catch ( final Exception e ) {
            throw new RuntimeException( e );
        }

        final String title = request.getTitle();

        final String body = request.getBody();

        final String url = request.getHtmlUrl().toString();

        final GHPullRequest pr = new GHPullRequest();
        pr.setClosedAt( closedAt );
        pr.setOpenedAt( openedAt );
        pr.setOpenedBy( openedBy );
        pr.setMergedBy( mergedBy );
        pr.setNumber( number );
        pr.setPullRequestComments( pullRequestComments );
        pr.setRepository( repository );
        pr.setTitle( title );
        pr.setBody( body );
        pr.setUrl( url );

        return pr;

    }

    public List<GHPullRequest> findByRepository ( final GHRepository repository ) {
        return this.repository.findByRepository( repository );
    }

    public List<GHPullRequest> findOpenedBy ( final GitUser user ) {
        return repository.findByOpenedBy( user );
    }

    public List<GHPullRequest> findMergedBy ( final GitUser user ) {
        return repository.findByMergedBy( user );
    }

}
