package bittech.lib.protocol.websocket;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public class KeystoreLoader {

	public static KeyStore loadKeyStore(String name, String password) throws Exception {
		final InputStream stream = new FileInputStream(name);
		try (InputStream is = stream) {
			KeyStore loadedKeystore = KeyStore.getInstance("JKS");
			loadedKeystore.load(is, password.toCharArray());
			return loadedKeystore;
		}
	}

	public static SSLContext createSSLContext(final KeyStore keyStore, String password) throws Exception {
		KeyManager[] keyManagers;
		KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagerFactory.init(keyStore, password.toCharArray());
		keyManagers = keyManagerFactory.getKeyManagers();

		SSLContext sslContext;
		sslContext = SSLContext.getInstance("TLS");
		sslContext.init(keyManagers, null, null);

		return sslContext;
	}

}
