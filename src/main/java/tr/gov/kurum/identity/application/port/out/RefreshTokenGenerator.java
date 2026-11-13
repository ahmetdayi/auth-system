package tr.gov.kurum.identity.application.port.out;

public interface RefreshTokenGenerator {

    /** A new opaque, unguessable refresh token (not a JWT). */
    String generate();
}
