package edu.ncsu.csc.autovcs.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DefaultController {

    @GetMapping ( { "/", "/index" } )
    public String getLanding ( final Model model ) {
        return "/index";
    }

    @GetMapping ( "populateData" )
    public String getPopulateData ( final Model model ) {
        return "/populateData";
    }

    @GetMapping ( "viewCommitCharts" )
    public String getCharts ( final Model model ) {
        return "viewCommitCharts";
    }

    @GetMapping ( "remapUsers" )
    public String getRemapUsers ( final Model model ) {
        return "remapUsers";
    }

    @GetMapping ( "userView" )
    public String getUserView ( final Model model ) {
        return "userView";
    }

    @GetMapping ( "viewTeamNetwork" )
    public String getTeamNetwork ( final Model model ) {
        return "viewTeamNetwork";
    }

    @GetMapping ( "viewContributions" )
    public String getContributions ( final Model model ) {
        return "viewContributions";
    }

    @GetMapping ( "manageExcludedUsers" )
    public String getManageExcludedUsers ( final Model model ) {
        return "manageExcludedUsers";
    }
}
