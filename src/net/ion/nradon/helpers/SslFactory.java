package net.ion.nradon.helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.Security;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import net.ion.framework.util.IOUtil;

public class SslFactory {
    private static final String PROTOCOL = "TLS";
    private final KeyStore ks;

    public SslFactory(File keyStore, String storePass) {
    	
    	InputStream keyInput = null ;
        try {
        	keyInput = new FileInputStream(keyStore) ;
            // Create and load keystore file
            ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(keyInput, storePass.toCharArray());
        } catch (Exception e) {
            throw new RadonException(e);
        } finally {
        	IOUtil.closeQuietly(keyInput) ;
        }
        
    }

    public SSLContext getServerContext(String keyPass) throws RadonException {
        try {
            // Set up key manager factory to use our key store
            String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
            if (algorithm == null) algorithm = "SunX509";
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
            kmf.init(ks, keyPass.toCharArray());

            // Initialize the SSLContext to work with our key managers.
            SSLContext sslContext = SSLContext.getInstance(PROTOCOL);
            sslContext.init(kmf.getKeyManagers(), null, null);
            return sslContext;
        } catch (Exception e) {
            throw new RadonException(e);
        }
    }

    public SSLContext getClientContext() throws RadonException {
        try {
            SSLContext sslContext = SSLContext.getInstance(PROTOCOL);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509", "SunJSSE");
            tmf.init(ks);
            TrustManager[] trustManagers = tmf.getTrustManagers();
            sslContext.init(null, trustManagers, null);
            return sslContext;
        } catch (Exception e) {
            throw new RadonException(e);
        }
    }
}