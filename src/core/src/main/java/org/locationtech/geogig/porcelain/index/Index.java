package org.locationtech.geogig.porcelain.index;

import static com.google.common.base.Preconditions.*;
import java.util.Objects;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.storage.IndexDatabase;

/**
 * A value object resulting of creating or updating an index, that provides access to the
 * {@link IndexInfo index information} and the {@link RevTree} the index points to.
 *
 */
public final class Index {

    private final IndexInfo indexInfo;

    private final ObjectId indexTree;

    private final IndexDatabase indexdb;

    public Index(IndexInfo indexInfo, ObjectId indexTree, IndexDatabase indexdb) {
        checkNotNull(indexInfo);
        checkNotNull(indexTree);
        this.indexInfo = indexInfo;
        this.indexTree = indexTree;
        this.indexdb = indexdb;
    }

    public IndexInfo info() {
        return indexInfo;
    }

    public ObjectId indexTreeId() {
        return indexTree;
    }

    public RevTree indexTree() {
        return indexdb.getTree(indexTree);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Index) {
            Index i = (Index) o;
            return info().equals(i.info()) && indexTreeId().equals(i.indexTreeId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(indexInfo, indexTree);
    }

    @Override
    public String toString() {
        return String.format("Index(%s) %s on %s(%s)", indexTree.toString().substring(0, 8),
                indexInfo.getIndexType(), indexInfo.getTreeName(), indexInfo.getAttributeName());
    }
}
