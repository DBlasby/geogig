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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//simple class to allow setting of variables
public  class Variables {

    static Map<String,String> variables = new HashMap<>();

    static {
        variables.put("GEOSERVER_HOST","localhost:8080");
        variables.put("DB_HOST","localhost");
        variables.put("POSTGRES_USER","postgres");
        variables.put("POSTGRES_PASS","postgres");
        variables.put("UUID",UUID.randomUUID().toString().replace("-","_"));
    }

    public static void setVariable(String key, String value) throws Exception {
        value = replaceVariables(value); //so can use older values
        variables.put(key,value);
    }

    public static String getVariable(String key) throws Exception {
        String val= variables.get(key);
        if (val == null)
            throw new Exception("couldnt parse: "+key);
        return val;
    }



    // "abc${var}"
    public static String replaceVariables(String text) throws Exception {
        Pattern pattern = Pattern.compile("\\$\\{([^}]+)\\}");

        boolean keep_going = true;
        while (keep_going) {
            Matcher matcher = pattern.matcher(text);
            keep_going = matcher.find();
            if (keep_going) {
                String phrase = matcher.group(0); // ${key}
                String key = matcher.group(1); //"key"
                text = text.replace(phrase, getVariable(key));
            }
        }
        return text;
    }
}
