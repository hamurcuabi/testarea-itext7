package mkl.testarea.itext7.signature;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfOutline;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.kernel.pdf.navigation.PdfExplicitDestination;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.signatures.BouncyCastleDigest;
import com.itextpdf.signatures.DigestAlgorithms;
import com.itextpdf.signatures.IExternalDigest;
import com.itextpdf.signatures.IExternalSignature;
import com.itextpdf.signatures.PdfSignatureAppearance;
import com.itextpdf.signatures.PdfSigner;
import com.itextpdf.signatures.PdfSigner.CryptoStandard;
import com.itextpdf.signatures.PrivateKeySignature;

/**
 * @author mkl
 */
public class MultipleSignaturesAndTagging {
    final static File RESULT_FOLDER = new File("target/test-outputs", "signature");

    public static final String KEYSTORE = "keystores/demo-rsa2048.p12"; 
    public static final char[] PASSWORD = "demo-rsa2048".toCharArray(); 

    public static KeyStore ks = null;
    public static PrivateKey pk = null;
    public static Certificate[] chain = null;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        RESULT_FOLDER.mkdirs();

        BouncyCastleProvider bcp = new BouncyCastleProvider();
        Security.addProvider(bcp);

        ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(new FileInputStream(KEYSTORE), PASSWORD);
        String alias = (String) ks.aliases().nextElement();
        pk = (PrivateKey) ks.getKey(alias, PASSWORD);
        chain = ks.getCertificateChain(alias);
    }

    /**
     * <p>
     * This test illustrates an issue with tagged PDFs and multiple signatures
     * </p>
     * <p>
     * The first signature signs a PDF with an Outline element which
     * points to a structure element which is changed by the second
     * signature due to adding a reference to the second signature
     * field to the structure tree. As a result Adobe Reader currently
     * claims that the first signature is invalidated by disallowed
     * changes.
     * </p> 
     */
    @Test
    public void testSignSimpleTaggedPdfOutlineTwiceCms() throws IOException, GeneralSecurityException {
        File simpleTagged = new File(RESULT_FOLDER, "simpleTagged.pdf");
        File simpleTaggedSignedOnce = new File(RESULT_FOLDER, "simpleTagged-signed.pdf");
        File simpleTaggedSignedTwice = new File(RESULT_FOLDER, "simpleTagged-signed-signed.pdf");

        try (   FileOutputStream os = new FileOutputStream(simpleTagged);
                PdfWriter pdfWriter = new PdfWriter(os, new WriterProperties()/*.setFullCompressionMode(true)*/);
                PdfDocument pdfDocument = new PdfDocument(pdfWriter)    ) {
            pdfDocument.setTagged();
            try (   Document document = new Document(pdfDocument)   ) {
                document.add(new Paragraph("Ein Testdokument mit Tags."));
                PdfOutline outline = pdfDocument.getOutlines(false).addOutline("Seite 1");
                outline.addDestination(PdfExplicitDestination.createFit(pdfDocument.getFirstPage()));
            }
        }

        try (   PdfReader pdfReader = new PdfReader(simpleTagged);
                FileOutputStream os = new FileOutputStream(simpleTaggedSignedOnce)  ) {
            PdfSigner pdfSigner = new PdfSigner(pdfReader, os, new StampingProperties());
            pdfSigner.setFieldName("FirstSignature");
            PdfSignatureAppearance appearance = pdfSigner.getSignatureAppearance();
            appearance.setPageNumber(1);
            appearance.setPageRect(new Rectangle(30, 700, 200, 100));

            pdfSigner
                .getDocument()
                .getCatalog()
                .getPdfObject()
                .getAsDictionary(PdfName.Outlines)
                .getAsDictionary(PdfName.First)
                .put(new PdfName("SE"), pdfSigner
                        .getDocument()
                        .getCatalog()
                        .getPdfObject()
                        .getAsDictionary(PdfName.StructTreeRoot)
                        .getAsArray(PdfName.K)
                        .get(0, false));

            IExternalDigest digest = new BouncyCastleDigest();
            IExternalSignature signature = new PrivateKeySignature(pk, DigestAlgorithms.SHA256, null);
            pdfSigner.signDetached(digest, signature, chain, null, null, null, 0, CryptoStandard.CMS);
        }

        try (   PdfReader pdfReader = new PdfReader(simpleTaggedSignedOnce);
                FileOutputStream os = new FileOutputStream(simpleTaggedSignedTwice)  ) {
            PdfSigner pdfSigner = new PdfSigner(pdfReader, os, new StampingProperties().useAppendMode());
            pdfSigner.setFieldName("SecondSignature");
            PdfSignatureAppearance appearance = pdfSigner.getSignatureAppearance();
            appearance.setPageNumber(1);
            appearance.setPageRect(new Rectangle(230, 700, 200, 100));

            IExternalDigest digest = new BouncyCastleDigest();
            IExternalSignature signature = new PrivateKeySignature(pk, DigestAlgorithms.SHA256, null);
            pdfSigner.signDetached(digest, signature, chain, null, null, null, 0, CryptoStandard.CMS);
        }
    }

}