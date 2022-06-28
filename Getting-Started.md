## Getting Started



## Configuration

There are a couple of things you have to setup before AutoVCS will work:

* Build and install [ChangeDistiller](https://github.com/AutoVCS/ChangeDistiller).  You can either build the ChangeDistiller JAR file yourself, or use a prebuilt JAR from the `Releases` page.  You must then install the JAR file into your local Maven repository by running `mvn install:install-file -Dfile=/path/to/ChangeDistiller.jar -DgroupId=ch.uzh.ifi.seal -DartifactId=ChangeDistiller -Dversion=1.0.0 -Dpackaging=jar -DgeneratePom=true`.  *This requires Maven on your path*

* Configure database.  AutoVCS will create a database with the correct tables automatically, but it must be told how to connect to a MySQL/MariaDB database.  Copy `AutoVCS/src/main/resources/hibernate-template.cfg.xml` to `AutoVCS/src/main/resources/hibernate.cfg.xml` and put your database password on line 24.  If you're using a non-root user for the database, update the username on line 23 as well.

* Configure Github Properties file.  Copy `AutoVCS/gh-template.properties` to `AutoVCS/gh.properties` and fill in the following options:

* `githubEnterprise`: `true`/`false` depending on whether you want to use a configured Github Enterprise site (`true`) or Github.com (`false`).  If set to `true`, fill in:

    * `enterpriseAPI`: API URL of your Github Enterprise installation, if any.  The default API URL is `<your Github Enterprise URL>/api/v3` (for example; `https://github.ncsu.edu/api/v3`), but may be different depending on your Github Enterprise settings.  If this doesn't work, contact your Github administrators.

    * `enterpriseURL`: URL of your Github Enterprise installation, such as `https://github.ncsu.edu`
  
    * `enterpriseToken`: API token for connecting to your Github Enterprise site.

    * `enterpriseUsername`: Username for connecting to your Github Enterprise site.

    * `enterprisePassword`: Password for connecting to your Github Enterprise site.  This may be the same as your token.
  
* alternatively, if `githubEnterprise` is set to `false`, instead configure:
  
    * `token`: API token for connecting to Github.com
  
    * `username`: Username for connecting to Github.com
  
    * `password`: Password for connecting to Github.com.  This may be the same as your token.

* If you regularly use `Github.com` and a Github Enterprise installation, you may configure all eight options and simply switch the `githubEnterprise` flag back and forth when you wish to use the other configuration.  Regardless, you must also configure:

* `desiredEmailDomain`: AutoVCS identifies users by their `name` and `email address`.  When fetching information from the Github API, users with no known email address will have an identifying email address generated for them, according to the format `username@desiredEmailDomain`.  We suggest populating the URL of your institution (for example `ncsu.edu`), or using `gmail.com`




## Running AutoVCS

AutoVCS is built using Spring Boot, and can be run as a web application (for interactive use) or as a console application (for batch mode).  Interactive mode binds to port 8080 like most Java web applications.  Batch mode requires no extra ports, but uses two extra files to run the process.


## Running AutoVCS

### Interactive Mode

The AutoVCS web application can be launched either from the terminal (if you have Maven installed) or through an IDE (such as Eclipse/IntelliJ).  To launch it from the terminal, run `mvn spring-boot:run` from the directory containing `pom.xml` (that is, inside the AutoVCS folder in this repository).  

You can also run AutoVCS through your IDE; import this project (in Eclipse, right-click in the Project Explorer, then Import -> Maven -> Existing Maven Projects & browse to where you cloned it; follow a similar process in other IDEs).  You can then run the `AutoVCSApplication` class (`edu.ncsu.csc.autovcs.AutoVCSApplication`).  No command-line arguments are needed.


### Batch Mode

If you have more than a couple repositories to analyse, or would like to integrate AutoVCS into your grading workflow, we **strongly suggest** that you use Batch Mode.  The main advantages of batch mode are:

* Build a standalone HTML report for each repository.  Rather than relying on data pulled live from the application server, a HTML page is built for each repository that is completely standalone, and can be hosted on Github, uploaded to Google Drive, etc.  Javascript libraries are pulled from CDNs, and all contributions data is stored in the page as JSON.

* Analyses are run in parallel.  This does what it says, and runs much faster than sequential analyses, particularly when you have a lot of repositories to analyse.

Batch mode takes the following command-line parameters (and has the associated defaults).  All parameters are optional.

- `template` (default: `AutoVCS.template`): The template file to use for building up each HTML report page.  For each repository, the computed JSON data is inserted into this template to make the report.

- `config` (default: `config.json`): A JSON configuration file detailing which repositories to create summaries for.  The format of this file is explained in more detail, with an example, below.

- `debug` (default: `false`): If enabled, prints out details for any repository where summary reports could not be made.  If disabled (default), only shows a list of repositories where summary reports could not be made.  Note, unlike all other parameters, as this is a binary toggle, it does not use the `--parameterName=value` syntax.  Instead, just use `--debug` to enable debug mode.

- `timeout` (default: `1` (hours)): Maximum to wait for all analyses to complete.  If you're running many repositories but on few threads, consider increasing this.

- `threads` (default: `min(8, numCPUs)`): Number of worker threads to use for running analyses in parallel.  From our experience, analysis performance is limited more by filesystem performance than CPU performance, but we suggest not increasing this past `2*numCPUs` or 16 threads total, whichever is lower.


Parameters are specified using the format `--parameterName=value`, and can be provided in any order.

#### Config File Format

The JSON configuration file expects the following format:

```
{
  "organisation": string,  // The organisation where your repositories are located.  This will use Github.com or a Github Enterprise location as configured in your `gh.properties` file.
  
  "repositories": [  // List of one or more repository matchers for creating summary pages.
  
    {
      "exactMatch": boolean,  // If `true`, this will exactly match the name (below) to a repository in the organisation (above); if `false` will match against any repositories that _start_ with the name below. 
      
      "name": string,  // Name of the repository to match; either full name, or prefix for wildcard matching.
      
      "excludeGUI": boolean,   // exclude GUI files from analysis
      
      "startDate": string,  // optional.  Used in combination with endDate, below, to just analyse contributions that fall within a certain time window.  Uses the ISO-8601 format: https://en.wikipedia.org/wiki/ISO_8601
      
      "endDate": string    // same as startDate, above.
      
    }
  ]

}

```

You can pass multiple repository matchers to the `repositories` field, and can use any combination of prefix matching or exact matching.  For example, the following configuration matches against the repository `LabRepository`, or any repositories that start with `LabRepositoryA`.  Additionally, it performs no date filtering, and considers commits from any time window.

```
{
  "organisation": "AutoVCS",
  
  "repositories": [
  
    {
      "exactMatch": true,
      
      "name": "LabRepository",
      
      "excludeGUI": true
      
    },
    
    {
      "exactMatch": false,
      
      "name": "LabRepositoryA",
      
      "excludeGUI": true
      
    }
  ]

}
```


