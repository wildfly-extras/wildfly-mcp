# Release mcp server

* mvn clean install
* mvn versions:set -DnewVersion=1.0.0.Alpha3
* mvn versions:commit
* git add *
* git commit -m "Release version 1.0.0.Alpha3"
* mvn -Pjboss-release -Pjboss-staging-deploy deploy
* Check that all is ok on https://repository.jboss.org/nexus/#browse/browse:wildfly-extras-staging
* mvn -Pjboss-staging-move nxrm3:staging-move
* git tag 1.0.0.Alpha3
* git push upstream 1.0.0.Alpha3
* mvn versions:set -DnewVersion=1.0.0.Alpha4-SNAPSHOT
* mvn versions:commit
* git add *
* git commit -m "New development iteration"
* git push upstream main

# Update the jbang-catalog

Open PR against https://github.com/mcp-java/jbang-catalog to update to the released version.
 
