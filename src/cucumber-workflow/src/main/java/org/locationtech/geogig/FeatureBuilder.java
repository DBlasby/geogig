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


import org.geotools.data.DataUtilities;
import org.geotools.util.Converters;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

//simple class to allow construction of features
//according to a specification
public class FeatureBuilder {
    SimpleFeatureType featureType;

     List<String> values;
    List<KeyValue> keyValues  ;


    static int currentFeatureNumber;
    static int currentGroupNumber;
    int currentFeatureNumbInGroup;

    static {
        System.setProperty("user.timezone", "GMT");
        TimeZone.setDefault( TimeZone.getTimeZone("GMT"));
    }

    public FeatureBuilder(SimpleFeatureType featureType, List<String> values) {
        this.featureType = featureType;
         this.values = values;
        keyValues = new ArrayList<>(values.size());
        for (String val: values) {
            String[] keyvalStrings = val.split("=");
            keyValues.add(new KeyValue(keyvalStrings[0], keyvalStrings[1]));
        }
    }



    List<SimpleFeature> createFeatures(int number) throws Exception {
        currentGroupNumber++;
        currentFeatureNumbInGroup =0;

        ArrayList<SimpleFeature> result = new ArrayList<>(number);
        for (int t=0;t<number;t++) {
            result.add( createFeature());
        }
        return result;
    }

    SimpleFeature createFeature(  ) throws Exception {
        Variables.setVariable("currentFeatureNumber", String.valueOf(currentFeatureNumber));
        Variables.setVariable("currentGroupNumber", String.valueOf(currentGroupNumber));
        Variables.setVariable("currentFeatureNumbInGroup", String.valueOf(currentFeatureNumbInGroup));
        SimpleFeature f = DataUtilities.template(featureType, "abc."+currentFeatureNumber);
        for (KeyValue keyValue : keyValues) {
            AttributeDescriptor desc = featureType.getDescriptor(keyValue.key);
            String valString = Variables.replaceVariables( keyValue.value);
            Object val  =  Converters.convert(valString, desc.getType().getBinding());
            f.setAttribute(keyValue.key, val);
        }
        currentFeatureNumber++;
        currentFeatureNumbInGroup++;
        return f;
    }

    public static class KeyValue {
        public String key;
        public String value;

        public KeyValue (String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
