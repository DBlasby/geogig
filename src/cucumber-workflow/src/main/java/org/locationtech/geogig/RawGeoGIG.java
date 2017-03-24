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

import com.google.common.base.Optional;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.cli.CLIContextBuilder;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.porcelain.index.Index;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.PropertyDescriptor;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Optional.absent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


//provides access to raw geogig functionality
//   some functions directly assess the very low-level
//   features of geogig (i.e. read tree/features)
public class RawGeoGIG {


    String layername;
    URI repositoryUri;

    static {
        GlobalContextBuilder.builder(new CLIContextBuilder());
    }

    public RawGeoGIG(String repositoryUri,String layername) throws  Exception {
        this.repositoryUri = new URI ( repositoryUri) ;
        this.layername = layername;

    }

    public void assertIndexExists() throws RepositoryConnectionException {
        final RepositoryResolver initializer= RepositoryResolver.lookup( repositoryUri );
        Repository repository = initializer.open(repositoryUri);
        Index index = getIndex(repository);
        repository.close();
        assertFalse(index.indexTreeId().equals(ObjectId.NULL));
    }

    RevFeatureType getType(Repository repository) {
        Context repo = repository.context();

        NodeRef cannonicalRef = resolveCanonicalTree(repo,"master", layername).get();
        return repo.objectDatabase().getFeatureType(cannonicalRef.getMetadataId()) ;
    }


    Index getIndex(Repository repository) throws RepositoryConnectionException {
        Context repo = repository.context();
        NodeRef cannonicalRef = resolveCanonicalTree(repo,"master", layername).get();
        SimpleFeatureType schema = (SimpleFeatureType) repo.objectDatabase().getFeatureType(cannonicalRef.getMetadataId()).type();
        Optional<Index>[] indexes = resolveIndex(repo,cannonicalRef.getObjectId(), cannonicalRef.getObjectId(),layername,schema.getGeometryDescriptor().getLocalName() );
        Index index = indexes[0].get();
        return index;
    }

    private Optional<NodeRef> resolveCanonicalTree(Context repo,@Nullable String head, String treeName) {
        Optional<NodeRef> treeRef = Optional.absent();
        if (head != null) {
            Optional<ObjectId> rootTree = repo.command(ResolveTreeish.class).setTreeish(head)
                    .call();
            if (rootTree.isPresent()) {
                RevTree tree = repo.objectDatabase().getTree(rootTree.get());
                treeRef = repo.command(FindTreeChild.class).setParent(tree).setChildPath(treeName)
                        .call();
            }
        }
        return treeRef;
    }

    private static final Optional<Index>[] NO_INDEX = new Optional[] { absent(), absent() };

    @SuppressWarnings("unchecked")
    private Optional<Index>[] resolveIndex(Context repo,final ObjectId oldCanonical, final ObjectId newCanonical,
                                           final String treeName, final String attributeName) {
        if (Boolean.getBoolean("geogig.ignoreindex")) {
            // TODO: remove debugging aid
            System.err.printf(
                    "Ignoring index lookup for %s as indicated by -Dgeogig.ignoreindex=true\n",
                    treeName);
            return NO_INDEX;
        }

        Optional<Index>[] indexes = NO_INDEX;
        final IndexDatabase indexDatabase = repo.indexDatabase();
        Optional<IndexInfo> indexInfo = indexDatabase.getIndexInfo(treeName, attributeName);
        if (indexInfo.isPresent()) {
            IndexInfo info = indexInfo.get();
            Optional<Index> oldIndex = resolveIndex(oldCanonical, info, indexDatabase);
            if (oldIndex.isPresent()) {
                Optional<Index> newIndex = resolveIndex(newCanonical, info, indexDatabase);
                if (newIndex.isPresent()) {
                    indexes = new Optional[2];
                    indexes[0] = oldIndex;
                    indexes[1] = newIndex;
                }
            }
        }
        return indexes;
    }

    private Optional<Index> resolveIndex(ObjectId canonicalTreeId, IndexInfo indexInfo,
                                         IndexDatabase indexDatabase) {

        Index index = new Index(indexInfo, RevTree.EMPTY_TREE_ID, indexDatabase);
        if (!RevTree.EMPTY_TREE_ID.equals(canonicalTreeId)) {
            Optional<ObjectId> indexedTree = indexDatabase.resolveIndexedTree(indexInfo,
                    canonicalTreeId);
            if (indexedTree.isPresent()) {
                index = new Index(indexInfo, indexedTree.get(), indexDatabase);
            }
        }
        return Optional.fromNullable(index);
    }

    public void execute(String cmd) throws Exception {
        GlobalContextBuilder.builder(new CLIContextBuilder());
        GeogigCLI cli = new GeogigCLI(new Console());
        cli.setRepositoryURI(repositoryUri.toString());
        String[] params = cmd.split(" ");
        int result = cli.execute(params);
        if (result != 0)
            throw new Exception("something went wrong executing - "+cmd+" see log for details");
    }

    //Walk the tree
    // for every boundedfeature, retreive the underlying feature
    //     and verify that the feature's bounds is contained by the boundedfeature's bounds
    // NOTE: SLOW if verifying features
    public void verifyTreeIndex(boolean verifyFeatures) throws Exception {
        final RepositoryResolver initializer= RepositoryResolver.lookup( repositoryUri );
        Repository repository = initializer.open(repositoryUri);
        Index index = getIndex(repository);

        NodeRef cannonicalRef = resolveCanonicalTree(repository.context(),"master", layername).get();
        long nFeaturesCannonical = ((RevTree)repository.objectDatabase().get(cannonicalRef.getObjectId())).size();
        long nFeaturesIndex = ((RevTree)repository.indexDatabase().get(index.indexTreeId())).size();
        assertEquals("nFeaturesCannonical != nFeaturesIndex",nFeaturesCannonical,nFeaturesIndex,0);


        RevFeatureType type = getType(repository);

        BoundsVerifyingWalker walker = new BoundsVerifyingWalker(repository.indexDatabase(),index.indexTreeId(),type);
        ObjectStore featureStore = verifyFeatures ? repository.objectDatabase() : null;
        walker.walk(featureStore);
        repository.close();

        if (walker.nFeatures != nFeaturesCannonical)
            throw new Exception("Index advertised "+nFeaturesCannonical+" features, but could only find "+walker.nFeatures);
    }

    //Walk the tree
    // for every boundedfeature, retreive the underlying feature
    //     and verify that the feature's bounds is contained by the boundedfeature's bounds
    // NOTE: SLOW if verifying features
    public void verifyTreeCanonical(boolean verifyFeatures) throws Exception {
        final RepositoryResolver initializer= RepositoryResolver.lookup( repositoryUri );
        Repository repository = initializer.open(repositoryUri);

        NodeRef cannonicalRef = resolveCanonicalTree(repository.context(),"master", layername).get();
        long nFeaturesCannonical = ((RevTree)repository.objectDatabase().get(cannonicalRef.getObjectId())).size();

        RevFeatureType type = getType(repository);

        BoundsVerifyingWalker walker = new BoundsVerifyingWalker(repository.objectDatabase(),cannonicalRef.getObjectId(),type);
        ObjectStore featureStore = verifyFeatures ? repository.objectDatabase() : null;
        walker.walk(featureStore);
        repository.close();

        if (walker.nFeatures != nFeaturesCannonical)
            throw new Exception("Cannonical advertised "+nFeaturesCannonical+" features, but could only find "+walker.nFeatures);
    }

    public void verifyExtraData() throws Exception {
        final RepositoryResolver initializer= RepositoryResolver.lookup( repositoryUri );
        Repository repository = initializer.open(repositoryUri);


        Index index = getIndex(repository);
        RevFeatureType type = getType(repository);

        NodeRef cannonicalRef = resolveCanonicalTree(repository.context(),"master", layername).get();
        long nFeaturesCannonical = ((RevTree)repository.objectDatabase().get(cannonicalRef.getObjectId())).size();
        long nFeaturesIndex = ((RevTree)repository.indexDatabase().get(index.indexTreeId())).size();
        assertEquals("nFeaturesCannonical != nFeaturesIndex",nFeaturesCannonical,nFeaturesIndex,0);

        ExtraAttributeVerifyingConsumer consumer = new ExtraAttributeVerifyingConsumer(repository.objectDatabase(), type);


        PreOrderDiffWalk walker = new PreOrderDiffWalk(RevTree.EMPTY, index.indexTree(),  repository.objectDatabase(),repository.indexDatabase(),true);
        walker.walk(consumer);

        if (consumer.numberNodes != nFeaturesIndex)
            throw new Exception("Index advertised "+nFeaturesIndex+" features, but could only find "+consumer.numberNodes);
    }

    public class BoundsVerifyingWalker {
        ObjectStore db;
        ObjectId rootNodeId;
        ObjectStore featureDataStore;

        int geomindx = 0; // index in the Feature of the geometry attribute
        Map<ObjectId, BoundsCheck> computedItems = new HashMap<>(); // already computed items
        RevFeatureType type;

        int nFeatures = 0;

        public BoundsVerifyingWalker(ObjectStore db, ObjectId rootNodeId, RevFeatureType type) {
            this.db = db;
            this.rootNodeId = rootNodeId;
            this.type = type;
            int index = 0;
            for(PropertyDescriptor prop : type.descriptors() ){
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
            walk (rootNodeId );
            verify();
        }


        //looks at all the computed vs stored bounds and make sure they are the same
        private void verify() throws Exception {
            for(Map.Entry<ObjectId, BoundsCheck> itemEntry : computedItems.entrySet()) {
                BoundsCheck item = itemEntry.getValue();
                if ( (item.getComputedEnvelope() != null ) && (item.getStoredEnvelope() != null) ) {
                    if (!item.getComputedEnvelope().equals(item.getStoredEnvelope()))
                        throw new Exception(itemEntry.getKey() + " - has incorrect bounds");
                }
            }
        }


        //simple walker -
        // if its a leaf node, just calculate all the feature bounds inside it
        // if its a bucket, then calculate the bounds of the bucket (cf computeBounds(bucket))
        void walk(ObjectId nodeId) throws Exception {
            RevTree node = (RevTree) db.get(nodeId);
            if (node.features().size() >0) {
               computeFeatureBounds(node);
            }

            if (node.buckets().size() >0) {
                for (Map.Entry<Integer, Bucket> bucket: node.buckets().entrySet()) {
                    Bucket b = bucket.getValue();
                    BoundsCheck item = new BoundsCheck(b.getObjectId());
                    item.setStoredEnvelope ( b.bounds().get() );
                    item.setComputedEnvelope ( computeBounds(b) );
                    item.isBucket  = true;
                    computedItems.put(b.getObjectId(), item);
                }
            }

        }

        // this is recursive
        //
        private Envelope computeBounds(Bucket bucket) throws Exception {

            //short-cut if already computed!
            if (computedItems.containsKey(bucket.getObjectId())) {
                BoundsCheck item = computedItems.get(bucket.getObjectId());
                if (item.getComputedEnvelope()  != null)
                    return item.getComputedEnvelope();
            }

            RevTree node = (RevTree) db.get(bucket.getObjectId());
            if (node.features().size() > 0)
                return computeFeatureBounds(node);

            Envelope computedBounds = new Envelope();

            //for every child bucket, compute bounds and store the result
            // the main node bounds is the sum ("expandToInclude") of all the
            // children.
            for (Map.Entry<Integer, Bucket> b2: node.buckets().entrySet()) {
                Bucket b = b2.getValue();
                BoundsCheck item = new BoundsCheck(b.getObjectId());
                item.setStoredEnvelope ( b.bounds().get() );
                item.setComputedEnvelope( computeBounds(b) );
                item.isBucket  = true;
                computedItems.put(b.getObjectId(), item);
                computedBounds.expandToInclude( item.getComputedEnvelope());
            }
            //store computed for the main node
            BoundsCheck item = new BoundsCheck(bucket.getObjectId());
            item.setStoredEnvelope (bucket.bounds().get() );
            item.setComputedEnvelope ( computedBounds );
            item.isBucket  = true;
            computedItems.put(bucket.getObjectId(), item);
            return computedBounds;
        }

        //given a feature node, compute its bounds and remember it
        private Envelope computeFeatureBounds(RevTree node) throws Exception {
            ObjectId id = node.getId();
            Envelope computedBounds = new Envelope();
            for( Node feature: node.features()) {
                Envelope featEnv = feature.bounds().get() ;
                computedBounds.expandToInclude(featEnv);
                nFeatures++;
                if (this.featureDataStore != null) {
                    verifyFeature(feature.getObjectId(), featEnv);
                }
            }
            BoundsCheck item = new BoundsCheck(id);
            item.setComputedEnvelope ( computedBounds);
            item.isLeaf = true;
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
            RevFeature feature = (RevFeature) featureDataStore.get(objectId) ;
            Geometry g = (Geometry) feature.get(geomindx).get();
            Envelope geomEnv = g.getEnvelopeInternal();

            assertTrue(featEnv.contains(geomEnv));
        }
    }


    //store information about the node
    class BoundsCheck {
        private Envelope _computedEnvelope;
        private Envelope _storedEnvelope;

        public boolean isLeaf;
        public boolean isBucket;

        ObjectId id;

        public BoundsCheck(ObjectId id) {
            this.id = id;
        }

        public void setComputedEnvelope(Envelope e) throws Exception {
            this._computedEnvelope = e;
            validate();
        }

        public Envelope getComputedEnvelope () {
            return _computedEnvelope;
        }

        public Envelope getStoredEnvelope () {
            return _storedEnvelope;
        }

        public void setStoredEnvelope(Envelope e) throws Exception {
            this._storedEnvelope = e;
            validate();
        }

        private void validate() throws Exception {
            if ( (_storedEnvelope != null) && (_computedEnvelope != null) ){
                if (!_computedEnvelope.equals(_storedEnvelope))
                    throw new Exception(id+" - has incorrect bounds");
            }
        }

    }








    public class ExtraAttributeVerifyingConsumer extends PreOrderDiffWalk.AbstractConsumer {

        ObjectDatabase db;
        int numberNodes = 0;
        RevFeatureType type;
        int geomindx;

        public ExtraAttributeVerifyingConsumer(ObjectDatabase db, RevFeatureType type) {
            this.db = db;
            this.type = type;
            int index = 0;
            for(PropertyDescriptor prop : type.descriptors() ){
                if (Geometry.class.isAssignableFrom(prop.getType().getBinding())) {
                    geomindx = index;
                }
                index++;
            }
        }

        @Override
        public synchronized boolean feature(@Nullable final NodeRef left,
                                        @Nullable final NodeRef right) {
           Envelope  nodeEnv = right.getNode().bounds().get();
           RevFeature feature = (RevFeature) db.get(right.getObjectId()) ;

           //bounds
           Geometry g = (Geometry) feature.get(geomindx).get();
           Envelope geomEnv = g.getEnvelopeInternal();
           assertTrue(nodeEnv.contains(geomEnv));

           //extra attributes
            Map<String,Object> extraAttributes = (Map<String, Object>) right.getNode().getExtraData().get(("@attributes"));
            for (Map.Entry<String, Object> entry : extraAttributes.entrySet()) {
                Object extraValue =entry.getValue();
                 Object actualValue = feature.get(findAttributeNumber(entry.getKey())).get();
                 if (!extraValue.equals(actualValue)) {
                     throw new RuntimeException("extra data - values don't match - "+entry.getValue() +"; "+extraValue +" != "+actualValue+" -- id="+feature.getId());
                 }
            }

            numberNodes++;
            return true;
        }

        public int findAttributeNumber(String name)   {
            int index = 0;
            for(PropertyDescriptor prop : type.descriptors() ){
                if (prop.getName().getLocalPart().equals(name)) {
                    return index;
                }
                index++;
            }
            throw new RuntimeException("cannot find attribute - "+name);
        }
    }
}
