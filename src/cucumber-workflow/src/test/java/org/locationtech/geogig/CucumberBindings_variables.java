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

import java.util.UUID;

public class CucumberBindings_variables {


    @Given("^Variable: Set ([^=]+)=(.*)$")
    public void set_variable(String key, String value) throws Exception {
        Variables.setVariable(key, value);
    }


    @Given("^Variable: Create New UUID$")
    public void new_uuid() throws Exception {
        Variables.setVariable("UUID", UUID.randomUUID().toString().replace("-","_"));
    }
}
