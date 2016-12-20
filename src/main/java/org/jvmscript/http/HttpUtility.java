package org.jvmscript.http;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class HttpUtility {

    private static final Logger logger = LoggerFactory.getLogger(HttpUtility.class);

    public static void httpDownloadFile(String url) throws Exception {
        String filename = FilenameUtils.getName(url);
        httpDownloadFile(url, filename);
    }

    public static void httpDownloadFile(String url, String filename) throws Exception{
        boolean sucess = false;
        int cnt = 0;
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        CloseableHttpResponse response = null;

        do {
            try {
                response = httpclient.execute(httpGet);
                HttpEntity entity = response.getEntity();

                if (entity != null) {
                    InputStream inputStream = entity.getContent();
                    OutputStream outputStream = new FileOutputStream(new File(filename));
                    IOUtils.copy(inputStream, outputStream);
                    sucess = true;
                    logger.info("URL {} downloaded to file {}", url, filename);
                }

            } catch (Exception e) {

                logger.error("Http Download ERROR = {}", e);
                Thread.sleep(1000);
            }
            finally {
                response.close();
            }

        } while (sucess == false && cnt++ < 5);
    }
}
