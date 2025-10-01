package repositories

import models.User
import models.tables.UsersTable.users
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

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

}
