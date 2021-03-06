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

package com.scalyr.api;

/**
 * Tunable parameters.
 */
public class TuningConstants {
  /**
   * Time span during which API operations may be retried (e.g. in response to a network
   * error). Once this many milliseconds have elapsed from the initial API invocation, we
   * no longer issue retries.
   */
  public static final int MAXIMUM_RETRY_PERIOD_MS = 60000;
  
  /**
   * Maximum time (in milliseconds) for opening an HTTP connection to the Scalyr server.
   * If this time is exceeded, we consider server invocation to have failed.
   */
  public static final int HTTP_CONNECT_TIMEOUT_MS = 10000;
  
  /**
   * Time (in milliseconds) from when HostedConfigurationFile completes fetching a file from
   * the server, until it issues the next request. If the fetch failed, subsequent requests
   * use a longer interval, up to MAXIMUM_FETCH_INTERVAL.
   */
  public static final int MINIMUM_FETCH_INTERVAL = 500;
  
  /**
   * Minimum interval used when the previous request was unsuccessful.
   */
  public static final int MINIMUM_FETCH_INTERVAL_AFTER_ERROR = 5000;
  
  public static final int MAXIMUM_FETCH_INTERVAL = 60000;
  
  /**
   * Maximum length of an individual attribute in a Scalyr Logs event.
   */
  public static final int MAXIMUM_EVENT_ATTRIBUTE_LENGTH = 1000;
  
  /**
   * Interval between warnings that events are being discarded due to buffer overflow.
   */
  public static final int EVENT_UPLOAD_MEMORY_WARNING_INTERVAL_MS = 10000;
  
  /**
   * Interval between sampling of registered Gauges.
   */
  public static final int GAUGE_SAMPLE_INTERVAL_MS = 60000;
  
  /**
   * Maximum payload size for a single invocation of LogService.uploadEvents.
   * This is the maximum size, in bytes, of the serialized events array. 
   */
  public static final int MAX_EVENT_UPLOAD_BYTES = 1024 * 1024;
  
  /**
   * Payload size which triggers invocation of LogService.uploadEvents. We wait for
   * this payload size (or EVENT_UPLOAD_TIME_THRESHOLD_MS).
   */
  public static final int EVENT_UPLOAD_BYTE_THRESHOLD = 100 * 1024;
  
  /**
   * Interval for checking whether to upload a new batch of events to the Scalyr
   * Logs service. We don't necessarily upload this often, but this specifies how
   * frequently we evaluate to see whether it's time to upload a new batch.
   */
  public static final int EVENT_UPLOAD_CHECK_INTERVAL = 1000;
  
  /**
   * Time delay which triggers invocation of LogService.uploadEvents. We wait for
   * this time delay (or EVENT_UPLOAD_BYTE_THRESHOLD). We choose a value below 5
   * seconds to prevent keepalive connections from being dropped.
   */
  public static final int EVENT_UPLOAD_TIME_THRESHOLD_MS = 4500;
  
  /**
   * Minimum time delay between event batch uploads (measured start-to-start;
   * deliberately set slightly shorter than EVENT_UPLOAD_CHECK_INTERVAL, so that
   * we can initiate an upload on each check).
   */
  public static final int MIN_EVENT_UPLOAD_SPACING_MS = 900;
  
  /**
   * Maximum time delay between event batch uploads. This comes into play when
   * the server issues backoff responses and we begin increasing our interval.
   */
  public static final int MAX_EVENT_UPLOAD_SPACING_MS = 50000;
  
  /**
   * Factor by which we adjust our upload spacing after a backoff response.
   */
  public static final double UPLOAD_SPACING_FACTOR_ON_BACKOFF = 1.5;
  
  /**
   * Factor by which we adjust our upload spacing after a successful upload.
   */
  public static final double UPLOAD_SPACING_FACTOR_ON_SUCCESS = 0.9;
  
  /**
   * Percentage of EventUploader's buffer that we reserve for use by end
   * events and buffer-overflow messages. Start events and leaf events
   * will not use this space. This ensures that end events usually do not
   * need to be discarded, thus preserving the integrity of event nesting.
   */
  public static final int EVENT_BUFFER_RESERVED_PERCENT = 5;
  
  /**
   * Percentage of EventUploader's buffer that we reserve for use only
   * for buffer-overflow messages. End events won't use this space (nor
   * will start or leaf events). This ensures that we (almost) always have
   * room to at least record a buffer-overflow message.
   */
  public static final int EVENT_BUFFER_END_EVENT_RESERVED_PERCENT = 1;
}
