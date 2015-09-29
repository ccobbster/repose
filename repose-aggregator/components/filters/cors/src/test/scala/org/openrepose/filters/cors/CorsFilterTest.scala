/*
 * _=_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=
 * Repose
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
 * Copyright (C) 2010 - 2015 Rackspace US, Inc.
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
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
 * =_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=_
 */
package org.openrepose.filters.cors

import javax.servlet.FilterChain

import org.junit.runner.RunWith
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.openrepose.commons.utils.http.CommonHttpHeader
import org.openrepose.filters.cors.config._
import org.openrepose.filters.cors.config.Origins.Origin
import org.scalatest.{Matchers, BeforeAndAfter, FunSpec}
import org.scalatest.junit.JUnitRunner
import org.springframework.mock.web.{MockHttpServletResponse, MockHttpServletRequest}

import scala.collection.JavaConverters._

@RunWith(classOf[JUnitRunner])
class CorsFilterTest extends FunSpec with BeforeAndAfter with Matchers {

  val HttpMethods = List("OPTIONS", "GET", "HEAD", "POST", "PUT", "DELETE", "TRACE", "CONNECT", "CUSTOM")

  var corsFilter: CorsFilter = _
  var servletRequest: MockHttpServletRequest = _
  var servletResponse: MockHttpServletResponse = _
  var filterChain: FilterChain = _

  before {
    servletRequest = new MockHttpServletRequest
    servletResponse = new MockHttpServletResponse
    filterChain = mock(classOf[FilterChain])

    corsFilter = new CorsFilter(null)
    allowAllOriginsAndGets()
  }

  describe("the doFilter method") {
    describe("when a non-CORS request is received") {
      HttpMethods.foreach { httpMethod =>
        it(s"should call the next filter in the filter chain for HTTP method $httpMethod") {
          // given no request headers
          servletRequest.setMethod(httpMethod)

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          verify(filterChain).doFilter(servletRequest, servletResponse)
        }

        it(s"should not add CORS specific headers for HTTP method $httpMethod") {
          // given no request headers
          servletRequest.setMethod(httpMethod)

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getHeader(CommonHttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN.toString) shouldBe null
          servletResponse.getHeader(CommonHttpHeader.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString) shouldBe null
          servletResponse.getHeader(CommonHttpHeader.ACCESS_CONTROL_EXPOSE_HEADERS.toString) shouldBe null
          servletResponse.getHeader(CommonHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString) shouldBe null
          servletResponse.getHeader(CommonHttpHeader.ACCESS_CONTROL_ALLOW_HEADERS.toString) shouldBe null
        }

        it(s"should not have an HTTP status set for HTTP method $httpMethod") {
          // given no request headers
          servletRequest.setMethod(httpMethod)
          servletResponse.setStatus(-321)

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getStatus shouldBe -321  // verify unchanged
        }
      }

      HttpMethods.filter{_ != "OPTIONS"}.foreach { httpMethod =>
        it(s"should have 'Origin' in the Vary header for HTTP method $httpMethod") {
          // given no request headers
          servletRequest.setMethod(httpMethod)

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getHeaders(CommonHttpHeader.VARY.toString) should contain theSameElementsAs List(CommonHttpHeader.ORIGIN.toString)
        }
      }

      it("should have the preflight request headers in the Vary header for HTTP method OPTIONS") {
        // given no request headers
        servletRequest.setMethod("OPTIONS")

        corsFilter.doFilter(servletRequest, servletResponse, filterChain)

        servletResponse.getHeaders(CommonHttpHeader.VARY.toString) should contain theSameElementsAs List(
          CommonHttpHeader.ORIGIN.toString, CommonHttpHeader.ACCESS_CONTROL_REQUEST_HEADERS.toString)
      }
    }

    describe("when a preflight request is received") {
      HttpMethods.foreach { requestMethod =>
        it(s"should return an HTTP status of 200 for request HTTP method $requestMethod") {
          servletRequest.setMethod("OPTIONS")
          servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
          servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, requestMethod)
          servletResponse.setStatus(-321)  // since default value is 200 (the test success value)

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getStatus shouldBe 200
          servletResponse.getHeader(CommonHttpHeader.ACCESS_CONTROL_ALLOW_HEADERS.toString) shouldBe null
        }

        it(s"should not call the next filter for request HTTP method $requestMethod") {
          servletRequest.setMethod("OPTIONS")
          servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
          servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, requestMethod)

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          verify(filterChain, never()).doFilter(servletRequest, servletResponse)
        }

        it(s"should not add actual request specific headers for HTTP method $requestMethod") {
          servletRequest.setMethod("OPTIONS")
          servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
          servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, requestMethod)

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getHeader(CommonHttpHeader.ACCESS_CONTROL_EXPOSE_HEADERS.toString) shouldBe null
        }

        it(s"should have the Access-Control-Allow-Methods header set for request HTTP method $requestMethod") {
          servletRequest.setMethod("OPTIONS")
          servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
          servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, requestMethod)

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getHeader(CommonHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString) should not be null
        }

        List("X-Auth-Token", "X-Panda, X-Unicorn", "Accept, User-Agent, X-Trans-Id").foreach { requestHeader =>
          it(s"should have the Access-Control-Allow-Headers header set for request HTTP method $requestMethod and request headers $requestHeader") {
            servletRequest.setMethod("OPTIONS")
            servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
            servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, requestMethod)
            servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_HEADERS.toString, requestHeader)

            corsFilter.doFilter(servletRequest, servletResponse, filterChain)

            servletResponse.getHeader(CommonHttpHeader.ACCESS_CONTROL_ALLOW_HEADERS.toString) shouldEqual requestHeader
          }
        }

        it(s"should not have the Access-Control-Allow-Headers header set when none requested for request HTTP method $requestMethod") {
          servletRequest.setMethod("OPTIONS")
          servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
          servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, requestMethod)

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getHeader(CommonHttpHeader.ACCESS_CONTROL_ALLOW_HEADERS.toString) shouldBe null
        }

        it(s"should have the Access-Control-Allow-Credentials header set to true for request HTTP method $requestMethod") {
          servletRequest.setMethod("OPTIONS")
          servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
          servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, requestMethod)

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getHeader(CommonHttpHeader.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString) shouldEqual "true"
        }

        List("http://totally.allowed", "http://completely.legit:8080", "https://seriously.safe:8443").foreach { origin =>
          it(s"should have the Access-Control-Allow-Origin set to the Origin of the request for request HTTP method $requestMethod and origin $origin") {
            servletRequest.setMethod("OPTIONS")
            servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, origin)
            servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, requestMethod)

            corsFilter.doFilter(servletRequest, servletResponse, filterChain)

            servletResponse.getHeader(CommonHttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN.toString) shouldEqual origin
          }
        }

        it(s"should have the Vary header correctly populated for request HTTP method $requestMethod") {
          servletRequest.setMethod("OPTIONS")
          servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
          servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, requestMethod)

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getHeaders(CommonHttpHeader.VARY.toString) should contain theSameElementsAs List(
            CommonHttpHeader.ORIGIN.toString, CommonHttpHeader.ACCESS_CONTROL_REQUEST_HEADERS.toString)
        }
      }
    }

    describe("when an actual request is received") {
      HttpMethods.foreach { httpMethod =>
        it (s"should call the next filter in the filter chain for HTTP method $httpMethod") {
          servletRequest.setMethod(httpMethod)
          servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          verify(filterChain).doFilter(servletRequest, servletResponse)
        }

        it(s"should not add preflight specific headers for HTTP method $httpMethod") {
          servletRequest.setMethod(httpMethod)
          servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getHeader(CommonHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString) shouldBe null
          servletResponse.getHeader(CommonHttpHeader.ACCESS_CONTROL_ALLOW_HEADERS.toString) shouldBe null
        }

        it(s"should not have an HTTP status set for HTTP method $httpMethod") {
          servletRequest.setMethod(httpMethod)
          servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
          servletResponse.setStatus(-321)

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getStatus shouldBe -321  // verify unchanged
        }

        List(
          List("X-Auth-Token"),
          List("X-Auth-Token", "X-Trans-Id"),
          List("X-Trans-Id", "Content-Type", "X-Panda", "X-OMG-Ponies")
        ).foreach { responseHeaders =>
          it(s"should include the response headers in Access-Control-Expose-Headers for HTTP method $httpMethod and headers $responseHeaders") {
            servletRequest.setMethod(httpMethod)
            servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")

            // only add the headers to the response when the filterchain doFilter method is called
            doAnswer(new Answer[Void]() {
              def answer(invocation: InvocationOnMock): Void = {
                responseHeaders.foreach(servletResponse.addHeader(_, "totally legit value"))
                null
              }
            }).when(filterChain).doFilter(servletRequest, servletResponse)

            corsFilter.doFilter(servletRequest, servletResponse, filterChain)

            // Access-Control-Expose-Headers should have all of the response headers in it except for itself and the Vary header
            servletResponse.getHeaders(CommonHttpHeader.ACCESS_CONTROL_EXPOSE_HEADERS.toString) should contain theSameElementsAs
              servletResponse.getHeaderNames.asScala.filter { headerName =>
                headerName != CommonHttpHeader.ACCESS_CONTROL_EXPOSE_HEADERS.toString &&
                  headerName != CommonHttpHeader.VARY.toString}
          }
        }

        it(s"should have the Access-Control-Allow-Credentials header set to true for request HTTP method $httpMethod") {
          servletRequest.setMethod(httpMethod)
          servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getHeader(CommonHttpHeader.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString) shouldEqual "true"
        }

        List("http://totally.allowed", "http://completely.legit:8080", "https://seriously.safe:8443").foreach { origin =>
          it(s"should have the Access-Control-Allow-Origin set to the Origin of the request for request HTTP method $httpMethod and origin $origin") {
            servletRequest.setMethod(httpMethod)
            servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, origin)

            corsFilter.doFilter(servletRequest, servletResponse, filterChain)

            servletResponse.getHeader(CommonHttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN.toString) shouldEqual origin
          }
        }
      }

      HttpMethods.filter{_ != "OPTIONS"}.foreach { httpMethod =>
        it(s"should have 'Origin' in the Vary header for HTTP method $httpMethod") {
          servletRequest.setMethod(httpMethod)
          servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getHeaders(CommonHttpHeader.VARY.toString) should contain theSameElementsAs List(CommonHttpHeader.ORIGIN.toString)
        }
      }

      it("should have the preflight request headers in the Vary header for HTTP method OPTIONS") {
        servletRequest.setMethod("OPTIONS")
        servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")

        corsFilter.doFilter(servletRequest, servletResponse, filterChain)

        servletResponse.getHeaders(CommonHttpHeader.VARY.toString) should contain theSameElementsAs List(
          CommonHttpHeader.ORIGIN.toString, CommonHttpHeader.ACCESS_CONTROL_REQUEST_HEADERS.toString)
      }
    }

    describe("when origin filtering") {
      it("should allow a preflight request with a specific origin") {
        servletRequest.setMethod("OPTIONS")
        servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
        servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, "GET")
        servletResponse.setStatus(-321)  // since default value is 200 (the test success value)

        val configOrigin = new Origin
        configOrigin.setValue("http://totally.allowed")
        setConfiguredAllowedOriginsTo(List(configOrigin))

        corsFilter.doFilter(servletRequest, servletResponse, filterChain)

        servletResponse.getStatus shouldBe 200  // preflight success
        servletResponse.getHeader(CommonHttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN.toString) shouldBe "http://totally.allowed"
      }

      it("should allow a preflight request with a regex matched origin") {
        servletRequest.setMethod("OPTIONS")
        servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://good.enough.com:8080")
        servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, "GET")
        servletResponse.setStatus(-321)  // since default value is 200 (the test success value)

        val configOrigin = new Origin
        configOrigin.setValue("http://.*good.enough.*")
        configOrigin.setRegex(true)
        setConfiguredAllowedOriginsTo(List(configOrigin))

        corsFilter.doFilter(servletRequest, servletResponse, filterChain)

        servletResponse.getStatus shouldBe 200  // preflight success
        servletResponse.getHeader(CommonHttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN.toString) shouldBe "http://good.enough.com:8080"
      }

      it("should deny a preflight request with an unmatched origin") {
        servletRequest.setMethod("OPTIONS")
        servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://not.going.to.work:9000")
        servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, "GET")
        servletResponse.setStatus(-321)  // since default value is 200 (the test success value)

        val configOrigin = new Origin
        configOrigin.setValue("NOPE")
        setConfiguredAllowedOriginsTo(List(configOrigin))

        corsFilter.doFilter(servletRequest, servletResponse, filterChain)

        servletResponse.getStatus shouldBe 403
      }

      it("should allow an actual request with a specific origin") {
        servletRequest.setMethod("GET")
        servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://let.me.in:8000")
        servletResponse.setStatus(-321)

        val configOrigin = new Origin
        configOrigin.setValue("http://let.me.in:8000")
        setConfiguredAllowedOriginsTo(List(configOrigin))

        corsFilter.doFilter(servletRequest, servletResponse, filterChain)

        servletResponse.getStatus shouldBe -321  // verify unchanged
        servletResponse.getHeader(CommonHttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN.toString) shouldBe "http://let.me.in:8000"
      }

      it("should allow an actual request with a regex matched origin") {
        servletRequest.setMethod("GET")
        servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "https://you.can.trust.me:8443")
        servletResponse.setStatus(-321)

        val configOrigin = new Origin
        configOrigin.setValue("https://.*trust.*443")
        configOrigin.setRegex(true)
        setConfiguredAllowedOriginsTo(List(configOrigin))

        corsFilter.doFilter(servletRequest, servletResponse, filterChain)

        servletResponse.getStatus shouldBe -321  // verify unchanged
        servletResponse.getHeader(CommonHttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN.toString) shouldBe "https://you.can.trust.me:8443"
      }

      it("should deny an actual request with an unmatched origin") {
        servletRequest.setMethod("GET")
        servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://no.way.bro:80")
        servletResponse.setStatus(-321)

        val configOrigin = new Origin
        configOrigin.setValue("NOPE")
        configOrigin.setRegex(true)
        setConfiguredAllowedOriginsTo(List(configOrigin))

        corsFilter.doFilter(servletRequest, servletResponse, filterChain)

        servletResponse.getStatus shouldBe 403
      }

      it("should allow a non-CORS request that does not have an origin header") {
        servletRequest.setMethod("GET")
        servletResponse.setStatus(-321)

        val configOrigin = new Origin
        configOrigin.setValue("NOPE")
        setConfiguredAllowedOriginsTo(List(configOrigin))

        corsFilter.doFilter(servletRequest, servletResponse, filterChain)

        servletResponse.getStatus shouldBe -321  // verify unchanged
      }
    }

    describe("when specifying which HTTP methods are allowed for a resource") {
      HttpMethods.foreach { httpMethod =>
        it(s"should permit HTTP method $httpMethod when it is globally allowed in config") {
          corsFilter.configurationUpdated(createCorsConfig(List(".*"), List(httpMethod), List()))
          servletRequest.setMethod("OPTIONS")
          servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
          servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, httpMethod)
          servletRequest.setRequestURI("/")

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getHeaders(CommonHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString) should contain (httpMethod)
        }

        it(s"should not permit HTTP method $httpMethod when it is not globally allowed in config") {
          corsFilter.configurationUpdated(createCorsConfig(List(".*"), List("TRANSMUTE"), List()))
          servletRequest.setMethod("OPTIONS")
          servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
          servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, httpMethod)
          servletRequest.setRequestURI("/")

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getHeaders(CommonHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString) should not contain httpMethod
        }

        it(s"should permit HTTP method $httpMethod when it is configured for the root resource") {
          corsFilter.configurationUpdated(createCorsConfig(List(".*"), List("NOTHING"), List(("/.*", List(httpMethod)))))
          servletRequest.setMethod("OPTIONS")
          servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
          servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, httpMethod)
          servletRequest.setRequestURI("/")

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getHeaders(CommonHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString) should contain (httpMethod)
        }

        it(s"should not permit HTTP method $httpMethod when it is not configured and a root resource allows something else") {
          corsFilter.configurationUpdated(createCorsConfig(List(".*"), List("TRANSMUTE"), List(("/.*", List("DESTROY")))))
          servletRequest.setMethod("OPTIONS")
          servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
          servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, httpMethod)
          servletRequest.setRequestURI("/")

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getHeaders(CommonHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString) should not contain httpMethod
        }

        it(s"should not permit HTTP method $httpMethod when a specific child resource eclipses the root resource permission") {
          corsFilter.configurationUpdated(createCorsConfig(List(".*"), List("TRANSMUTE"), List(("/servers", List("CREATE")), ("/.*", List(httpMethod)))))
          servletRequest.setMethod("OPTIONS")
          servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
          servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, httpMethod)
          servletRequest.setRequestURI("/servers")

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getHeaders(CommonHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString) should not contain httpMethod
        }

        it(s"should permit HTTP method $httpMethod when a specific child resource does not but global config does") {
          corsFilter.configurationUpdated(createCorsConfig(List(".*"), List(httpMethod), List(("/servers", List("TRANSMUTE")), ("/.*", List("DESTROY")))))
          servletRequest.setMethod("OPTIONS")
          servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
          servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, httpMethod)
          servletRequest.setRequestURI("/servers")

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getHeaders(CommonHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString) should contain (httpMethod)
        }

        it(s"should permit HTTP method $httpMethod when a specific child resource allows it") {
          corsFilter.configurationUpdated(createCorsConfig(List(".*"), List("STARE"), List(("/servers", List(httpMethod)), ("/.*", List("DESTROY")))))
          servletRequest.setMethod("OPTIONS")
          servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
          servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, httpMethod)
          servletRequest.setRequestURI("/servers")

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getHeaders(CommonHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString) should contain (httpMethod)
        }

        it(s"should always permit the same HTTP methods no matter what the request method is for method $httpMethod") {
          corsFilter.configurationUpdated(createCorsConfig(List(".*"), List("POKE"), List()))
          servletRequest.setMethod("OPTIONS")
          servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
          servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, httpMethod)
          servletRequest.setRequestURI("/servers")

          corsFilter.doFilter(servletRequest, servletResponse, filterChain)

          servletResponse.getHeaders(CommonHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString) should contain ("POKE")
        }
      }

      it("should permit multiple HTTP methods specified in global config") {
        corsFilter.configurationUpdated(createCorsConfig(List(".*"), List("GET", "POST", "PUT", "DELETE"), List()))
        servletRequest.setMethod("OPTIONS")
        servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
        servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, "PING")
        servletRequest.setRequestURI("/")

        corsFilter.doFilter(servletRequest, servletResponse, filterChain)

        servletResponse.getHeaders(CommonHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString) should contain theSameElementsAs List("GET", "POST", "PUT", "DELETE")
      }

      it("should permit multiple HTTP methods specified in both global config and a specific resource") {
        corsFilter.configurationUpdated(createCorsConfig(List(".*"), List("GET", "POST"), List(("/players", List("PUT", "DELETE")))))
        servletRequest.setMethod("OPTIONS")
        servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
        servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, "PING")
        servletRequest.setRequestURI("/players")

        corsFilter.doFilter(servletRequest, servletResponse, filterChain)

        servletResponse.getHeaders(CommonHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString) should contain theSameElementsAs List("GET", "POST", "PUT", "DELETE")
      }

      it("should permit multiple HTTP methods specified in both global config and a specific root resource") {
        corsFilter.configurationUpdated(createCorsConfig(List(".*"), List("GET", "POST"), List(("/.*", List("PUT", "PATCH")))))
        servletRequest.setMethod("OPTIONS")
        servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
        servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, "PING")
        servletRequest.setRequestURI("/players")

        corsFilter.doFilter(servletRequest, servletResponse, filterChain)

        servletResponse.getHeaders(CommonHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString) should contain theSameElementsAs List("GET", "POST", "PUT", "PATCH")
      }

      it("should be able to handle a path param with a configured resource path specified with regex") {
        corsFilter.configurationUpdated(createCorsConfig(List(".*"), List("GET"), List(("/players/[^/]+/achievements", List("POST", "PUT", "PATCH")))))
        servletRequest.setMethod("OPTIONS")
        servletRequest.addHeader(CommonHttpHeader.ORIGIN.toString, "http://totally.allowed")
        servletRequest.addHeader(CommonHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString, "PING")
        servletRequest.setRequestURI("/players/bob_loblaw/achievements")

        corsFilter.doFilter(servletRequest, servletResponse, filterChain)

        servletResponse.getHeaders(CommonHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString) should contain theSameElementsAs List("GET", "POST", "PUT", "PATCH")
      }
    }
  }

  describe("configuration") {
    for (
      origins <- List(
        List("http://legit.com:8080"),
        List("http://potato.com", "https://panda.com:8443", "pancakes.and.bacon"));
      methods <- List(
        List(),
        List("GET"),
        List("OPTIONS", "POST", "PATCH"));
      resources <- List(
        List(),
        List(("/v1/.*", List("GET", "PUT"))),
        List(("/v1/.*", List("GET", "PUT")), ("/v2/.*", List("DELETE"))))
    ) {
      it(s"should be able to load configuration for origins $origins, methods $methods, resources $resources") {
        corsFilter.configurationUpdated(createCorsConfig(origins, methods, resources))
      }
    }
  }

  def createCorsConfig(allowedOrigins: List[String],
                   allowedMethods: List[String],
                   resources: List[(String, List[String])]): CorsConfig = {
    val config = new CorsConfig

    val configOrigins = new Origins
    configOrigins.getOrigin.addAll(allowedOrigins.map { value =>
      val origin = new Origin
      origin.setValue(value)
      origin.setRegex(true)
      origin
    }.asJava)
    config.setAllowedOrigins(configOrigins)

    // leave the list of methods null if there's nothing to configure
    if (allowedMethods.nonEmpty) {
      val configMethods = new Methods
      configMethods.getMethod.addAll(allowedMethods.asJava)
      config.setAllowedMethods(configMethods)
    }

    // leave the list of resources null if there's nothing to configure
    if (resources.nonEmpty) {
      val configResources = new Resources
      configResources.getResource.addAll(resources.map { case (path, resourceAllowedMethods) =>
        val configResource = new Resource
        configResource.setPath(path)
        val resourceConfigMethods = new Methods
        resourceConfigMethods.getMethod.addAll(resourceAllowedMethods.asJava)
        configResource.setAllowedMethods(resourceConfigMethods)
        configResource
      }.asJava)
      config.setResources(configResources)
    }

    config
  }

  def allowAllOriginsAndGets(): Unit = {
    corsFilter.configurationUpdated(createCorsConfig(List(".*"), List("GET"), List()))
  }

  def setConfiguredAllowedOriginsTo(origins: List[Origin]): Unit = {
    val config = new CorsConfig
    val configOrigins = new Origins
    configOrigins.getOrigin.addAll(origins.asJava)
    config.setAllowedOrigins(configOrigins)
    corsFilter.configurationUpdated(config)
  }
}
