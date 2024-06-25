# RSpace Snapgene Adapter

This is Java RESTclient for accessing the Snapgene webservice by RSpace.
Depends on code published in rspace-os/snapgene-java-client.

## Tests
The integration tests in
`src/test/java/com/researchspace/snapgene/wclient/SnapgeneWSClientITTest.java` require a snapgene server
instance and are therefore @Ignored. 

To run them, set `snapgene.web.url` in `src/main/resources/application.properties`  
and remove the `@Ignore` annotation on the test class.