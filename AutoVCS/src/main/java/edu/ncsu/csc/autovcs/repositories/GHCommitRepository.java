package edu.ncsu.csc.autovcs.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.ncsu.csc.autovcs.models.persistent.GHCommit;
import edu.ncsu.csc.autovcs.models.persistent.GHRepository;
import edu.ncsu.csc.autovcs.models.persistent.GitUser;

public interface GHCommitRepository extends JpaRepository<GHCommit, Long> {

    public List<GHCommit> findByAuthor ( GitUser user );

    public List<GHCommit> findByRepository ( GHRepository repository );

    public GHCommit findFirstByRepositoryOrderByCommitDateDesc ( GHRepository repository );

}
