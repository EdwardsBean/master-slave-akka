package com.baidu.akka.deploy

/**
 * Created by edwardsbean on 14-8-14.
 */
trait DeployMessage extends Serializable

object DeployMessages {

  case class RegisterWorker(
                             id: String,
                             host: String,
                             port: Int)
    extends DeployMessage {
    //检查master的地址
    assert(host.indexOf(':') != -1, "Required hostname")
    assert(port > 0)
  }
  case class RegisteredWorker(masterUrl: String) extends DeployMessage

  case object SendHeartbeat

  case class Heartbeat(workerId: String) extends DeployMessage

  case class RegisterWorkerFailed(message: String) extends DeployMessage
}
