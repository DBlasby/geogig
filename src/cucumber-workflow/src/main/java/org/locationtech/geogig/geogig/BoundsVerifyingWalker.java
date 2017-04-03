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

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.ObjectStore;
import org.opengis.feature.type.PropertyDescriptor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertTrue;


public class BoundsVerifyingWalker {
    ObjectStore db;
    ObjectId rootNodeId;
    ObjectStore featureDataStore;

    int geomindx = 0; // index in the Feature of the geometry attribute
    Map<ObjectId, TreeMetadata> computedItems = new HashMap<>(); // already computed items
    RevFeatureType type;

    int nFeatures = 0;

    public BoundsVerifyingWalker(ObjectStore db, ObjectId rootNodeId, RevFeatureType type) {
        this.db = db;
        this.rootNodeId = rootNodeId;
        this.type = type;
        int index = 0;
        for (PropertyDescriptor prop : type.descriptors()) {
            if (Geometry.class.isAssignableFrom(prop.getType().getBinding())) {
                geomindx = index;
            }
            index++;
        }
    }

    //featureDataStore = null --> do NOT verify that the bounded features are correct
    // (SLOW)
    public void walk(ObjectStore featureDataStore) throws Exception {
        this.featureDataStore = featureDataStore;
        walk(rootNodeId);
        verify();
    }


    //looks at all the computed vs stored bounds and make sure they are the same
    private void verify() throws Exception {
        for (Map.Entry<ObjectId, TreeMetadata> itemEntry : computedItems.entrySet()) {
            TreeMetadata item = itemEntry.getValue();
            item.validate();
        }
    }


    //simple walker -
    // if its a leaf node, just calculate all the feature bounds inside it
    // if its a bucket, then calculate the bounds of the bucket (cf computeBounds(bucket))
    void walk(ObjectId nodeId) throws Exception {
        RevTree node = (RevTree) db.get(nodeId);
        if (node.features().size() > 0) {
            computeFeatureBounds(node);
        }

        if (node.buckets().size() > 0) {
            for (Map.Entry<Integer, Bucket> bucket : node.buckets().entrySet()) {
                Bucket b = bucket.getValue();
                TreeMetadata item = new TreeMetadata(b );
                item.setComputedEnvelope(computeBounds(b));
                computedItems.put(b.getObjectId(), item);
            }
        }

    }

    // this is recursive
    //
    private Envelope computeBounds(Bucket bucket) throws Exception {

        //short-cut if already computed!
        if (computedItems.containsKey(bucket.getObjectId())) {
            TreeMetadata item = computedItems.get(bucket.getObjectId());
            if (item.getComputedEnvelope() != null)
                return item.getComputedEnvelope();
        }

        RevTree node = (RevTree) db.get(bucket.getObjectId());
        if (node.features().size() > 0)
            return computeFeatureBounds(node);

        Envelope computedBounds = new Envelope();

        //for every child bucket, compute bounds and store the result
        // the main node bounds is the sum ("expandToInclude") of all the
        // children.
        for (Map.Entry<Integer, Bucket> b2 : node.buckets().entrySet()) {
            Bucket b = b2.getValue();
            TreeMetadata item = new TreeMetadata(b);
            item.setComputedEnvelope(computeBounds(b));
            computedItems.put(b.getObjectId(), item);
            computedBounds.expandToInclude(item.getComputedEnvelope());
        }
        //store computed for the main node
        TreeMetadata item = new TreeMetadata(bucket);
        item.setComputedEnvelope(computedBounds);
        computedItems.put(bucket.getObjectId(), item);
        return computedBounds;
    }

    //given a feature node, compute its bounds and remember it
    private Envelope computeFeatureBounds(RevTree node) throws Exception {
        ObjectId id = node.getId();
        Envelope computedBounds = new Envelope();
        for (Node feature : node.features()) {
            Envelope featEnv = feature.bounds().or(new Envelope());
            computedBounds.expandToInclude(featEnv);
            nFeatures++;
            if (this.featureDataStore != null) {
                verifyFeature(feature.getObjectId(), featEnv);
            }
        }
        TreeMetadata item = new TreeMetadata(node);
        item.setComputedEnvelope(computedBounds);
        //no stored bounds
        computedItems.put(id, item);
        return computedBounds;
    }

    //for each of the boundedFeaturenodes, get the corresponding
    // RevFeature (actual data) -- SLOW
    // and compute the bounds of the actual geometry and compare to the
    // BoundedFeatureNode bounds.
    // NOTE: they will be different.  However, the BFN's bounds should
    //       be larger than (contain) the actual geometry.
    private void verifyFeature(ObjectId objectId, Envelope featEnv) {
        RevFeature feature = (RevFeature) featureDataStore.get(objectId);
        com.google.common.base.Optional<Object>  gg =  feature.get(geomindx);
        if (!gg.isPresent())
            return;
        Geometry g = (Geometry) gg.get();
        Envelope geomEnv = g.getEnvelopeInternal();

        assertTrue(featEnv.contains(geomEnv));
    }
}
