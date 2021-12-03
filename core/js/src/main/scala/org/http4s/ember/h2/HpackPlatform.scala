package org.http4s.ember.h2

import cats.effect._
import cats.effect.std._
import cats.syntax.all._
import scodec.bits._
import cats.data._

import scala.scalajs.js.JSConverters._

private[h2] trait HpackPlatform {

  def create[F[_]](implicit F: Async[F]): F[Hpack[F]] = F.delay {
    val compressor = new facade.Compressor(facade.HpackOptions(128))
    val decompressor = new facade.Decompressor(facade.HpackOptions(128))
    new Hpack[F] {
      def encodeHeaders(headers: NonEmptyList[(String, String, Boolean)]): F[ByteVector] = {
        val jsHeaders = headers.map { case (name, value, huffman) =>
          facade.Header(name, value, huffman)
        }.toList.toJSArray
        F.delay(compressor.write(jsHeaders)) *> F.delay(ByteVector.view(compressor.read()))
      }

      def decodeHeaders(bv: ByteVector): F[NonEmptyList[(String, String)]] = {
        F.delay(decompressor.write(bv.toUint8Array)) *>
          F.delay(decompressor.execute()) *>
          WriterT.liftF[F, List[facade.Header], List[facade.Header]](F.delay(Option(decompressor.read()).toList))
            .flatTap(WriterT.tell)
            .iterateUntil(_.isEmpty)
            .written
            .flatMap { jsHeaders =>
              NonEmptyList.fromList(jsHeaders.toList.map(h => (h.name, h.value))).liftTo(new NoSuchElementException)
            }
      }
    }
  }

}