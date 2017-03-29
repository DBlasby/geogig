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
import org.locationtech.geogig.REST.GeoGIGRest;
import org.locationtech.geogig.geogig.RawGeoGIG;

import java.util.ArrayList;
import java.util.List;


public class CucumberBindings_geogig {

    @Given("^GeoGIG: Init repo ([^ ]+) ([^ ]+)")
    public void init_repo(String dbname, String reponame) throws Exception {
        reponame = Variables.replaceVariables(reponame);
        dbname = Variables.replaceVariables(dbname);
        String user = Variables.replaceVariables("${POSTGRES_USER}");
        String pass = Variables.replaceVariables("${POSTGRES_PASS}");
        String baseURL = Variables.replaceVariables("http://${GEOSERVER_HOST}/geoserver/geogig");

        GeoGIGRest geoGIGRest = new GeoGIGRest(baseURL);
        geoGIGRest.initPostgresRepo(reponame, dbname, user, pass);
    }

    @Given("^GeoGig: Execute \"([^\"]+)\" \"([^\"]+)\"$")
    public void execute_geogig(String repourl,String cmd) throws Exception {
        repourl = Variables.replaceVariables(repourl);
        cmd = Variables.replaceVariables(cmd);
        RawGeoGIG rawGeoGIG = new RawGeoGIG(repourl, "");
        rawGeoGIG.execute(cmd);
    }

    @Given("^GeoGIG: Verify the Index Exists \"([^\"]+)\" ([^ ]*)$")
    public void verify_Index_Exists(String reponame,String layername) throws Throwable {
        verify_Index_Exists(reponame,layername, new ArrayList<String>() );
    }
        @Given("^GeoGIG: Verify the Index Exists \"([^\"]+)\" (.*) WITH (.*)$")
    public void verify_Index_Exists(String reponame,String layername,List<String> extras) throws Throwable {
        reponame = Variables.replaceVariables(reponame);
        layername = Variables.replaceVariables(layername);
        RawGeoGIG rawGeoGIG = new RawGeoGIG(reponame,layername);
        rawGeoGIG.assertIndexExists(extras);
    }

    @Given("^GeoGIG: Verify Tree and Feature Bounds \"([^\"]+)\" (.*) against ([^ ]+)$")
    public void verifyTreeFeatureBounds(String reponame,String layername,List<String> indexes ) throws Throwable {
        reponame = Variables.replaceVariables(reponame);
        layername = Variables.replaceVariables(layername);

        RawGeoGIG rawGeoGIG = new RawGeoGIG(reponame,layername);

        if (indexes.contains("INDEX"))
            rawGeoGIG.verifyTreeIndex(true);
        if (indexes.contains("CANONICAL"))
            rawGeoGIG.verifyTreeCanonical(true);
    }


    @Given("^GeoGIG: Verify Tree Bounds \"([^\"]+)\" (.*) against ([^ ]+)$")
    public void Verify_boundededtree(String reponame,String layername,List<String> indexes ) throws Throwable {
        reponame = Variables.replaceVariables(reponame);
        layername = Variables.replaceVariables(layername);

        RawGeoGIG rawGeoGIG = new RawGeoGIG(reponame,layername);

        if (indexes.contains("INDEX"))
            rawGeoGIG.verifyTreeIndex(false);
        if (indexes.contains("CANONICAL"))
            rawGeoGIG.verifyTreeCanonical(false);
    }


    @Given("^GeoGIG: Verify Index Extra Data \"([^\"]+)\" (.*)$")
    public void Verify_indexExtraData(String reponame,String layername) throws Throwable {
        reponame = Variables.replaceVariables(reponame);
        layername = Variables.replaceVariables(layername);

        RawGeoGIG rawGeoGIG = new RawGeoGIG(reponame,layername);

        rawGeoGIG.verifyExtraData();
    }



    GeoGIGRest transactionSharingGeogigRest;

    @Given("^GeoGIG: Start Transaction (.*)$")
    public void start_geogig_transaction(String reponame) throws Exception {
        reponame = Variables.replaceVariables(reponame);
        String baseURL = Variables.replaceVariables("http://${GEOSERVER_HOST}/geoserver/geogig");

        if (transactionSharingGeogigRest != null)
            throw new Exception("another transaction is in progress");

        transactionSharingGeogigRest = new GeoGIGRest(baseURL);
        transactionSharingGeogigRest.startTransaction(reponame);
    }

    @Given("^GeoGIG: End Transaction (.*) (.*)$")
    public void start_geogig_transaction(String reponame, boolean commit) throws Exception {
        reponame = Variables.replaceVariables(reponame);
        transactionSharingGeogigRest.endTransaction(reponame, commit);
        transactionSharingGeogigRest = null;
    }

    @Given("^GeoGIG: Import from PG  ([^ ]+) ([^ ]+) ([^ ]+) ([^ ]+)$")
    public void import_from_pg(String dbname, String tablename, String user, String pass) throws Exception {
        dbname = Variables.replaceVariables(dbname);
        tablename = Variables.replaceVariables(tablename);
        user = Variables.replaceVariables(user);
        pass = Variables.replaceVariables(pass);

        transactionSharingGeogigRest.importFromPG(dbname,tablename,user,pass);
    }

    @Given("^GeoGIG: Wait for import to finish$")
    public void import_wait_finish() throws Exception {
        transactionSharingGeogigRest.waitForImportToFinish();
    }

    @Given("^GeoGIG: Add$")
    public void add() throws Exception {
        transactionSharingGeogigRest.add();
    }

    @Given("^GeoGIG: Commit \"([^\"]+)\"$")
    public void commit(String message) throws Exception {
        message = Variables.replaceVariables(message);

        transactionSharingGeogigRest.commit(message);
    }

    @Given("^GeoGIG: Assert Commit affect at least (\\d+) features$")
    public void minaffected(int n) throws Exception {
        if (transactionSharingGeogigRest.ntotalactions < n)
            throw new Exception("only " + transactionSharingGeogigRest.ntotalactions + " features affected.");
    }


    @Given("^GeoGIG: Verify Tree Names \"([^\"]+)\" (.*)$")
    public void verify_names(String reponame,String layername) throws Throwable {
        reponame = Variables.replaceVariables(reponame);
        layername = Variables.replaceVariables(layername);

        RawGeoGIG rawGeoGIG = new RawGeoGIG(reponame,layername);

        rawGeoGIG.verifyNames();
     }
}
