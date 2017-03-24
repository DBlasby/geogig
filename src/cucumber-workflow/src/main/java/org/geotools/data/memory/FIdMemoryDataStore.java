/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.geotools.data.memory;

import org.geotools.data.FeatureWriter;
import org.geotools.data.Query;
import org.geotools.data.QueryCapabilities;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.factory.Hints;
import org.geotools.feature.simple.SimpleFeatureImpl;
import org.geotools.filter.identity.FeatureIdImpl;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.identity.FeatureId;

import java.io.IOException;



//memory datastore - but allows you to set fids
public class FIdMemoryDataStore extends MemoryDataStore {

    public FIdMemoryDataStore(SimpleFeatureType featureType) {
        super(featureType);
    }

    // advertise this supports setting FID for features
    @Override
    protected ContentFeatureSource createFeatureSource(ContentEntry entry, Query query) {
        return new MemoryFeatureStore(entry, query) {
            @Override
            protected QueryCapabilities buildQueryCapabilities() {
                return new QueryCapabilities() {
                    @Override
                    public boolean isUseProvidedFIDSupported() {
                        return true;
                    }
                };
            }

            //need to actually use the provided FID
            @Override
            protected FeatureWriter<SimpleFeatureType, SimpleFeature> getWriterInternal(
                    Query query, int flags) throws IOException {
                return new MemoryFeatureWriter(getState(), query) {
                    @Override
                    public void write() throws IOException {
                        if (Boolean.TRUE.equals(current.getUserData().get(Hints.USE_PROVIDED_FID))) {
                            if (current.getUserData().containsKey(Hints.PROVIDED_FID)) {
                                String fid = (String) current.getUserData().get(Hints.PROVIDED_FID);
                                FeatureId id = new FeatureIdImpl(fid);
                                current = new SimpleFeatureImpl(current.getAttributes(), current.getFeatureType(), id);
                            }
                        }
                        super.write();
                    }
                };
            }
        };
    }
}
