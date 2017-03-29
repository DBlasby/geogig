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
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;


//store information about the node
public class TreeMetadata {
    private Envelope _computedEnvelope;
    private Envelope _storedEnvelope;



    public boolean isLeaf;
    public boolean isBucket;

    ObjectId id;

    public TreeMetadata(Bucket b) {
        this.isBucket = true;
        this.id = b.getObjectId();
        this._storedEnvelope = b.bounds().get();
    }


    public TreeMetadata(RevTree tree) {
        this.isLeaf = true;
        this.id = tree.getId();
        this._storedEnvelope = null; //not present
    }

//    public TreeMetadata(ObjectId id) {
//        this.id = id;
//    }

    public void setComputedEnvelope(Envelope e) throws Exception {
        this._computedEnvelope = e;
        validate();
    }

    public Envelope getComputedEnvelope() {
        return _computedEnvelope;
    }

    public Envelope getStoredEnvelope() {
        return _storedEnvelope;
    }

    public void setStoredEnvelope(Envelope e) throws Exception {
        this._storedEnvelope = e;
        validate();
    }

    public void validate() throws Exception {
        if ( (_storedEnvelope != null) && (_computedEnvelope == null) )
            throw new Exception("unable to compute an evelope");

        if ((_storedEnvelope != null) && (_computedEnvelope != null)) {
            if (!_computedEnvelope.equals(_storedEnvelope))
                throw new Exception(id + " - has incorrect bounds");
        }
    }

}
