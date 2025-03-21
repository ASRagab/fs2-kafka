/*
 * Copyright 2018-2022 OVO Energy Limited
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package fs2.kafka.internal

import cats.effect.{Async, Resource, Sync}
import cats.syntax.all._
import fs2.kafka.AdminClientSettings
import fs2.kafka.admin.MkAdminClient
import fs2.kafka.internal.syntax._
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.common.KafkaFuture

private[kafka] sealed abstract class WithAdminClient[F[_]] {
  def apply[A](f: AdminClient => KafkaFuture[A]): F[A]
}

private[kafka] object WithAdminClient {
  def apply[F[_], G[_]](
    mk: MkAdminClient[F],
    settings: AdminClientSettings
  )(implicit F: Sync[F], G: Async[G]): Resource[F, WithAdminClient[G]] =
    Resource {
      mk(settings).map { adminClient =>
        val withAdminClient =
          new WithAdminClient[G] {
            override def apply[A](f: AdminClient => KafkaFuture[A]): G[A] =
              G.delay(f(adminClient)).cancelable
          }

        val close =
          F.blocking(adminClient.close(settings.closeTimeout.asJava))

        (withAdminClient, close)
      }
    }
}
