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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

//execute simple Postgresql commands
public class Postgresql {

    String dbName;
    String user;
    String password;

    public Postgresql(String dbName, String user, String password)  {
        this.dbName = dbName;
        this.user = user;
        this.password = password;
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void createDB() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/", user, password) ) {
            Statement statement = c.createStatement();
            statement.executeUpdate("CREATE DATABASE " + dbName);
        }
    }

    public void executeSQL(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/"+dbName, user, password) ) {
            Statement statement = c.createStatement();
            statement.executeUpdate(sql);
        }
    }

}
