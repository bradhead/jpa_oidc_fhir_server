package ca.uhn.fhir.jpa.starter;

import java.text.ParseException;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.hl7.fhir.r4.model.IdType;
import org.mitre.jwt.signer.service.JWTSigningAndValidationService;
import org.mitre.jwt.signer.service.impl.JWKSetCacheService;
import org.mitre.openid.connect.client.service.ServerConfigurationService;
import org.mitre.openid.connect.config.ServerConfiguration;

import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Service;

import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.interceptor.auth.AuthorizationInterceptor;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRule;
import ca.uhn.fhir.rest.server.interceptor.auth.RuleBuilder;

@SuppressWarnings("ConstantConditions")
@Service()
@Configurable()
public class OIDCAuthorizationInterceptor extends AuthorizationInterceptor {

	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(OIDCAuthorizationInterceptor.class);

    private int myTimeSkewAllowance = 300;

	private String authHeader = HapiProperties.getAccessTokenHeaderName(); 
	private String tokenPrefix = HapiProperties.getAccessTokenHeaderPrefix(); 
	
	private ServerConfigurationService myServerConfigurationService;

    private JWKSetCacheService myValidationServices;
    
    OIDCAuthorizationInterceptor () {
        super();
		myValidationServices = new JWKSetCacheService();
    }

	public void setServerConfigurationService(ServerConfigurationService service) {
		myServerConfigurationService = service;
	}

	public void authenticate(RequestDetails theRequest) throws AuthenticationException {
		ourLog.info("Getting auth header with name '" + authHeader + "' and prefix '" + tokenPrefix + "'");
		String token = theRequest.getHeader(authHeader);
		if (token == null) {
			throw new AuthenticationException("Not authorized (no authorization header found in request)");
		}
		if (!token.startsWith(tokenPrefix)) {
			throw new AuthenticationException("Not authorized (authorization header does not contain a bearer token)");
		}

		token = token.substring(tokenPrefix.length());

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

    }

	public int getTimeSkewAllowance() {
		return myTimeSkewAllowance;
    }
        
   @Override
   public List<IAuthRule> buildRuleList(RequestDetails theRequestDetails) {
      
      IdType userIdPatientId = null;
      boolean userIsAdmin = true;
	  ourLog.info("Doing rulelist");
	  //ourLog.info();
	  try {
		  authenticate(theRequestDetails);
	  } catch (AuthenticationException ex) {
		return new RuleBuilder()
			.allow("Anonymous Metadata").metadata().andThen()
	  		.denyAll(ex.getMessage())
	  		.build();
	  }
      /*if ("Bearer dfw98h38r".equals(authHeader)) {
         // This user has access only to Patient/1 resources
         userIdPatientId = new IdType("Patient", 1L);
      } else if ("Bearer 39ff939jgg".equals(authHeader)) {
         // This user has access to everything
         userIsAdmin = true;
      } else {
         // Throw an HTTP 401
         throw new AuthenticationException("Missing or invalid Authorization header value");
      }*/

      // If the user is a specific patient, we create the following rule chain:
      // Allow the user to read anything in their own patient compartment
      // Allow the user to write anything in their own patient compartment
      // If a client request doesn't pass either of the above, deny it
      if (userIdPatientId != null) {
         return new RuleBuilder()
            .allow().read().allResources().inCompartment("Patient", userIdPatientId).andThen()
            .allow().write().allResources().inCompartment("Patient", userIdPatientId).andThen()
            .denyAll()
            .build();
      }
      
      // If the user is an admin, allow everything
      if (userIsAdmin) {
         return new RuleBuilder()
            .allowAll()
            .build();
      }
      
      // Fallback to deny everything (something unexppected happend if this is hit) 
 	  return new RuleBuilder()
         .denyAll()
         .build();
   }
}
