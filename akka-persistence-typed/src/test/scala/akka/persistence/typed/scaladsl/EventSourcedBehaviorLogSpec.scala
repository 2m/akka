/*
 * Copyright (C) 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.typed.scaladsl
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.event.Logging.LogEvent
import akka.persistence.typed.{PersistenceId, RecoveryCompleted, SnapshotCompleted, SnapshotFailed}
import akka.testkit.EventFilter
import com.typesafe.config.ConfigFactory
import org.scalatest.WordSpecLike

object EventSourcedBehaviorLogSpec {

  val conf = ConfigFactory.parseString("akka.loggers = [akka.testkit.TestEventListener]")
    .withFallback(EventSourcedBehaviorSpec.conf)
    .withFallback(ConfigFactory.load())

  sealed trait Command
  case object Command extends Command
  sealed trait Event
  case object Event extends Event
  case class State()

  def eventFilterFor(logMsg: String) = EventFilter.custom({
    case l: LogEvent if l.logClass == classOf[EventSourcedBehaviorLogSpec] && l.message == logMsg ⇒ true
    case l: LogEvent ⇒
      println(l.logClass -> l.message)
      false
  }, occurrences = 1)
}

class EventSourcedBehaviorLogSpec() extends ScalaTestWithActorTestKit(EventSourcedBehaviorLogSpec.conf) with WordSpecLike {

  import EventSourcedBehaviorLogSpec._

  private val pidCounter = new AtomicInteger(0)
  private def nextPid(): PersistenceId = PersistenceId(s"c${pidCounter.incrementAndGet()})")

  def behavior: Behavior[Command] = Behaviors.setup { ctx ⇒
    ctx.log.info("setting-up-behavior")
    EventSourcedBehavior[Command, Event, State](
      nextPid(),
      emptyState = State(),
      commandHandler = (_, _) => {
        ctx.log.info("command-received")
        Effect.persist(Event)
      },
      eventHandler = (state, _) ⇒ {
        ctx.log.info("event-received")
        state
      }
    ).receiveSignal {
      case RecoveryCompleted(_) ⇒ ctx.log.info("recovery-completed")
      case SnapshotCompleted(_) ⇒
      case SnapshotFailed(_, _) ⇒
    }
  }

  implicit val untyped = system.toUntyped

  "log from context" should {

    "log from setup" in {
      eventFilterFor("setting-up-behavior").intercept {
        spawn(behavior)
      }
    }

    "log from recovery completed" in {
      eventFilterFor("recovery-completed").intercept {
        spawn(behavior)
      }
    }

    "log from command handler" in {
      eventFilterFor("command-received").intercept {
        spawn(behavior) ! Command
      }
    }

    "log from event handler" in {
      eventFilterFor("event-received").intercept {
        spawn(behavior) ! Command
      }
    }
  }

}
