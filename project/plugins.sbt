val playGrpcV = "0.12.2"

addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.8")
addSbtPlugin("org.apache.pekko" % "pekko-grpc-sbt-plugin" % "1.0.2")

libraryDependencies += "org.playframework" %% "play-grpc-generators" % playGrpcV
