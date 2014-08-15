package com.baidu.akka.deploy.worker

import akka.actor._
import com.typesafe.config.{ConfigFactory, Config}
import scala.concurrent.duration._
import java.util.Date
import java.text.SimpleDateFormat
import scala.Some
import com.baidu.akka.deploy.DeployMessages.{Heartbeat, SendHeartbeat, RegisteredWorker, RegisterWorker}
import com.baidu.akka.deploy.master.Master

/**
 * Created by edwardsbean on 14-8-14.
 * 启动时带入固定参数，其他可选参数由config带入
 */
class Worker(host: String,
             port: Int,
             conf: Config,
             actorSystemName: String,
             actorName: String
              ) extends Actor {

  import context.dispatcher

  //用于生成workerId
  def createDateFormat = new SimpleDateFormat("yyyyMMddHHmmss")

  // 发送心跳包的时间间隔 (heartbeat timeout) / 4 milliseconds
  val HEARTBEAT_MILLIS = conf.getLong("worker.timeout") * 1000 / 4
  //注册超时时间
  val REGISTRATION_TIMEOUT = 20.seconds
  //注册重试次数
  val REGISTRATION_RETRIES = 3
  //worker唯一标示符
  val workerId = generateWorkerId()
  //worker所在的akkUrl
  val akkaUrl = "akka.tcp://%s@%s:%s/user/%s".format(actorSystemName, host, port, actorName)


  //master相关可能会变，所以用变量可修改
  var master: ActorSelection = null
  var masterAddress: Address = null
  var activeMasterUrl: String = ""
  //为什么要同步呢
  @volatile var registered = false
  @volatile var connected = false
  var registrationRetryTimer: Option[Cancellable] = None

  override def preStart(): Unit = {
    val masterHost = conf.getConfig("master").getString("host")
    val masterPort = conf.getConfig("master").getInt("port")
    activeMasterUrl = "akka.tcp://%s@%s:%s/user/%s"
      .format("edwardsbeanCluster", masterHost, masterPort, "Master")
    //注册到master
    RegisterWithMaster()

  }

  def receive = {
    //一旦注册成功，master会发送RegisteredWorker消息过来。
    case RegisteredWorker(masterUrl) =>
      println("注册成功，开始发送心跳包")
      //logInfo("Successfully registered with master " + masterUrl)
      registered = true
      connected = true
      //和master通信成功之后，定期发送心跳包
      context.system.scheduler.schedule(0 millis, HEARTBEAT_MILLIS millis, self, SendHeartbeat)

    //发送心跳包
    case SendHeartbeat =>
      println("发送心跳包")
      if (connected) {
        master ! Heartbeat(workerId)
      }

    //处理其他工作
    //case ?
    case _ => context.stop(self)
  }

  def RegisterWithMaster() {
     tryRegisterWithMaster()
  }

  def tryRegisterWithMaster() {
    val actor = context.actorSelection(activeMasterUrl)
    println("尝试连接到master " + activeMasterUrl)
//    actor ! RegisterWorker(workerId, host, port)
    actor ! RegisterWorker(workerId, host, port)
    master = actor
  }

  def generateWorkerId(): String = {
    "worker-%s-%s-%d".format(createDateFormat.format(new Date), host, port)
  }
}

object Worker {
  def main(argStrings: Array[String]) {
    val conf = ConfigFactory.load("worker")
    val host = conf.getConfig("worker").getString("host")
    val port = conf.getConfig("worker").getInt("port")

    val actorSystem = ActorSystem("edwardsWorker", conf)
    val actor = actorSystem.actorOf(Props(classOf[Worker],
      host,
      port,
      conf,
      "edwardsWorker",
      "Worker")
      , "Worker")
    actorSystem.awaitTermination()
  }
}