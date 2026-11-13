package tr.gov.kurum.identity.application.port.in;

/**
 * Both parts are optional (nullable): a browser may hold only one of the cookies, and an already expired
 * access token needs no revocation.
 */
public record LogoutCommand(String refreshToken, RevocableAccessToken accessToken) {

    @Override
    public String toString() {
        return "LogoutCommand[refreshToken=PROTECTED, accessToken=" + accessToken + "]";
    }
}
