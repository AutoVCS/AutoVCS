package edu.ncsu.csc.autovcs.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.ncsu.csc.autovcs.models.persistent.GHPullRequest;
import edu.ncsu.csc.autovcs.models.persistent.GHRepository;
import edu.ncsu.csc.autovcs.models.persistent.GitUser;

public interface GHPullRequestRepository extends JpaRepository<GHPullRequest, Long> {

    public List<GHPullRequest> findByRepository ( GHRepository repository );

    public List<GHPullRequest> findByOpenedBy ( GitUser user );

    public List<GHPullRequest> findByMergedBy ( GitUser user );

}
