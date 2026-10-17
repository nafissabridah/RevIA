public class Mailer {
    private static final String SMTP_PASSWORD = "Sup3rS3cret!";

    public void envoyer(String destinataire, String message) {
        Session session = Session.getInstance(proprietes(), authentificateur(SMTP_PASSWORD));
        Transport.send(new MimeMessage(session));
    }
}
