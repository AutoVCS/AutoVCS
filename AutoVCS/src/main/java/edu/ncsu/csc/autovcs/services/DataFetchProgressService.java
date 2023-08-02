package edu.ncsu.csc.autovcs.services;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import edu.ncsu.csc.autovcs.models.persistent.DataFetchProgress;
import edu.ncsu.csc.autovcs.repositories.DataFetchProgressRepository;

@Component
@Transactional
public class DataFetchProgressService extends Service<DataFetchProgress, Long> {
	
	@Autowired
	private DataFetchProgressRepository repository;

	@Override
	protected JpaRepository<DataFetchProgress, Long> getRepository() {
		return repository;
	}
	
	

}
