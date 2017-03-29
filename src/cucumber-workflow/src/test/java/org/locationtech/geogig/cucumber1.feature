Feature: CucumberJava

  Scenario: Load Data From Shp NY Style
    Given Variable: Create New UUID
    And Variable: Set DB_NAME=gigdb_${UUID}
    And Variable: Set REPO_NAME=gigrepo_${UUID}
    And Variable: Set WS_NAME=gigws_${UUID}
    And Variable: Set DS_NAME=gigds_${UUID}
    And Variable: Set GIG_REPO=postgresql://localhost:5432/${DB_NAME}/${REPO_NAME}?user=${POSTGRES_USER}&password=${POSTGRES_PASS}

    And SQL Execute template1 "CREATE DATABASE ${DB_NAME}"
    And GeoGIG: Init repo ${DB_NAME} ${REPO_NAME}
    And GeoGig: Execute "${GIG_REPO}" "shp import --fid-attrib GID /Users/dblasby/Downloads/nyc_buildings/small/buildingsm.shp"
    And GeoGig: Execute "${GIG_REPO}" "add"
    And GeoGig: Execute "${GIG_REPO}" "commit -m shpload"
    And GeoGig: Execute "${GIG_REPO}" "index create --tree buildingsm --attribute the_geom"


    And Geoserver: Create Workspace ${WS_NAME}
    And Geoserver: Create GeoGIG Datastore ${WS_NAME} ${REPO_NAME} ${DS_NAME}
    And Geoserver: Publish Layer ${WS_NAME} ${DS_NAME} buildingsm

    And   I setup a transaction against WFS
    And      Update set the_geom = "MULTIPOLYGON(((20 20, 20 21, 21 21,21 20,20 20)))" WHERE "IN ('0de17f0e-119c-4b49-94d6-c79e6fef870b')"
    And   I commit the transaction

    When I Query "GID = '0de17f0e-119c-4b49-94d6-c79e6fef870b'" against WFS
    Then Assert query returns 1 features


  Scenario: Create Feature Type
    Given Variable: Create New UUID
    And  Variable: Set DB_NAME=gigdb_${UUID}
    And Variable: Set REPO_NAME=gigrepo_${UUID}
    And Variable: Set WS_NAME=gigws_${UUID}
    And Variable: Set DS_NAME=gigds_${UUID}
    And Variable: Set LAYER_NAME=giglayer_${UUID}
    And Variable: Set GIG_REPO=postgresql://localhost:5432/${DB_NAME}/${REPO_NAME}?user=${POSTGRES_USER}&password=${POSTGRES_PASS}

    And SQL Execute template1 "CREATE DATABASE ${DB_NAME}"
    And GeoGIG: Init repo ${DB_NAME} ${REPO_NAME}
    And Geoserver: Create Workspace ${WS_NAME}
    And Geoserver: Create GeoGIG Datastore ${WS_NAME} ${REPO_NAME} ${DS_NAME}
    And Create FeatureType ${WS_NAME} ${DS_NAME} ${LAYER_NAME} "geom:MultiPolygon:srid=3857,tag:String,featureNumber:Integer,groupNumber:Integer,numbInGroup:Integer,int1:Integer,string1:String,double1:Double,guid:String"

    And I setup a transaction against WFS,MEMORY
    And      I insert 5 features "geom=MULTIPOLYGON(((10 10, 10 11, 11 11,11 10,10 10)));tag=group1;featureNumber=${currentFeatureNumber};groupNumber=${currentGroupNumber};numbInGroup=${currentFeatureNumbInGroup};int1=111;string1=my string;double1=666;guid=guid.s"
    And I commit the transaction

    When I Query "INCLUDE" against WFS,MEMORY
    Then Assert Query results are equivalent


    Scenario: Load data mapstory-style
      Given Variable: Create New UUID
      And  Variable: Set DB_NAME=gigdb_${UUID}
      And Variable: Set REPO_NAME=gigrepo_${UUID}
      And Variable: Set WS_NAME=gigws_${UUID}
      And Variable: Set DS_NAME=gigds_${UUID}
      And Variable: Set LAYER_NAME=giglayer_${UUID}
      And Variable: Set GIG_REPO=postgresql://localhost:5432/${DB_NAME}/${REPO_NAME}?user=${POSTGRES_USER}&password=${POSTGRES_PASS}

      And SQL Execute template1 "CREATE DATABASE ${DB_NAME}"
    #  And SQL Execute ${DB_NAME} "CREATE EXTENSION postgis"
    #  And Shell Execute "shp2pgsql -s 3857 /Users/dblasby/Downloads/nyc_buildings/small/buildingsm.shp | psql -U postgres -q ${DB_NAME}"
      And GeoGIG: Init repo ${DB_NAME} ${REPO_NAME}

      And GeoGIG: Start Transaction ${REPO_NAME}
      And   GeoGIG: Import from PG  postgis uscntypopcopy_project_small postgres postgres
      And   GeoGIG: Wait for import to finish
      And   GeoGIG: Add
      And   GeoGIG: Commit "initial"
      And   GeoGIG: Assert Commit affect at least 1000 features
      And GeoGIG: End Transaction ${REPO_NAME} true

      And Geoserver: Create Workspace ${WS_NAME}
      And Geoserver: Create GeoGIG Datastore ${WS_NAME} ${REPO_NAME} ${DS_NAME} autoIndexing=true;branch=master
      And Geoserver: Publish Layer ${WS_NAME} ${DS_NAME} uscntypopcopy_project_small time:decade:ISO8601:LIST:MINIMUM


  Scenario: Simple Bounds test
    Given Variable: Create New UUID
    And   Variable: Set DB_NAME=gigdb_${UUID}
    And Variable: Set REPO_NAME=gigrepo_${UUID}
    And Variable: Set WS_NAME=gigws_${UUID}
    And Variable: Set DS_NAME=gigds_${UUID}
    And Variable: Set LAYER_NAME=buildingsm

    And Variable: Set GIG_REPO=postgresql://localhost:5432/${DB_NAME}/${REPO_NAME}?user=${POSTGRES_USER}&password=${POSTGRES_PASS}

    And SQL Execute template1 "CREATE DATABASE ${DB_NAME}"
    And GeoGIG: Init repo ${DB_NAME} ${REPO_NAME}
    And GeoGig: Execute "${GIG_REPO}" "shp import --fid-attrib GID /Users/dblasby/Downloads/nyc_buildings/small/buildingsm.shp"
    And GeoGig: Execute "${GIG_REPO}" "add"
    And GeoGig: Execute "${GIG_REPO}" "commit -m shpload"
    And GeoGig: Execute "${GIG_REPO}" "index create --tree ${LAYER_NAME} --attribute the_geom"

    Then GeoGIG: Verify the Index Exists "${GIG_REPO}" ${LAYER_NAME}
    Then GeoGIG: Verify Tree and Feature Bounds "${GIG_REPO}" ${LAYER_NAME} against INDEX,CANONICAL

    And Geoserver: Create Workspace ${WS_NAME}
    And Geoserver: Create GeoGIG Datastore ${WS_NAME} ${REPO_NAME} ${DS_NAME} branch=master
    And Geoserver: Publish Layer ${WS_NAME} ${DS_NAME} ${LAYER_NAME}


  Scenario: More Complex Bounds Test
    Given Variable: Create New UUID
    And  Variable: Set DB_NAME=gigdb_${UUID}
    And Variable: Set REPO_NAME=gigrepo_${UUID}
    And Variable: Set WS_NAME=gigws_${UUID}
    And Variable: Set DS_NAME=gigds_${UUID}
    And Variable: Set LAYER_NAME=giglayer_${UUID}
    And Variable: Set GIG_REPO=postgresql://localhost:5432/${DB_NAME}/${REPO_NAME}?user=${POSTGRES_USER}&password=${POSTGRES_PASS}

    And SQL Execute template1 "CREATE DATABASE ${DB_NAME}"
    And GeoGIG: Init repo ${DB_NAME} ${REPO_NAME}
    And Geoserver: Create Workspace ${WS_NAME}
    And Geoserver: Create GeoGIG Datastore ${WS_NAME} ${REPO_NAME} ${DS_NAME}
    And Create FeatureType ${WS_NAME} ${DS_NAME} ${LAYER_NAME} "geom:MultiPolygon:srid=3857,tag:String,featureNumber:Integer,groupNumber:Integer,numbInGroup:Integer,int1:Integer,string1:String,double1:Double,guid:String"
    And GeoGig: Execute "${GIG_REPO}" "index create --tree ${LAYER_NAME} --attribute geom -e featureNumber"

    And I setup a transaction against WFS,MEMORY
    And      I insert 130 features "geom=MULTIPOLYGON(((10 10, 10 11, 11 11,11 10,10 10)));tag=group1;featureNumber=${currentFeatureNumber};groupNumber=${currentGroupNumber};numbInGroup=${currentFeatureNumbInGroup};int1=111;string1=my string;double1=666;guid=guid.s"
    And I commit the transaction

    When I Query "INCLUDE" against WFS,MEMORY
    Then Assert Query results are equivalent

    Then GeoGIG: Verify the Index Exists "${GIG_REPO}" ${LAYER_NAME} WITH featureNumber
    Then GeoGIG: Verify Tree and Feature Bounds "${GIG_REPO}" ${LAYER_NAME} against INDEX,CANONICAL
    Then GeoGIG: Verify Index Extra Data "${GIG_REPO}" ${LAYER_NAME}
    Then GeoGIG: Verify Tree Names "${GIG_REPO}" ${LAYER_NAME}

    And I setup a transaction against WFS,MEMORY
    And      I delete features "featureNumber=0 or featureNumber=1"
    And I commit the transaction

    When I Query "INCLUDE" against WFS,MEMORY
    Then Assert Query results are equivalent

    Then GeoGIG: Verify the Index Exists "${GIG_REPO}" ${LAYER_NAME}
    Then GeoGIG: Verify Tree and Feature Bounds "${GIG_REPO}" ${LAYER_NAME} against INDEX,CANONICAL
    Then GeoGIG: Verify Index Extra Data "${GIG_REPO}" ${LAYER_NAME}
    Then GeoGIG: Verify Tree Names "${GIG_REPO}" ${LAYER_NAME}


  Scenario: Null geometry test
    Given Variable: Create New UUID
    And  Variable: Set DB_NAME=gigdb_${UUID}
    And Variable: Set REPO_NAME=gigrepo_${UUID}
    And Variable: Set WS_NAME=gigws_${UUID}
    And Variable: Set DS_NAME=gigds_${UUID}
    And Variable: Set LAYER_NAME=giglayer_${UUID}
    And Variable: Set GIG_REPO=postgresql://localhost:5432/${DB_NAME}/${REPO_NAME}?user=${POSTGRES_USER}&password=${POSTGRES_PASS}

    And SQL Execute template1 "CREATE DATABASE ${DB_NAME}"
    And GeoGIG: Init repo ${DB_NAME} ${REPO_NAME}
    And Geoserver: Create Workspace ${WS_NAME}
    And Geoserver: Create GeoGIG Datastore ${WS_NAME} ${REPO_NAME} ${DS_NAME}
    And Create FeatureType ${WS_NAME} ${DS_NAME} ${LAYER_NAME} "geom:MultiPolygon:srid=3857,tag:String,featureNumber:Integer,groupNumber:Integer,numbInGroup:Integer,int1:Integer,string1:String,double1:Double,guid:String"
    And GeoGig: Execute "${GIG_REPO}" "index create --tree ${LAYER_NAME} --attribute geom -e featureNumber"

    And I setup a transaction against WFS,MEMORY
    And      I insert 1 features "geom=MULTIPOLYGON(((10 10, 10 11, 11 11,11 10,10 10)));tag=group1;featureNumber=${currentFeatureNumber};groupNumber=${currentGroupNumber};numbInGroup=${currentFeatureNumbInGroup};int1=111;string1=my string;double1=666;guid=guid.s"
    And      I insert 1 features "tag=group1;featureNumber=${currentFeatureNumber};groupNumber=${currentGroupNumber};numbInGroup=${currentFeatureNumbInGroup};int1=111;string1=my string;double1=666;guid=guid.s"
    And I commit the transaction

    Then GeoGIG: Verify the Index Exists "${GIG_REPO}" ${LAYER_NAME} WITH featureNumber
    Then GeoGIG: Verify Tree and Feature Bounds "${GIG_REPO}" ${LAYER_NAME} against INDEX,CANONICAL
    Then GeoGIG: Verify Index Extra Data "${GIG_REPO}" ${LAYER_NAME}
    Then GeoGIG: Verify Tree Names "${GIG_REPO}" ${LAYER_NAME}

    When I Query "INCLUDE" against WFS,MEMORY
    Then Assert Query results are equivalent