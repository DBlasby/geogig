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
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureStore;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.data.wfs.internal.TransactionResponse;
import org.geotools.factory.Hints;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.simple.SimpleFeatureImpl;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.util.Converters;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.identity.FeatureId;

import javax.xml.namespace.QName;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

//allows simple Transaction-based access to;
//  WFS-T (via WFSSimpleClient)
//   MemoryDataStore
public class DataStoreActions {

    MemoryDataStore memoryDataStore;
    WFSSimpleClient wfsclient;
    SimpleFeatureType type;

    //fids from WFS transaction response
    // will use these for actual fids in MemoryDataStore
    //  so features added to WFS will have the same fids in the MemoryDatastore
    Queue<FeatureId>  insertedFeatureIds = new ArrayDeque();

    private List<Action> actions = new ArrayList();

    public DataStoreActions(DataStore memoryDataStore, WFSSimpleClient wfsclient, SimpleFeatureType type) {
        this.memoryDataStore = (MemoryDataStore) memoryDataStore;
        this.wfsclient = wfsclient;
        this.type = type;
    }

    public void add(Action a) {
        actions.add(a);
    }


    public void execute() throws  Exception {
        insertedFeatureIds.clear();
        if (wfsclient != null) {
            wfsclient.startTransaction("DatastoreActions transaction");

            for (Action action : actions) {
                action.execute(wfsclient);
            }
            TransactionResponse response = wfsclient.sendTransaction();
            processInserts(response);
        }

        if (memoryDataStore != null) {
            String tname = memoryDataStore.getTypeNames()[0];
            FeatureStore store = (FeatureStore) memoryDataStore.getFeatureSource(tname);
            Transaction t = new DefaultTransaction("memoryDataStore tx");
            store.setTransaction(t);
            for (Action action : actions) {
                action.execute(store);
            }
            t.commit();
            t.close();
        }
    }

    private void processInserts(TransactionResponse response) {
       this.insertedFeatureIds.addAll( response.getInsertedFids() );
    }


    public interface  Action {
          void execute(FeatureStore store) throws Exception;
        void execute(WFSSimpleClient wfsClient) throws Exception;

    }

    public static class InsertAction implements Action {
        ArrayList<SimpleFeature> features;
        DataStoreActions actions;

        public InsertAction(ArrayList<SimpleFeature> features, DataStoreActions actions) {
            this.features = features;
            this.actions = actions;
        }

        public void execute(FeatureStore store ) throws Exception {

            ArrayList<SimpleFeature> features2 = new ArrayList<>(features.size());
            for (int t=0;t<features.size(); t++) {
                SimpleFeature f=features.get(t);
                FeatureId id = actions.insertedFeatureIds.remove();
                SimpleFeature f2 = new SimpleFeatureImpl(f.getAttributes(),f.getFeatureType(),id);
                f2.getUserData().put(Hints.USE_PROVIDED_FID, true);
                f2.getUserData().put(Hints.PROVIDED_FID, f2.getID());
                features2.add(f2);
            }
            FeatureCollection fc = new ListFeatureCollection(features.get(0).getFeatureType(), features2.toArray(new SimpleFeature[0]));
            store.addFeatures(fc);
        }

       public void execute(WFSSimpleClient wfsClient) throws Exception {
            wfsClient.insert(features);
       }

    }

    public static class UpdateAction  implements Action {

        Name attribute;
        Object newValue;
        Filter filter;
        SimpleFeatureType type;

        public UpdateAction(String attribute, String value, String cqlFilter, SimpleFeatureType type) throws Exception {
            this.type = type;
            AttributeDescriptor desc = type.getDescriptor(attribute);
            if (desc == null) {
                throw new Exception("property '"+attribute+"' on type doesn't exist.");
            }
            this.attribute = desc.getName();
            this.newValue =  Converters.convert(value, type.getDescriptor(attribute).getType().getBinding());
            filter = ECQL.toFilter(cqlFilter);
        }



        @Override
        public void execute(FeatureStore store) throws Exception {
            store.modifyFeatures(attribute,newValue,filter);
        }

        @Override
        public void execute(WFSSimpleClient wfsClient) throws Exception {
            List<QName> pnames = new ArrayList<>();
            pnames.add(new QName(attribute.getNamespaceURI(),attribute.getLocalPart()));
            List<Object> values = new ArrayList<>();
            values.add(newValue);
            wfsClient.update(filter,pnames,values);
        }
    }

    public static class DeleteAction implements Action  {

        Filter filter;

        public DeleteAction(String cqlFilter) throws CQLException {
            filter = ECQL.toFilter(cqlFilter);
         }


        @Override
        public void execute(FeatureStore store) throws Exception {
            store.removeFeatures(filter);
        }

        @Override
        public void execute(WFSSimpleClient wfsClient) throws Exception {
            wfsClient.delete(filter);
        }
    }
}
