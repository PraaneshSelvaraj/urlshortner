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

  def addUser(user: User): Future[Long] = {
    dbConfig.db.run(
      (users returning users.map(_.id)) += user
    )
  }

  def findUserByEmail(email: String): Future[Option[User]] = {
    dbConfig.db.run(users.filter(_.email === email).result.headOption)
  }

  def authenticate(email: String, password: String): Future[Option[User]] = {
    findUserByEmail(email) map {
      case Some(user) =>
        if (BCrypt.checkpw(password, user.password)) Some(user)
        else None
      case None => None
    }
  }

}
