import play.core.PlayVersion.{pekkoVersion, pekkoHttpVersion}
import play.grpc.gen.scaladsl.{PlayScalaClientCodeGenerator, PlayScalaServerCodeGenerator}
import com.typesafe.sbt.packager.docker.{DockerAlias, DockerChmodType, DockerPermissionStrategy}

lazy val commonSettings = Seq(
  scalaVersion := "2.13.16",
  scalacOptions ++= List("-encoding", "utf8", "-deprecation", "-feature", "-unchecked"),
  dockerExposedPorts := Seq(9000),
  dockerBaseImage := "eclipse-temurin:21-jdk",
  dockerChmodType := DockerChmodType.UserGroupWriteExecute,
  dockerPermissionStrategy := DockerPermissionStrategy.CopyChown
)

lazy val root = (project in file("."))
  .aggregate(`notification-service`, `rest-service`, `user-service`)
  .settings(commonSettings *)
  .settings(
    name := "url-shortner",
    publish / skip := true
  )

lazy val `notification-service` = (project in file("services/notification"))
  .enablePlugins(PlayScala, PekkoGrpcPlugin, PlayPekkoHttp2Support, DockerPlugin)
  .settings(commonSettings *)
  .settings(
    name := "notification-service",
    version := "1.0.0",
    pekkoGrpcGeneratedLanguages := Seq(PekkoGrpc.Scala),
    pekkoGrpcExtraGenerators += PlayScalaServerCodeGenerator,
    libraryDependencies ++= Seq(
      guice,
      "org.playframework" %% "play-grpc-runtime" % "0.12.3",
      "org.apache.pekko" %% "pekko-discovery" % pekkoVersion,
      "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion,
      "com.typesafe.play" %% "play-slick" % "5.4.0",
      "mysql" % "mysql-connector-java" % "8.0.33",
      "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test,
      "com.h2database" % "h2" % "2.3.232" % Test,
      "org.mockito" %% "mockito-scala" % "2.0.0" % Test
    ),
    Docker / dockerAlias := DockerAlias(None, None, "notification-service", Some("1.0.0"))
  )

lazy val `user-service` = (project in file("services/user"))
  .enablePlugins(PlayScala, PekkoGrpcPlugin, PlayPekkoHttp2Support, DockerPlugin)
  .settings(commonSettings *)
  .settings(
    name := "user-service",
    version := "1.0.0",
    pekkoGrpcGeneratedLanguages := Seq(PekkoGrpc.Scala),
    pekkoGrpcExtraGenerators += PlayScalaServerCodeGenerator,
    libraryDependencies ++= Seq(
      guice,
      "org.playframework" %% "play-grpc-runtime" % "0.12.3",
      "org.apache.pekko" %% "pekko-discovery" % pekkoVersion,
      "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion,
      "com.typesafe.play" %% "play-slick" % "5.4.0",
      "mysql" % "mysql-connector-java" % "8.0.33",
      "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test,
      "com.h2database" % "h2" % "2.3.232" % Test,
      "org.mockito" %% "mockito-scala" % "2.0.0" % Test
    ),
    Docker / dockerAlias := DockerAlias(None, None, "user-service", Some("1.0.0"))
  )

lazy val `rest-service` = (project in file("services/rest"))
  .enablePlugins(PlayScala, PekkoGrpcPlugin, PlayPekkoHttp2Support, DockerPlugin)
  .settings(commonSettings *)
  .settings(
    name := "rest-service",
    version := "1.0.0",
    pekkoGrpcGeneratedLanguages := Seq(PekkoGrpc.Scala),
    pekkoGrpcExtraGenerators += PlayScalaClientCodeGenerator,
    Compile / PB.protoSources ++= Seq(
      baseDirectory.value / ".." / "notification" / "app" / "protobuf",
      baseDirectory.value / ".." / "user" / "app" / "protobuf"
    ),
    libraryDependencies ++= Seq(
      guice,
      "org.playframework" %% "play" % play.core.PlayVersion.current,
      "org.playframework" %% "play-json" % "3.0.5",
      "org.playframework" %% "play-grpc-runtime" % "0.12.3",
      "com.typesafe.play" %% "play-slick" % "5.4.0",
      "com.typesafe.play" %% "play-slick-evolutions" % "5.4.0",
      "mysql" % "mysql-connector-java" % "8.0.33",
      "io.lettuce" % "lettuce-core" % "6.8.1.RELEASE",
      "org.apache.pekko" %% "pekko-discovery" % pekkoVersion,
      "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test,
      "com.h2database" % "h2" % "2.3.232" % Test,
      "org.mockito" %% "mockito-scala" % "2.0.0" % Test
    ),
    Docker / dockerAlias := DockerAlias(None, None, "rest-service", Some("1.0.0"))
  )
