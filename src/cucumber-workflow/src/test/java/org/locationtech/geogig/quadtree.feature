Feature: QuadTree

  Background:
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


Scenario: Quadtree root unpromotable

    Given Create FeatureType ${WS_NAME} ${DS_NAME} ${LAYER_NAME} "geom:MultiPolygon:srid=4326,tag:String,featureNumber:Integer,groupNumber:Integer,numbInGroup:Integer,int1:Integer,string1:String,double1:Double,guid:String"
    And GeoGig: Execute "${GIG_REPO}" "index create --tree ${LAYER_NAME} --attribute geom -e featureNumber"

    And I setup a transaction against WFS,MEMORY
    #these are un-promotable because they cross 0,0 (4326 epsg boundary)
    And      I insert 130 features "geom=MULTIPOLYGON(((-1 -1,-1 1,1 1,1 -1,-1 -1)));tag=group1;featureNumber=${currentFeatureNumber};groupNumber=${currentGroupNumber};numbInGroup=${currentFeatureNumbInGroup};int1=111;string1=my string;double1=666;guid=guid.s"
    And I commit the transaction

    When I Query "INCLUDE" against WFS,MEMORY
    Then Assert Query results are equivalent

    Then GeoGIG: Verify the Index Exists "${GIG_REPO}" ${LAYER_NAME} WITH featureNumber
    Then GeoGIG: Verify Tree and Feature Bounds "${GIG_REPO}" ${LAYER_NAME} against INDEX,CANONICAL
    Then GeoGIG: Verify Index Extra Data "${GIG_REPO}" ${LAYER_NAME}
    Then GeoGIG: Verify Tree Names "${GIG_REPO}" ${LAYER_NAME}

    And I setup a transaction against WFS,MEMORY
    And      Update set geom="MULTIPOLYGON(((0.1 0.1,0.2 0.1,0.2 0.2,0.1 0.2,0.1 0.1)))" WHERE "INCLUDE"
    And I commit the transaction

    When I Query "INCLUDE" against WFS,MEMORY
    Then Assert Query results are equivalent

    Then GeoGIG: Verify the Index Exists "${GIG_REPO}" ${LAYER_NAME} WITH featureNumber
    Then GeoGIG: Verify Tree and Feature Bounds "${GIG_REPO}" ${LAYER_NAME} against INDEX,CANONICAL
    Then GeoGIG: Verify Index Extra Data "${GIG_REPO}" ${LAYER_NAME}
    Then GeoGIG: Verify Tree Names "${GIG_REPO}" ${LAYER_NAME}


  Scenario: Quadtree update extraAttribute value

    Given Create FeatureType ${WS_NAME} ${DS_NAME} ${LAYER_NAME} "geom:MultiPolygon:srid=4326,tag:String,featureNumber:Integer,groupNumber:Integer,numbInGroup:Integer,int1:Integer,string1:String,double1:Double,guid:String,date:Date"
    And GeoGig: Execute "${GIG_REPO}" "index create --tree ${LAYER_NAME} --attribute geom -e date"

    And I setup a transaction against WFS,MEMORY
    #these are un-promotable because they cross 0,0 (4326 epsg boundary)
    And      I insert 130 features "geom=MULTIPOLYGON(((-1 -1,-1 1,1 1,1 -1,-1 -1)));tag=group1;featureNumber=${currentFeatureNumber};groupNumber=${currentGroupNumber};numbInGroup=${currentFeatureNumbInGroup};int1=111;string1=my string;double1=666;guid=guid.s;date=2000-01-01 01:01:01"
    And I commit the transaction

    When I Query "INCLUDE" against WFS,MEMORY
    Then Assert Query results are equivalent

    Then GeoGIG: Verify the Index Exists "${GIG_REPO}" ${LAYER_NAME} WITH featureNumber
    Then GeoGIG: Verify Tree and Feature Bounds "${GIG_REPO}" ${LAYER_NAME} against INDEX,CANONICAL
    Then GeoGIG: Verify Index Extra Data "${GIG_REPO}" ${LAYER_NAME}
    Then GeoGIG: Verify Tree Names "${GIG_REPO}" ${LAYER_NAME}

    And I setup a transaction against WFS,MEMORY
    And      Update set date="2000-02-02 02:02:02TZ" WHERE "INCLUDE"
    And I commit the transaction

    When I Query "INCLUDE" against WFS,MEMORY
    Then Assert Query results are equivalent

    Then GeoGIG: Verify the Index Exists "${GIG_REPO}" ${LAYER_NAME} WITH featureNumber
    Then GeoGIG: Verify Tree and Feature Bounds "${GIG_REPO}" ${LAYER_NAME} against INDEX,CANONICAL
    Then GeoGIG: Verify Index Extra Data "${GIG_REPO}" ${LAYER_NAME}
    Then GeoGIG: Verify Tree Names "${GIG_REPO}" ${LAYER_NAME}