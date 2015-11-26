package akka.stream.impl.fusing

import akka.pattern.ask
import akka.stream.testkit.AkkaSpec
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Sink
import akka.stream.impl.ActorMaterializerImpl
import akka.stream.impl.StreamSupervisor._
import akka.util.Timeout
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.duration._
import scala.util.Try
import akka.stream.scaladsl.FlowGraph
import akka.stream.ClosedShape
import akka.stream.scaladsl.Broadcast
import akka.stream.scaladsl.RunnableGraph
import akka.stream.scaladsl.Merge

class FusingSpec extends AkkaSpec with ScalaFutures {

  implicit val timeout = Timeout(1.second)

  "fusing" should {
    "fuse source.single -> map -> map -> sink.foreach" in {
      implicit val mat = ActorMaterializer().asInstanceOf[ActorMaterializerImpl]

      println("===== SOURCE TO MAP =======")
      val g1 = Source.maybe[Int].map(_ + 1)

      println("===== MAP TO MAP ======")
      val g2 = g1.map(_ * 3)

      println("====== TO SINK ======")
      val g3 = g2.to(Sink.foreach(println))

      // TODO fix mat value computation and complete source.maybe
      g3.run()

      whenReady((mat.supervisor ? GetChildren).mapTo[Children]) { c ⇒
        c.children.size shouldBe 2 // shouldBe 1 when able to fuse graph stages on different stream layout levels
      }

      mat.shutdown()
    }

    "fuse graph" in {
      // ignored
      implicit val mat = ActorMaterializer().asInstanceOf[ActorMaterializerImpl]

      println("===== SOURCE =======")
      val g1 = Source.maybe[Int]

      println("===== SOURCE TO GRAPH =======")
      val g2 = FlowGraph.create(g1) { implicit b ⇒
        source ⇒
          import FlowGraph.Implicits._

          val broadcast = b.add(Broadcast[Int](2))
          val merge = b.add(Merge[Int](2))

          source ~> broadcast.in
          broadcast.out(0) ~> merge.in(0)
          broadcast.out(1) ~> merge.in(1)
          merge.out ~> Sink.foreach(println)

          ClosedShape
      }

      val runnable = RunnableGraph.fromGraph(g2)
      runnable.run()

      whenReady((mat.supervisor ? GetChildren).mapTo[Children]) { c ⇒
        c.children.size shouldBe 2 // shouldBe 1 when able to fuse graph stages on different stream layout levels
      }

      mat.shutdown()
    }
  }

}
