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



import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.memory.FIdMemoryDataStore;
import org.locationtech.geogig.REST.GeoserverRest;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CucumberBindings {

    WFSSimpleClient wfsClient;
    FIdMemoryDataStore memoryDataStore;

    Query query;

    DataStoreActions actions;
    Verifications verifications;


    public CucumberBindings() throws Exception {
        System.setProperty("org.geotools.referencing.forceXY", "true");

        String uniqueID = Variables.replaceVariables("${UUID}");
        System.out.println("main identifier is "+uniqueID);
        System.out.println(" gg --repo \"postgresql://localhost:5432/gigdb_"+uniqueID+"/gigrepo_"+uniqueID+"?user=postgres&password=\" ls giglayer_"+uniqueID);
        System.out.println("");
     }



    @Given("^Create FeatureType ([^ ]+) ([^ ]+) ([^ ]+) \"([^\"]+)\"$")
    public void create_featuretype(String wsname, String dsname, String layername, String defn) throws Exception {
        wsname = Variables.replaceVariables(wsname);
        dsname = Variables.replaceVariables(dsname);
        layername = Variables.replaceVariables(layername);
        defn = Variables.replaceVariables(defn);

        SimpleFeatureType featureType  = DataUtilities.createType( wsname,layername, defn);
        String baseURL = Variables.replaceVariables("http://${GEOSERVER_HOST}/geoserver/rest");
        GeoserverRest geoserverRest = new GeoserverRest(baseURL);
        geoserverRest.createLayer(wsname, dsname,layername,featureType);

        String getCapURL = Variables.replaceVariables("http://${GEOSERVER_HOST}/geoserver/wfs?REQUEST=GetCapabilities&VERSION=1.0.0");

        wfsClient = new WFSSimpleClient(getCapURL,"admin", "geoserver", wsname, layername);
        memoryDataStore = new FIdMemoryDataStore(wfsClient.type);
    }





    @Given("^Geoserver: Publish Layer ([^ ]+) ([^ ]+) ([^ ]+)$")
    public void publish_on_geoserver(String wsname, String dsname, String layername) throws Exception {
        publish_on_geoserver(wsname,dsname,layername,"");
    }


    @Given("^Geoserver: Publish Layer ([^ ]+) ([^ ]+) ([^ ]+) ([^ ]+)$")
    public void publish_on_geoserver(String wsname, String dsname, String layername,String diminfo) throws Exception {
        wsname = Variables.replaceVariables(wsname);
        dsname = Variables.replaceVariables(dsname);
        layername = Variables.replaceVariables(layername);
        diminfo = Variables.replaceVariables(diminfo);

        //time:decade:ISO8601:LIST:MINIMUM -->
        //<entry key="time">
        //  <dimensionInfo>
        //    	<enabled>true</enabled>
        //    	<attribute>decade</attribute>
        //    	<presentation>LIST</presentation>
        //    	<units>ISO8601</units>
        //    	<defaultValue>
        //      	<strategy>MINIMUM</strategy>
        //    	</defaultValue>
        //  </dimensionInfo>
        //</entry>

        GeoserverRest.DimensionInfo dimensionConfig = null;
        if ( (diminfo != null) && (diminfo.length() >0) ){
            String[] details= diminfo.split(":");
            dimensionConfig = new GeoserverRest.DimensionInfo(details[0],true,details[1],details[2], details[3],details[4]);
        }

        String baseURL = Variables.replaceVariables("http://${GEOSERVER_HOST}/geoserver/rest");
        GeoserverRest geoserverRest = new GeoserverRest(baseURL);
        geoserverRest.publishLayer(wsname,dsname,layername,dimensionConfig);
       //  wfsQuery = new WFS(wsname,layername);
        String getCapURL = Variables.replaceVariables("http://${GEOSERVER_HOST}/geoserver/wfs?REQUEST=GetCapabilities&VERSION=1.0.0");
        wfsClient = new WFSSimpleClient(getCapURL,"admin", "geoserver", wsname, layername);
    }




    @Given("^I setup a transaction against (.*)")
    public void I_setup_a_transaction(List<String> stores) throws Throwable {
        WFSSimpleClient wfsDS = null;
        DataStore memoryDS = null;
        if (stores.contains("WFS")) {
            wfsDS = wfsClient;
        }
        if (stores.contains("MEMORY")) {
            memoryDS = memoryDataStore;
        }
        actions = new DataStoreActions(memoryDS,wfsDS, wfsClient.type);
    }

    @Given("^I Query \"([^\"]+)\" against (.*)")
    public void i_query_against(String cql, List<String> stores) throws Exception {
        cql = Variables.replaceVariables(cql);

        WFSSimpleClient wfsDS = null;
        DataStore memoryDS = null;
        if (stores.contains("WFS")) {
            wfsDS = wfsClient;
        }
        if (stores.contains("MEMORY")) {
            memoryDS = memoryDataStore;
        }
        query = new Query(memoryDS,wfsDS,wfsClient.type, cql);
    }

    @Given("^Assert query returns (\\d+) features")
    public void assert_query_nfeatures(int nfeatures) throws Exception {
        query.assertNFeatures(nfeatures);
    }


    @Given("^I insert (\\d+) features \"([^\"]+)\"$")
    public void i_insert_n_features(int number,String definition) throws Throwable {
        List<String> definitions =  Arrays.asList(definition.split(";") );
        FeatureBuilder featureBuilder = new FeatureBuilder(wfsClient.type, definitions);
        ArrayList<SimpleFeature> features = (ArrayList<SimpleFeature>) featureBuilder.createFeatures( number);
        DataStoreActions.InsertAction insert = new DataStoreActions.InsertAction(features,actions);
        actions.add(insert);
    }

    @Given("^I delete features \"([^\"]+)\"$")
    public void i_delete_features(String cqlfilter) throws Throwable {
        cqlfilter = Variables.replaceVariables(cqlfilter);

        DataStoreActions.DeleteAction delete = new DataStoreActions.DeleteAction(cqlfilter);
        actions.add(delete);
    }


        @Given("^I commit the transaction$")
    public void i_commit_the_transaction() throws Throwable {
        actions.execute();
    }

    @Given("^I verify Fids$")
    public void I_verify_Fids() throws Throwable {
        verifications.verifyFIDs();
    }



    @Given("^I verify Query \"([^\"]+)\"")
    public void I_verify_query(String cqlFilter) throws Throwable {
        cqlFilter = Variables.replaceVariables(cqlFilter);

        verifications.verifyQuery(cqlFilter);
    }



    @Given("^Update set ([^ ]+)=\"([^\"]+)\" WHERE \"([^\"]+)\"$")
    public void update(String attribute, String value, String cqlFilter) throws  Exception {
        attribute = Variables.replaceVariables(attribute);
        value = Variables.replaceVariables(value);
        cqlFilter = Variables.replaceVariables(cqlFilter);

        actions.add(new DataStoreActions.UpdateAction(attribute,value,cqlFilter,wfsClient.type));
    }

    @And("^Assert Query results are equivalent")
    public void assertQueryResultsAreEquivalent() throws Throwable {
        query.assertEquivalent();
    }
}
