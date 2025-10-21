package controllers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import models.User
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play._
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc.{BodyParsers, ControllerComponents}
import play.api.test.Helpers._
import play.api.test._
import services.UserService
import play.api.mvc.Result
import scala.concurrent.Future
import java.sql.Timestamp
import java.time.Instant
import scala.concurrent.ExecutionContext
import helpers.StubAuthenticatedAction
import security.JwtUtility
import repositories.UserRepo
import dtos.CreateUserDTO
import io.grpc.StatusRuntimeException
import io.grpc.Status
import org.scalatest.BeforeAndAfterEach

class UserControllerSpec
    extends PlaySpec
    with MockitoSugar
    with DefaultAwaitTimeout
    with BeforeAndAfterEach {

  implicit val system: ActorSystem = ActorSystem("TestSystem")
  implicit val mat: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = system.dispatcher
  val stubControllerComponents: ControllerComponents = Helpers.stubControllerComponents()
  val mockUserService: UserService = mock[UserService]
  val mockJwtUtility: JwtUtility = mock[JwtUtility]
  val mockUserRepo: UserRepo = mock[UserRepo]
  val mockConfig: Configuration = mock[Configuration]

  val stubAuthenticatedAction = new StubAuthenticatedAction(
    new BodyParsers.Default(stubControllerComponents.parsers),
    mockJwtUtility,
    mockUserRepo,
    shouldAuthenticate = true,
    userRole = "USER",
    userId = 1L
  )

  val controller = new UserController(
    mockUserService,
    stubControllerComponents,
    stubAuthenticatedAction
  )

  val sampleUser = User(
    id = 1L,
    username = "testuser",
    email = "test@example.com",
    password = Some("hashedPassword"),
    role = "USER",
    google_id = None,
    auth_provider = "LOCAL",
    is_deleted = false,
    created_at = Timestamp.from(Instant.now()),
    updated_at = Timestamp.from(Instant.now())
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUserService)
  }

  "UserController#addUser" should {

    "create a new user successfully" in {
      val createUserDTO = CreateUserDTO(
        username = "newuser",
        email = "newuser@example.com",
        password = "password123",
        role = Some("USER")
      )

      when(mockUserService.addUser(any[CreateUserDTO]()))
        .thenReturn(Future.successful(sampleUser))

      val request = FakeRequest(POST, "/user")
        .withHeaders("Content-Type" -> "application/json")
        .withJsonBody(
          Json.obj(
            "username" -> "newuser",
            "email" -> "newuser@example.com",
            "password" -> "password123"
          )
        )

      val result: Future[Result] = controller.addUser().apply(request)

      status(result) mustBe OK
      contentAsJson(result).\("message").as[String] mustBe "User Created"
      verify(mockUserService).addUser(any[CreateUserDTO]())
    }

    "return 400 when request body is not JSON" in {
      val request = FakeRequest(POST, "/user")
        .withTextBody("not json")

      val result: Future[Result] = controller.addUser().apply(request)

      status(result) mustBe BAD_REQUEST
      contentAsJson(result).\("message").as[String] mustBe "Request Body needs to be JSON"
    }

    "return 400 when JSON schema is invalid" in {
      val request = FakeRequest(POST, "/user")
        .withHeaders("Content-Type" -> "application/json")
        .withJsonBody(
          Json.obj(
            "username" -> "newuser"
          )
        )

      val result: Future[Result] = controller.addUser().apply(request)

      status(result) mustBe BAD_REQUEST
      contentAsJson(result).\("message").as[String] mustBe "Invalid Request Body Schema"
    }

    "return 409 when username or email already exists" in {
      when(mockUserService.addUser(any[CreateUserDTO]()))
        .thenReturn(
          Future.failed(
            new StatusRuntimeException(Status.ALREADY_EXISTS)
          )
        )

      val request = FakeRequest(POST, "/user")
        .withHeaders("Content-Type" -> "application/json")
        .withJsonBody(
          Json.obj(
            "username" -> "existinguser",
            "email" -> "existing@example.com",
            "password" -> "password123"
          )
        )

      val result: Future[Result] = controller.addUser().apply(request)

      status(result) mustBe CONFLICT
      contentAsString(result) must include("Username or Email already exists")
    }

    "return 500 when gRPC error occurs" in {
      when(mockUserService.addUser(any[CreateUserDTO]()))
        .thenReturn(
          Future.failed(
            new StatusRuntimeException(Status.INTERNAL)
          )
        )

      val request = FakeRequest(POST, "/user")
        .withHeaders("Content-Type" -> "application/json")
        .withJsonBody(
          Json.obj(
            "username" -> "newuser",
            "email" -> "newuser@example.com",
            "password" -> "password123"
          )
        )

      val result: Future[Result] = controller.addUser().apply(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsString(result) must include("gRPC error")
    }

    "return 500 when generic exception occurs" in {
      when(mockUserService.addUser(any[CreateUserDTO]()))
        .thenReturn(Future.failed(new Exception("Database error")))

      val request = FakeRequest(POST, "/user")
        .withHeaders("Content-Type" -> "application/json")
        .withJsonBody(
          Json.obj(
            "username" -> "newuser",
            "email" -> "newuser@example.com",
            "password" -> "password123"
          )
        )

      val result: Future[Result] = controller.addUser().apply(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsString(result) must include("ERROR OCCURRED")
    }
  }

  "UserController#getUserById" should {

    "return user when user exists" in {
      when(mockUserService.getUserById(1L))
        .thenReturn(Future.successful(sampleUser))

      val request = FakeRequest(GET, "/user/1")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.getUserById(1L).apply(request)

      status(result) mustBe OK
      contentAsJson(result).\("message").as[String] must include("User with Id: 1 was fetched")
      verify(mockUserService).getUserById(1L)
    }

    "return 404 when user not found (INVALID_ARGUMENT)" in {
      when(mockUserService.getUserById(999L))
        .thenReturn(
          Future.failed(
            new StatusRuntimeException(Status.INVALID_ARGUMENT)
          )
        )

      val request = FakeRequest(GET, "/user/999")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.getUserById(999L).apply(request)

      status(result) mustBe NOT_FOUND
      contentAsString(result) must include("Unable to find User with Id: 999")
    }

    "return 404 when user not found (NOT_FOUND)" in {
      when(mockUserService.getUserById(999L))
        .thenReturn(
          Future.failed(
            new StatusRuntimeException(Status.NOT_FOUND)
          )
        )

      val request = FakeRequest(GET, "/user/999")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.getUserById(999L).apply(request)

      status(result) mustBe NOT_FOUND
      contentAsString(result) must include("Unable to find User with Id: 999")
    }

    "return 500 when gRPC error occurs" in {
      when(mockUserService.getUserById(1L))
        .thenReturn(
          Future.failed(
            new StatusRuntimeException(Status.INTERNAL)
          )
        )

      val request = FakeRequest(GET, "/user/1")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.getUserById(1L).apply(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsString(result) must include("gRPC error")
    }

    "return 500 when generic exception occurs" in {
      when(mockUserService.getUserById(1L))
        .thenReturn(Future.failed(new Exception("Service error")))

      val request = FakeRequest(GET, "/user/1")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.getUserById(1L).apply(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsString(result) must include("ERROR OCCURRED")
    }
  }

  "UserController#deleteUserById" should {

    "delete user successfully when deleting own account" in {
      when(mockUserService.deleteUserById(1L))
        .thenReturn(Future.successful(true))

      val request = FakeRequest(DELETE, "/user/1")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.deleteUserById(1L).apply(request)

      status(result) mustBe NO_CONTENT
      verify(mockUserService).deleteUserById(1L)
    }

    "return 403 when trying to delete another user's account" in {
      val request = FakeRequest(DELETE, "/user/2")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.deleteUserById(2L).apply(request)

      status(result) mustBe FORBIDDEN
      contentAsJson(result).\("message").as[String] mustBe "You can only delete your own account"
      verify(mockUserService, never).deleteUserById(any[Long]())
    }

    "return 404 when user not found (service returns false)" in {
      when(mockUserService.deleteUserById(1L))
        .thenReturn(Future.successful(false))

      val request = FakeRequest(DELETE, "/user/1")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.deleteUserById(1L).apply(request)

      status(result) mustBe NOT_FOUND
      contentAsJson(result).\("message").as[String] must include("Unalbe to delete User with Id: 1")
    }

    "return 404 when gRPC returns INVALID_ARGUMENT" in {
      when(mockUserService.deleteUserById(1L))
        .thenReturn(
          Future.failed(
            new StatusRuntimeException(Status.INVALID_ARGUMENT)
          )
        )

      val request = FakeRequest(DELETE, "/user/1")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.deleteUserById(1L).apply(request)

      status(result) mustBe NOT_FOUND
      contentAsString(result) must include("Unable to delete User with Id: 1")
    }

    "return 404 when gRPC returns UNKNOWN" in {
      when(mockUserService.deleteUserById(1L))
        .thenReturn(
          Future.failed(
            new StatusRuntimeException(Status.UNKNOWN)
          )
        )

      val request = FakeRequest(DELETE, "/user/1")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.deleteUserById(1L).apply(request)

      status(result) mustBe NOT_FOUND
      contentAsString(result) must include("Unable to delete User with Id: 1")
    }

    "return 500 when gRPC error occurs" in {
      when(mockUserService.deleteUserById(1L))
        .thenReturn(
          Future.failed(
            new StatusRuntimeException(Status.INTERNAL)
          )
        )

      val request = FakeRequest(DELETE, "/user/1")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.deleteUserById(1L).apply(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsString(result) must include("gRPC error")
    }

    "return 500 when generic exception occurs" in {
      when(mockUserService.deleteUserById(1L))
        .thenReturn(Future.failed(new Exception("Delete failed")))

      val request = FakeRequest(DELETE, "/user/1")
        .withHeaders("Authorization" -> "Bearer valid_token")

      val result: Future[Result] = controller.deleteUserById(1L).apply(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsString(result) must include("ERROR OCCURRED")
    }
  }
}
