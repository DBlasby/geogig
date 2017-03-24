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

import org.geotools.data.DataStore;
import org.geotools.data.FeatureReader;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.filter.text.cql2.CQL;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;



public class Verifications {

    DataStore memoryDS;
    WFSSimpleClient wfs;
    SimpleFeatureType type;
    RawGeoGIG rawGeoGIG;

    public Verifications(DataStore memoryDS, WFSSimpleClient wfs, SimpleFeatureType type,RawGeoGIG rawGeoGIG) {
        this.memoryDS = memoryDS;
        this.wfs = wfs;
        this.type = type;
        this.rawGeoGIG = rawGeoGIG;
    }

    //fids should match the two datastores
    public void verifyFIDs() throws Exception {
        Query q = new Query(type.getTypeName(), Filter.INCLUDE, new String[] {"guid" }); //need to request at least one property (or gives all)

        List<SimpleFeature> memFidsFeatures =  executeQuery(q,memoryDS);
        List<SimpleFeature> wfsFidsFeatures =  executeQuery(q,wfs);
        verifyFids(memFidsFeatures,wfsFidsFeatures);
    }

    public void verifyFids(Collection<SimpleFeature> featsMemory, Collection<SimpleFeature> featsWfs) throws Exception {
        List<String> memFids = featsMemory.stream().map(f->f.getID()).sorted(String::compareTo).collect(Collectors.toList());
        List<String> wfsFids = featsWfs.stream().map(f->f.getID()).sorted(String::compareTo).collect(Collectors.toList());
        if (!memFids.equals( wfsFids ) ) {
            List<String> onlyInMem = new ArrayList<>(memFids);
            onlyInMem.removeAll(wfsFids);

            List<String> onlyInWfs = new ArrayList<>(wfsFids);
            onlyInWfs.removeAll(memFids);

            String message = "FIDs only in memorydatastore="+ String.join(",",onlyInMem) +"\n";
            message += "FIDs only in WFS="+ String.join(",",onlyInWfs);
            throw new Exception("FIDS are not the same - \n"+message);
        }
    }

    public void verifyQuery(String cqlFilter) throws Exception {
        Filter filter = CQL.toFilter(cqlFilter);
        Query q = new Query(type.getTypeName(), filter);

        Map<String,SimpleFeature> memFeatures =  executeQueryMap(q,memoryDS);
        Map<String,SimpleFeature> wfsFeatures =  executeQueryMap(q,wfs);

        verifyFids(memFeatures.values(), wfsFeatures.values());
        for(String fid : memFeatures.keySet() ){
            SimpleFeature fmem = memFeatures.get(fid);
            SimpleFeature fwfs = wfsFeatures.get(fid);

            for (int t=0;t<fmem.getFeatureType().getAttributeCount();t++) {
                Object attMem = fmem.getAttribute(t);
                Object attWfs = fwfs.getAttribute(t);
                if (!attMem.equals(attWfs)) {
                    throw new Exception("Query results in different items:\nfid: "+ fid+", attribute: "+fmem.getFeatureType().getDescriptor(t).getLocalName()+"\n"+
                     "mem: "+attMem+"\n"+
                    "wfs:"+attWfs);

                }
            }

        }
    }

    public List<SimpleFeature> executeQuery(Query query,WFSSimpleClient wfs) throws IOException {
        return wfs.query(query);
    }


        public List<SimpleFeature> executeQuery(Query query, DataStore ds) throws IOException {
        List<SimpleFeature> result = new ArrayList<>();
        try (FeatureReader fr = ds.getFeatureReader(query, Transaction.AUTO_COMMIT) ) {
            while (fr.hasNext()) {
                result.add((SimpleFeature) fr.next());
            }
        }
        return result;
    }

    public Map<String,SimpleFeature> executeQueryMap(Query query, DataStore ds) throws IOException {
        Map<String,SimpleFeature> result = new HashMap<>();
        try (FeatureReader fr = ds.getFeatureReader(query, Transaction.AUTO_COMMIT) ) {
            while (fr.hasNext()) {
                SimpleFeature  f= (SimpleFeature) fr.next();
                result.put(f.getID(), f);
            }
        }
        return result;
    }
    public Map<String,SimpleFeature> executeQueryMap(Query query,WFSSimpleClient wfs) throws IOException {
        Map<String,SimpleFeature> result = new HashMap<>();
        List<SimpleFeature> features = executeQuery( query, wfs);
        Iterator<SimpleFeature> fr = features.iterator();
             while (fr.hasNext()) {
                SimpleFeature  f= (SimpleFeature) fr.next();
                result.put(f.getID(), f);
            }
        return result;
    }

}
