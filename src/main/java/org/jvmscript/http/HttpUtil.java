package org.jvmscript.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.AttributeKey;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.CharBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * <code>HttpUtility</code> provides methods for uploading and downloading content from an HTTP server.
 * <p>
 * The following example prints the content retrieved from <code>www.example.com</code>.
 * <pre><code>
 * String str = httpGet("http://www.example.com");
 * System.out.println(str);
 * </code></pre>
 */
// TODO add ssl/tls support
// TODO enforce max redirects
public class HttpUtil
{
    private static final Logger logger = LogManager.getLogger(HttpUtil.class);
    private  final long DEFAULT_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(15);
    private  AttributeKey<Queue<FullHttpResponse>> RESPONSE_QUEUE_KEY;
    private  final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private  EventLoopGroup eventLoopGroup;
    private  Bootstrap bootstrap;

    /**
     * Initializes resources used by the utility.  This method is optional provided to allow preallocation of resources
     * where desirable.
     */

    public HttpUtil() {
        Random random = new Random();
        String responseQueueNumber = Integer.toString(random.nextInt(1024));
        RESPONSE_QUEUE_KEY = AttributeKey.newInstance("ResponseQueue-" + responseQueueNumber);
    }
    public  void httpInitialize(URL url)
    {
        if (INITIALIZED.compareAndSet(false, true))
        {
            eventLoopGroup = new OioEventLoopGroup();
            bootstrap = new Bootstrap()
                    .group(eventLoopGroup)
                    .channel(OioSocketChannel.class)
                    .handler(new Initializer(url));
        }
    }

    /**
     * Releases resources used by the utility, must be invoked once the utility is no longer needed.
     */
    public  void httpDispose()
    {
        try
        {
            eventLoopGroup.shutdownGracefully(1, 5, TimeUnit.SECONDS).sync();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }

        INITIALIZED.set(false);
    }

    /**
     * Sends an HTTP GET request and returns the HTTP response body.  This method is equivalent to invoking
     * <code>httpGet(String, HttpHeaders)</code> without any headers.
     *
     * @param urlString the requested URL.
     * @return the HTTP response body.
     */
    public  String httpGet(String urlString)
    {
        return httpGet(urlString, HttpHeaders.EMPTY_HEADERS);
    }

    /**
     * Sends an HTTP GET request and returns the HTTP response body.  This method is equivalent to invoking
     * <code>httpGet(String, HttpHeaders)</code> with a <code>Map</code> instead of an <code>HttpHeaders</code>
     * instance.  This method is typically more convenient but doesn't enforce ordering of headers, when ordering is
     * necessary use an <code>HttpHeaders</code> instance instead.
     * <p>
     * The following example illustrates retrieving a web page.
     * <pre><code>
     *     Map&lt;String, Object&gt; headers = Collections.singletonMap("Authorization", "myauth");
     *     String response = httpGet("http://www.example.com", headers);
     * </code></pre>
     *
     * @param urlString the requested URL.
     * @param headerMap the HTTP headers sent with the request.
     * @return the HTTP response body.
     */
    public  String httpGet(String urlString, Map<String, Object> headerMap)
    {
        return httpGet(urlString, toHeaders(headerMap));
    }

    /**
     * Sends an HTTP GET request and returns the HTTP response body.
     * <p>
     * The following example illustrates retrieving a web page.
     * <pre><code>
     *     HttpHeaders headers = new HttpHeaders().add("Authorization", "myauth");
     *     String response = httpGet("http://www.example.com", headers);
     * </code></pre>
     *
     * @param urlString the requested URL.
     * @param headers   the HTTP headers sent with the request.
     * @return the HTTP response body.
     */
    public  String httpGet(String urlString, HttpHeaders headers)
    {
        URL url = parseUrl(urlString);
        FullHttpRequest request = createRequest(HttpMethod.GET, url, headers);
        Channel channel = connect(url);
        channel.writeAndFlush(request);

        String responseContent;
        try
        {
            responseContent = receiveString(channel);
            logger.info("GET {} completed successfully.", urlString);
        }
        catch (RedirectedException e)
        {
            String location = e.getLocation();
            logger.info("GET {} redirected to {}.", urlString, location);
            //responseContent = httpGet(location, headers);
            httpDispose();
            responseContent = httpGet(location);
        }

        return responseContent;
    }


    public  String httpPost(String urlString, String content)
    {
        return httpPost(urlString, content, HttpHeaders.EMPTY_HEADERS);
    }

    public  String httpPost(String urlString, String content, Map<String, Object> headerMap)
    {
        return httpPost(urlString, content, toHeaders(headerMap));
    }

    public  String httpPost(String urlString, String content, HttpHeaders headers)
    {
        URL url = parseUrl(urlString);
        FullHttpRequest request = createRequestWithString(HttpMethod.POST, url, content, headers);
        Channel channel = connect(url);
        channel.writeAndFlush(request);
        String responseContent = receiveString(channel);

        logger.info("POST {} completed successfully.", urlString);

        return responseContent;
    }


    public  void httpPut(String urlString, String content)
    {
        httpPut(urlString, content, HttpHeaders.EMPTY_HEADERS);
    }

    public  void httpPut(String urlString, String content, Map<String, Object> headerMap)
    {
        httpPut(urlString, content, toHeaders(headerMap));
    }

    public  void httpPut(String urlString, String content, HttpHeaders headers)
    {
        URL url = parseUrl(urlString);
        FullHttpRequest request = createRequestWithString(HttpMethod.PUT, url, content, headers);
        Channel channel = connect(url);
        channel.writeAndFlush(request);
        receive(channel);

        logger.info("PUT {} completed successfully.", urlString);
    }


    public  void httpDelete(String urlString)
    {
        httpDelete(urlString, HttpHeaders.EMPTY_HEADERS);
    }

    public  void httpDelete(String urlString, Map<String, Object> headerMap)
    {
        httpDelete(urlString, toHeaders(headerMap));
    }

    public  void httpDelete(String urlString, HttpHeaders headers)
    {
        URL url = parseUrl(urlString);
        FullHttpRequest request = createRequest(HttpMethod.DELETE, url, headers);
        Channel channel = connect(url);
        channel.writeAndFlush(request);
        receive(channel);

        logger.info("DELETE {} completed successfully.", urlString);
    }

    public  void httpDownload(String urlString)
    {
        httpDownload(urlString, FilenameUtils.getName(urlString));
    }
    public  void httpDownload(String urlString, String fileName) {httpDownload(urlString, fileName, HttpHeaders.EMPTY_HEADERS);}
    public  void httpDownload(String urlString, String fileName, Map<String, Object> headerMap) {httpDownload(urlString, fileName, toHeaders(headerMap));}

    public  void httpDownload(String urlString, String fileName, HttpHeaders headers)
    {
        String content = httpGet(urlString, headers);
        File file = new File(fileName);
        try
        {
            if (!file.createNewFile())
            {
                throw new IllegalArgumentException("File already exists: '" + fileName + "'.");
            }
            FileWriter writer = new FileWriter(file);
            IOUtils.write(content, writer);
            writer.close();

            logger.info("URL {} downloaded to file {}", urlString, fileName);
        }
        catch (FileNotFoundException e)
        {
            // never happens
            throw new IllegalStateException(e);
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException("Unable to write to file: '" + fileName + "'.", e);
        }
    }


    private  FullHttpRequest createRequest(HttpMethod method, URL url, HttpHeaders headers)
    {
        String requestLine = getRequestLine(url);
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, method, requestLine);
        request.headers().add(headers);
        addDefaultHeaders(url, request.headers());
        return request;
    }

    private  FullHttpRequest createRequestWithString(HttpMethod method, URL url, String content, HttpHeaders headers)
    {
        String requestLine = getRequestLine(url);
        ByteBuf buffer = ByteBufUtil.encodeString(ByteBufAllocator.DEFAULT, CharBuffer.wrap(content), UTF_8);
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, method, requestLine, buffer);
        request.headers().set(HttpHeaders.Names.CONTENT_LENGTH, buffer.readableBytes());
        request.headers().add(headers);
        addDefaultHeaders(url, request.headers());
        return request;
    }

    private  void addDefaultHeaders(URL url, HttpHeaders headers)
    {
        if (!headers.contains(HttpHeaders.Names.HOST))
        {
            String headerValue = url.getHost();
            int port = url.getPort();
            if (port != -1) {
                headerValue += ":" + port;
            }
            headers.add(HttpHeaders.Names.HOST, headerValue);
            System.out.println("HOST=" + headerValue);
        }

        if (!headers.contains(HttpHeaders.Names.CONNECTION))
        {
            headers.add(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        }

        if (!headers.contains(HttpHeaders.Names.ACCEPT_ENCODING))
        {
            headers.add(HttpHeaders.Names.ACCEPT_ENCODING,
                    Arrays.asList(HttpHeaders.Values.GZIP, HttpHeaders.Values.DEFLATE));
        }

        if (!headers.contains(HttpHeaders.Names.ACCEPT_CHARSET))
        {
            headers.add(HttpHeaders.Names.ACCEPT_CHARSET, "UTF-8");
        }
    }

    private  String getRequestLine(URL url)
    {
        StringBuilder builder = new StringBuilder(url.getPath());
        String query = url.getQuery();
        if (null != query)
        {
            builder.append('?')
                    .append(query);
        }
        return builder.toString();
    }


    private  Channel connect(URL url)
    {
        httpInitialize(url);

        try
        {
            int port = url.getPort() != -1 ? url.getPort() : url.getDefaultPort();
            return bootstrap.connect(url.getHost(), port).sync().channel();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted establishing connection.", e);
        }
    }


    private  void receive(Channel channel)
    {
        receiveResponse(channel).release();
    }

    private  String receiveString(Channel channel)
    {
        String str;
        FullHttpResponse response = receiveResponse(channel);
        try
        {
            str = response.content().toString(UTF_8);
        }
        finally
        {
            response.release();
        }
        return str;
    }

    private  FullHttpResponse receiveResponse(Channel channel)
    {
        FullHttpResponse response;
        final long timeoutNanos = System.nanoTime() + DEFAULT_TIMEOUT_NANOS;
        Queue<FullHttpResponse> responseQueue = channel.attr(RESPONSE_QUEUE_KEY).get();
        while ((response = responseQueue.poll()) == null)
        {
            if (System.nanoTime() > timeoutNanos)
            {
                throw new IllegalStateException("Timed out awaiting response.");
            }

            try
            {
                Thread.sleep(10);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted awaiting response.", e);
            }
        }

        HttpResponseStatus status = response.getStatus();
        if (HttpResponseStatus.MOVED_PERMANENTLY.equals(status) || HttpResponseStatus.TEMPORARY_REDIRECT.equals(status) || HttpResponseStatus.FOUND.equals(status))
        {
            throw new RedirectedException(response.headers().get(HttpHeaders.Names.LOCATION));
        }
        else if (!HttpResponseStatus.OK.equals(status) && !HttpResponseStatus.CREATED.equals(status))
        {
            throw new IllegalStateException("Unexpected response status " + status + ".");
        }

        return response;
    }


    private  HttpHeaders toHeaders(Map<String, Object> headerMap)
    {
        HttpHeaders headers = new DefaultHttpHeaders();
        for (String o : headerMap.keySet())
        {
            Object value = headerMap.get(o);
            if (value instanceof Iterable)
            {
                headers.add(o, (Iterable<?>) value);
            }
            else
            {
                headers.add(o, value);
            }
        }
        return headers;
    }

    private  URL parseUrl(String urlString)
    {
        try
        {
            return new URL(urlString);
        }
        catch (MalformedURLException e)
        {
            throw new IllegalArgumentException("Invalid URL.", e);
        }
    }

    private  final class Initializer extends ChannelInitializer<SocketChannel>
    {

        private boolean ssl;
        private String host;
        private int port;

        Initializer() {
            ssl = false;
        }

        Initializer(URL url) {
            super();

            ssl = url.getProtocol().equalsIgnoreCase("https") ? true : false;
            host = url.getHost();
            port = url.getPort();
        }

        @Override
        protected void initChannel(SocketChannel ch) throws Exception
        {
            Queue<FullHttpResponse> responseQueue = new ConcurrentLinkedQueue<>();

            if (ssl) {
                SslContext sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
                ch.pipeline().addLast(sslCtx.newHandler(ch.alloc(), host, port));
            }

            ch.pipeline()
                    .addLast(new HttpClientCodec())
                    .addLast(new HttpContentDecompressor())
                    .addLast(new HttpObjectAggregator(200*1024*1024))
                    .addLast(new Receiver(responseQueue));

            ch.attr(RESPONSE_QUEUE_KEY).set(responseQueue);
        }
    }

    private  final class Receiver extends SimpleChannelInboundHandler<FullHttpResponse>
    {
        private Queue<FullHttpResponse> responseQueue;

        Receiver(Queue<FullHttpResponse> responseQueue)
        {
            this.responseQueue = responseQueue;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception
        {
            responseQueue.add(msg);
            msg.retain();
            ctx.close();
        }
    }

    private  class RedirectedException extends RuntimeException
    {
        private String location;

        private RedirectedException(String location)
        {
            super("Redirected to '" + location + "'.");
            this.location = location;
        }

        private String getLocation()
        {
            return location;
        }
    }

    public static void main(String[] args) {

        HttpUtil httpUtil = new HttpUtil();

        HashMap<String, Object> headers = new HashMap<>();
        headers.put("Authorization", "test");

        httpUtil.httpDownload("https://api.orats.io/data/strikes/all", "/develop/strikes.csv", headers);
        httpUtil.httpDispose();

    }
}
