package comun;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.HexFormat;

/** Huella SHA-256 de un certificado, para que el usuario pueda comprobar que habla con el servidor correcto. */
public final class Huella {
    private Huella() {}

    public static String sha256(Certificate certificado) throws CertificateEncodingException {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(certificado.getEncoded());
            return HexFormat.ofDelimiter(":").withUpperCase().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
