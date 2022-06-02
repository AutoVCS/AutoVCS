package edu.ncsu.csc.autovcs.forms;

public class PopulateDataForm {

    /* Organisation or user that owns the repo */
    private String  organisation;

    private String  repository;

    /* True if this represents a user & not an organisation */
    private boolean user;

    private boolean commit;

    private boolean pr;

    private boolean fetchAllHistory;

    private boolean checkDuplicates;

    public boolean isFetchAllHistory () {
        return fetchAllHistory;
    }

    public void setFetchAllHistory ( final boolean fetchAllHistory ) {
        this.fetchAllHistory = fetchAllHistory;
    }

    public PopulateDataForm () {
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

    public boolean getCommit () {
        return commit;
    }

    public void setCommit ( final boolean commit ) {
        this.commit = commit;
    }

    public boolean isPr () {
        return pr;
    }

    public void setPr ( final boolean pr ) {
        this.pr = pr;
    }

    public boolean isCheckDuplicates () {
        return checkDuplicates;
    }

    public void setCheckDuplicates ( final boolean checkDuplicates ) {
        this.checkDuplicates = checkDuplicates;
    }

    public boolean isUser () {
        return user;
    }

    public void setUser ( final boolean isUser ) {
        this.user = isUser;
    }
}
