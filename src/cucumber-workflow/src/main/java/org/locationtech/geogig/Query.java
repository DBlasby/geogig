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
import org.geotools.data.Transaction;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.filter.text.cql2.CQL;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


//query WFS/Memory datastore and provide some verification (comparison)
// of the results.
public class Query {
    MemoryDataStore memoryDataStore;
    WFSSimpleClient wfsclient;
    SimpleFeatureType type;

    List<SimpleFeature> features_wfs;
    List<SimpleFeature> features_memory;
    Map<String,SimpleFeature> features_wfs_map;
    Map<String,SimpleFeature> features_memory_map;

    Filter filter;
    org.geotools.data.Query query;

    public Query(DataStore memoryDataStore, WFSSimpleClient wfsclient, SimpleFeatureType type, String cqlFilter) throws Exception {
        this.memoryDataStore = (MemoryDataStore) memoryDataStore;
        this.wfsclient = wfsclient;
        this.type = type;

        filter = CQL.toFilter(cqlFilter);
        query = new org.geotools.data.Query(type.getTypeName(), filter);

        if (memoryDataStore != null) {
            features_memory = executeQuery(query, memoryDataStore);
            features_memory_map = mapfeatures(features_memory);
        }

        if (wfsclient != null) {
            features_wfs = executeQuery(query, wfsclient);
            features_wfs_map = mapfeatures(features_wfs);
        }
    }

    private Map<String,SimpleFeature> mapfeatures(List<SimpleFeature> features) {
        Map<String,SimpleFeature> result = new HashMap<>(features.size());
        for (SimpleFeature f : features)
            result.put(f.getID(),f);
        return result;
    }

    public List<SimpleFeature> executeQuery(org.geotools.data.Query query, WFSSimpleClient wfsclient) throws IOException {
        return wfsclient.query(query);
    }

        public List<SimpleFeature> executeQuery(org.geotools.data.Query query, DataStore ds) throws IOException {
        List<SimpleFeature> result = new ArrayList<>();
        try (FeatureReader fr = ds.getFeatureReader(query, Transaction.AUTO_COMMIT) ) {
            while (fr.hasNext()) {
                result.add((SimpleFeature) fr.next());
            }
        }
        return result;
    }

    public void assertNFeatures(int nfeatures) throws Exception {
        if ( (memoryDataStore != null)  && (features_memory.size() != nfeatures) ){
           throw new Exception("memoryDatastore has "+features_memory.size()+" features in query -- not "+nfeatures) ;
        }

        if (wfsclient != null && (features_wfs.size() != nfeatures) ){
            throw new Exception("WFS has "+features_wfs.size()+" features in query -- not "+nfeatures) ;

        }
    }


    public void assertEquivalent() throws Exception {
        assertFids();

        for(String fid : features_memory_map.keySet() ){
            SimpleFeature fmem = features_memory_map.get(fid);
            SimpleFeature fwfs = features_wfs_map.get(fid);

            for (int t=0;t<fmem.getFeatureType().getAttributeCount();t++) {
                Object attMem = fmem.getAttribute(t);
                Object attWfs = fwfs.getAttribute(t);

                //handle both null = okay
                if (attMem == attWfs)
                    continue;

                // not both null!
                if ( (attMem==null) || (attMem==null) ) {
                    throw new Exception("Query results in different items (null case):\nfid: "+ fid+", attribute: "+fmem.getFeatureType().getDescriptor(t).getLocalName()+"\n"+
                            "mem: "+attMem+"\n"+
                            "wfs:"+attWfs);
                }

                if (!attMem.equals(attWfs)) {
                    throw new Exception("Query results in different items:\nfid: "+ fid+", attribute: "+fmem.getFeatureType().getDescriptor(t).getLocalName()+"\n"+
                            "mem: "+attMem+"\n"+
                            "wfs:"+attWfs);

                }
            }

        }
    }

    public void assertFids() throws Exception {
        List<String> memFids = features_memory.stream().map(f->f.getID()).sorted(String::compareTo).collect(Collectors.toList());
        List<String> wfsFids = features_wfs.stream().map(f->f.getID()).sorted(String::compareTo).collect(Collectors.toList());
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
}
