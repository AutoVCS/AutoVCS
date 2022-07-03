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

    @GetMapping ( { "populateData", "populateData.html" } )
    public String getPopulateData ( final Model model ) {
        return "/populateData";
    }

    @GetMapping ( { "remapUsers", "remapUsers.html" } )
    public String getRemapUsers ( final Model model ) {
        return "remapUsers";
    }

    @GetMapping ( { "viewContributions", "viewContributions.html" } )
    public String getContributions ( final Model model ) {
        return "viewContributions";
    }

    @GetMapping ( { "manageExcludedUsers", "manageExcludedUsers.html" } )
    public String getManageExcludedUsers ( final Model model ) {
        return "manageExcludedUsers";
    }
}
