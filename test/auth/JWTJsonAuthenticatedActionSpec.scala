package auth

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import pdi.jwt.{JwtAlgorithm, JwtJson}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Cookie, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import java.security.{KeyPair, KeyPairGenerator, PrivateKey, SecureRandom}
import java.util.Base64

import scala.concurrent.Future

class JWTJsonAuthenticatedActionSpec extends PlaySpec with MockitoSugar with GuiceOneAppPerTest with ScalaFutures {

  private val rsaKeyPair = generateRsaKeyPair()

  override def fakeApplication(): Application = {
    GuiceApplicationBuilder()
      .configure(Map(
        "smui.JWTJsonAuthenticatedAction.login.url" -> "https://redirect.com",
        "smui.JWTJsonAuthenticatedAction.cookie.name" -> "test_token",
        "smui.JWTJsonAuthenticatedAction.public.key" -> new String(Base64.getEncoder.encode(rsaKeyPair.getPublic.getEncoded)),
        "smui.JWTJsonAuthenticatedAction.algorithm" -> "rsa",
        "smui.JWTJsonAuthenticatedAction.authorization.active" -> "true",
        "smui.JWTJsonAuthenticatedAction.authorization.json.path" -> "$.roles",
        "smui.JWTJsonAuthenticatedAction.authorization.roles" -> "admin, search-manager",
      ))
      .build()
  }

  "The JWTJsonAuthenticatedAction" must {

    "redirect if no jwt token is provided" in {
      val request = FakeRequest(GET, "/")
      val home: Future[Result] = route(app, request).get

      whenReady(home) { result =>
        result.header.status mustBe 303
        result.header.headers(LOCATION) must equal(getJwtConfiguration("login.url"))
      }
    }

    "redirect if an invalid jwt token is provided" in {
      val request = FakeRequest(GET, "/")
        .withCookies(buildJWTCookie("test_user", Seq("admin"), Some("invalid_token")))

      val home: Future[Result] = route(app, request).get

      whenReady(home) { result =>
        result.header.status mustBe 303
        result.header.headers(LOCATION) must equal(getJwtConfiguration("login.url"))
      }
    }

    "redirect if the user has not the right permissions" in {
      val request = FakeRequest(GET, "/")
        .withCookies(buildJWTCookie("test_user", Seq("not_admin")))

      val home: Future[Result] = route(app, request).get

      whenReady(home) { result =>
        result.header.status mustBe 303
        result.header.headers(LOCATION) must equal(getJwtConfiguration("login.url"))
      }
    }

    "lead user to SMUI if a valid rsa encoded token is provided" in {
      val request = FakeRequest(GET, "/")
        .withCookies(buildJWTCookie("test_user", Seq("search-manager")))

      val home: Future[Result] = route(app, request).get

      whenReady(home) { result =>
        result.header.status mustBe 200
      }
    }

    "should secure API routes" in {
      var request = FakeRequest(GET, "/api/v1/inputTags")
        .withCookies(buildJWTCookie("test_user", Seq("search-manager")))

      var home: Future[Result] = route(app, request).get

      whenReady(home) { result =>
        result.header.status mustBe 200
      }

      request = FakeRequest(GET, "/api/v1/inputTags")
      home = route(app, request).get

      whenReady(home) { result =>
        result.header.status mustBe 303
        result.header.headers(LOCATION) must equal(getJwtConfiguration("login.url"))
      }
    }
  }

  private def generateRsaKeyPair() = {
    val keyGen = KeyPairGenerator.getInstance("RSA")
    keyGen.initialize(2048, new SecureRandom())
    keyGen.generateKeyPair()
  }

  private def buildJWTCookie(userName: String, roles: Seq[String], value: Option[String] = None): Cookie = {
    val token = Json.obj(("roles", roles), ("user", userName))

    Cookie(
      name = getJwtConfiguration("cookie.name"),
      value = if (value.isEmpty) JwtJson.encode(token, rsaKeyPair.getPrivate, JwtAlgorithm.RS512) else value.get
    )
  }

  private def getJwtConfiguration(key: String): String = {
    app.configuration.get[String]("smui.JWTJsonAuthenticatedAction." + key)
  }

}
