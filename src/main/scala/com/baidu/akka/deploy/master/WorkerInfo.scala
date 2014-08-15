package com.baidu.akka.deploy.master

import akka.actor.ActorRef

/**
 * Created by edwardsbean on 14-8-15.
 */
class WorkerInfo(
                  val id: String,
                  val host: String,
                  val port: Int,
                  val actor: ActorRef
                  ) extends Serializable {
  //transient标志着序列化时不需要保存该变量
  @transient var state: WorkerState.Value = _
  @transient var lastHeartbeat: Long = _
  init()
  private def init() {
    state = WorkerState.ALIVE
    lastHeartbeat = System.currentTimeMillis()
  }

  private def readObject(in: java.io.ObjectInputStream) : Unit = {
    in.defaultReadObject()
    init()
  }

  def setState(state: WorkerState.Value) = {
    this.state = state
  }

}
