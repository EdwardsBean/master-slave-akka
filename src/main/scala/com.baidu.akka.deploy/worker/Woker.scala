package deploy.worker

import akka.actor._
import com.typesafe.config.Config
import scala.concurrent.duration._
import java.util.Date
import java.text.SimpleDateFormat
import scala.Some
import com.baidu.akka.deploy.DeployMessages.{Heartbeat, SendHeartbeat, RegisteredWorker, RegisterWorker}

/**
 * Created by edwardsbean on 14-8-14.
 * 启动时带入固定参数，其他可选参数由config带入
 */
class Worker(conf: Config,
             host: String,
             port: Int,
             actorSystemName: String,
             actorName: String
              ) extends Actor {
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
  var activeMasterWebUiUrl: String = ""
  //为什么要同步呢
  @volatile var registered = false
  @volatile var connected = false
  var registrationRetryTimer: Option[Cancellable] = None

  override def preStart(): Unit = {
    //注册到master
    RegisterWithMaster()

  }

  def receive = {
    //一旦注册成功，master会发送RegisteredWorker消息过来。
    case RegisteredWorker(masterUrl, masterWebUiUrl) =>
      //logInfo("Successfully registered with master " + masterUrl)
      registered = true
      //和master通信成功之后，定期发送心跳包
      context.system.scheduler.schedule(0 millis, HEARTBEAT_MILLIS millis, self, SendHeartbeat)

    //发送心跳包
    case SendHeartbeat =>
        if (connected) { master ! Heartbeat(workerId) }

    //处理其他工作
    //case ?
    case _ => context.stop(self)
  }

  def RegisterWithMaster() {
    var retries = 0
    registrationRetryTimer = Some {
      context.system.scheduler.schedule(REGISTRATION_TIMEOUT, REGISTRATION_TIMEOUT) {
        try{
          retries += 1
          if (registered) {
            registrationRetryTimer.foreach(_.cancel())
            //重试超过3次，放弃连接
          } else if (retries >= REGISTRATION_RETRIES) {
            //logError("All masters are unresponsive! Giving up.")
            System.exit(1)
          } else {
            tryRegisterWithMaster()
          }
        }catch {
          //是从scheduler中跑的任务，所以要抛出uncatch异常？
          case t: Throwable => println("connect error,此处要抛异常")
        }
      }
    }
  }

  def tryRegisterWithMaster(){
    val actor = context.actorSelection(activeMasterUrl)
    actor ! RegisterWorker(workerId, host, port)
  }

  def generateWorkerId(): String = {
    "worker-%s-%s-%d".format(createDateFormat.format(new Date), host, port)
  }
}
