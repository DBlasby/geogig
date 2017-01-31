/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data.reader;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureImpl;
import org.geotools.filter.identity.FeatureIdImpl;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.repository.AutoCloseableIterator;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.NodeRef;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.filter.identity.FeatureId;
import org.opengis.geometry.BoundingBox;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;

class MaterializedIndexFeatureIterator implements AutoCloseableIterator<SimpleFeature> {

    private final AutoCloseableIterator<NodeRef> nodes;

    private final SimpleFeatureBuilder featureBuilder;

    private final GeometryFactory geometryFactory;

    private MaterializedIndexFeatureIterator(final SimpleFeatureBuilder builder,
            AutoCloseableIterator<NodeRef> nodes, GeometryFactory geometryFactory) {
        this.featureBuilder = builder;
        this.nodes = nodes;
        this.geometryFactory = geometryFactory;
    }

    public static MaterializedIndexFeatureIterator create(SimpleFeatureType outputSchema,
            AutoCloseableIterator<NodeRef> nodes, GeometryFactory geometryFactory) {

        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(outputSchema);
        return new MaterializedIndexFeatureIterator(builder, nodes, geometryFactory);
    }

    @Override
    public void close() {
        nodes.close();
    }

    @Override
    public boolean hasNext() {
        return nodes.hasNext();
    }

    @Override
    public SimpleFeature next() {
        if (!nodes.hasNext()) {
            throw new NoSuchElementException();
        }
        NodeRef node = nodes.next();
        SimpleFeature feature = adapt(node);
        return feature;
    }

    private SimpleFeature adapt(NodeRef node) {
        final SimpleFeatureType featureType = featureBuilder.getFeatureType();
        final List<AttributeDescriptor> attributeDescriptors = featureType
                .getAttributeDescriptors();
        if (attributeDescriptors.isEmpty()) {

            return BoundedSimpleFeature.empty(featureType, node.getNode());

        } else {
            final Map<String, Object> extraData = node.getNode().getExtraData();
            checkNotNull(extraData);

            final Map<String, Object> materializedAttributes;
            materializedAttributes = IndexInfo.getMaterializedAttributes(node.getNode());
            checkNotNull(materializedAttributes);

            featureBuilder.reset();
            for (int i = 0; i < attributeDescriptors.size(); i++) {
                AttributeDescriptor descriptor = attributeDescriptors.get(i);
                String localName = descriptor.getLocalName();
                Object value = materializedAttributes.get(localName);
                if (value instanceof Geometry) {
                    value = geometryFactory.createGeometry((Geometry) value);
                }
                featureBuilder.set(localName, value);
            }
        }
        String id = node.name();
        SimpleFeature feature = featureBuilder.buildFeature(id);
        return feature;
    }

    private static class BoundedSimpleFeature extends SimpleFeatureImpl {

        private ReferencedEnvelope bounds;

        BoundedSimpleFeature(List<Object> v, SimpleFeatureType t, FeatureId fid,
                ReferencedEnvelope bounds) {
            super(v, t, fid);
            this.bounds = bounds;
        }

        @Override
        public BoundingBox getBounds() {
            return bounds;
        }

        static BoundedSimpleFeature empty(SimpleFeatureType type, Node node) {

            FeatureId fid = new FeatureIdImpl(node.getName());
            ReferencedEnvelope env = new ReferencedEnvelope(type.getCoordinateReferenceSystem());
            node.expand(env);
            return new BoundedSimpleFeature(Collections.emptyList(), type, fid, env);
        }
    }
}
