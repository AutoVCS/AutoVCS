package edu.ncsu.csc.autovcs.batch;

import edu.ncsu.csc.autovcs.forms.ContributionsSummaryForm;

public class RepositoryOptions {

    private Boolean exactMatch;

    private String  name;

    private Boolean excludeGUI;

    private String  startDate;

    private String  endDate;

    public Boolean getExactMatch () {
        return exactMatch;
    }

    public void setExactMatch ( final Boolean exactMatch ) {
        this.exactMatch = exactMatch;
    }

    public String getName () {
        return name;
    }

    public void setName ( final String name ) {
        this.name = name;
    }

    public Boolean getExcludeGUI () {
        return excludeGUI;
    }

    public void setExcludeGUI ( final Boolean excludeGUI ) {
        this.excludeGUI = excludeGUI;
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

    public ContributionsSummaryForm toForm ( final String organisation ) {
        final ContributionsSummaryForm csf = new ContributionsSummaryForm();
        csf.setOrganisation( organisation );
        csf.setRepository( name );
        csf.setType( "BY_USER" );
        csf.setInitialiseUnknown( false );
        csf.setExcludeGUI( excludeGUI );
        csf.setStartDate( startDate );
        csf.setEndDate( endDate );

        return csf;
    }

    public ContributionsSummaryForm toForm ( final String organisation, final String fullName ) {
        final ContributionsSummaryForm csf = toForm( organisation );
        csf.setRepository( fullName );
        return csf;
    }

}
