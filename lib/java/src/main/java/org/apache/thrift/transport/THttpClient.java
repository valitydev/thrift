/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.thrift.transport;

import dev.vality.woody.api.interceptor.CommonInterceptor;
import dev.vality.woody.api.interceptor.EmptyCommonInterceptor;
import dev.vality.woody.api.trace.ContextUtils;
import dev.vality.woody.api.trace.TraceData;
import dev.vality.woody.api.trace.context.TraceContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.thrift.TConfiguration;

/**
 * HTTP implementation of the TTransport interface. Used for working with a Thrift web services
 * implementation (using for example TServlet).
 *
 * <p>This class offers two implementations of the HTTP transport. One uses HttpURLConnection
 * instances, the other HttpClient from Apache Http Components. The chosen implementation depends on
 * the constructor used to create the THttpClient instance. Using the THttpClient(String url)
 * constructor or passing null as the HttpClient to THttpClient(String url, HttpClient client) will
 * create an instance which will use HttpURLConnection.
 *
 * <p>When using HttpClient, the following configuration leads to 5-15% better performance than the
 * HttpURLConnection implementation:
 *
 * <p>http.protocol.version=HttpVersion.HTTP_1_1 http.protocol.content-charset=UTF-8
 * http.protocol.expect-continue=false http.connection.stalecheck=false
 *
 * <p>Also note that under high load, the HttpURLConnection implementation may exhaust the open file
 * descriptor limit.
 *
 * @see <a href="https://issues.apache.org/jira/browse/THRIFT-970">THRIFT-970</a>
 */
public class THttpClient extends TEndpointTransport {

  private final URL url_;

  private final ByteArrayOutputStream requestBuffer_ = new ByteArrayOutputStream();

  private InputStream inputStream_ = null;

  private int connectTimeout_ = 0;

  private int readTimeout_ = 0;

  private Map<String, String> customHeaders_ = null;

  private final HttpHost host;

  private final HttpClient client;

  private final CommonInterceptor interceptor;

  private static final Map<String, String> DEFAULT_HEADERS =
      Collections.unmodifiableMap(getDefaultHeaders());

  public static class Factory extends TTransportFactory {

    private final String url;
    private final HttpClient client;
    private final CommonInterceptor interceptor;

    public Factory(String url) {
      this(url, (HttpClient) null);
    }

    public Factory(String url, CommonInterceptor interceptor) {
      this(url, null, interceptor);
    }

    public Factory(String url, HttpClient client) {
      this(url, client, null);
    }

    public Factory(String url, HttpClient client, CommonInterceptor interceptor) {
      this.url = url;
      this.client = client;
      this.interceptor = interceptor;
    }

    @Override
    public TTransport getTransport(TTransport trans) {
      try {
        if (null != client) {
          return new THttpClient(trans.getConfiguration(), url, client, interceptor);
        } else {
          return new THttpClient(trans.getConfiguration(), url, interceptor);
        }
      } catch (TTransportException tte) {
        return null;
      }
    }
  }

  public THttpClient(TConfiguration config, String url) throws TTransportException {
    super(config);
    try {
      url_ = new URL(url);
      this.client = null;
      this.host = null;
      this.interceptor = new EmptyCommonInterceptor();
    } catch (IOException iox) {
      throw new TTransportException(iox);
    }
  }

  public THttpClient(TConfiguration config, String url, CommonInterceptor interceptor) throws TTransportException {
    super(config);
    try {
      url_ = new URL(url);
      this.client = null;
      this.host = null;
      this.interceptor = interceptor == null ? new EmptyCommonInterceptor() : interceptor;
    } catch (IOException iox) {
      throw new TTransportException(iox);
    }
  }

  public THttpClient(String url) throws TTransportException {
    this(url, (CommonInterceptor) null);
  }

  public THttpClient(String url, CommonInterceptor interceptor) throws TTransportException {
    super(new TConfiguration());
    try {
      url_ = new URL(url);
      this.client = null;
      this.host = null;
      this.interceptor = interceptor == null ? new EmptyCommonInterceptor() : interceptor;
    } catch (IOException iox) {
      throw new TTransportException(iox);
    }
  }

  public THttpClient(TConfiguration config, String url, HttpClient client)
      throws TTransportException {
    super(config);
    try {
      url_ = new URL(url);
      this.client = client;
      this.host =
          new HttpHost(
              url_.getHost(),
              -1 == url_.getPort() ? url_.getDefaultPort() : url_.getPort(),
              url_.getProtocol());
      this.interceptor = new EmptyCommonInterceptor();
    } catch (IOException iox) {
      throw new TTransportException(iox);
    }
  }

  public THttpClient(TConfiguration config, String url, HttpClient client, CommonInterceptor interceptor) throws TTransportException {
    super(config);
    try {
      url_ = new URL(url);
      this.client = client;
      this.host =
              new HttpHost(
                      url_.getHost(),
                      -1 == url_.getPort() ? url_.getDefaultPort() : url_.getPort(),
                      url_.getProtocol());
      this.interceptor = interceptor == null ? new EmptyCommonInterceptor() : interceptor;
    } catch (IOException iox) {
      throw new TTransportException(iox);
    }
  }

  public THttpClient(String url, HttpClient client) throws TTransportException {
    this(url, client, null);
  }

  public THttpClient(String url, HttpClient client, CommonInterceptor interceptor) throws TTransportException {
    super(new TConfiguration());
    try {
      url_ = new URL(url);
      this.client = client;
      this.host =
          new HttpHost(
              url_.getHost(),
              -1 == url_.getPort() ? url_.getDefaultPort() : url_.getPort(),
              url_.getProtocol());
      this.interceptor = interceptor == null ? new EmptyCommonInterceptor() : interceptor;
    } catch (IOException iox) {
      throw new TTransportException(iox);
    }
  }

  public void setConnectTimeout(int timeout) {
    connectTimeout_ = timeout;
  }

  public void setReadTimeout(int timeout) {
    readTimeout_ = timeout;
  }

  public void setCustomHeaders(Map<String, String> headers) {
    customHeaders_ = new HashMap<>(headers);
  }

  public void setCustomHeader(String key, String value) {
    if (customHeaders_ == null) {
      customHeaders_ = new HashMap<>();
    }
    customHeaders_.put(key, value);
  }

  public HttpClient getHttpClient() {
    return client;
  }

  @Override
  public void open() {}

  @Override
  public void close() {
    if (null != inputStream_) {
      try {
        inputStream_.close();
      } catch (IOException ioe) {
      }
      inputStream_ = null;
    }
  }

  @Override
  public boolean isOpen() {
    return true;
  }

  @Override
  public int read(byte[] buf, int off, int len) throws TTransportException {
    if (inputStream_ == null) {
      throw new TTransportException("Response buffer is empty, no request.");
    }

    checkReadBytesAvailable(len);

    try {
      int ret = inputStream_.read(buf, off, len);
      if (ret == -1) {
        throw new TTransportException("No more data available.");
      }
      countConsumedMessageBytes(ret);

      return ret;
    } catch (IOException iox) {
      throw new TTransportException(iox);
    }
  }

  @Override
  public void write(byte[] buf, int off, int len) {
    requestBuffer_.write(buf, off, len);
  }

  private RequestConfig getRequestConfig() {
    RequestConfig requestConfig = RequestConfig.DEFAULT;
    if (connectTimeout_ > 0) {
      requestConfig =
          RequestConfig.copy(requestConfig).setConnectionRequestTimeout(connectTimeout_).build();
    }
    if (readTimeout_ > 0) {
      requestConfig = RequestConfig.copy(requestConfig).setSocketTimeout(readTimeout_).build();
    }
    return requestConfig;
  }

  private static Map<String, String> getDefaultHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/x-thrift");
    headers.put("Accept", "application/x-thrift");
    headers.put("User-Agent", "Java/THttpClient/HC");
    return headers;
  }

  /**
   * copy from org.apache.http.util.EntityUtils#consume. Android has it's own httpcore that doesn't
   * have a consume.
   */
  private static void consume(final HttpEntity entity) throws IOException {
    if (entity == null) {
      return;
    }
    if (entity.isStreaming()) {
      InputStream instream = entity.getContent();
      if (instream != null) {
        instream.close();
      }
    }
  }

  private void setMainHeaders(BiConsumer<String, String> hSetter) {
    hSetter.accept("Content-Type", "application/x-thrift");
    hSetter.accept("Accept", "application/x-thrift");
    hSetter.accept("User-Agent", "Java/THttpClient/HC");
  }

  private void setCustomHeaders(BiConsumer<String, String> hSetter) {
    if (null != customHeaders_) {
      for (Map.Entry<String, String> header : customHeaders_.entrySet()) {
        hSetter.accept(header.getKey(), header.getValue());
      }
    }
  }

  private void intercept(BooleanSupplier interception, String errMsg) throws TTransportException {
    if (!interception.getAsBoolean()) {
      Throwable reqErr = ContextUtils.getInterceptionError(TraceContext.getCurrentTraceData().getClientSpan());
      if (reqErr != null) {
        if (reqErr instanceof RuntimeException) {
          throw (RuntimeException) reqErr;
        } else {
          throw new TTransportException(errMsg, reqErr);
        }
      }
    }
  }

  private void flushUsingHttpClient() throws TTransportException {
    if (null == this.client) {
      throw new TTransportException("Null HttpClient, aborting.");
    }

    // Extract request and reset buffer
    byte[] data = requestBuffer_.toByteArray();
    requestBuffer_.reset();

    HttpPost post = new HttpPost(this.url_.getFile());
    try {
      // Set request to path + query string
      post.setConfig(getRequestConfig());
      DEFAULT_HEADERS.forEach(post::addHeader);
      if (null != customHeaders_) {
        customHeaders_.forEach(post::addHeader);
      }
      TraceData traceData = TraceContext.getCurrentTraceData();
      intercept(() -> interceptor.interceptRequest(traceData, newPost, this.url_), "Request interception error");
      post.setEntity(new ByteArrayEntity(data));
      HttpResponse response = this.client.execute(this.host, post);
      intercept(() -> interceptor.interceptResponse(traceData, response), "Response interception error");
      handleResponse(response);
    } catch (IOException ioe) {
      // Abort method so the connection gets released back to the connection manager
      post.abort();
      throw new TTransportException(ioe);
    } finally {
      resetConsumedMessageSize(-1);
      post.releaseConnection();
    }
  }

  private void handleResponse(HttpResponse response) throws TTransportException {
    // Retrieve the InputStream BEFORE checking the status code so
    // resources get freed in the with clause.
    try (InputStream is = response.getEntity().getContent()) {
      int responseCode = response.getStatusLine().getStatusCode();
      if (responseCode != HttpStatus.SC_OK) {
        throw new TTransportException("HTTP Response code: " + responseCode);
      }
      byte[] readByteArray = readIntoByteArray(is);
      try {
        // Indicate we're done with the content.
        consume(response.getEntity());
      } catch (IOException ioe) {
        // We ignore this exception, it might only mean the server has no
        // keep-alive capability.
      }
      inputStream_ = new ByteArrayInputStream(readByteArray);
    } catch (IOException ioe) {
      throw new TTransportException(ioe);
    }
  }

  /**
   * Read the responses into a byte array so we can release the connection early. This implies that
   * the whole content will have to be read in memory, and that momentarily we might use up twice
   * the memory (while the thrift struct is being read up the chain). Proceeding differently might
   * lead to exhaustion of connections and thus to app failure.
   *
   * @param is input stream
   * @return read bytes
   * @throws IOException when exception during read
   */
  private static byte[] readIntoByteArray(InputStream is) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buf = new byte[1024];
    int len;
    do {
      len = is.read(buf);
      if (len > 0) {
        baos.write(buf, 0, len);
      }
    } while (-1 != len);
    return baos.toByteArray();
  }

  public void flush() throws TTransportException {

    if (null != this.client) {
      flushUsingHttpClient();
      return;
    }

    // Extract request and reset buffer
    byte[] data = requestBuffer_.toByteArray();
    requestBuffer_.reset();

    try {
      // Create connection object
      HttpURLConnection connection = (HttpURLConnection) url_.openConnection();

      // Timeouts, only if explicitly set
      if (connectTimeout_ > 0) {
        connection.setConnectTimeout(connectTimeout_);
      }
      if (readTimeout_ > 0) {
        connection.setReadTimeout(readTimeout_);
      }

      // Make the request
      connection.setRequestMethod("POST");
      setMainHeaders((key, val) -> connection.setRequestProperty(key, val));

      setCustomHeaders((key, val) -> connection.setRequestProperty(key, val));

      TraceData traceData = TraceContext.getCurrentTraceData();

      intercept(() -> interceptor.interceptRequest(traceData, connection, url_), "Request interception error");

      connection.setDoOutput(true);
      connection.connect();
      connection.getOutputStream().write(data);

      intercept(() -> interceptor.interceptResponse(traceData, connection), "Response interception error");

      // Read the responses
      inputStream_ = connection.getInputStream();

    } catch (IOException iox) {
      throw new TTransportException(iox);
    } finally {
      resetConsumedMessageSize(-1);
    }
  }
}
