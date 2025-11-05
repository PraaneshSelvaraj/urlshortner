package repositories

import models.User
import models.tables.UsersTable.users
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import org.mindrot.jbcrypt.BCrypt
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UserRepo @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig.profile.api._

  def getAllUsers: Future[Seq[User]] =
    dbConfig.db.run(users.result)

  def getUserById(id: Long): Future[Option[User]] =
    dbConfig.db.run(users.filter(_.id === id).result.headOption)

  def deleteUserById(id: Long): Future[Int] =
    dbConfig.db.run(
      users
        .filter(user => user.id === id && user.is_deleted === false)
        .map(_.is_deleted)
        .update(true)
    )

  def getRefreshToken(id: Long): Future[Option[String]] = {
    dbConfig.db
      .run(
        users.filter(_.id === id).map(_.refresh_token).result.headOption
      )
      .map(_.flatten)
  }

  def updateRefreshToken(id: Long, refreshToken: String): Future[Int] = {
    dbConfig.db.run(
      users.filter(_.id === id).map(_.refresh_token).update(Some(refreshToken))
    )
  }

  def addUser(user: User): Future[Long] = {
    val userToAdd = user.password match {
      case Some(password) if !password.startsWith("$2") =>
        user.copy(password = Some(BCrypt.hashpw(password, BCrypt.gensalt())))
      case _ => user
    }
    dbConfig.db.run(
      (users returning users.map(_.id)) += userToAdd
    )
  }

  def findUserByEmail(email: String): Future[Option[User]] = {
    dbConfig.db.run(users.filter(_.email === email).result.headOption)
  }

  def logoutUser(userId: Long): Future[Int] =
    dbConfig.db.run(users.filter(_.id === userId).map(_.refresh_token).update(None))

  def authenticate(email: String, password: String): Future[Option[User]] = {
    findUserByEmail(email) map {
      case Some(user) =>
        user.password match {
          case Some(hashedPassword) =>
            if (BCrypt.checkpw(password, hashedPassword)) Some(user)
            else None
          case None =>
            None
        }
      case None => None
    }
  }

}
