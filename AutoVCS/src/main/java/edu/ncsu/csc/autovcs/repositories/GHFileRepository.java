package edu.ncsu.csc.autovcs.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.ncsu.csc.autovcs.models.persistent.GHCommit;
import edu.ncsu.csc.autovcs.models.persistent.GHFile;

public interface GHFileRepository extends JpaRepository<GHFile, Long> {

    public List<GHFile> findByAssociatedCommit ( GHCommit commit );

}
