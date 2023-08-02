package edu.ncsu.csc.autovcs.models.persistent;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;


@Entity
public class DataFetchProgress extends DomainObject{
	
	@Id
	@GeneratedValue ( strategy = GenerationType.IDENTITY )
	private Long    id;
	
	private Integer howManyFetched;
	
	private Integer howManyToFetch;
	
	private String completionMessage;
	
	private Boolean isComplete;
	
	@Enumerated
	private CompletionStatus status;
	
	@ElementCollection
	private List<String> duplicates;
	
	@ElementCollection
	private Map<String, String> unableToCheckDuplicates;
	
	// for hibernate
	public DataFetchProgress() {
		howManyFetched = -1;
		howManyToFetch = -1;
		setIsComplete(false);
		
	}

	@Override
	public Serializable getId() {
		return id;
	}

	/**
	 * @return the howManyFetched
	 */
	public Integer getHowManyFetched() {
		return howManyFetched;
	}

	/**
	 * @param howManyFetched the howManyFetched to set
	 */
	public void setHowManyFetched(Integer howManyFetched) {
		this.howManyFetched = howManyFetched;
	}

	/**
	 * @return the howManyToFetch
	 */
	public Integer getHowManyToFetch() {
		return howManyToFetch;
	}

	/**
	 * @param howManyToFetch the howManyToFetch to set
	 */
	public void setHowManyToFetch(Integer howManyToFetch) {
		this.howManyToFetch = howManyToFetch;
	}

	/**
	 * @return the completionMessage
	 */
	public String getCompletionMessage() {
		return completionMessage;
	}

	/**
	 * @param completionMessage the completionMessage to set
	 */
	public void setCompletionMessage(String completionMessage) {
		this.completionMessage = completionMessage;
	}

	public void setFailure(String failureMessage) {
		setCompletionMessage(failureMessage);
		this.setIsComplete(true);
		setStatus(CompletionStatus.FAILURE);
		
		
	}

	/**
	 * @return the isComplete
	 */
	public Boolean getIsComplete() {
		return isComplete;
	}

	/**
	 * @param isComplete the isComplete to set
	 */
	public void setIsComplete(Boolean isComplete) {
		this.isComplete = isComplete;
	}
	
	public CompletionStatus getStatus() {
		return status;
	}

	public void setStatus(CompletionStatus status) {
		this.status = status;
	}



	/**
	 * @return the duplicates
	 */
	public List<String> getDuplicates() {
		return duplicates;
	}

	/**
	 * @param duplicates the duplicates to set
	 */
	public void setDuplicates(List<String> duplicates) {
		this.duplicates = duplicates;
	}



	/**
	 * @return the unableToCheckDuplicates
	 */
	public Map<String, String> getUnableToCheckDuplicates() {
		return unableToCheckDuplicates;
	}

	/**
	 * @param unableToCheckDuplicates the unableToCheckDuplicates to set
	 */
	public void setUnableToCheckDuplicates(Map<String, String> unableToCheckDuplicates) {
		this.unableToCheckDuplicates = unableToCheckDuplicates;
	}



	public enum CompletionStatus {
		SUCCESS, DUPLICATES, FAILURE;
	}


}
