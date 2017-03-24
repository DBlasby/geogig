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

import cucumber.api.java.en.Given;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class CucumberBindings_util {
    @Given("^SQL Execute ([^ ]+) \"([^\"]+)\"$")
    public void execute_sql(String dbname, String sql) throws Exception {
        dbname = Variables.replaceVariables(dbname);
        sql = Variables.replaceVariables(sql);
        String user = Variables.replaceVariables("${POSTGRES_USER}");
        String pass = Variables.replaceVariables("${POSTGRES_PASS}");
        Postgresql postgresql  = new Postgresql(dbname, user,pass);
        postgresql.executeSQL(sql);
    }




    @Given("^Shell Execute \"([^\"]+)\"$")
    public void shell_execute(String cmd) throws Exception {
        cmd = Variables.replaceVariables(cmd);
        String[] cmdShell = { "/bin/sh", "-c", cmd };

        Process process  = Runtime.getRuntime().exec(cmdShell);
        process.waitFor();
        if (process.exitValue() !=0) {
            InputStream outputFromCmd = process.getInputStream();
            String result = IOUtils.toString(outputFromCmd, "UTF-8");
            IOUtils.closeQuietly(outputFromCmd);

            InputStream errorFromCmd = process.getErrorStream();
            String error = IOUtils.toString(errorFromCmd, "UTF-8");
            IOUtils.closeQuietly(errorFromCmd);

            throw new Exception("error "+ process.exitValue()+" while running command '"+cmd+"' with output '"+result+"' and error '"+error+"'");
        }
    }

    public static Map<String,String> parseKeyValues(String KVString) {
        if  ( (KVString == null) || (KVString.length()==0) ){
            return new HashMap<>(0);
        }
        List<String> keyvalues =  Arrays.asList(KVString.split(";") );
        Map<String,String> keyValues = new HashMap<>(keyvalues.size());
        for (String val: keyvalues) {
            String[] keyvalStrings = val.split("=");
            keyValues.put(keyvalStrings[0], keyvalStrings[1]);
        }
        return keyValues;
    }


}
