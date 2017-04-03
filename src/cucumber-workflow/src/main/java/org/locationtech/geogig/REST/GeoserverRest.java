/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.REST;

import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;

import java.util.Map;

//provides access to geoserver REST
public class GeoserverRest  extends Rest {

    public GeoserverRest(String baseURL) {
        super(baseURL);
    }

    public   void geoserverRunning() throws Exception {
        String response = doGet("", 200);
    }

    public   void createWorkSpace(String name) throws Exception {
        String response = doPost("/workspaces", "<workspace><name>"+name+"</name></workspace>", 201);
     }

    public void deleteWorkSpace(String wsname) throws Exception {
        String response = doDelete("/workspaces/"+wsname+"?recurse=true");
    }

    public   void verifyWorkSpace(String name) throws Exception {
        String response = doGet("/workspaces/"+name,200);
    }

    public void createGigDatastore(String workspaceName, String repoName,String storeName,Map<String,String> configParams) throws Exception {
        configParams.put("geogig_repository","geoserver://"+repoName);
        String connectSection = "";
        for (Map.Entry<String, String> item: configParams.entrySet()) {
            String itemStr = "<entry key=\""+item.getKey()+"\">"+item.getValue()+"</entry>";
            connectSection += itemStr;
        }
        String xml = "<dataStore><name>"+storeName+"</name><connectionParameters>"+connectSection+"</connectionParameters></dataStore>";
        doPost("/workspaces/"+workspaceName+"/datastores", xml, 201);
    }



    public void createLayer(String workspace, String dsName, String layerName, SimpleFeatureType ftype) throws Exception {
        String srid = ftype.getGeometryDescriptor().getCoordinateReferenceSystem().getIdentifiers().iterator().next().toString();
        String attributes = "";
        for (int t=0;t<ftype.getAttributeCount();t++) {
            AttributeDescriptor desc = ftype.getDescriptor(t);
            attributes +=  "    <attribute>\n" +
                    "      <name>"+desc.getLocalName()+ "</name>\n" +
                    "      <binding>"+desc.getType().getBinding().getCanonicalName()+"</binding>\n" +
                    "    <nillable>true</nillable>" + //set to true so that we can only ask for a few properties at a time (or always get all)
                    "    </attribute>\n" ;
        }
        String xml = "<featureType>\n" +
                "  <name>"+layerName+"</name>\n" +
                "  <nativeName>"+layerName+"</nativeName>\n" +
                "  <namespace>\n" +
                "    <name>"+workspace+"</name>\n" +
                "  </namespace>\n" +
                "  <title>"+layerName+"</title>\n" +
                "  <srs>"+srid+"</srs>\n" +
                "  <attributes>\n" +
                attributes +
                "  </attributes>\n" +
                "</featureType>";

        doPost("/workspaces/"+workspace+"/datastores/"+dsName+"/featuretypes", xml,201);
    }

    public void publishLayer(String wsname, String dsname, String layername,DimensionInfo dimensionInfo) throws Exception {
        String xml;
        if (dimensionInfo == null)
            xml ="<featureType><name>"+layername+"</name></featureType>";
        else
            xml ="<featureType><name>"+layername+"</name><metadata>"+dimensionInfo.toXML()+"</metadata></featureType>";

        String result = doPost("/workspaces/"+wsname+"/datastores/"+dsname+"/featuretypes", xml,201);
    }

    public void updatePublishLayer(String wsname, String dsname, String layername,DimensionInfo dimensionInfo) throws Exception {
        String xml;
        if (dimensionInfo == null)
            xml ="<featureType><name>"+layername+"</name><enabled>true</enabled></featureType>";
        else
            xml ="<featureType><name>"+layername+"</name><enabled>true</enabled><metadata>"+dimensionInfo.toXML()+"</metadata></featureType>";

        String result = doPutXML("/workspaces/"+wsname+"/datastores/"+dsname+"/featuretypes/"+layername, xml,200);
    }



    public static class DimensionInfo {

        public boolean enabled = true;
        public String attribute;
        public String presentation;

        public String units;
        public String strategy;
        public String dimension;

        public DimensionInfo(String dimension, boolean enabled, String attribute, String units, String presentation, String strategy) {
            this.enabled = enabled;
            this.attribute = attribute;
            this.presentation = presentation;
            this.units = units;
            this.strategy = strategy;
            this.dimension = dimension;
        }

        public String toXML() {
            String result = "";
            result += "<entry key=\""+dimension+"\">";
            result += "  <dimensionInfo>";
            result += "    	<enabled>"+enabled+"</enabled>";
            result += "    	<attribute>"+attribute+"</attribute>";
            result += "    	<presentation>"+presentation+"</presentation>";
            result += "    	<units>"+units+"</units>";
            result += "    	<defaultValue>";
            result += "      	<strategy>"+strategy+"</strategy>";
            result += "    	</defaultValue>";
            result += "  </dimensionInfo>";
            result += "</entry>";
            return result;
        }

    }
}
