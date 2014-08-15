package com.baidu.akka.deploy.master

object RecoveryState extends Enumeration {
  type MasterState = Value

  val STANDBY, ALIVE, RECOVERING, COMPLETING_RECOVERY = Value
}
