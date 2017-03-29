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
import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.cli.CLIContextBuilder;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
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
import org.locationtech.geogig.storage.ObjectStore;
import org.opengis.feature.simple.SimpleFeatureType;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    public void assertIndexExists(List<String> extras) throws RepositoryConnectionException {
        final RepositoryResolver initializer= RepositoryResolver.lookup( repositoryUri );
        Repository repository = initializer.open(repositoryUri);
        Index index = getIndex(repository);
        repository.close();
        assertFalse(index.indexTreeId().equals(ObjectId.NULL));
        String[] indexMetadataAttributes = (String[]) index.info().getMetadata().get("@attributes");
        List<String> actualExtra = indexMetadataAttributes == null ? new ArrayList() :Arrays.asList(indexMetadataAttributes );
        Collections.sort(extras);
        Collections.sort(actualExtra);
        assertTrue( "index doesn't have correct extra attributes",extras.equals(actualExtra));
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

        ExtraAttributeVerifyingConsumer consumer = new ExtraAttributeVerifyingConsumer(index,repository.objectDatabase(), type);


        PreOrderDiffWalk walker = new PreOrderDiffWalk(RevTree.EMPTY, index.indexTree(),  repository.objectDatabase(),repository.indexDatabase(),true);
        walker.walk(consumer);

        if (consumer.numberNodes != nFeaturesIndex)
            throw new Exception("Index advertised "+nFeaturesIndex+" features, but could only find "+consumer.numberNodes);
    }

    public void verifyNames() throws Exception {
        final RepositoryResolver initializer= RepositoryResolver.lookup( repositoryUri );
        Repository repository = initializer.open(repositoryUri);

        NodeRef cannonicalRef = resolveCanonicalTree(repository.context(),"master", layername).get();
        RevTree cannonicalNode =  repository.objectDatabase().getTree(cannonicalRef.getObjectId());
        Index index = getIndex(repository);

        NameExtractingConsumer cannonicalConsumer = new NameExtractingConsumer();
        NameExtractingConsumer indexConsumer = new NameExtractingConsumer();

        PreOrderDiffWalk walker = new PreOrderDiffWalk(RevTree.EMPTY, cannonicalNode,  repository.objectDatabase(),repository.objectDatabase(),true);
        walker.walk(cannonicalConsumer);

        walker = new PreOrderDiffWalk(RevTree.EMPTY, index.indexTree(),  repository.objectDatabase(),repository.indexDatabase(),true);
        walker.walk(indexConsumer);

        indexConsumer.validate(cannonicalConsumer);
    }

}
