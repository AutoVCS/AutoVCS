package edu.ncsu.csc.autovcs.batch;

import java.util.List;

public class BatchConfiguration {

    private String                  ghBaseUrl;

    private String                  organisation;

    private List<RepositoryOptions> repositories;

    public void setRepositories ( final List<RepositoryOptions> repositories ) {
        this.repositories = repositories;
    }

    public List<RepositoryOptions> getRepositories () {
        return this.repositories;
    }

    public String getGhBaseUrl () {
        return ghBaseUrl;
    }

    public void setGhBaseUrl ( final String ghBaseUrl ) {
        this.ghBaseUrl = ghBaseUrl;
    }

    public String getOrganisation () {
        return organisation;
    }

    public void setOrganisation ( final String organisation ) {
        this.organisation = organisation;
    }

}
