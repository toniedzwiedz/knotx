/*
 * Knot.x - Reactive microservice assembler - View Engine Verticle
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
package com.cognifide.knotx.engine.view.impl;

import com.cognifide.knotx.dataobjects.ClientRequest;
import com.cognifide.knotx.dataobjects.ClientResponse;
import com.cognifide.knotx.dataobjects.KnotContext;
import com.cognifide.knotx.engine.view.ViewEngineConfiguration;
import com.cognifide.knotx.fragments.Fragment;
import com.cognifide.knotx.junit.FileReader;
import com.cognifide.knotx.junit.Logback;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.parser.Parser;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.buffer.Buffer;
import io.vertx.rxjava.core.eventbus.EventBus;
import io.vertx.rxjava.core.eventbus.Message;
import rx.Observable;

import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.spy;

@RunWith(VertxUnitRunner.class)
public class TemplateEngineTest {

  private static final String TEST_RESOURCES_ROOT = "template-engine";
  private static final String TEMPLATE_ENGINE_CONFIG_JSON = TEST_RESOURCES_ROOT + "/test-config.json";

  private RunTestOnContext runTestOnContext = new RunTestOnContext();
  private Vertx vertx;

  @Rule
  public RuleChain chain = RuleChain.outerRule(new Logback()).around(runTestOnContext);

  @Before
  public void setUp() throws Exception {
    vertx = Vertx.newInstance(runTestOnContext.vertx());
    vertx.eventBus().<JsonObject>consumer("knotx.mock.service-adapter").handler(this::mockServiceAdapter);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void whenRequestedWithOneRawFragment_expectNotChangedHtmlAndNoServiceCalls(TestContext context) throws Exception {
    Async async = context.async();

    // given
    final String testName = "one-raw-fragment";
    final EventBus eventBus = spy(vertx.eventBus());
    final TemplateEngine templateEngine = new TemplateEngine(eventBus, getConfig(TEMPLATE_ENGINE_CONFIG_JSON));

    final String expectedResponse = getExpectedResponse(testName);

    // when
    Observable<String> result = templateEngine.process(getContext(testName, 1));

    // then
    result.subscribe(
        response -> {
          context.assertEquals(unifyHtml(expectedResponse), unifyHtml(response));
          verify(eventBus, times(0)).send(anyString(), anyObject(), (Handler<AsyncResult<Message<Object>>>) anyObject());
        },
        error -> context.fail(error.getMessage()),
        async::complete);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void whenRequestedWithOneSnippetFragment_expectHtmlAndOneServiceCall(TestContext context) throws Exception {
    Async async = context.async();

    // given
    final String testName = "one-snippet-fragment";
    final EventBus eventBus = spy(vertx.eventBus());
    final TemplateEngine templateEngine = new TemplateEngine(eventBus, getConfig(TEMPLATE_ENGINE_CONFIG_JSON));

    final String expectedResponse = getExpectedResponse(testName);

    // when
    Observable<String> result = templateEngine.process(getContext(testName, 1));

    // then
    result.subscribe(
        response -> {
          context.assertEquals(unifyHtml(expectedResponse), unifyHtml(response));
          verify(eventBus, times(1)).send(anyString(), anyObject(), (Handler<AsyncResult<Message<Object>>>) anyObject());
        },
        error -> context.fail(error.getMessage()),
        async::complete);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void whenRequestedWithSevenMixedFragments_expectHtmlAndThreeServiceCall(TestContext context) throws Exception {
    Async async = context.async();

    // given
    final String testName = "seven-mixed-fragments";
    final EventBus eventBus = spy(vertx.eventBus());
    final TemplateEngine templateEngine = new TemplateEngine(eventBus, getConfig(TEMPLATE_ENGINE_CONFIG_JSON));

    final String expectedResponse = getExpectedResponse(testName);

    // when
    Observable<String> result = templateEngine.process(getContext(testName, 7));

    // then
    result.subscribe(
        response -> {
          context.assertEquals(unifyHtml(expectedResponse), unifyHtml(response));
          verify(eventBus, times(3)).send(anyString(), anyObject(), (Handler<AsyncResult<Message<Object>>>) anyObject());
        },
        error -> context.fail(error.getMessage()),
        async::complete);
  }

  private ViewEngineConfiguration getConfig(String configFile) throws Exception {
    return new ViewEngineConfiguration(new JsonObject(FileReader.readText(configFile)));
  }

  private KnotContext getContext(String testResourcesPath, int fragmentsCount) throws Exception {
    ArrayList<Fragment> fragments = new ArrayList<>(fragmentsCount);
    for (int i = 1; i <= fragmentsCount; ++i) {
      final String fragmentContent = FileReader.readText(getFragmentResourcePath(testResourcesPath, i));
      String id = fragmentContent.startsWith("<script") ? "_snippet" : "_raw";
      fragments.add(new Fragment(new JsonObject().put("_ID", id).put("_CONTENT", fragmentContent)));
    }
    return new KnotContext().setFragments(fragments).setClientRequest(new ClientRequest());
  }

  private String getFragmentResourcePath(String testResourcesPath, int i) {
    return TEST_RESOURCES_ROOT + File.separator + testResourcesPath + File.separator + "fragment" + i + ".txt";
  }

  private String getExpectedResponse(String testName) throws Exception {
    return FileReader.readText(TEST_RESOURCES_ROOT + "/" + testName + "/expected.html");
  }

  private String unifyHtml(String html) {
    Document.OutputSettings outputSettings = new Document.OutputSettings()
        .escapeMode(Entities.EscapeMode.xhtml)
        .indentAmount(0)
        .prettyPrint(false);
    return Jsoup.parse(html.replace("\n", ""), "UTF-8", Parser.xmlParser()).outputSettings(outputSettings).html().trim();
  }

  private void mockServiceAdapter(Message<JsonObject> message) {
    Observable.just(message.body())
        .subscribe(
            req -> {
              final String resourcePath = req.getJsonObject("params").getString("path");
              try {
                final String responseBody = FileReader.readText(resourcePath);
                message.reply(new ClientResponse().setStatusCode(HttpResponseStatus.OK).setBody(Buffer.buffer(responseBody)).toJson());
              } catch (Exception e) {
                message.reply(new ClientResponse().setStatusCode(HttpResponseStatus.NOT_FOUND).toJson());
              }
            },
            error -> {
              message.reply(new ClientResponse().setStatusCode(HttpResponseStatus.NOT_FOUND).toJson());
            }
        );
  }
}
