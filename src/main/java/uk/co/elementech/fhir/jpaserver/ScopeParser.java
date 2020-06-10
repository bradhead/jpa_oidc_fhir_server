package uk.co.elementech.fhir.jpaserver;

import java.util.ArrayList;
import java.util.Iterator;

public class ScopeParser {
   
    private ArrayList<SmartScope> scopesList = new ArrayList<SmartScope>();

    ScopeParser(String[] scopesArr) {
        parseScopes(scopesArr);
    }

    void parseScopes(String[] scopesArr) {
        for (String scope: scopesArr) {
            SmartScope smartScope = new SmartScope();
            if(smartScope.matchSmartScope(scope)) {
                scopesList.add(smartScope);
            }
        }
    }

    Boolean hasSmartScopes() {
        return (scopesList!=null) && (scopesList.size()>0);
    }

    Iterator<SmartScope> getSmartScopes(){
        return scopesList.iterator();
    }
}


