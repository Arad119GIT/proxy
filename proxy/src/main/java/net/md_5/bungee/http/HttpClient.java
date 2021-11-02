package net.md_5.bungee.http;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.md_5.bungee.api.Callback;
import net.md_5.bungee.netty.PipelineUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class HttpClient
{

    public static final int TIMEOUT = 5000;
    private static final Cache<String, InetAddress> addressCache = CacheBuilder.newBuilder().expireAfterWrite( 1, TimeUnit.MINUTES ).build();
    private static final CopyOnWriteArraySet<JavaIOGetRequest> requests = new CopyOnWriteArraySet<>();
    private static Thread requestsRunner;

    @SuppressWarnings("UnusedAssignment")
    public static void get(String url, EventLoop eventLoop, final Callback<String> callback)
    {
        System.out.println("get tout court");
        Preconditions.checkNotNull( url, "url" );
        Preconditions.checkNotNull( eventLoop, "eventLoop" );
        Preconditions.checkNotNull( callback, "callBack" );

        final URI uri = URI.create( url );

        Preconditions.checkNotNull( uri.getScheme(), "scheme" );
        Preconditions.checkNotNull( uri.getHost(), "host" );
        boolean ssl = uri.getScheme().equals( "https" );
        int port = uri.getPort();
        if ( port == -1 )
        {
            switch ( uri.getScheme() )
            {
                case "http":
                    port = 80;
                    break;
                case "https":
                    port = 443;
                    break;
                default:
                    throw new IllegalArgumentException( "Unknown scheme " + uri.getScheme() );
            }
        }

        InetAddress inetHost = addressCache.getIfPresent( uri.getHost() );
        if ( inetHost == null )
        {
            try
            {
                inetHost = InetAddress.getByName( uri.getHost() );
            } catch ( UnknownHostException ex )
            {
                callback.done( null, ex );
                return;
            }
            addressCache.put( uri.getHost(), inetHost );
        }

        ChannelFutureListener future = new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception
            {
                if ( future.isSuccess() )
                {
                    String path = uri.getRawPath() + ( ( uri.getRawQuery() == null ) ? "" : "?" + uri.getRawQuery() );

                    HttpRequest request = new DefaultHttpRequest( HttpVersion.HTTP_1_1, HttpMethod.GET, path );
                    request.headers().set( HttpHeaders.Names.HOST, uri.getHost() );
                    request.headers().set( HttpHeaders.Names.USER_AGENT, "Mozilla/5.0 (X11; Linux x86_64; rv:2.0.1) Gecko/20100101 Firefox/4.0.1" );

                    future.channel().writeAndFlush( request );
                } else
                {
                    addressCache.invalidate( uri.getHost() );
                    callback.done( null, future.cause() );
                }
            }
        };

        new Bootstrap().channel( PipelineUtils.getChannel() ).group( eventLoop ).handler( new HttpInitializer( callback, ssl, uri.getHost(), port ) ).
                option( ChannelOption.CONNECT_TIMEOUT_MILLIS, TIMEOUT ).remoteAddress( inetHost, port ).connect().addListener( future );
    }

    public static void getWithJavaIO(final String url, final Callback<String> callback) throws IOException {
        System.out.println("getWithJavaIO");
        Preconditions.checkNotNull( url, "url" );
        Preconditions.checkNotNull( callback, "callBack" );

        requests.add(new JavaIOGetRequest(new URL(url), callback));

        if (requestsRunner == null) {
            requestsRunner = new Thread(new RequestRunner(), "[Dabsunter] JavaIO Request Runner");
            requestsRunner.start();
        }

    }

    private static HttpURLConnection createUrlConnection(URL url) throws IOException {
        Preconditions.checkNotNull( url, "url" );
        //LOGGER.debug("Opening connection to " + url);
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setUseCaches(false);
        return connection;
    }

    private static class JavaIOGetRequest {
        private final URL url;
        private final Callback<String> callback;

        private JavaIOGetRequest(URL url, Callback<String> callback) {
            this.url = url;
            this.callback = callback;
        }
    }

    private static class RequestRunner implements Runnable {

        @Override
        public void run() {
            try {
                while (true) {
                    for (JavaIOGetRequest request : requests) {
                        try {
                            HttpURLConnection connection = createUrlConnection(request.url);
                            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:2.0.1) Gecko/20100101 Firefox/4.0.1");
                            System.out.println("Reading data from " + request.url);
                            InputStream inputStream = null;

                            try {
                                inputStream = connection.getInputStream();
                                Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
                                String result = scanner.hasNext() ? scanner.next() : ""; // IOUtils.toString(inputStream, Charsets.UTF_8);
                                System.out.println("Successful read, server response was " + connection.getResponseCode());
                                System.out.println("Response: " + result);
                                request.callback.done(result, null);
                            } catch (IOException ex) {
                                if (inputStream != null)
                                    inputStream.close();
                                inputStream = connection.getErrorStream();
                                if (inputStream == null) {
                                    System.out.println("Request failed");
                                    request.callback.done(null, ex);
                                }

                                System.out.println("Reading error page from " + request.url);
                                Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
                                String result = scanner.hasNext() ? scanner.next() : ""; //IOUtils.toString(inputStream, Charsets.UTF_8);
                                System.out.println("Successful read, server response was " + connection.getResponseCode());
                                System.out.println("Response: " + result);
                                request.callback.done(result, null);
                            } finally {
                                if (inputStream != null)
                                    inputStream.close();
                            }
                        } catch (IOException ex) {
                            System.err.println("Epic fail");
                            ex.printStackTrace();
                            request.callback.done(null, ex);
                        }
                        requests.remove(request);
                    }
                }
            } catch (Throwable t) {
                System.err.println("!!!! MEGA EPIC FAIL !!!!");
                t.printStackTrace();
                requestsRunner = null;
            }
        }
    }
}
