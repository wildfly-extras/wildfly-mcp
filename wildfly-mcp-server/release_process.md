# Release mcp server

* mvn clean install
* mvn versions:set -DnewVersion=1.0.0.Alpha3
* mvn versions:commit
* git add *
* git commit -m "Release version 1.0.0.Alpha3"
* mvn clean deploy
* git tag 1.0.0.Alpha3
* git push upstream 1.0.0.Alpha3
* Release in nexus: https://repository.jboss.org/nexus
* mvn versions:set -DnewVersion=1.0.0.Alpha4-SNAPSHOT
* mvn versions:commit
* git add *
* git commit -m "New development iteration"
* git push upstream main

# Update the jbang-catalog

Open PR against https://github.com/mcp-java/jbang-catalog to update to the released version.
 
