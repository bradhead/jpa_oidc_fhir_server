package ca.uhn.fhir.jpa.starter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmartScope {
	
    private String specificity;
    private String resource;
    private String operation;

    private Pattern pattern = Pattern.compile("^(patient|user)/([^.]+)\\.(.+)$");

    private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(OIDCAuthorizationInterceptor.class);

    void ScopeParser(){
    }

    Boolean matchSmartScope(String scope){
        Matcher m = pattern.matcher(scope);
        if (m.find()) {
            specificity = m.group(1);
            resource = m.group(2);
            operation = m.group(3);
            return true;
        }
        return false;
    }

    public Boolean isPatient(){
        return specificity.equals("patient");
    }

    public Boolean isUser(){
        return specificity.equals("user");
    }

    public Boolean canRead(){
        ourLog.info("Checking can read from operation '" + operation + "'");
        return ((operation.equals("read")) || (operation.equals("*")));
    }

    public Boolean canWrite(){
        return ((operation.equals("write")) || (operation.equals("*")));
    }

    public Boolean allResourceS(){
        return resource.equals("*");
    }

    public String getResource(){
        return resource;
    }
}
