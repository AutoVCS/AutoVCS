package edu.ncsu.csc.autovcs.forms;

public class ContributionsSummaryForm {

    private String  organisation;

    private String  repository;

    private String  type;

    private boolean initialiseUnknown;

    private boolean excludeGUI;

    private String  startDate;

    private String  endDate;

    public ContributionsSummaryForm () {

    }

    public String getOrganisation () {
        return organisation;
    }

    public void setOrganisation ( final String organisation ) {
        this.organisation = organisation;
    }

    public String getRepository () {
        return repository;
    }

    public void setRepository ( final String repository ) {
        this.repository = repository;
    }

    public String getType () {
        return type;
    }

    public void setType ( final String type ) {
        this.type = type;
    }

    public boolean isInitialiseUnknown () {
        return initialiseUnknown;
    }

    public void setInitialiseUnknown ( final boolean initialiseUnknown ) {
        this.initialiseUnknown = initialiseUnknown;
    }

    public String getStartDate () {
        return startDate;
    }

    public void setStartDate ( final String startDate ) {
        this.startDate = startDate;
    }

    public String getEndDate () {
        return endDate;
    }

    public void setEndDate ( final String endDate ) {
        this.endDate = endDate;
    }

    public boolean isExcludeGUI () {
        return excludeGUI;
    }

    public void setExcludeGUI ( final boolean excludeGUI ) {
        this.excludeGUI = excludeGUI;
    }

}
