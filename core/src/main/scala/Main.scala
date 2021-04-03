package org.http4s.ember.h2

import cats._
import cats.syntax.all._
import cats.effect._
import cats.effect.syntax.all._
import cats.effect.std._
import cats.effect.kernel._
import scodec.bits._
import com.twitter.hpack._
import fs2._
import fs2.concurrent._
import fs2.io.net._
import fs2.io.net.tls._
import com.comcast.ip4s._
import org.http4s.implicits._

import org.typelevel.ci.CIString
import scala.concurrent.duration._

object ServerMain extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {

    ServerTest.test[IO]
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }

}

object ServerTest {
  import fs2._
  import org.http4s._
  import org.http4s.implicits._
  import java.nio.file.{Paths, Path}
  import com.comcast.ip4s._
  def simpleApp[F[_]: Monad] = HttpRoutes.of[F]{ case _ => Response[F](Status.Ok).withEntity("Hi There!").pure[F]}.orNotFound

  def test[F[_]: Async] = for {
    // sg <- Network[F].socketGroup()
    wd <- Resource.eval(Sync[F].delay(System.getProperty("user.dir")))
    currentFilePath <- Resource.eval(Sync[F].delay(Paths.get(wd, "keystore.jks")))
    tlsContext <- Resource.eval(Network[F].tlsContext.fromKeyStoreFile(currentFilePath, "changeit".toCharArray, "changeit".toCharArray))//)
    _ <- H2Server.impl(
      Ipv4Address.fromString("0.0.0.0").get,
      Port.fromInt(8080).get,
      tlsContext, 
      simpleApp[F]
      )
  } yield ()
  
}

object ClientMain extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {

    ClientTest.test[IO]
      // .use(_ => IO.never)
      .as(ExitCode.Success)
  }
}

object ClientTest {

  def test[F[_]: Async: Parallel] = {
    Resource.eval(Network[F].tlsContext.insecure).flatMap{tls => 
    H2Client.impl[F](Frame.Settings.ConnectionSettings.default, tls
      // .copy(
      // initialWindowSize = Frame.Settings.SettingsInitialWindowSize(1000000),
      // maxFrameSize = Frame.Settings.SettingsMaxFrameSize(500000)
      // )
    )}.use{ c => 
      val p = c.run(org.http4s.Request[F](
        org.http4s.Method.GET, 
        // uri = uri"https://github.com/"
        // uri = uri"https://en.wikipedia.org/wiki/HTTP/2"
        // uri = uri"https://twitter.com/"
        // uri = uri"https://banno.com/"
        // uri = uri"https://http2.golang.org/reqinfo"
        uri = uri"https://localhost:8080/"
      ))//.putHeaders(org.http4s.headers.Connection(CIString("keep-alive")) ))
        .use(_.body.compile.drain)
        // .use(_.body.chunks.fold(0){case (i, c) => i + c.size}.evalMap(i => Sync[F].delay(println("Total So Far: $i"))).compile.drain >> Sync[F].delay(println("Body Received")))
        // (p,  p, p).parTupled
        // (p,p, p, p).parTupled
        // Temporal[F].sleep(10.second) >> 
        p
        // List.fill(50)(p.attempt).parSequence.flatTap(a => Sync[F].delay(println(a)))
    }
  }
}





