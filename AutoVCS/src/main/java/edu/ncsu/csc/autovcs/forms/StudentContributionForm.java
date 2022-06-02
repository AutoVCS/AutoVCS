package edu.ncsu.csc.autovcs.forms;

public class StudentContributionForm {

    private String startDate;

    private String endDate;

    private String repoName;

    private String organisation;

    private Long   studentId;

    public StudentContributionForm () {
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

    public Long getStudentId () {
        return studentId;
    }

    public void setStudentId ( final Long studentId ) {
        this.studentId = studentId;
    }

    public String getRepoName () {
        return repoName;
    }

    public void setRepoName ( final String repoName ) {
        this.repoName = repoName;
    }

    public String getOrganisation () {
        return organisation;
    }

    public void setOrganisation ( final String organisation ) {
        this.organisation = organisation;
    }
}
