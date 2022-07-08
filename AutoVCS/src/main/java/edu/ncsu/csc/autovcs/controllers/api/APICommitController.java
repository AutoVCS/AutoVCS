package edu.ncsu.csc.autovcs.controllers.api;

import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import edu.ncsu.csc.autovcs.forms.ContributionsSummaryForm;
import edu.ncsu.csc.autovcs.services.ContributionAnalysisService;

@RestController
@SuppressWarnings ( { "rawtypes", "unchecked" } )
public class APICommitController extends APIController {

    @Autowired
    private ContributionAnalysisService cas;

    @PostMapping ( BASE_PATH + "contributions" )
    public ResponseEntity extractContributions ( @RequestBody final ContributionsSummaryForm form ) {

        try {
            final String response = cas.getContributionSummaries( form );
            return new ResponseEntity( response, HttpStatus.OK );
        }
        catch ( final NoSuchElementException nsee ) {
            return new ResponseEntity( nsee.getMessage(), HttpStatus.NOT_FOUND );
        }
        catch ( final Exception e ) {
            return new ResponseEntity( e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR );
        }

    }

}
