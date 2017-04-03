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

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


//Basic REST services
public class Rest {

    String baseURL;

     CloseableHttpClient httpclient  ;


    public Rest(String baseURL) {
        this.baseURL = baseURL;
        CredentialsProvider provider = new BasicCredentialsProvider();
        Credentials defaultcreds = new UsernamePasswordCredentials("admin", "geoserver");
        provider.setCredentials(AuthScope.ANY, defaultcreds);

        httpclient = HttpClientBuilder.create()
                .setDefaultCredentialsProvider(provider)
                .build();

        Authenticator.setDefault (new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication ("admin", "geoserver".toCharArray());
            }
        });
    }

    public   String doGet(String url,int expectedResultCode) throws Exception {
        HttpGet request = new HttpGet( baseURL + url);
        CloseableHttpResponse response = httpclient.execute(request);
        verifyResponseCode(response, expectedResultCode);
        String result = getBody(response);
        response.close();
        return result;
    }

    public String doDelete( String url) throws Exception {
        HttpURLConnection httpCon = (HttpURLConnection)(new URL(baseURL+url)).openConnection();
        httpCon.setRequestMethod("DELETE"); //it's a post request
        int responseCode = httpCon.getResponseCode();
        InputStream response = httpCon.getInputStream();
        String responseString = IOUtils.toString(response, "UTF-8");
        response.close();


        if  ( (responseCode != 200)  && (responseCode != 202) && (responseCode != 204) )
            throw new Exception("Delete failed! "+responseString);
        return responseString;
    }


    public   String doGet(String url, List<NameValuePair> params,int expectedResultCode) throws Exception {
        String query= "";
        if (params.size() >0)
            query = "?";
        for (NameValuePair keyvalue : params) {
            query += "&"+keyvalue.getName()+"="+keyvalue.getValue();
        }
        HttpURLConnection httpCon = (HttpURLConnection)(new URL(baseURL+url+query)).openConnection();
        httpCon.setRequestMethod("GET"); //it's a post request
        int responseCode = httpCon.getResponseCode();
        InputStream response = httpCon.getInputStream();
        String responseString = IOUtils.toString(response, "UTF-8");
        response.close();
        if (responseCode != expectedResultCode)
            throw new Exception("got response code "+responseCode+", expected "+expectedResultCode);
        return responseString;
    }



    public   String doPost(String url, String xml,int expectedResultCode) throws Exception {
        HttpPost request = new HttpPost( baseURL + url );
        request.setHeader("Content-type","text/xml");
        HttpEntity entity = new ByteArrayEntity(xml.getBytes("UTF-8"));
        request.setEntity(entity);
        CloseableHttpResponse response = httpclient.execute(request);
        verifyResponseCode(response, expectedResultCode);
        String result = getBody(response);
        response.close();
        return result;
    }
    public   String doPutXML(String url, String xml,int expectedResultCode) throws Exception {
        HttpPut request = new HttpPut( baseURL + url );
        request.setHeader("Content-type","text/xml");
        HttpEntity entity = new ByteArrayEntity(xml.getBytes("UTF-8"));
        request.setEntity(entity);
        CloseableHttpResponse response = httpclient.execute(request);
        verifyResponseCode(response, expectedResultCode);
        String result = getBody(response);
        response.close();
        return result;
    }

    public   String doPutJson(String url, String json,int expectedResultCode) throws Exception {
        HttpPut request = new HttpPut( baseURL + url );
        request.setHeader("Content-type","application/json");
        HttpEntity entity = new ByteArrayEntity(json.getBytes("UTF-8"));
        request.setEntity(entity);
        CloseableHttpResponse response = httpclient.execute(request);
        verifyResponseCode(response, expectedResultCode);
        String result = getBody(response);
        response.close();
        return result;
    }

    public   String doPutSimple(String url, List<NameValuePair> params, int expectedResultCode) throws Exception {
        String query= "";
        if (params.size() >0)
            query = "?";
       for (NameValuePair keyvalue : params) {
           query += "&"+keyvalue.getName()+"="+keyvalue.getValue();
       }
        HttpURLConnection httpCon = (HttpURLConnection)(new URL(baseURL+url+query)).openConnection();
        httpCon.setRequestMethod("PUT"); //it's a post request
        int responseCode = httpCon.getResponseCode();
        InputStream response = httpCon.getInputStream();
        String responseString = IOUtils.toString(response, "UTF-8");
        response.close();
        if (responseCode != expectedResultCode)
            throw new Exception("got response code "+responseCode+", expected "+expectedResultCode);
        return responseString;
    }

    public void verifyResponseCode(HttpResponse response, int expectedResultCode) throws Exception {
        if (response.getStatusLine().getStatusCode() == expectedResultCode)
            return;
        String responseString =getBody(response);
        throw new Exception("expected response code = "+expectedResultCode+
                ", but got "+response.getStatusLine().getStatusCode()+"\n"+"body: "+responseString);
    }

    public String getBody(HttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        String responseString = EntityUtils.toString(entity, "UTF-8");
        return responseString;
    }

    public String getTagText(String tag,String xml) {
        Pattern pattern = Pattern.compile("<"+tag+">(.*)</"+tag+">" );

        Matcher matcher = pattern.matcher(xml);
        matcher.find();
        return  matcher.group(1);
    }
}
