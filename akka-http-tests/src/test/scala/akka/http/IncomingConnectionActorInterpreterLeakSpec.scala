package akka.http

import akka.actor._
//import akka.http.scaladsl._
import akka.http._
import model._
import HttpMethods._
import marshalling._
import akka.stream.ActorFlowMaterializer
import akka.stream.impl.fusing.ActorInterpreter
import akka.stream.scaladsl.{ Flow, Sink, Source }
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import org.scalatest.{ Matchers, WordSpec }

class IncomingConnectionActorInterpreterLeakSpec extends WordSpec with Matchers {

  val defaultTimeout = 20.seconds

  /**
   * A simple server which replies to all requests with 200 OK and mirrors the request's entity (to confirm that it's
   * been streamed properly both ways). We are interested in the server's [[ActorSystem]]
   */
  class TestServer() {

    implicit val actorSystem = ActorSystem("ServerActorSystem")
    implicit val materializer = ActorFlowMaterializer(namePrefix = Some("ServerMaterializer"))

    val testServerFuture = Http().bind("127.0.0.1", 0).to {
      Sink.foreach { incomingConnection ⇒
        incomingConnection.handleWith(Flow[HttpRequest].map { request ⇒
          HttpResponse(entity = request.entity)
        })
      }
    }.run()

    val server = Await.result(testServerFuture, defaultTimeout)

    val host: String = server.localAddress.getHostName
    val port: Int = server.localAddress.getPort
  }

  /**
   * A simple test client which sends requets to a particular host and port.
   * We are not interested in its [[ActorSystem]]; there do not appear to be any actor leaks from outgoing connections.
   */
  class TestClient(host: String, port: Int) {

    private implicit val actorSystem = ActorSystem("ClientActorSystem")
    implicit val materializer = ActorFlowMaterializer(namePrefix = Some("clientMaterializer"))

    def sendRequest(request: HttpRequest): Future[HttpResponse] = {

      val connection =
        Http().outgoingConnection(host, port)

      Source.single(request) via connection runWith Sink.head()
    }
  }

  "A simple Akka server" should {

    "not leak ActorInterpreters after incoming connections are closed" in {
      // Given
      implicit val testSystem = ActorSystem("TestSystem")
      implicit val materializer = ActorFlowMaterializer()
      import testSystem.dispatcher

      val server = new TestServer()
      val client = new TestClient(server.host, server.port)

      val entityString = "Test HTTP entity"
      val request = HttpRequest(POST, Uri(s"http://${server.host}:${server.port}"), Nil, Await.result(Marshal(entityString).to[RequestEntity], defaultTimeout))

      /**
       * We make a request, stream in the response and check that the entity matches what we expect.
       */
      def makeRequestAndCheckResponse() = {
        val response = client.sendRequest(request)
          .flatMap(_.entity.toStrict(defaultTimeout))
          .map(_.data.utf8String)

        Await.result(response, defaultTimeout) shouldBe entityString
      }

      // When
      /**
       * We make an initial request to ensure all flows are initialized
       */
      makeRequestAndCheckResponse()

      val initialInterpreterCount: Int = countActorsInSystem(server.actorSystem)(classOf[ActorInterpreter])

      /**
       * We make a series of requests. Each of these will leak one [[ActorInterpreter]] in the server's actor system.
       */
      val numberOfRequests = 100
      (1 to numberOfRequests).foreach(_ ⇒ makeRequestAndCheckResponse())

      // Then
      /**
       * We wait to allow all streams enough time to complete. In M4, 1 second is enough
       * for the [[ActorInterpreter]] count to fall back to its initial value.
       */
      val cooldownAllowance = 30.seconds
      Thread.sleep(cooldownAllowance.toMillis)

      val finalInterpreterCount = countActorsInSystem(server.actorSystem)(classOf[ActorInterpreter])

      /**
       * In RC1, this will fail: the difference between the final and initial counts
       * will be precisely equal to the [[numberOfRequests]].
       */
      finalInterpreterCount should be <= initialInterpreterCount
    }
  }

  /**
   * A method that counts the actors of each type inside a given actor system.
   */
  def countActorsInSystem(system: ActorSystem) = {

    val actorCountsByClass = new mutable.Map.WithDefault[Class[_ <: Actor], Int](mutable.Map.empty, _ ⇒ 0)

    def walk(ref: ActorRef): Unit = {
      ref match {
        case c: ActorRefWithCell ⇒
          c.underlying match {
            case ac: ActorCell ⇒
              if (ac.actor != null) {
                val actorClass: Class[_ <: Actor] = ac.actor.getClass
                actorCountsByClass.put(actorClass, actorCountsByClass(actorClass) + 1)
              }
              c.children.foreach(walk)

            case unrecognizedCell: Cell ⇒
              unrecognizedCell.childrenRefs.children.foreach(walk)
          }
        case _ ⇒
      }
    }

    walk(system.asInstanceOf[ActorSystemImpl].lookupRoot)

    actorCountsByClass
  }
}
