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


import cucumber.api.java.After;
import org.locationtech.geogig.REST.GeoserverRest;

public class CucumberHooks {

    @After
    public void afterScenario() throws Exception {
        String cleanup = Variables.replaceVariables("${CLEANUP}");
        if  ( (cleanup == null) || (cleanup.length() ==0) || (cleanup.equalsIgnoreCase("false")) )
            return;

       String wsname = Variables.getVariable("WS_NAME");
       String dbname = Variables.getVariable("DB_NAME");
       String reponame = Variables.getVariable("REPO_NAME");

        String baseURL = Variables.replaceVariables("http://${GEOSERVER_HOST}/geoserver/rest");
        GeoserverRest geoserverRest = new GeoserverRest(baseURL);
        geoserverRest.deleteWorkSpace(wsname);

        //cannot remove repo from geoserver
        // db will likely be active (due to geoserver connection pool) so cannot easily drop it

//        dbname = Variables.replaceVariables(dbname);
//        String sql = "DROP DATABASE "+dbname;
//        String user = Variables.replaceVariables("${POSTGRES_USER}");
//        String pass = Variables.replaceVariables("${POSTGRES_PASS}");
//        Postgresql postgresql  = new Postgresql(dbname, user,pass);
//        postgresql.executeSQL(sql);
    }
}
