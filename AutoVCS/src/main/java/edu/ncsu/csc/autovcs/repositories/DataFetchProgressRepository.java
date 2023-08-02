package edu.ncsu.csc.autovcs.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.ncsu.csc.autovcs.models.persistent.DataFetchProgress;

public interface DataFetchProgressRepository extends JpaRepository<DataFetchProgress, Long> {
	
	

}
