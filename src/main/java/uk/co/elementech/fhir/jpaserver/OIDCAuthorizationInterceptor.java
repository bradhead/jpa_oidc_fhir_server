//package ca.uhn.fhir.jpa.starter;
package uk.co.elementech.fhir.jpaserver;

import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.hl7.fhir.r4.model.IdType;
import org.mitre.jwt.signer.service.JWTSigningAndValidationService;
import org.mitre.jwt.signer.service.impl.JWKSetCacheService;
import org.mitre.openid.connect.client.service.ServerConfigurationService;
import org.mitre.openid.connect.config.ServerConfiguration;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.stereotype.Service;

import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.interceptor.auth.AuthorizationInterceptor;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRule;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRuleBuilderRule;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRuleBuilderRuleOpClassifier;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRuleBuilderRuleOpClassifierFinished;
import ca.uhn.fhir.rest.server.interceptor.auth.RuleBuilder;

@SuppressWarnings("ConstantConditions")
@Service()
@Configurable()
public class OIDCAuthorizationInterceptor extends AuthorizationInterceptor {

	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(OIDCAuthorizationInterceptor.class);

    private int myTimeSkewAllowance = 300;

	private String authHeader = Constants.HEADER_AUTHORIZATION; 
	private String tokenPrefix = Constants.HEADER_AUTHORIZATION_VALPREFIX_BEARER; 
	private String altAuthHeader = HapiProperties.getAccessTokenHeaderName(); 
	private String altTokenPrefix = HapiProperties.getAccessTokenHeaderPrefix(); 
	
	private ServerConfigurationService myServerConfigurationService;

    private JWKSetCacheService myValidationServices;
	
    OIDCAuthorizationInterceptor () {
        super();
		myValidationServices = new JWKSetCacheService();
    }

	public void setServerConfigurationService(ServerConfigurationService service) {
		myServerConfigurationService = service;
	}

	public SignedJWT authenticate(RequestDetails theRequest) throws AuthenticationException {
		//Check for a "standard" bearer token first. 
		//If the auth was handled by Kong we get the access token in a different header so check for that too....
		ourLog.debug("Getting auth header with name '" + authHeader + "' and prefix '" + tokenPrefix + "'");
		String token = theRequest.getHeader(authHeader);
		if (token == null)  {
			if (altAuthHeader!=null) {
				ourLog.debug("Checking alt auth header with name '" + altAuthHeader + "'");
				token = theRequest.getHeader(altAuthHeader);
			}
			if (token == null) {
				throw new AuthenticationException("Not authorized (no authorization header found in request)");
			}
		}
		if (!token.startsWith(tokenPrefix)) {
			ourLog.debug("Checking alt token prefix '" + altTokenPrefix + "'");
			if (!token.startsWith(altTokenPrefix)) {
				throw new AuthenticationException("Not authorized (authorization header does not contain a bearer token)");
			}
			token = token.substring(altTokenPrefix.length());
		} else {
			token = token.substring(tokenPrefix.length());
		}
		ourLog.info("Got token:" + token);
		SignedJWT idToken;
		try {
			idToken = SignedJWT.parse(token);
		} catch (ParseException e) {
			throw new AuthenticationException("Not authorized (bearer token could not be validated)", e);
		}

		// validate our ID Token over a number of tests
		JWTClaimsSet idClaims;
		try {
			idClaims = idToken.getJWTClaimsSet();
		} catch (ParseException e) {
			throw new AuthenticationException("Not authorized (bearer token could not be validated)", e);
		}

		String issuer = idClaims.getIssuer();

		ourLog.info("Getting server config for issuer '" + issuer + "'");
		if (myServerConfigurationService == null){
			throw new AuthenticationException("Server config is null");
		}

		ServerConfiguration serverConfig = myServerConfigurationService.getServerConfiguration(issuer);
		if (serverConfig == null) {
			ourLog.error("No server configuration found for issuer: " + issuer);
			throw new AuthenticationException("Not authorized (no server configuration found for issuer " + issuer + ")");
		}

		// check the signature
		JWTSigningAndValidationService jwtValidator = null;

		JWSAlgorithm alg = idToken.getHeader().getAlgorithm();
		if (alg.equals(JWSAlgorithm.HS256) || alg.equals(JWSAlgorithm.HS384) || alg.equals(JWSAlgorithm.HS512)) {

			ourLog.info("Checking signature using client secret");
			throw new AuthenticationException("Not authorized. Signature algorithm not supported");
		} else {
			// otherwise load from the server's public key
			ourLog.info("checking signature using public key");
			jwtValidator = myValidationServices.getValidator(serverConfig.getJwksUri());
		}

		if (jwtValidator != null) {
			if (!jwtValidator.validateSignature(idToken)) {
				throw new AuthenticationException("Not authorized (signature validation failed)");
			}
		} else {
			ourLog.error("No validation service found. Skipping signature validation");
			throw new AuthenticationException("Not authorized (can't determine signature validator)");
		}

		// check expiration
		if (idClaims.getExpirationTime() == null) {
			throw new AuthenticationException("Id Token does not have required expiration claim");
		} else {
			// it's not null, see if it's expired
			Date minAllowableExpirationTime = new Date(System.currentTimeMillis() - (myTimeSkewAllowance * 1000L));
			Date expirationTime = idClaims.getExpirationTime();
			if (!expirationTime.after(minAllowableExpirationTime)) {
				throw new AuthenticationException("Id Token is expired: " + idClaims.getExpirationTime());
			}
		}

		// check not before
		if (idClaims.getNotBeforeTime() != null) {
			Date now = new Date(System.currentTimeMillis() + (myTimeSkewAllowance * 1000));
			if (now.before(idClaims.getNotBeforeTime())) {
				throw new AuthenticationException("Id Token not valid untill: " + idClaims.getNotBeforeTime());
			}
		}

		// check issued at
		if (idClaims.getIssueTime() == null) {
			throw new AuthenticationException("Id Token does not have required issued-at claim");
		} else {
			// since it's not null, see if it was issued in the future
			Date now = new Date(System.currentTimeMillis() + (myTimeSkewAllowance * 1000));
			if (now.before(idClaims.getIssueTime())) {
				throw new AuthenticationException("Id Token was issued in the future: " + idClaims.getIssueTime());
			}
		}
		return idToken;
    }

	public int getTimeSkewAllowance() {
		return myTimeSkewAllowance;
    }
		
   @Override
   public List<IAuthRule> buildRuleList(RequestDetails theRequestDetails) {
      
		IdType userIdPatientId = null;
		SignedJWT token;
		ourLog.info("Doing rulelist");
		ourLog.info("OIDC value is " + HapiProperties.getOIDCEnabled());
		if(!HapiProperties.getOIDCEnabled()){ //Auth is disabled - just allow everything. Hopefully we're just testing!
			return new RuleBuilder()
				.allowAll().build();
		}
		try {
			token = authenticate(theRequestDetails);
		} catch (AuthenticationException ex) {
			return new RuleBuilder()
				.allow("Anonymous Metadata").metadata().andThen()
				.denyAll(ex.getMessage())
				.build();
		}

		String[] scopes;
		try {
			scopes = token.getJWTClaimsSet().getStringClaim("scope").split(" ");
		} catch (ParseException ex) {
			return new RuleBuilder()
				.allow("Anonymous Metadata").metadata().andThen()
				.denyAll(ex.getMessage())
				.build();
		}
			
		ScopeParser scopeParser = new ScopeParser(scopes);
		if(!scopeParser.hasSmartScopes()){
			return new RuleBuilder()
				.allow("").metadata().andThen()
				.denyAll("No scope found")
				.build();	
		}

		RuleBuilder rules = new RuleBuilder();
		Iterator<SmartScope> smartScopes = scopeParser.getSmartScopes();
		ourLog.info("Checking for smart scopes");
		while(smartScopes.hasNext()){
			ourLog.info("got smart scopes");
			SmartScope s = smartScopes.next();
			IAuthRuleBuilderRuleOpClassifier classifier = null;
			if(s.canRead()) {
				ourLog.info("Adding read rule");
				IAuthRuleBuilderRule rule = rules.allow();
				if(s.getResource().equals("*")){
					ourLog.info("Adding wildcard resource");
					classifier = rule.read().allResources();
				} else {
					classifier = rule.read().resourcesOfType(s.getResource());
				}
				if(s.isPatient()) { 
					userIdPatientId = getPatientClaim(token);
					if(userIdPatientId==null){
						return new RuleBuilder()
						.allow("").metadata().andThen()
						.denyAll("No patient claim found")
						.build();			
					}
					rules = (RuleBuilder) classifier.inCompartment("Patient",userIdPatientId).andThen();
				} else {
					ourLog.info("Adding user level access");
					rules = (RuleBuilder) classifier.withAnyId().andThen();
				}
			}
			if(s.canWrite()) {
				ourLog.info("Adding write rule");
				IAuthRuleBuilderRule rule = rules.allow();
				if(s.getResource().equals("*")){
					classifier = rule.write().allResources();
				} else {
					classifier = rule.write().resourcesOfType(s.getResource());
				}
				if(s.isPatient()) { 
					userIdPatientId = getPatientClaim(token);
					if(userIdPatientId==null){
						return new RuleBuilder()
						.allow("").metadata().andThen()
						.denyAll("No patient claim found")
						.build();			
					}
					rules = (RuleBuilder) classifier.inCompartment("Patient",userIdPatientId).andThen();
				} else {
					rules = (RuleBuilder) classifier.withAnyId().andThen();
				}
			}
		}
		List<IAuthRule> r = rules.allow().metadata().andThen().denyAll().build();
		ourLog.info(r.toString());
		return r;
	}

	private IdType getPatientClaim(SignedJWT token){
		try {
			String patientClaim = token.getJWTClaimsSet().getStringClaim("patient");
			return new IdType(patientClaim);
		} catch(ParseException ex){
			ourLog.info("Failed to parse patient from token");
			return null;
		}
	}
}

