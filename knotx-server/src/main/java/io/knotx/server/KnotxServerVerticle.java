/*
 * Copyright (C) 2016 Cognifide Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.knotx.server;

import io.knotx.server.configuration.KnotxCSRFConfig;
import io.knotx.server.configuration.KnotxServerConfiguration;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.vertx.reactivex.ext.web.handler.CSRFHandler;
import io.vertx.reactivex.ext.web.handler.CookieHandler;
import io.vertx.reactivex.ext.web.handler.ErrorHandler;
import io.vertx.reactivex.ext.web.handler.LoggerHandler;

public class KnotxServerVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(KnotxServerVerticle.class);

  private static final HttpResponseStatus BAD_REQUEST = HttpResponseStatus.BAD_REQUEST;

  private KnotxServerConfiguration configuration;

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    configuration = new KnotxServerConfiguration(config());
  }

  @Override
  public void start(Future<Void> fut) {
    LOGGER.info("Starting <{}>", this.getClass().getSimpleName());
    KnotxCSRFConfig csrfConfig = configuration.getCsrfConfig();
    CSRFHandler csrfHandler = CSRFHandler.create(csrfConfig.getSecret())
        .setNagHttps(true) //Generates warning message in log if https is not used
        .setCookieName(csrfConfig.getCookieName())
        .setCookiePath(csrfConfig.getCookiePath())
        .setHeaderName(csrfConfig.getHeaderName())
        .setTimeout(csrfConfig.getTimeout());

    Router router = Router.router(vertx);
    if (configuration.getAccessLogConfig().isEnabled()) {
      router.route().handler(LoggerHandler.create(configuration.getAccessLogConfig().isImmediate(),
          configuration.getAccessLogConfig().getFormat()));
    }
    router.route().handler(KnotxHeaderHandler.create(configuration));
    router.route().handler(SupportedMethodsAndPathsHandler.create(configuration));
    router.route().handler(CookieHandler.create());
    router.route().handler(BodyHandler.create(configuration.getFileUploadDirectory())
        .setBodyLimit(configuration.getFileUploadLimit()));

    router.route().handler(KnotxContextHandler.create());

    configuration.getDefaultFlow().getEngineRouting().forEach((key, value) -> {
      value.forEach(
          criteria -> {
            if (criteria.isCsrfEnabled()) {
              router.route().method(key)
                  .pathRegex(criteria.path())
                  .handler(csrfHandler);
            }
            router.route()
                .method(key)
                .pathRegex(criteria.path())
                .handler(KnotxRepositoryHandler.create(vertx, configuration));

            router.route()
                .method(key)
                .pathRegex(criteria.path())
                .handler(KnotxSplitterHandler.create(vertx, configuration));

            router.route()
                .method(key)
                .pathRegex(criteria.path())
                .handler(KnotxEngineHandler
                    .create(vertx, configuration, criteria.address(), criteria.onTransition()));

            router.route()
                .method(key)
                .pathRegex(criteria.path())
                .handler(KnotxAssemblerHandler.create(vertx, configuration));
          }
      );
    });

    if (configuration.getCustomFlow().getEngineRouting() != null) {
      configuration.getCustomFlow().getEngineRouting().forEach((key, value) -> {
        value.forEach(
            criteria -> {
              if (criteria.isCsrfEnabled()) {
                router.route().method(key)
                    .pathRegex(criteria.path())
                    .handler(csrfHandler);
              }
              router.route().method(key)
                  .pathRegex(criteria.path())
                  .handler(
                      KnotxGatewayContextHandler.create(vertx, configuration, criteria.address()));

              router.route()
                  .method(key)
                  .pathRegex(criteria.path())
                  .handler(KnotxEngineHandler
                      .create(vertx, configuration, criteria.address(), criteria.onTransition()));

              router.route()
                  .method(key)
                  .pathRegex(criteria.path())
                  .handler(KnotxGatewayResponseProviderHandler.create(vertx, configuration));
            }
        );
      });
    }

    router.route().failureHandler(ErrorHandler.create(configuration.displayExceptionDetails()));

    createHttpServer()
        .requestHandler(request -> {
          try {
            router.accept(request);
          } catch (IllegalArgumentException ex) {
            LOGGER.warn("Problem decoding Query String", ex);

            request.response()
                .setStatusCode(BAD_REQUEST.code())
                .setStatusMessage(BAD_REQUEST.reasonPhrase())
                .end("Invalid characters in Query Parameter");
          }
        })
        .rxListen()
        .subscribe(ok -> {
              LOGGER.info("Knot.x HTTP Server started. Listening on port {}",
                  configuration.getServerOptions().getInteger("port"));
              fut.complete();
            },
            error -> {
              LOGGER.error("Unable to start Knot.x HTTP Server.", error.getCause());
              fut.fail(error);
            }
        );

  }

  private HttpServer createHttpServer() {
    JsonObject serverOptions = configuration.getServerOptions();

    return serverOptions.isEmpty()
        ? vertx.createHttpServer()
        : vertx.createHttpServer(new HttpServerOptions(serverOptions));
  }
}
