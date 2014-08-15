package com.baidu.akka.deploy.master

import akka.actor._
import com.typesafe.config.{ConfigFactory, Config}
import scala.collection.mutable.{HashMap,HashSet}
import com.baidu.akka.deploy.DeployMessages.RegisterWorkerFailed
import scala.Some
import com.baidu.akka.deploy.DeployMessages.Heartbeat
import com.baidu.akka.deploy.DeployMessages.RegisteredWorker
import com.baidu.akka.deploy.DeployMessages.RegisterWorker

/**
 * Created by edwardsbean on 14-8-14.
 */
class Master(host: String, port: Int, conf: Config) extends Actor with ActorLogging{
  val masterUrl = host + ":" + port
  //
  val workers = new HashSet[WorkerInfo]
  //worker id获取worker详细信息
  val idToWorker = new HashMap[String, WorkerInfo]
  //存放actor的akka url
  val addressToWorker = new HashMap[Address, WorkerInfo]

  var state = RecoveryState.ALIVE

  override def preStart(){
    log.debug("master starting at " + context.self.path)
//    context.system.scheduler.schedule(0 millis, WORKER_TIMEOUT millis, self, CheckForWorkerTimeOut)

    //保存master信息，以便故障恢复

    //通过zookeeper进行热备
  }
  override def receive: Receive = {
    //worker请求注册上来
    case RegisterWorker(id, workerHost, workerPort) =>
    {
      log.info("Registering worker " + workerHost)
      if (state == RecoveryState.STANDBY) {
        // ignore, don't send response
      } else if (idToWorker.contains(id)) {
        sender ! RegisterWorkerFailed("Duplicate worker ID")
      } else {
        val worker = new WorkerInfo(id, workerHost, workerPort, sender)
        if (registerWorker(worker)) {
          log.debug("响应注册请求")
          sender ! RegisteredWorker(masterUrl)
          //do something
          schedule()
        } else {
          val workerAddress = worker.actor.path.address
//          logWarning("Worker registration failed. Attempted to re-register worker at same " +
//            "address: " + workerAddress)
          sender ! RegisterWorkerFailed("Attempted to re-register worker at same address: "
            + workerAddress)
        }
      }
    }

    //worker定时汇报心跳
    case Heartbeat(workerId) => {
      idToWorker.get(workerId) match {
        case Some(workerInfo) =>
          log.debug("worker " + workerInfo.host + "汇报心跳")
          workerInfo.lastHeartbeat = System.currentTimeMillis()
        case None =>
//          logWarning("Got heartbeat from unregistered worker " + workerId)
      }
    }
  }

  def registerWorker(worker: WorkerInfo): Boolean = {

    val workerAddress = worker.actor.path.address
    if (addressToWorker.contains(workerAddress)) {
      val oldWorker = addressToWorker(workerAddress)
      if (oldWorker.state == WorkerState.UNKNOWN) {
        // A worker registering from UNKNOWN implies that the worker was restarted during recovery.
        // The old worker must thus be dead, so we will remove it and accept the new worker.
        removeWorker(oldWorker)
      } else {
//        logInfo("Attempted to re-register worker at same address: " + workerAddress)
        return false
      }
    }

    workers += worker
    idToWorker(worker.id) = worker
    addressToWorker(workerAddress) = worker
    log.debug("worker " + worker.host + "注册到master")
    true
  }

  //do something
  def schedule(){
      log.debug("开始调度任务")
  }
  def removeWorker(worker: WorkerInfo) {
    //    logInfo("Removing worker " + worker.id + " on " + worker.host + ":" + worker.port)
    worker.setState(WorkerState.DEAD)
    idToWorker -= worker.id
    addressToWorker -= worker.actor.path.address
  }
}




object Master {
  val systemName = "edwardsbeanCluster"
  private val actorName = "Master"

  def main(argStrings: Array[String]) {
    val conf = ConfigFactory.load("master")
    val host = conf.getConfig("master").getString("host")
    val port = conf.getConfig("master").getInt("port")
    val actorSystem = ActorSystem(systemName,conf)
    actorSystem.actorOf(Props(classOf[Master],host,port,conf),actorName)
//    actorSystem.awaitTermination()
  }

}