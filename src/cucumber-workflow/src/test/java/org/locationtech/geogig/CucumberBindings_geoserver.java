/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig;

import cucumber.api.java.en.Given;
import org.locationtech.geogig.REST.GeoserverRest;

import java.util.Map;

import static org.locationtech.geogig.CucumberBindings_util.parseKeyValues;


public class CucumberBindings_geoserver {

    @Given("^Geoserver: Create GeoGIG Datastore ([^ ]+) ([^ ]+) ([^ ]+)$")
    public void create_geogig_datastore(String wsname, String reponame, String storename) throws Exception {
        create_geogig_datastore(wsname,reponame,storename,"");
    }

    @Given("^Geoserver: Create Workspace ([^ ]+)$")
    public void create_workspace(String wsname) throws Exception {
        wsname = Variables.replaceVariables(wsname);
        String baseURL = Variables.replaceVariables("http://${GEOSERVER_HOST}/geoserver/rest");
        GeoserverRest geoserverRest = new GeoserverRest(baseURL);
        geoserverRest.createWorkSpace(wsname);
    }

        @Given("^Geoserver: Create GeoGIG Datastore ([^ ]+) ([^ ]+) ([^ ]+) (.*)$")
    public void create_geogig_datastore(String wsname, String reponame, String storename, String config) throws Exception {
        wsname = Variables.replaceVariables(wsname);
        reponame = Variables.replaceVariables(reponame);
        storename = Variables.replaceVariables(storename);

        if (config == null)
            config="";
        config = Variables.replaceVariables(config);
        Map<String,String> configParams = parseKeyValues(config);

        String baseURL = Variables.replaceVariables("http://${GEOSERVER_HOST}/geoserver/rest");
        GeoserverRest geoserverRest = new GeoserverRest(baseURL);
        geoserverRest.createGigDatastore(wsname,reponame,storename,configParams);
    }

}
