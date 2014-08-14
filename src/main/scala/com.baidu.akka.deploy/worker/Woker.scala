package deploy.worker

import akka.actor.{Props, Actor}
import com.typesafe.config.Config

/**
 * Created by edwardsbean on 14-8-14.
 */
class Worker(conf:Config) extends Actor{
  // 发送心跳包 (heartbeat timeout) / 4 milliseconds
  val HEARTBEAT_MILLIS = conf.getLong("scy.worker.timeout") * 1000 / 4

  override def preStart(): Unit = {

  }

  def receive = {
    case _ => context.stop(self)
  }
}
