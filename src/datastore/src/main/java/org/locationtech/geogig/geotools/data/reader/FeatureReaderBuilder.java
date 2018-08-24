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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.notNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureReader;
import org.geotools.data.Query;
import org.geotools.data.ReTypeFeatureReader;
import org.geotools.data.store.ContentDataStore;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.filter.spatial.ReprojectingFilterVisitor;
import org.geotools.filter.visitor.SimplifyingFilterVisitor;
import org.geotools.filter.visitor.SpatialFilterVisitor;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultEngineeringCRS;
import org.geotools.renderer.ScreenMap;
import org.locationtech.geogig.data.retrieve.BulkFeatureRetriever;
import org.locationtech.geogig.geotools.data.GeoGigDataStore.ChangeType;
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.CanonicalNodeOrder;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeOrdering;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.QuadTreeBuilder;
import org.locationtech.geogig.plumbing.DiffTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.porcelain.index.Index;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.Id;
import org.opengis.filter.identity.FeatureId;
import org.opengis.filter.identity.Identifier;
import org.opengis.filter.sort.SortBy;
import org.opengis.filter.spatial.BBOX;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * A builder object for a {@link FeatureReader} that fetches data from a geogig {@link RevTree}
 * representing a "layer".
 * <p>
 * 
 */
public class FeatureReaderBuilder {

    private static final GeometryFactory DEFAULT_GEOMETRY_FACTORY = new GeometryFactory(
            new PackedCoordinateSequenceFactory());

    // cache filter factory to avoid the overhead of repeated calls to
    // CommonFactoryFinder.getFilterFactory2
    private static final FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2();

    private final Context leftRepo, rightRepo;

    /**
     * The feature tree's geogig type
     */
    private final RevFeatureType nativeType;

    /**
     * The absolute GeoGig native type, coming from {@link #nativeType}
     */
    private final SimpleFeatureType nativeSchema;

    /**
     * The set of attribute names from {@link #nativeSchema}
     */
    private final Set<String> nativeSchemaAttributeNames;

    /**
     * The required full schema, might differ from {@link #nativeSchema} in the type name or the
     * list and/or order of properties, as requested by {@link #targetSchema(SimpleFeatureType)}.
     * This happens when the ContentDataStore is assigned a different
     * {@link ContentDataStore#setNamespaceURI(String) namespace} or the
     * {@link ContentFeatureSource} has been given a "definition query" on its constructor.
     */
    private SimpleFeatureType targetSchema;

    private @Nullable String oldHeadRef;

    private String headRef = Ref.HEAD;

    private Filter filter = Filter.INCLUDE;

    private @Nullable String[] outputSchemaPropertyNames = Query.ALL_NAMES;

    private @Nullable ScreenMap screenMap;

    private @Nullable SortBy[] sortBy;

    private @Nullable Integer limit;

    private @Nullable Integer offset;

    private ChangeType changeType = ChangeType.ADDED;

    private GeometryFactory geometryFactory = DEFAULT_GEOMETRY_FACTORY;

    private @Nullable Double simplificationDistance;

    private NodeRef typeRef;

    private boolean ignoreIndex;

    private boolean retypeIfNeeded = true;

    private boolean screenMapReplaceGeometryWithPx = true;

    FeatureReaderBuilder(Context leftRepo, Context rightRepo, RevFeatureType nativeType,
            NodeRef typeRef) {
        this.leftRepo = leftRepo;
        this.rightRepo = rightRepo;
        this.nativeType = nativeType;
        this.nativeSchema = (SimpleFeatureType) nativeType.type();
        this.typeRef = typeRef;
        this.nativeSchemaAttributeNames = Sets.newHashSet(
                Lists.transform(nativeSchema.getAttributeDescriptors(), (a) -> a.getLocalName()));
    }

    /**
     * Sets the {@code ignoreIndex} flag to true, indicating that a spatial index lookup will be
     * ignored and only the canonical tree will be used by the returned feature reader.
     * <p>
     * This is particularly useful if the reader is constructed out of a diff between two version of
     * a featuretype tree (e.g. both {@link #oldHeadRef(String)} and {@link #headRef(String)} have
     * been provided), in order for the {@link DiffTree} op used to create the feature stream to
     * accurately represent changes, since most of the time an index reports changes as two separate
     * events for the removal and addition of the feature instead of one single event for the
     * change.
     */
    public FeatureReaderBuilder ignoreIndex() {
        this.ignoreIndex = true;
        return this;
    }

    public static FeatureReaderBuilder builder(Context repo, RevFeatureType nativeType,
            NodeRef typeRef) {
        return builder(repo, repo, nativeType, typeRef);
    }

    public static FeatureReaderBuilder builder(Context leftRepo, Context rightRepo,
            RevFeatureType nativeType, NodeRef typeRef) {
        checkNotNull(leftRepo);
        checkNotNull(rightRepo);
        checkNotNull(nativeType);
        checkNotNull(typeRef);
        return new FeatureReaderBuilder(leftRepo, rightRepo, nativeType, typeRef);
    }

    public FeatureReaderBuilder oldHeadRef(@Nullable String oldHeadRef) {
        this.oldHeadRef = oldHeadRef;
        return this;
    }

    public FeatureReaderBuilder changeType(ChangeType changeType) {
        checkNotNull(changeType);
        this.changeType = changeType;
        return this;
    }

    public FeatureReaderBuilder headRef(String headRef) {
        checkNotNull(headRef);
        this.headRef = headRef;
        return this;
    }

    /**
     * @param propertyNames which property names to include as the output schema, {@code null} means
     *        all properties
     */
    public FeatureReaderBuilder propertyNames(@Nullable String... propertyNames) {
        this.outputSchemaPropertyNames = propertyNames;
        return this;
    }

    public FeatureReaderBuilder filter(Filter filter) {
        checkNotNull(filter);
        this.filter = filter;
        return this;
    }

    public FeatureReaderBuilder screenMap(@Nullable ScreenMap screenMap) {
        this.screenMap = screenMap;
        return this;
    }

    public FeatureReaderBuilder geometryFactory(@Nullable GeometryFactory geometryFactory) {
        this.geometryFactory = geometryFactory == null ? DEFAULT_GEOMETRY_FACTORY : geometryFactory;
        return this;
    }

    public FeatureReaderBuilder simplificationDistance(@Nullable Double simplifDistance) {
        this.simplificationDistance = simplifDistance;
        return this;
    }

    public FeatureReaderBuilder sortBy(@Nullable SortBy... sortBy) {
        this.sortBy = sortBy;
        return this;
    }

    public FeatureReaderBuilder offset(@Nullable Integer offset) {
        this.offset = offset;
        return this;
    }

    public FeatureReaderBuilder limit(@Nullable Integer limit) {
        this.limit = limit;
        return this;
    }

    public static class WalkInfo {
        public SimpleFeatureType fullSchema;

        // query filter in native CRS
        public Filter nativeFilter;

        public Filter preFilter;

        public Filter postFilter;

        @Nullable
        Set<String> requiredProperties;

        // properties present in the RevTree nodes' extra data
        public Set<String> materializedIndexProperties;

        // whether the RevTree nodes contain all required properties (hence no need to fetch
        // RevFeatures from the database)
        public boolean indexContainsAllRequiredProperties;

        // whether the filter is fully supported by the NodeRef filtering (hence no need for
        // pos-processing filtering). This is the case if the filter is a simple BBOX, Id, or
        // INCLUDE, or all the required properties are present in the index Nodes
        public boolean filterIsFullySupportedByIndex;

        public boolean diffUsesIndex;

        public DiffTree diffOp;

        public ScreenMapPredicate screenMapFilter;

    }

    public WalkInfo buildTreeWalk() {
        WalkInfo info = new WalkInfo();
        info.fullSchema = resolveFullSchema();

        // query filter in native CRS
        info.nativeFilter = resolveNativeFilter();

        // properties needed by the output schema and the in-process filter, null means all
        // properties, empty list means no-properties needed
        info.requiredProperties = resolveRequiredProperties(info.nativeFilter);

        final ObjectId featureTypeId = typeRef.getMetadataId();

        // perform the diff op with the supported Bucket/NodeRef filtering that'll provide the
        // NodeRef iterator to back the FeatureReader with
        // it doesn't matter if we use left or right repo, we'll be setting left/right source on the
        // command
        info.diffOp = leftRepo.command(DiffTree.class);

        // the RevTree id at the left side of the diff
        final ObjectId oldFeatureTypeTree;
        // the RevTree id at the right side of the diff
        final ObjectId newFeatureTypeTree;
        // where to get RevTree instances from (either the object or the index database)
        final ObjectStore leftSource, rightSource;
        final NodeOrdering diffNodeOrdering;
        {
            final String nativeTypeName = nativeSchema.getTypeName();

            // TODO: resolve based on filter, in case the feature type has more than one geometry
            // attribute
            final @Nullable GeometryDescriptor geometryAttribute = nativeSchema
                    .getGeometryDescriptor();
            final Optional<Index> oldHeadIndex;
            final Optional<Index> headIndex;

            final Optional<NodeRef> oldCanonicalTree = resolveCanonicalTree(oldHeadRef,
                    nativeTypeName, leftRepo);
            final Optional<NodeRef> newCanonicalTree = resolveCanonicalTree(headRef, nativeTypeName,
                    rightRepo);
            final ObjectId oldCanonicalTreeId = oldCanonicalTree.map(r -> r.getObjectId())
                    .orElse(RevTree.EMPTY_TREE_ID);
            final ObjectId newCanonicalTreeId = newCanonicalTree.map(r -> r.getObjectId())
                    .orElse(RevTree.EMPTY_TREE_ID);

            // featureTypeId = newCanonicalTree.isPresent() ? newCanonicalTree.get().getMetadataId()
            // : oldCanonicalTree.get().getMetadataId();

            Optional<Index> indexes[];

            // if native filter is a simple "fid filter" then force ignoring the index for a faster
            // look-up (looking up for a fid in the canonical tree is much faster)
            final boolean ignoreIndex = geometryAttribute == null || this.ignoreIndex
                    || info.nativeFilter instanceof Id;
            if (ignoreIndex) {
                indexes = NO_INDEX;
            } else {
                indexes = resolveIndex(oldCanonicalTreeId, newCanonicalTreeId, nativeTypeName,
                        geometryAttribute.getLocalName());
            }
            oldHeadIndex = indexes[0];
            headIndex = indexes[1];
            // neither is present or both have the same indexinfo
            checkState(!(oldHeadIndex.isPresent() || headIndex.isPresent()) || //
                    headIndex.get().info().equals(oldHeadIndex.get().info()));

            if (oldHeadIndex.isPresent()) {
                oldFeatureTypeTree = oldHeadIndex.get().indexTreeId();
                newFeatureTypeTree = headIndex.get().indexTreeId();
                leftSource = leftRepo.indexDatabase();
                rightSource = rightRepo.indexDatabase();
                IndexInfo indexInfo = oldHeadIndex.get().info();
                Envelope maxBounds = IndexInfo.getMaxBounds(indexInfo);
                if (maxBounds == null) {
                    maxBounds = IndexInfo.getMaxBounds(headIndex.get().info());
                }
                Preconditions.checkNotNull(maxBounds);
                diffNodeOrdering = QuadTreeBuilder.nodeOrdering(maxBounds);
                info.diffUsesIndex = true;
            } else {
                oldFeatureTypeTree = oldCanonicalTreeId;
                newFeatureTypeTree = newCanonicalTreeId;
                leftSource = leftRepo.objectDatabase();
                rightSource = rightRepo.objectDatabase();
                diffNodeOrdering = CanonicalNodeOrder.INSTANCE;
            }

            info.materializedIndexProperties = resolveMaterializedProperties(headIndex);

            PrePostFilterSplitter filterSplitter;
            filterSplitter = new PrePostFilterSplitter()
                    .extraAttributes(info.materializedIndexProperties).filter(info.nativeFilter)
                    .build();
            info.preFilter = filterSplitter.getPreFilter();
            info.postFilter = filterSplitter.getPostFilter();

            info.indexContainsAllRequiredProperties = info.materializedIndexProperties
                    .containsAll(info.requiredProperties);

            info.filterIsFullySupportedByIndex = Filter.INCLUDE.equals(info.postFilter);
        }
        // TODO: for some reason setting the default metadata id is making several tests fail,
        // though it's not really needed here because we have the FeatureType already. Nonetheless
        // this is strange and needs to be revisited.
        final boolean preserveIterationOrder = shallPreserveIterationOrder();
        Predicate<Bounded> indexPreFilter = createIndexPreFilter(info);
        info.diffOp.setDefaultMetadataId(featureTypeId) //
                .setPreserveIterationOrder(preserveIterationOrder)//
                .setPathFilter(createFidFilter(info.nativeFilter)) //
                .setCustomFilter(indexPreFilter) //
                // no need to set a bounds filter, the preFilter takes care of it
                // .setBoundsFilter(createBoundsFilter(info.fullSchema, info.nativeFilter,
                // newFeatureTypeTree, treeSource)) //
                .setChangeTypeFilter(resolveChangeType()) //
                .setOldTree(oldFeatureTypeTree) //
                .setNewTree(newFeatureTypeTree) //
                .setLeftSource(leftSource) //
                .setRightSource(rightSource) //
                .setNodeOrdering(diffNodeOrdering)//
                .recordStats();

        return info;
    }

    public FeatureReader<SimpleFeatureType, SimpleFeature> build() {
        final WalkInfo walkInfo = buildTreeWalk();

        AutoCloseableIterator<DiffEntry> diffs = walkInfo.diffOp.call();

        AutoCloseableIterator<NodeRef> featureRefs = toFeatureRefs(diffs, changeType);

        // post-processing
        if (walkInfo.filterIsFullySupportedByIndex) {
            featureRefs = applyOffsetAndLimit(featureRefs);
        }

        final ObjectStore leftFeatureSource = leftRepo.objectDatabase();
        final ObjectStore rightFeatureSource = rightRepo.objectDatabase();

        AutoCloseableIterator<? extends SimpleFeature> features;

        // contains only the attributes required to satisfy the output schema and the in-process
        // filter
        final SimpleFeatureType resultSchema;

        if (walkInfo.indexContainsAllRequiredProperties) {
            resultSchema = resolveMinimalNativeSchema(walkInfo.fullSchema,
                    walkInfo.requiredProperties);
            CoordinateReferenceSystem nativeCrs = walkInfo.fullSchema
                    .getCoordinateReferenceSystem();
            features = MaterializedIndexFeatureIterator.create(resultSchema, featureRefs,
                    geometryFactory, nativeCrs);
        } else {
            BulkFeatureRetriever retriever;
            retriever = new BulkFeatureRetriever(leftFeatureSource, rightFeatureSource);
            Name typeNameOverride;
            if (simpleNames(nativeSchema).equals(simpleNames(walkInfo.fullSchema))) {
                resultSchema = walkInfo.fullSchema;
                typeNameOverride = walkInfo.fullSchema.getName();
            } else {
                resultSchema = nativeSchema;
                typeNameOverride = null;
            }
            // using fullSchema here will build "normal" full-attribute lazy features
            features = retriever.getGeoToolsFeatures(featureRefs, nativeType, typeNameOverride,
                    geometryFactory);
        }

        if (!walkInfo.filterIsFullySupportedByIndex) {
            features = applyPostFilter(walkInfo.postFilter, features);
            features = applyOffsetAndLimit(features);
        }

        if ((screenMap != null)) {
            features = AutoCloseableIterator.transform(features,
                    new ScreenMapGeometryReplacer(screenMap, screenMapReplaceGeometryWithPx));
        }

        if ((simplificationDistance != null) && (simplificationDistance.doubleValue() > 0)) {
            features = AutoCloseableIterator.transform(features, new SimplifyingGeometryReplacer(
                    simplificationDistance.doubleValue(), geometryFactory));
        }

        FeatureReader<SimpleFeatureType, SimpleFeature> featureReader;
        featureReader = new FeatureReaderAdapter<SimpleFeatureType, SimpleFeature>(resultSchema,
                features);

        // we only want a sub-set of the attributes provided - we need to re-type
        // the features (either from the index or the full-feature)
        final boolean retypeRequired = isRetypeRequired(walkInfo.fullSchema, resultSchema);
        if (retypeRequired) {
            List<String> outputSchemaProperties;
            if (this.outputSchemaPropertyNames == Query.ALL_NAMES) {
                outputSchemaProperties = simpleNames(walkInfo.fullSchema);
            } else {
                outputSchemaProperties = Lists.newArrayList(this.outputSchemaPropertyNames);
            }
            SimpleFeatureType outputSchema;
            outputSchema = SimpleFeatureTypeBuilder.retype(walkInfo.fullSchema,
                    outputSchemaProperties);

            boolean cloneValues = false;
            featureReader = new ReTypeFeatureReader(featureReader, outputSchema, cloneValues);
        }

        return featureReader;
    }

    private SimpleFeatureType resolveFullSchema() {
        SimpleFeatureType targetSchema = this.targetSchema;
        if (targetSchema == null) {
            targetSchema = (SimpleFeatureType) nativeType.type();
        }
        return targetSchema;
    }

    public FeatureReaderBuilder targetSchema(@Nullable SimpleFeatureType targetSchema) {
        this.targetSchema = targetSchema;
        return this;
    }

    public FeatureReaderBuilder screenMapReplaceGeometryWithPx(Boolean replace) {
        if (replace != null)
            this.screenMapReplaceGeometryWithPx = replace;
        return this;
    }

    public FeatureReaderBuilder retypeIfNeeded(boolean retype) {
        this.retypeIfNeeded = retype;
        return this;
    }

    private boolean isRetypeRequired(final SimpleFeatureType fullSchema,
            SimpleFeatureType resultSchema) {
        if (!retypeIfNeeded) {
            return false;
        }

        List<String> fullSchemaNames = simpleNames(fullSchema);
        List<String> resultNames = simpleNames(resultSchema);

        final String[] queryProps = outputSchemaPropertyNames;
        if (Query.ALL_NAMES == queryProps) {
            return !fullSchemaNames.equals(resultNames);
        }

        boolean retypeRequired = !Arrays.asList(queryProps).equals(resultNames);
        return retypeRequired;
    }

    private List<String> simpleNames(SimpleFeatureType type) {
        return Lists.transform(type.getAttributeDescriptors(), (ad) -> ad.getLocalName());
    }

    private AutoCloseableIterator<? extends SimpleFeature> applyPostFilter(Filter postFilter,
            AutoCloseableIterator<? extends SimpleFeature> features) {

        Predicate<SimpleFeature> filterPredicate = PostFilter.forFilter(postFilter);
        features = AutoCloseableIterator.filter(features, filterPredicate);
        if (screenMap != null) {
            Predicate<SimpleFeature> screenMapFilter = new FeatureScreenMapPredicate(screenMap);
            features = AutoCloseableIterator.filter(features, screenMapFilter);
        }

        return features;
    }

    private SimpleFeatureType resolveMinimalNativeSchema(SimpleFeatureType fullSchema,
            Set<String> requiredProperties) {

        SimpleFeatureType resultSchema;

        if (requiredProperties.equals(nativeSchemaAttributeNames)) {
            resultSchema = fullSchema;
        } else {
            List<String> atts = new ArrayList<>();
            for (AttributeDescriptor d : fullSchema.getAttributeDescriptors()) {
                String attName = d.getLocalName();
                if (requiredProperties.contains(attName)) {
                    atts.add(attName);
                }
            }
            try {
                String[] properties = atts.toArray(new String[requiredProperties.size()]);
                resultSchema = DataUtilities.createSubType(fullSchema, properties);
            } catch (SchemaException e) {
                throw new RuntimeException(e);
            }
        }

        return resultSchema;
    }

    private <T> AutoCloseableIterator<T> applyOffsetAndLimit(AutoCloseableIterator<T> iterator) {
        Integer offset = this.offset;
        Integer limit = this.limit;
        if (offset != null) {
            Iterators.advance(iterator, offset.intValue());
        }
        if (limit != null) {
            iterator = AutoCloseableIterator.limit(iterator, limit.intValue());
        }
        return iterator;
    }

    @SuppressWarnings("unchecked")
    private static final Optional<Index>[] NO_INDEX = new Optional[] { Optional.empty(),
            Optional.empty() };

    @SuppressWarnings("unchecked")
    private Optional<Index>[] resolveIndex(final ObjectId oldCanonical, final ObjectId newCanonical,
            final String treeName, final String attributeName) {
        if (Boolean.getBoolean("geogig.ignoreindex")) {
            // TODO: remove debugging aid
            System.err.printf(
                    "Ignoring index lookup for %s as indicated by -Dgeogig.ignoreindex=true\n",
                    treeName);
            return NO_INDEX;
        }
        Optional<Index> leftIndex = resolveIndex(oldCanonical, treeName, attributeName, leftRepo);
        Optional<Index> rightIndex = leftIndex.isPresent()
                ? resolveIndex(newCanonical, treeName, attributeName, rightRepo)
                : Optional.empty();
        if (!leftIndex.isPresent() || !rightIndex.isPresent()) {
            return NO_INDEX;
        }
        Optional<Index>[] indexes = new Optional[2];
        indexes[0] = leftIndex;
        indexes[1] = rightIndex;
        return indexes;
    }

    private Optional<Index> resolveIndex(final ObjectId oldCanonical, final String treeName,
            final String attributeName, final Context repo) {
        if (Boolean.getBoolean("geogig.ignoreindex")) {
            // TODO: remove debugging aid
            System.err.printf(
                    "Ignoring index lookup for %s as indicated by -Dgeogig.ignoreindex=true\n",
                    treeName);
            return Optional.empty();
        }
        final IndexDatabase indexDatabase = repo.indexDatabase();
        IndexInfo info = indexDatabase.getIndexInfo(treeName, attributeName).orNull();
        Optional<Index> index = Optional.empty();
        if (info != null) {
            index = resolveIndex(oldCanonical, info, indexDatabase);
        }
        return index;
    }

    private Optional<NodeRef> resolveCanonicalTree(@Nullable String head, String treeName,
            Context repo) {
        NodeRef treeRef = null;
        if (head != null) {
            ObjectId rootTree = repo.command(ResolveTreeish.class).setTreeish(head).call().orNull();
            if (rootTree != null) {
                RevTree tree = repo.objectDatabase().getTree(rootTree);
                treeRef = repo.command(FindTreeChild.class).setParent(tree).setChildPath(treeName)
                        .call().orNull();
            }
        }
        return Optional.ofNullable(treeRef);
    }

    private Optional<Index> resolveIndex(ObjectId canonicalTreeId, IndexInfo indexInfo,
            IndexDatabase indexDatabase) {

        Index index = new Index(indexInfo, RevTree.EMPTY_TREE_ID, indexDatabase);
        if (!RevTree.EMPTY_TREE_ID.equals(canonicalTreeId)) {
            ObjectId indexedTree = indexDatabase.resolveIndexedTree(indexInfo, canonicalTreeId)
                    .orNull();
            if (indexedTree != null) {
                index = new Index(indexInfo, indexedTree, indexDatabase);
            }
        }
        return Optional.ofNullable(index);
    }

    private Set<String> resolveMaterializedProperties(Optional<Index> index) {
        Set<String> availableAtts = ImmutableSet.of();
        if (index.isPresent()) {
            IndexInfo info = index.get().info();
            availableAtts = IndexInfo.getMaterializedAttributeNames(info);
        }
        return availableAtts;
    }

    /**
     * Resolves the properties needed for the output schema, which are mandated both by the
     * properties requested by {@link #propertyNames} and any other property needed to evaluate the
     * {@link #filter} in-process.
     */
    Set<String> resolveRequiredProperties(Filter nativeFilter) {
        if (outputSchemaPropertyNames == Query.ALL_NAMES) {
            return nativeSchemaAttributeNames;
        }

        final Set<String> filterAttributes = requiredAttributes(nativeFilter);

        if (outputSchemaPropertyNames.length == 0
                /* Query.NO_NAMES */ && filterAttributes.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> requiredProps = Sets.newHashSet(outputSchemaPropertyNames);
        // if the filter is a simple BBOX filter against the default geometry attribute, don't force
        // it, we can optimize bbox filter out of Node.bounds()
        if (!(nativeFilter instanceof BBOX)) {
            requiredProps.addAll(filterAttributes);
        }
        // props required to evaluate the filter in-process
        return requiredProps;
    }

    private Set<String> requiredAttributes(Filter filter) {
        String[] filterAttributes = DataUtilities.attributeNames(filter);
        if (filterAttributes == null || filterAttributes.length == 0) {
            return Collections.emptySet();
        }

        return Sets.newHashSet(filterAttributes);
    }

    public static AutoCloseableIterator<NodeRef> toFeatureRefs(
            final AutoCloseableIterator<DiffEntry> diffs, final ChangeType changeType) {

        return AutoCloseableIterator.transform(diffs, (e) -> {
            if (e.isAdd()) {
                return e.getNewObject();
            }
            if (e.isDelete()) {
                return e.getOldObject();
            }
            return ChangeType.CHANGED_OLD.equals(changeType) ? e.getOldObject() : e.getNewObject();
        });
    }

    private Filter resolveNativeFilter() {
        Filter nativeFilter = this.filter;
        nativeFilter = SimplifyingFilterVisitor.simplify(nativeFilter, nativeSchema);
        nativeFilter = reprojectFilter(nativeFilter);

        if (this.nativeSchema.getGeometryDescriptor() != null) {
            String defaultGeometryPName = this.nativeSchema.getGeometryDescriptor().getName().getLocalPart();
            RenamePropertyFilterVisitor renameBoundsVisitor = new RenamePropertyFilterVisitor(
                    "@bounds", defaultGeometryPName);
            nativeFilter = (Filter) nativeFilter.accept(renameBoundsVisitor, null);
        }

        InReplacingFilterVisitor inreplacer = new InReplacingFilterVisitor();
        nativeFilter =  (Filter) nativeFilter.accept(inreplacer,null);

        return nativeFilter;
    }

    private Filter reprojectFilter(Filter filter) {
        if (hasSpatialFilter(filter)) {
            ReprojectingFilterVisitor visitor;
            visitor = new ReprojectingFilterVisitor(filterFactory, nativeSchema);
            filter = (Filter) filter.accept(visitor, null);
        }
        return filter;
    }

    private boolean hasSpatialFilter(Filter filter) {
        SpatialFilterVisitor spatialFilterVisitor = new SpatialFilterVisitor();
        filter.accept(spatialFilterVisitor, null);
        return spatialFilterVisitor.hasSpatialFilter();
    }

    private @Nullable org.locationtech.geogig.model.DiffEntry.ChangeType resolveChangeType() {
        switch (changeType) {
        case ALL:
            return null;
        case ADDED:
            return DiffEntry.ChangeType.ADDED;
        case REMOVED:
            return DiffEntry.ChangeType.REMOVED;
        case CHANGED:
        case CHANGED_NEW:
        case CHANGED_OLD:
        default:
            return DiffEntry.ChangeType.MODIFIED;
        }
    }

    private @Nullable ReferencedEnvelope createBoundsFilter(SimpleFeatureType fullSchema,
            Filter filterInNativeCrs, ObjectId featureTypeTreeId, ObjectStore treeSource) {
        if (RevTree.EMPTY_TREE_ID.equals(featureTypeTreeId)) {
            return null;
        }

        CoordinateReferenceSystem nativeCrs = fullSchema.getCoordinateReferenceSystem();
        if (nativeCrs == null) {
            nativeCrs = DefaultEngineeringCRS.GENERIC_2D;
        }

        final Envelope queryBounds = new Envelope();
        List<Envelope> bounds = ExtractBounds.getBounds(filterInNativeCrs);

        if (bounds != null && !bounds.isEmpty()) {
            final RevTree tree = treeSource.getTree(featureTypeTreeId);
            final Envelope fullBounds = SpatialOps.boundsOf(tree);
            expandToInclude(queryBounds, bounds);

            Envelope clipped = fullBounds.intersection(queryBounds);
            if (clipped.equals(fullBounds)) {
                queryBounds.setToNull();
            }
        }
        return queryBounds.isNull() ? null : new ReferencedEnvelope(queryBounds, nativeCrs);
    }

    private void expandToInclude(Envelope queryBounds, List<Envelope> bounds) {
        for (Envelope e : bounds) {
            queryBounds.expandToInclude(e);
        }
    }

    /**
     * @param walkInfo: create pre filter for {@code preFilter}, if {@code indexFullySupportsQuery}
     *        use the screenmap
     */
    private Predicate<Bounded> createIndexPreFilter(WalkInfo walkInfo) {
        final Filter preFilter = walkInfo.preFilter;
        final boolean indexFullySupportsQuery = walkInfo.filterIsFullySupportedByIndex;
        final boolean preserveIterationOrder = shallPreserveIterationOrder();

        Predicate<Bounded> predicate = PreFilter.forFilter(preFilter);
        final boolean ignore = Boolean.getBoolean("geogig.ignorescreenmap");
        // if the index is not fully supported, do not apply the screenmap filter at this stage
        // otherwise we will remove too many features
        if (screenMap != null && !ignore && indexFullySupportsQuery) {
            ScreenMapPredicate screenMapFilter = new ScreenMapPredicate(screenMap);
            walkInfo.screenMapFilter = screenMapFilter;
            final boolean filterBuckets = canFilterBuckets(preFilter);
            if (filterBuckets) {
                screenMapFilter.filterTrees();
            }
            if (preserveIterationOrder) {
                screenMapFilter.optimizeForSingleThreadedCalls();
            }
            predicate = Predicates.and(predicate, screenMapFilter);
        }
        return predicate;
    }

    /**
     * Determines if the screenmap filter predicate can filter both bucket nodes and feature nodes
     * or just feature nodes.
     * <p>
     * Bucket nodes are safe to filter out only of the feature {@code preFilter} is not filtering on
     * a non geometry attribute.
     * 
     * @return {@code true} if its safe to filter out whole subtrees based on their bounds
     */
    private boolean canFilterBuckets(Filter preFilter) {
        String[] filterProperties = DataUtilities.attributeNames(preFilter);
        for (String attName : filterProperties) {
            if (PrePostFilterSplitter.BOUNDS_META_PROPERTY.equals(attName)) {
                continue;
            }
            AttributeDescriptor descriptor = this.nativeSchema.getDescriptor(attName);
            if (!(descriptor instanceof GeometryDescriptor)) {
                return false;
            }
        }
        return true;// safe to pre filter buckets by bounds
    }

    private List<String> createFidFilter(Filter filter) {
        List<String> pathFilters = ImmutableList.of();
        if (filter instanceof Id) {
            final Set<Identifier> identifiers = ((Id) filter).getIdentifiers();
            Iterator<FeatureId> featureIds = Iterators
                    .filter(Iterators.filter(identifiers.iterator(), FeatureId.class), notNull());
            Preconditions.checkArgument(featureIds.hasNext(), "Empty Id filter");

            pathFilters = Lists.newArrayList(Iterators.transform(featureIds, (fid) -> fid.getID()));
        }

        return pathFilters;
    }

    /**
     * Determines if the returned iterator shall preserve iteration order among successive calls of
     * the same query.
     * <p>
     * This is the case if:
     * <ul>
     * <li>{@link #limit} and/or {@link #offset} have been set, since most probably the caller is
     * doing paging
     * </ul>
     */
    private boolean shallPreserveIterationOrder() {
        boolean preserveIterationOrder = false;
        preserveIterationOrder |= limit != null || offset != null;
        // TODO: revisit, sortBy should only force iteration order if we can return features in the
        // requested order, otherwise a higher level decorator will still perform the sort
        // in-process so there's no point in forcing the iteration order here
        // preserveIterationOrder |= sortBy != null && sortBy.length > 0;
        return true;// preserveIterationOrder;
    }
}
