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
 * 
 * Taken from the json-simple library, by Yidong Fang and Chris Nokleberg.
 * This copy has been modified by Scalyr, Inc.; see README.txt for details.
 */

package com.scalyr.api.json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import com.scalyr.api.internal.ScalyrUtil;

/**
 * RawJson encapsulates an unparsed UTF-8 JSON string.
 */
public abstract class RawJson implements JSONAware, JSONStreamAware {
  @Override public void writeJSONString(Writer out) throws IOException {
    out.write(toJSONString());
  }

  @Override public String toJSONString() {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    try {
      writeJSONBytes(stream);
    } catch (IOException ex) {
      // This should never happen, since we're working with in-memory data.
      throw new RuntimeException(ex);
    }
    return new String(stream.toByteArray(), ScalyrUtil.utf8);
  }

  @Override public abstract void writeJSONBytes(OutputStream out) throws IOException;
}
