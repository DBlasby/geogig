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


import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.storage.ObjectDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NameExtractingConsumer  extends PreOrderDiffWalk.AbstractConsumer {

    Map<String, ObjectId>  name2objectId = new HashMap<>();

    public NameExtractingConsumer( ) {
     }

    @Override
    public synchronized boolean feature(@Nullable final NodeRef left,
                                        @Nullable final NodeRef right) {
        String name = right.getNode().getName();
        ObjectId id =  right.getNode().getObjectId();
        if (name2objectId.containsKey(name))
            throw new RuntimeException("found name '"+name+"' more than once!");
        name2objectId.put(name,id);
        return true;
    }

    public void validate(NameExtractingConsumer other) throws Exception {
        if (this.name2objectId.size() != other.name2objectId.size())
            throw new Exception("comparing two NameExtractingConsumer - they have different # of names!");

        List<String> this_names =  new ArrayList<>(this.name2objectId.keySet());
        Collections.sort(this_names);
        List<String> other_names =  new ArrayList<>(other.name2objectId.keySet());
        Collections.sort(other_names);
        if (!this_names.equals( other_names ) ) {
            List<String> onlyInThis = new ArrayList<>(this_names);
            onlyInThis.removeAll(other_names);

            List<String> onlyInOther = new ArrayList<>(other_names);
            onlyInOther.removeAll(this_names);

            String message = "Names only in this="+ String.join(",",onlyInThis) +"\n";
            message += "Names only in other="+ String.join(",",onlyInOther);
            throw new Exception("Names are not the same - \n"+message);
        }

        for (String name : this.name2objectId.keySet()) {
            ObjectId this_id = this.name2objectId.get(name);
            ObjectId other_id = other.name2objectId.get(name);
            if (!this_id.equals(other_id))
                throw new Exception("for name="+name+", they point to two different features - "+this_id+" VS "+other_id);

        }

    }
}
