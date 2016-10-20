/*
 * Knot.x - Reactive microservice assembler - Common
 *
 * Copyright (C) 2016 Cognifide Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cognifide.knotx.fragments;

import com.google.common.base.Objects;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.json.JsonObject;

public class Fragment {

  private static final String RAW_FRAGMENT_ID = "_raw";

  private static final String ID = "_ID";

  private static final String CONTENT = "_CONTENT";

  private static final String CONTEXT = "_CONTEXT";

  private final String id;

  private final String content;

  private final JsonObject context;

  public Fragment(JsonObject fragment) {
    this.id = fragment.getString(ID);
    this.content = fragment.getString(CONTENT);
    this.context = fragment.getJsonObject(CONTEXT);
  }

  private Fragment(String id, String data) {
    if (StringUtils.isEmpty(id) || StringUtils.isEmpty(data)) {
      throw new IllegalArgumentException("Fragment is not valid [" + id + "], [" + data + "].");
    }
    this.id = id;
    this.content = data;
    this.context = new JsonObject();
  }

  public static Fragment raw(String data) {
    return new Fragment(RAW_FRAGMENT_ID, data);
  }

  public static Fragment snippet(String id, String data) {
    return new Fragment(id, data);
  }

  public JsonObject toJson() {
    return new JsonObject().put(ID, id).put(CONTENT, content).put(CONTEXT, context);
  }

  public String getId() {
    return id;
  }

  public String getContent() {
    return content;
  }

  public JsonObject getContext() {
    return context;
  }

  public boolean isRaw() {
    return RAW_FRAGMENT_ID.equals(id);
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Fragment)) return false;
    Fragment that = (Fragment) o;
    return Objects.equal(id, that.id) &&
        Objects.equal(content, that.content) &&
        Objects.equal(context, that.context);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id, content, context);
  }
}
