/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.geogig;

import com.google.common.base.Optional;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.porcelain.index.Index;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.opengis.feature.type.PropertyDescriptor;

import java.util.Map;

import static org.junit.Assert.assertTrue;


public class ExtraAttributeVerifyingConsumer extends PreOrderDiffWalk.AbstractConsumer {

    ObjectDatabase db;
    int numberNodes = 0;
    RevFeatureType type;
    int geomindx;
    Index index;
    String[] indexExtraAttributes;

    public ExtraAttributeVerifyingConsumer(Index index, ObjectDatabase db, RevFeatureType type) {
        this.db = db;
        this.type = type;
        this.index = index;
        indexExtraAttributes = (String[]) index.info().getMetadata().get("@attributes");
        int indx = 0;
        for (PropertyDescriptor prop : type.descriptors()) {
            if (Geometry.class.isAssignableFrom(prop.getType().getBinding())) {
                geomindx = indx;
            }
            indx++;
        }
    }

    @Override
    public synchronized boolean feature(@Nullable final NodeRef left,
                                        @Nullable final NodeRef right) {

        Optional<Envelope> nodeEnv = right.getNode().bounds() ;
        RevFeature feature = (RevFeature) db.get(right.getObjectId());

        //bounds
        com.google.common.base.Optional<Object>  gg =  feature.get(geomindx);
        if (gg.isPresent()) {
            Geometry g = (Geometry) gg.get();
            Envelope geomEnv = g.getEnvelopeInternal();
            assertTrue(nodeEnv.get().contains(geomEnv));
        }

        //extra attributes
        Map<String, Object> nodeExtraAttributes = (Map<String, Object>) right.getNode().getExtraData().get(("@attributes"));

        for (String extraAtt : indexExtraAttributes) {
            if (!nodeExtraAttributes.containsKey(extraAtt))
                throw new RuntimeException("expect to find attribute '"+extraAtt+"' in the node, but wasn't present");
        }

        for (Map.Entry<String, Object> entry : nodeExtraAttributes.entrySet()) {
            Object extraValue = entry.getValue();
            Object actualValue = feature.get(findAttributeNumber(entry.getKey())).get();
            if (!extraValue.equals(actualValue)) {
                throw new RuntimeException("extra data - values don't match - " + entry.getValue() + "; " + extraValue + " != " + actualValue + " -- id=" + feature.getId());
            }
        }

        numberNodes++;
        return true;
    }

    public int findAttributeNumber(String name) {
        int index = 0;
        for (PropertyDescriptor prop : type.descriptors()) {
            if (prop.getName().getLocalPart().equals(name)) {
                return index;
            }
            index++;
        }
        throw new RuntimeException("cannot find attribute - " + name);
    }
}
