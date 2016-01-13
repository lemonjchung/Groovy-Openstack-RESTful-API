/*
 * Copyright 2012 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.apache.ivy.plugins.resolver.URLResolver

/*//grails.project.fork.run
grails.project.fork = [
  test: false,
  run: [maxMemory: 1g, minMemory: 256, debug: true, maxPerm: 512, forkReserve: false],
  war: [maxMemory: 1g, minMemory: 256, debug: false, maxPerm: 512, forkReserve: false],
  console: [maxMemory: 1g, minMemory: 256, debug: false, maxPerm: 512]]*/

grails.project.work.dir = 'work'
grails.project.class.dir = 'target/classes'
grails.project.test.class.dir = 'target/test-classes'
grails.project.test.reports.dir = 'target/test-reports'
grails.project.war.file = "target/${appName}.war"

tomcat.deploy.username = "manager"
tomcat.deploy.password = "secret"
tomcat.deploy.url = "http://localhost:9000/manager"


grails.tomcat.jvmArgs = [
  "-server", "-XX:MaxPermSize=512m", "-XX:MaxNewSize=256m", "-XX:NewSize=256m",
  "-Xms768m", "-Xmx1024m", "-XX:SurvivorRatio=128", "-XX:MaxTenuringThreshold=0",
  "-XX:+UseTLAB", "-XX:+UseConcMarkSweepGC", "-XX:+CMSClassUnloadingEnabled",
  "-XX:+CMSIncrementalMode", "-XX:-UseGCOverheadLimit", "-XX:+ExplicitGCInvokesConcurrent"]

codenarc {
  reports = {
    AsgardXmlReport('xml') {
      outputFile = 'CodeNarc-Report.xml'
      title = 'Asgard CodeNarc Report'
    }
    AsgardHtmlReport('html') {
      outputFile = 'CodeNarc-Report.html'
      title = 'Asgard CodeNarc Report'
    }
  }
  ruleSetFiles = 'file:grails-app/conf/CodeNarcRuleSet.groovy'
  maxPriority1Violations = 0
  maxPriority2Violations = 0
  maxPriority3Violations = 0
}

grails.project.dependency.resolver="ivy"
grails.project.dependency.resolution = {
  // Inherit Grails' default dependencies
  inherits('global') {}
  legacyResolve true
  log 'warn'
  //log 'verbose'

  repositories {
    inherits true

     /*grailsPlugins()
     grailsHome()
     grailsCentral()
     mavenCentral()*/

    mavenRepo "http://artifactory.serve.com:8081/artifactory/repo1"
    mavenRepo "http://artifactory.serve.com:8081/artifactory/eg-java-release-local"
    mavenRepo "http://artifactory.serve.com:8081/artifactory/eg-java-snapshot-local"
    mavenRepo "http://artifactory.serve.com:8081/artifactory/sonatype.org/snapshots"
    mavenRepo "http://artifactory.serve.com:8081/artifactory/grails-plugins"

    // using virtual repo for all of dependencies. This is the default virtual repo that
    // Artifactory provides

    // Optional custom repository for dependencies.
    Closure internalRepo = {
      String repoUrl = 'http://artifactory.serve.com:8081/artifactory'  //'http://artifacts/ext-releases-local'
      String artifactPattern = '[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]'
      String ivyPattern = '[organisation]/[module]/[revision]/[module]-[revision]-ivy.[ext]'
      URLResolver urlLibResolver = new URLResolver()
      urlLibResolver.with {
        name = repoUrl
        addArtifactPattern("${repoUrl}/${artifactPattern}")
        addIvyPattern("${repoUrl}/${ivyPattern}")
        m2compatible = true
      }
      resolver urlLibResolver

      String localDir = System.getenv('IVY_LOCAL_REPO') ?: "${System.getProperty('user.home')}/ivy2-local"
      FileSystemResolver localLibResolver = new FileSystemResolver()
      localLibResolver.with {
        name = localDir
        addArtifactPattern("${localDir}/${artifactPattern}")
        addIvyPattern("${localDir}/${ivyPattern}")
      }
      resolver localLibResolver
    }
    // Comment or uncomment the next line to toggle the use of an internal artifacts repository.
    //internalRepo()
  }

  dependencies {

    compile(
      // Ease of use library for Amazon Simple Workflow Service (SWF), e.g., WorkflowClientFactory
      'com.netflix.glisten:glisten:0.3'
    ) {
      // If Glisten is using a different AWS Java SDK we don't want to pick up the transitive dep by accident.
      transitive = false
    }
      compile(
              'io.dropwizard.metrics:metrics-core:4.0.0-SNAPSHOT',
              'io.dropwizard.metrics:metrics-graphite:4.0.0-SNAPSHOT',
              'io.dropwizard.metrics:metrics-log4j:4.0.0-SNAPSHOT'
      )

    compile(
      // Transitive dependency of aws-java-sdk, but also used for JSON marshalling, e.g., JsonProperty
      'grails-docs:grails-docs:2.3.11',
      'com.fasterxml.jackson.core:jackson-core:2.3.2',
      'com.fasterxml.jackson.core:jackson-annotations:2.3.2',
      'com.fasterxml.jackson.core:jackson-databind:2.3.2',
      'org.glassfish.jersey.core:jersey-client:2.13',
      'org.glassfish.jersey.core:jersey-common:2.13',
      //'org.glassfish.jersey.core:jersey-guava:2.13',
      /*'com.sun.jersey.core:jersey-client:1.18.2',
      'com.sun.jersey.core:jersey-core:1.18.2',
      'com.sun.jersey.core:jersey-apache-client4:1.18.2',*/
      'org.pacesys:openstack4jwithdeps:2.0.0-SNAPSHOT',
      // 'javax:javaee-api:7.0',
      //'org.pacesys.openstack4j.connectors:openstack4j-jersey2:2.0.0-SNAPSHOT',
      //'com.fasterxml.jackson.core:jackson-jaxrs-json-provider:2.3.2',
      // Easier Java from Joshua Bloch and Google, e.g., Multiset, ImmutableSet, Maps, Table, Splitter
      'com.google.guava:guava:14.0.1'

    )

    compile(
      // Amazon Web Services programmatic interface. Transitive dependency of glisten, but also used directly.
      'com.amazonaws:aws-java-sdk:1.7.5',
      // 'com.amazonaws:aws-java-sdk:1.8.3',


      //'org.pacesys.openstack4j.connectors:openstack4j-httpclient:2.0.0-SNAPSHOT',


      // Enables publication of a health check URL for deploying Asgard, and an on/off switch for activities.
      // Transitive dependencies include:
      // rxjava, archaius, ribbon, servo, netflix-commons, netflix-statistics, jersey, guava
//      'com.netflix.eureka:eureka-client:1.1.127',

      // Transitive dependencies of eureka and aws-java-sdk, but also used for REST calls, e.g., HttpClient
      'org.apache.httpcomponents:httpcore:4.2',
      'org.apache.httpcomponents:httpclient:4.2.3',

      //YAML
      'org.yaml:snakeyaml:1.14',

      // Extra collection types and utilities, e.g., Bag
      'commons-collections:commons-collections:3.2.1',

      // Easier Java from of the Apache Foundation, e.g., WordUtils, DateUtils, StringUtils
      'commons-lang:commons-lang:2.4',

      // SSH calls to retrieve secret keys from remote servers, e.g., JSch, ChammelExec
      'com.jcraft:jsch:0.1.45',

      // Send emails about system errors and task completions
      'javax.mail:mail:1.4.3',

      // Better date API, e.g., DateTime
      'joda-time:joda-time:1.6.2',

      // Static analysis for Groovy code.
      'org.codenarc:CodeNarc:0.19',

      // This fixes ivy resolution issues we had with our transitive dependency on 1.4.
      'commons-codec:commons-codec:1.5',

      // Call Perforce in process. Delete when user data no longer come from Perforce at deployment time.
      'com.perforce:p4java:2010.1.269249',

      // Rules for AWS named objects, e.g., Names, AppVersion
      'com.netflix.frigga:frigga:0.11',

      // Groovy concurrency framework, e.g., GParsExecutorsPool, Dataflow, Promise
      'org.codehaus.gpars:gpars:1.0.0',

      // Used for JSON parsing of AWS Simple Workflow Service metadata.
      // Previously this was an indirect depencency through Grails itself, but this caused errors in some
      // Grails environments.
      'com.googlecode.json-simple:json-simple:1.1',

      "org.codehaus.groovy.modules.http-builder:http-builder:0.7.2"

      // 'org.mongodb:mongo-java-driver:2.12.2'


    ) { // Exclude superfluous and dangerous transitive dependencies
      excludes(
        // Some libraries bring older versions of JUnit as a transitive dependency and that can interfere
        // with Grails' built in JUnit
        'junit',
        'mockito-core',
       // 'guava',
        //'jersey-client',
        'jersey-core',
        'jersey-apache-client4',
        'jackson-annotations',
        'jackson-core',
        'jackson-jaxrs-base',
        //'jackson-jaxrs-json-provider',
      )
    }

    // Spock in Grails 2.2.x http://grails.org/plugin/spock
    test "org.spockframework:spock-grails-support:0.7-groovy-2.0"

    // Optional dependency for Spock to support mocking objects without a parameterless constructor.
    test 'org.objenesis:objenesis:1.2'
  }

  plugins {
    runtime ":hibernate:2.2.4", {
      export = false
    }
    compile ":compress:0.4"
    compile ":context-param:1.0"
    compile(":shiro:1.2.0") {
      excludes "hibernate"
    }
    compile ":standalone:1.1.1"
    compile ":mongodb:3.0.2"
    runtime ":cors:1.0.4"

    compile "com.netflix.eureka:eureka-client:1.1.127", {
      export=false
    }

    // Spock in Grails 2.2.x http://grails.org/plugin/spock
    test(":spock:0.7") {
      exclude "spock-grails-support"
    }

    test ':code-coverage:1.2.5'
    build ":tomcat:7.0.54"
    compile ':scaffolding:1.0.0'


  }
}
