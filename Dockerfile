FROM maven:3.6.2-jdk-11 as buildchain
RUN mkdir -p /usr/src/fhirjpa
WORKDIR /usr/src/fhirjpa
COPY . /usr/src/fhirjpa
RUN mvn -e -B dependency:resolve
RUN mvn clean install -DskipTests

FROM tomcat:9-jre11
RUN mkdir -p /data/hapi/lucenefiles && chmod 775 /data/hapi/lucenefiles
COPY --from=buildchain /usr/src/fhirjpa/target/*.war /usr/local/tomcat/webapps/
COPY server.xml /usr/local/tomcat/conf/server.xml
EXPOSE 8080

CMD ["catalina.sh", "run"]