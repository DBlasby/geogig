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

import com.vividsolutions.jts.geom.GeometryFactory;
import org.apache.commons.io.IOUtils;
import org.geotools.data.ows.HTTPClient;
import org.geotools.data.ows.SimpleHttpClient;
import org.geotools.data.wfs.internal.DescribeFeatureTypeRequest;
import org.geotools.data.wfs.internal.DescribeFeatureTypeResponse;
import org.geotools.data.wfs.internal.GetFeatureParser;
import org.geotools.data.wfs.internal.GetFeatureRequest;
import org.geotools.data.wfs.internal.GetFeatureResponse;
import org.geotools.data.wfs.internal.TransactionRequest;
import org.geotools.data.wfs.internal.TransactionResponse;
import org.geotools.data.wfs.internal.WFSClient;
import org.geotools.data.wfs.internal.WFSConfig;
import org.geotools.data.wfs.internal.WFSRequest;
import org.geotools.ows.ServiceException;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class WFSSimpleClient {

    WFSClient wfsClient;
    QName     typeName;
    SimpleFeatureType type;

    public WFSSimpleClient(String getCapURL, String user, String pass, String workspace, String layername) throws IOException, ServiceException {
        Map connectionParameters = new HashMap();
        connectionParameters.put("WFSDataStoreFactory:GET_CAPABILITIES_URL", getCapURL );
        connectionParameters.put("WFSDataStoreFactory:TIMEOUT", 60000 );
        connectionParameters.put("WFSDataStoreFactory:BUFFER_SIZE", 8000 );

        connectionParameters.put("WFSDataStoreFactory:USERNAME", user );
        connectionParameters.put("WFSDataStoreFactory:PASSWORD", pass );

        final WFSConfig config = WFSConfig.fromParams(connectionParameters);

        final HTTPClient http = new SimpleHttpClient();// new MultithreadedHttpClient();
        http.setUser(config.getUser());
        http.setPassword(config.getPassword());
        int timeoutMillis = config.getTimeoutMillis();
        http.setConnectTimeout(timeoutMillis / 1000);
        http.setReadTimeout(timeoutMillis / 1000);

        final URL capabilitiesURL = new URL(getCapURL);

        wfsClient  = new WFSClient(capabilitiesURL, http, config);
        typeName = findTypeName(workspace,layername);
        type = (SimpleFeatureType) getFeatureType(typeName);
    }

    public QName findTypeName(String workspace, String ftname) {
        DescribeFeatureTypeRequest request = wfsClient.createDescribeFeatureTypeRequest();
        for(QName qname : request.getStrategy().getFeatureTypeNames()) {
            if ( ( qname.getLocalPart().equals(ftname) ) && (qname.getPrefix().equals(workspace)) )
                return qname;
        }
        return null;
    }

    public FeatureType getFeatureType(QName ftname) throws IOException {
        DescribeFeatureTypeRequest request = wfsClient.createDescribeFeatureTypeRequest();
        request.setTypeName(ftname);

        DescribeFeatureTypeResponse response = wfsClient.issueRequest(request);

        return response.getFeatureType();
    }

    public List<SimpleFeature> query(org.geotools.data.Query q) throws IOException {
        GetFeatureRequest request = wfsClient.createGetFeatureRequest();
        request.setHandle("GetFeature.query");
        request.setFilter(q.getFilter());
        request.setPropertyNames(q.getPropertyNames());
        request.setTypeName( typeName );
        request.setQueryType( type);
        GetFeatureResponse response = wfsClient.issueRequest(request);
        GetFeatureParser parser = response.getSimpleFeatures( new GeometryFactory());

        List<SimpleFeature> result = new ArrayList<>();
        boolean keep_going = true;
        while (keep_going) {
            SimpleFeature feature = parser.parse();
            keep_going = !(feature ==null);
            if (keep_going)
                result.add(feature);
        }
        return result;
    }

    public String getRequestText(WFSRequest request) throws IOException {
        InputStream inputStream = request.getStrategy().getPostContents(request);
        String requestXMLBody =  IOUtils.toString(inputStream, "UTF-8");
        inputStream.close();
        return requestXMLBody;
    }

    TransactionRequest transactionRequest;
    public void startTransaction(String tag) {
        transactionRequest = wfsClient.createTransaction();
        transactionRequest.setHandle(tag);
    }

    public TransactionResponse sendTransaction() throws IOException {
        TransactionResponse transactionResponse = wfsClient.issueTransaction(transactionRequest);
        return transactionResponse;
    }

    public void insert(List<SimpleFeature> features) {
        TransactionRequest.Insert insert = transactionRequest.createInsert(this.typeName);
        for(SimpleFeature feature : features) {
            insert.add(feature);
        }
        transactionRequest.add(insert);
    }

    public void delete(org.opengis.filter.Filter f) {
        TransactionRequest.Delete delete = transactionRequest.createDelete(this.typeName, f);
        transactionRequest.add(delete);
    }

    public void update(org.opengis.filter.Filter f,List<QName> propertyNames, List<Object> values) {
        TransactionRequest.Update update = transactionRequest.createUpdate(this.typeName, propertyNames, values,f);
        transactionRequest.add(update);
    }

}
