package edu.ncsu.csc.autovcs.services;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import edu.ncsu.csc.autovcs.models.persistent.GHCommit;
import edu.ncsu.csc.autovcs.models.persistent.GHFile;
import edu.ncsu.csc.autovcs.repositories.GHFileRepository;

@Component
@Transactional
public class GHFileService extends Service<GHFile, Long> {

    @Autowired
    private GHFileRepository repository;

    @Override
    protected JpaRepository<GHFile, Long> getRepository () {
        return repository;
    }

    public List<GHFile> findByCommit ( final GHCommit commit ) {
        return repository.findByAssociatedCommit( commit );
    }

}
