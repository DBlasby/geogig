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


import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;
import java.util.List;

//provides access to GeoGIG rest api (inside geoserver)
public class GeoGIGRest extends Rest {

    public String transactionId;
    public String transactionRepo;

    public GeoGIGRest(String baseURL) {
        super(baseURL);
    }

    public void initPostgresRepo(String reponame, String dbname, String user, String passwd) throws Exception {
        String json = "{\n" +
                "                `dbHost`: `localhost`,\n" +
                "                `dbPort`: `5432`,\n" +
                "                `dbName`: `" + dbname + "`,\n" +
                "                `dbSchema`: `public`,\n" +
                "                `dbUser`: `" + user + "`,\n" +
                "                `dbPassword`: `" + passwd + "`,\n" +
                "                `authorName`: `geogig`,\n" +
                "                `authorEmail`: `geogig@geogig.org`\n" +
                "    }";
        json = json.replace("`", "\"");

        doPutJson("/repos/" + reponame + "/init.json", json, 201);
    }

    public String createIndex(String reponame, String layername, String[] extraAtts) throws Exception {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("treeRefSpec", layername));

        for (String att : extraAtts) {
            params.add(new BasicNameValuePair("extraAttributes", att));
        }

        String url = "/repos/" + reponame + "/index/create";
        String response = doPutSimple(url, params, 201);
        String success = getTagText("success", response);
        if (!success.equals("true")) {
            throw new Exception("index create did not work");
        }
        return getTagText("indexedTreeId", response);
    }


    public void startTransaction(String reponame) throws Exception {
        String url = "/repos/" + reponame + "/beginTransaction";
        String result = doGet(url, 200);
        transactionId = getTagText("ID", result);
        transactionRepo = reponame;
    }

    public void endTransaction(String reponame, boolean commit) throws Exception {
        String url = "/repos/" + reponame + "/endTransaction";

        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("cancel", !commit + ""));
        params.add(new BasicNameValuePair("transactionId", transactionId));

        String result = doGet(url, params,200);
        transactionId = null;
        String success = getTagText("success", result);
        if (!success.equalsIgnoreCase("true"))
            throw new Exception("failed to endTransaction - "+result);
    }

    String importTaskID;
    public void importFromPG(String database,String table,String user,String password) throws Exception {
        if (transactionId == null)
            throw new Exception("no transaction in progress");
        String url = "/repos/"+transactionRepo+"/postgis/import";
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("transactionId", transactionId));
        params.add(new BasicNameValuePair("table", table));
        params.add(new BasicNameValuePair("database", database));
        params.add(new BasicNameValuePair("user", user));
        params.add(new BasicNameValuePair("password", password));

        String result = doGet(url, params,200);
        importTaskID = getTagText("id", result);
    }

    public void waitForImportToFinish() throws Exception {
        boolean keep_going = true;
        String url = "/tasks/"+importTaskID+".xml";
        while (keep_going) {
            String result = doGet(url, 200);
            String status = getTagText("status", result);
            keep_going = status.equalsIgnoreCase("RUNNING");
        }
    }

    public void add() throws Exception {
        if (transactionId == null)
            throw new Exception("no transaction in progress");
        String url = "/repos/"+transactionRepo+"/add";
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("transactionId", transactionId));
        String result = doGet(url, params,200);
        String success = getTagText("success", result);
        if (!success.equalsIgnoreCase("true"))
            throw new Exception("failed to add - "+result);
    }

    public int nadded;
    public int ndeleted;
    public int nchanged;
    public int ntotalactions;

    public void commit(String message) throws Exception {
        if (transactionId == null)
            throw new Exception("no transaction in progress");
        String url = "/repos/"+transactionRepo+"/commit";
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("transactionId", transactionId));
        params.add(new BasicNameValuePair("message", message));

        String result = doGet(url, params,200);
        String success = getTagText("success", result);
        if (!success.equalsIgnoreCase("true"))
            throw new Exception("failed to add - "+result);
        nadded = Integer.parseInt(getTagText("added", result));
        nchanged = Integer.parseInt(getTagText("changed", result));
        ndeleted = Integer.parseInt(getTagText("deleted", result));
        ntotalactions = nadded+nchanged+ndeleted;
    }
}