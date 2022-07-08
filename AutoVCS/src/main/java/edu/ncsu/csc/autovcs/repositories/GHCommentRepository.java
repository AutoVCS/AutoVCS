package edu.ncsu.csc.autovcs.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.ncsu.csc.autovcs.models.persistent.GHComment;
import edu.ncsu.csc.autovcs.models.persistent.GitUser;

public interface GHCommentRepository extends JpaRepository<GHComment, Long> {

    public List<GHComment> findByCommenter ( GitUser user );

}
