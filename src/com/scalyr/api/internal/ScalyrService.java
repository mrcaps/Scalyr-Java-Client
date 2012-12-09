/*
 * Scalyr client library
 * Copyright 2012 Scalyr, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.scalyr.api.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Random;

import com.scalyr.api.ScalyrException;
import com.scalyr.api.ScalyrNetworkException;
import com.scalyr.api.TuningConstants;
import com.scalyr.api.json.JSONObject;
import com.scalyr.api.json.JSONParser;
import com.scalyr.api.logs.Severity;

/**
 * Base class for encapsulating the raw HTTP-level API to a Scalyr service.
 */
public abstract class ScalyrService {
  /**
   * Server URLs, each including a trailing slash. Synchronize access.
   */
  protected String[] serverAddresses;
  
  /**
   * Random number generator used to randomize server choices and retry
   * intervals. Synchronize access.
   */
  protected Random random = new Random();
  
  /**
   * API token we pass in all requests to the server.
   */
  protected final String apiToken;
  
  /**
   * Construct a ScalyrService.
   * 
   * @param apiToken The API authorization token to use when communicating with the server. (If you need
   *     to use multiple api tokens, construct a separate instance for each.)
   */
  public ScalyrService(String apiToken) {
    this.apiToken = apiToken;
    
    setServerAddress(
        "https://api1.scalyr.com," +
        "https://api2.scalyr.com," +
        "https://api3.scalyr.com," +
        "https://api4.scalyr.com");
  }
  
  /**
   * Return the server URL(s) we use, as a comma-delimited list.
   */
  public synchronized String getServerAddresses() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < serverAddresses.length; i++) {
      if (i > 0)
        sb.append(",");
      sb.append(serverAddresses[i]);
    }
    
    return sb.toString();
  }
  
  /**
   * Specify the URL of the Scalyr server, e.g. https://api.scalyr.com. Trailing slash is optional.
   * <p>
   * You may specify multiple addresses, separated by commas. If multiple addresses are specified,
   * the client will choose one as it sees fit, and retry failed requests on a different
   * server.
   * <p>
   * You should only call this method if using a staging server or other non-production instance
   * of the Scalyr service. Otherwise, we automatically default to the production Scalyr service.
   * It is best to call this method at most once, before issuing any requests through this
   * service object.
   * 
   * @return this ScalyrService object.
   */
  public synchronized ScalyrService setServerAddress(String value) {
    serverAddresses = value.split(",");
    for (int i = 0; i < serverAddresses.length; i++) {
      String s = serverAddresses[i];
      s = s.trim();
      if (!s.endsWith("/"))
        s += "/";
      
      serverAddresses[i] = s;
    }
    
    return this;
  }

  /**
   * Invoke methodName on a selected server, sending the specified parameters as the request
   * body. Return the (JSON-format) response.
   * <p>
   * If "retriable" error (e.g. a network error) occurs, we retry on another server. We continue
   * retrying until a non-retriable error occurs, there are no more servers to try, or a reasonable
   * deadline (TuningConstants.MAXIMUM_RETRY_PERIOD_MS) expires.
   * <p>
   * This method should not be called directly. Instead, work through method-specific wrappers.
   * 
   * @throws ScalyrException
   * @throws ScalyrNetworkException
   */
  public String invokeApi(String methodName, JSONObject parameters) {
    // Produce a shuffled copy of the server addresses, so that load is distributed
    // across the servers.
    int N = serverAddresses.length;
    String[] shuffled = new String[N];
    System.arraycopy(serverAddresses, 0, shuffled, 0, N);
           
    for (int i = 0; i < N - 1; i++) {
      int j = i + random.nextInt(N - i);
      String temp = shuffled[i];
      shuffled[i] = shuffled[j];
      shuffled[j] = temp;
    }
    
    // Try the operation on each server in turn.
    long startTimeMs = ScalyrUtil.currentTimeMillis();
    int serverIndex = 0;
    while (true) {
      String serverAddress = shuffled[serverIndex];
      long requestStartTimeMs = ScalyrUtil.currentTimeMillis();
      try {
        return invokeApiOnServer(serverAddress, methodName, parameters);
      } catch (ScalyrNetworkException ex) {
        // Fall into the loop and retry the operation on the next server.
        // If there are no more servers, or our deadline has expired, then
        // rethrow the exception.
        serverIndex++;
        
        long totalElapsedMs = ScalyrUtil.currentTimeMillis() - startTimeMs;
        
        if (serverIndex >= N) {
          long requestElapsedMs = ScalyrUtil.currentTimeMillis() - requestStartTimeMs;
          Logging.log(Severity.warning, Logging.tagServerError,
              "invokeApi: " + methodName + " failed on " + serverAddress
              + " (after " + requestElapsedMs + " milliseconds); no more servers to try, so giving up", ex);
          throw ex;
        } else if (totalElapsedMs >= TuningConstants.MAXIMUM_RETRY_PERIOD_MS) {
          Logging.log(Severity.warning, Logging.tagServerError,
              "invokeApi: " + methodName + " failed on " + serverAddress
              + "; maximum retry period of " + TuningConstants.MAXIMUM_RETRY_PERIOD_MS
              + " ms exceeded, so giving up", ex);
          throw ex;
        }
        
        Logging.log(Severity.warning, Logging.tagServerError,
            "invokeApi: " + methodName + " failed on " + serverAddress + "; will retry", ex);
      }
    }
  }

  private InetSocketAddress socketAddressFromName(String proxyName) {
    // trim off protocol part
    int ssdx;
    if ((ssdx = proxyName.indexOf("//")) > -1) {
      proxyName = proxyName.substring(ssdx + 2);
    }
    int proxyPort = 443;
    if ((ssdx = proxyName.indexOf(":")) > -1) {
      int ssdx2 = proxyName.indexOf("/", ssdx);
      if (ssdx2 < 0) {
        ssdx2 = proxyName.length();
      }
      proxyPort = Integer.parseInt(proxyName.substring(ssdx + 1, ssdx2));
      proxyName = proxyName.substring(0, ssdx);
    }
    return new InetSocketAddress(proxyName, proxyPort);
  }

  /**
   * Invoke serverAddress/methodName on the server, sending the specified parameters as the request
   * body. Return the (JSON-format) response.
   * 
   * @throws ScalyrException
   * @throws ScalyrNetworkException
   */
  protected String invokeApiOnServer(String serverAddress, String methodName, JSONObject parameters) {
    HttpURLConnection connection = null;  
    try {
      // Send the request.
      long startTimeMs = ScalyrUtil.currentTimeMillis();
      URL url = new URL(serverAddress + methodName);
      String proxyName = System.getenv("https_proxy");
      if (null == proxyName) {
        connection = (HttpURLConnection) url.openConnection();
      } else {
        InetSocketAddress inet = socketAddressFromName(proxyName);
        Logging.log(Severity.warning, Logging.tagServerCommunication, "Using proxy " + proxyName);
        connection = (HttpURLConnection) url.openConnection(new Proxy(Proxy.Type.HTTP, inet));
      }
      connection.setRequestMethod("POST");
      connection.setUseCaches(false);
      connection.setDoInput(true);
      connection.setConnectTimeout(TuningConstants.HTTP_CONNECT_TIMEOUT_MS);
      connection.setReadTimeout(TuningConstants.MAXIMUM_RETRY_PERIOD_MS);
      
      CountingOutputStream countingStream = new CountingOutputStream();
      parameters.writeJSONBytes(countingStream);
      int requestLength = countingStream.bytesWritten;
      
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setRequestProperty("Content-Length", "" + requestLength);
      connection.setDoOutput(true);
      
      OutputStream output = connection.getOutputStream();
      parameters.writeJSONBytes(output);
      output.flush();
      
      // We no longer explicitly close, so that HTTP keepalive can function.
      // output.close();
      
      // Retrieve the response.
      int responseCode = connection.getResponseCode();
      
      InputStream input = connection.getInputStream();
      BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
      String responseText = readEntireStream(reader);
      
      // We no longer explicitly close, so that HTTP keepalive can function.
      // reader.close();
      
      long runtimeMs = ScalyrUtil.currentTimeMillis() - startTimeMs;
      Logging.log(Severity.fine, Logging.tagServerCommunication,
          serverAddress + "/" + methodName + ": "
          + runtimeMs + " ms, "
          + requestLength + " bytes up, "
          + responseText.length() + " chars down, response status " + responseCode
          );
      
      if (responseCode != 200) {
        // TODO: log StringUtil.noisyTruncate(response.responseBody.trim(), 1000));
        // also do this in the "Malformed response" case
        throw new ScalyrNetworkException("Scalyr server returned error code " + responseCode);
      }
      
      Object responseJson = new JSONParser().parse(responseText);
      if (responseJson instanceof JSONObject) {
        Object status = ((JSONObject)responseJson).get("status");
        Logging.log(Severity.finer, Logging.tagServerCommunication,
            "Response status [" + (status == null ? "(none)" : status) + "]"
            );
        
        return responseText;
      } else {
        throw new ScalyrException("Malformed response from Scalyr server");
      }
    } catch (Exception ex) {
      if (ex instanceof SocketTimeoutException)
        throw new ScalyrNetworkException("Timeout while communicating with Scalyr server", ex);
      else
        throw new ScalyrNetworkException("Error while communicating with Scalyr server", ex);
    } finally {
      // We no longer explicitly disconnect, so that HTTP keepalive can function. 
      // 
      // if (connection != null)
      //   connection.disconnect(); 
    }
  }
  
  private String readEntireStream(Reader reader) throws IOException {
    StringBuilder sb = new StringBuilder();
    char[] buffer = new char[4096];
    while (true) {
      int count = reader.read(buffer, 0, buffer.length);
      if (count <= 0)
        break;
      sb.append(buffer, 0, count);
    }
    return sb.toString();
  }
}
