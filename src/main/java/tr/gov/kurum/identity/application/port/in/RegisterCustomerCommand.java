package tr.gov.kurum.identity.application.port.in;

public record RegisterCustomerCommand(String email, String password, String firstName, String lastName) {

    @Override
    public String toString() {
        return "RegisterCustomerCommand[email=" + email + ", password=PROTECTED]";
    }
}
