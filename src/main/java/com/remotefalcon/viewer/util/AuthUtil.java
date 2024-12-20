package com.remotefalcon.viewer.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.remotefalcon.library.documents.Show;
import com.remotefalcon.viewer.dto.TokenDTO;
import com.remotefalcon.library.enums.StatusResponse;
import com.remotefalcon.viewer.repository.ShowRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthUtil {
  private final ShowRepository showRepository;

  @Value("${jwt.viewer}")
  String jwtSignKey;

  public TokenDTO tokenDTO;

  public TokenDTO getJwtPayload(String token) {
    try {
      DecodedJWT decodedJWT = JWT.decode(token);
      String showSubdomain = decodedJWT.getClaims().get("showSubdomain").asString();
      return TokenDTO.builder()
              .showSubdomain(showSubdomain)
              .build();
    }catch (JWTDecodeException jde) {
      throw new RuntimeException(StatusResponse.INVALID_JWT.name());
    }
  }

  public Boolean isJwtValid(HttpServletRequest httpServletRequest) throws JWTVerificationException {
    try {
      String token = this.getTokenFromRequest(httpServletRequest);
      if (StringUtils.isEmpty(token)) {
        throw new RuntimeException(StatusResponse.INVALID_JWT.name());
      }
      Algorithm algorithm = Algorithm.HMAC256(jwtSignKey);
      JWTVerifier verifier = JWT.require(algorithm).withIssuer("remotefalcon").build();
      verifier.verify(token);
      this.tokenDTO = getJwtPayload(token);
      return true;
    } catch (JWTVerificationException e) {
      throw new RuntimeException(StatusResponse.INVALID_JWT.name());
    }
  }

  public Boolean isApiJwtValid(HttpServletRequest httpServletRequest) throws JWTVerificationException {
    try {
      String token = this.getTokenFromRequest(httpServletRequest);
      if (StringUtils.isEmpty(token)) {
        return false;
      }
      DecodedJWT decodedJWT = JWT.decode(token);
      String accessToken = decodedJWT.getClaims().get("accessToken").asString();
      Optional<Show> show = this.showRepository.findByApiAccessApiAccessToken(accessToken);
      if(show.isEmpty()) {
        return false;
      }
      Algorithm algorithm = Algorithm.HMAC256(show.get().getApiAccess().getApiAccessSecret());
      JWTVerifier verifier = JWT.require(algorithm).build();
      verifier.verify(token);
      this.tokenDTO = TokenDTO.builder().showSubdomain(show.get().getShowSubdomain()).build();
      return true;
    } catch (JWTVerificationException e) {
      return false;
    }
  }

  private String getTokenFromRequest(HttpServletRequest httpServletRequest) {
    String token = "";
    final String authorization = httpServletRequest.getHeader("Authorization");
    if (authorization != null && authorization.toLowerCase().startsWith("bearer")) {
      try {
        token = authorization.split(" ")[1];
      }catch (Exception e) {
        throw new RuntimeException(StatusResponse.INVALID_JWT.name());
      }
    }
    return token;
  }
}
