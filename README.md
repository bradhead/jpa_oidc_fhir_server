# HAPI-FHIR-SMART Starter Project

This project is a customisation of the HAPI-FHIR complete starter project you can use to deploy a FHIR server using HAPI FHIR JPA. The project adds support for Open Id Connect and for Smart on FHIR token scopes.

It does not include the default testing overlay normally included with the HAPI FHIR Starter project. A client application that supports the OIDC/OAuth2 authentication of this server is available [here](#todo)

## Prerequisites

To run this project locally, you should have:

- [This project](https://github.com/elementechemlyn/jpa_fhir_server) checked out. You may wish to create a GitHub Fork of the project and check that out instead so that you can customize the project and save the results to GitHub.
- Oracle Java (JDK) installed: Minimum JDK8 or newer.
- Apache Maven build tool (newest version)

To build and run this project in Docker you will only need the Docker runtime as the buildchain is included in the Dockerfile.

## Running locally

The easiest way to run this server is to run it directly in Maven using a built-in Jetty server. To do this, change `src/main/resources/hapi.properties` `server_address` and `server.base` with the values commented out as _For Jetty, use this_ and then execute the following command:

```bash
mvn jetty:run
```

Then, browse to the following link to use the server:

[http://localhost:8080/hapi-fhir-jpaserver/](http://localhost:8080/hapi-fhir-jpaserver/)

If you need to run this server on a different port (using Maven), you can change the port in the run command as follows:

```bash
mvn -Djetty.port=8888 jetty:run
```

And replacing 8888 with the port of your choice.

## Running in Docker
If you don't wish to (or can't) install all the dependencies for this project the Dockerfile in this project includes a buildchain that will download all the Maven dependencies, build the war file and copy the war file to a image running Tomcat. 

To build the image:  

`docker build -t yourrepo\yourimagename .`

To run the image:  

`docker run -p8080:8080 yourrepo\yourimagename`

The server will be available at:  

`http://localhost:8080/hapi-fhir-jpaserver/fhir/`

## Configurations

Much of this HAPI starter project can be configured using the properties file in _src/main/resources/hapi.properties_. By default, this starter project is configured to use Derby as the database.

### Token validation

By default only the metadata endpoint of the server is available without authentication.  

For all other endpoints you will need to include a BEARER token which includes at least one [SMART on FHIR scope](http://hl7.org/fhir/smart-app-launch/0.8.0/scopes-and-launch-context/).  

To validate the token, the issuing server must be whitelisted in the hapi.properties file. e.g:   

`oauth.whitelist=http://lhcr1:8081/auth/realms/lhcr1`

This URL is used to retrieve the well-known configuration of the auth server to enable the vlaidation of tokens.

### Alternative token headers

The hapi.properties file also allows the specification of an alternative header for location of the token. The server will look for a standard authroization header with a BEARER token and then fall back to the alternative header e.g.  
```
oauth.token.name=X-Access-Token
oauth.token.prefix=MYTOKEN
```
will cause the server to look in the header `X-Access-Token` for a token with a prefix of `MYTOKEN`. This is useful if the server is to be run behind a proxy which does not pass BEARER tokens directly or if a custom authentication scheme is used. The token must still be a valid OAUTH2 access token.

### MySql configuration

To configure the starter app to use MySQL, instead of the default Derby, update the hapi.properties file to have the following:

- datasource.driver=com.mysql.jdbc.Driver
- datasource.url=jdbc:mysql://localhost:3306/hapi_dstu3
- hibernate.dialect=org.hibernate.dialect.MySQL5InnoDBDialect
- datasource.username=admin
- datasource.password=admin

### PostgreSQL configuration

To configure the starter app to use PostgreSQL, instead of the default Derby, update the hapi.properties file to have the following:

- datasource.driver=org.postgresql.Driver
- datasource.url=jdbc:postgresql://localhost:5432/hapi_dstu3
- hibernate.dialect=org.hibernate.dialect.PostgreSQL95Dialect
- datasource.username=admin
- datasource.password=admin

Because the integration tests within the project rely on the default Derby database configuration, it is important to either explicity skip the integration tests during the build process, i.e., `mvn install -DskipTests`, or delete the tests altogether. Failure to skip or delete the tests once you've configured PostgreSQL for the datasource.driver, datasource.url, and hibernate.dialect as outlined above will result in build errors and compilation failure.

It is important to use PostgreSQL95Dialect when using PostgreSQL version 10+.

## Overriding application properties

You can override the properties that are loaded into the compiled web app (.war file) making a copy of the hapi.properties file on the file system, making changes to it, and then setting the JAVA_OPTS environment variable on the tomcat server to tell hapi-jpaserver-starter where the overriding properties file is. For example:

`-Dhapi.properties=/some/custom/directory/hapi.properties`

Note: This property name and the path is case-sensitive. "-DHAPI.PROPERTIES=XXX" will not work.

## Deploying a local build to a Container

Using the Maven-Embedded Jetty method above is convenient, but it is not a good solution if you want to leave the server running in the background.

Most people who are using HAPI FHIR JPA as a server that is accessible to other people (whether internally on your network or publically hosted) will do so using an Application Server, such as [Apache Tomcat](http://tomcat.apache.org/) or [Jetty](https://www.eclipse.org/jetty/). Note that any Servlet 3.0+ compatible Web Container will work (e.g Wildfly, Websphere, etc.).

Tomcat is very popular, so it is a good choice simply because you will be able to find many tutorials online. Jetty is a great alternative due to its fast startup time and good overall performance.

To deploy to a container, you should first build the project:

```bash
mvn clean install
```

This will create a file called `hapi-fhir-jpaserver.war` in your `target` directory. This should be installed in your Web Container according to the instructions for your particular container. For example, if you are using Tomcat, you will want to copy this file to the `webapps/` directory.

Again, browse to the following link to use the server (note that the port 8080 may not be correct depending on how your server is configured).

[http://localhost:8080/hapi-fhir-jpaserver/](http://localhost:8080/hapi-fhir-jpaserver/)


## Enabling Subscriptions

The server may be configured with subscription support by enabling properties in the [hapi.properties](https://github.com/hapifhir/hapi-fhir-jpaserver-starter/blob/master/src/main/resources/hapi.properties) file:

- `subscription.resthook.enabled` - Enables REST Hook subscriptions, where the server will make an outgoing connection to a remote REST server

- `subscription.email.enabled` - Enables email subscriptions. Note that you must also provide the connection details for a usable SMTP server.

- `subscription.websocket.enabled` - Enables websocket subscriptions. With this enabled, your server will accept incoming websocket connections on the following URL (this example uses the default context path and port, you may need to tweak depending on your deployment environment): [ws://localhost:8080/hapi-fhir-jpaserver/websocket](ws://localhost:8080/hapi-fhir-jpaserver/websocket)

## Using Elasticsearch

By default, the server will use embedded lucene indexes for terminology and fulltext indexing purposes. You can switch this to using lucene by editing the properties in [hapi.properties](https://github.com/hapifhir/hapi-fhir-jpaserver-starter/blob/master/src/main/resources/hapi.properties)

For example:

```properties
elasticsearch.enabled=true
elasticsearch.rest_url=http://localhost:9200
elasticsearch.username=SomeUsername
elasticsearch.password=SomePassword
elasticsearch.required_index_status=YELLOW
elasticsearch.schema_management_strategy=CREATE
```
