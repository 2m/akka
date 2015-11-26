package akka.stream.impl.fusing

import akka.stream._
import akka.stream.Attributes._
import akka.stream.impl.StreamLayout.CompositeModule
import akka.stream.impl.Stages.SymbolicGraphStage
import akka.stream.impl.Stages
import akka.stream.scaladsl._
import akka.stream.testkit.AkkaSpec

import scala.collection.immutable

class GraphAssemblySpec extends AkkaSpec with GraphInterpreterSpecKit {

  "GraphModule" should {
    "fuse flows" in {
      val f1 = new Flow[Int, Int, Unit](SymbolicGraphStage(Stages.Map(identity[Int], name("f1"))).module)
      val f2 = new Flow[Int, Int, Unit](SymbolicGraphStage(Stages.Map(identity[Int], name("f2"))).module)
      val fusedFlow = f1.via(f2)

      val m1 = f1.module.asInstanceOf[GraphModule]
      val m2 = f2.module.asInstanceOf[GraphModule]
      val fused = fusedFlow.module.asInstanceOf[CompositeModule].subModules.head.asInstanceOf[GraphModule]

      fused.assembly.stages(0) should be theSameInstanceAs m1.assembly.stages(0)
      fused.assembly.stages(1) should be theSameInstanceAs m2.assembly.stages(0)

      fused.assembly.ins shouldBe Array(m1.assembly.ins.head, m2.assembly.ins.head, null)
      fused.assembly.inOwners shouldBe Array(0, 1, -1)

      fused.assembly.outs shouldBe Array(null, m1.assembly.outs.last, m2.assembly.outs.last)
      fused.assembly.outOwners shouldBe Array(-1, 0, 1)

      fused.shape.inlets.map(_.toString) shouldBe Seq("f1.in")
      fused.shape.outlets.map(_.toString) shouldBe Seq("f2.out")
    }

    "fuse graphs without internal connections" in {
      /**
       * This illustrates Graph Assembly layouts before and after fusing
       * when connecting b.o1 to m.i1.
       *
       *                      Broadcast                           Merge
       *
       *                       ┌———————————————————————————————————┐
       *                       |                                   |
       *                       |      ┌——————————————+——————┐      |
       *                       |      |              |      |      |
       *            +——————+⋯  |  ⋯+——————+——————+   |   +——————+——————+———————+
       *  ins:      | b.in |   |   | null | null |   |   | m.i1 | m.i2 | null  |
       *  inOw:     | 0    |   |   | -1   | -1   |   |   | 0    | 0    | -1    |
       *  outs:     | null |   |   | b.o1 | b.o2 |   |   | null | null | m.out |
       *  outOw:    | -1   |   |   | 0    | 0    |   |   | -1   | -1   | 0     |
       *            +——————+⋯  |  ⋯+——————+——————+   |   +——————+——————+———————+
       *               |       |            |        |                     |
       *               |       |            |        |                     |
       * Fused:     +——V———+———V——+⋯        |    ⋯+——V———+⋯    ⋯+——————+———V———+
       *  ins:      | b.in | m.i2 |         |     | m.i1 |      | null | null  |
       *  inOw:     | 0    | 1    |         |     | -1   |      | -1   | -1    |
       *  outs:     | null | null |         |     | b.o1 |      | b.o2 | m.out |
       *  outOw:    | -1   | -1   |         |     | 0    |      | 0    | 1     |
       *            +——————+——————+⋯        |    ⋯+——————+⋯    ⋯+——^———+———————+
       *                                    |                      |
       *                                    └——————————————————————┘
       */
      val b = Broadcast[Int](2)
      val m = Merge[Int](2)

      val fusedGraph = FlowGraph.create() { implicit builder ⇒
        import FlowGraph.Implicits._

        val bcast = builder.add(b)
        val merge = builder.add(m)

        bcast.out(0) ~> merge.in(0)

        BidiShape(bcast.in, merge.out, merge.in(1), bcast.out(1))
      }

      val m1 = b.module.asInstanceOf[GraphModule]
      val m2 = m.module.asInstanceOf[GraphModule]
      val fused = fusedGraph.module.asInstanceOf[CompositeModule].subModules.head.subModules.head.asInstanceOf[GraphModule]

      fused.assembly.stages shouldBe m1.assembly.stages ++ m2.assembly.stages

      fused.assembly.ins shouldBe Array(b.in, m.in(1), m.in(0), null, null)
      fused.assembly.inOwners shouldBe Array(0, 1, 1, -1, -1)

      fused.assembly.outs shouldBe Array(null, null, b.out(0), b.out(1), m.out)
      fused.assembly.outOwners shouldBe Array(-1, -1, 0, 0, 1)

      fused.shape.inlets.map(_.toString) shouldBe Seq("Broadcast.in", "Merge.in1")
      fused.shape.outlets.map(_.toString) shouldBe Seq("Broadcast.out1", "Merge.out")
    }

    "fuse graphs with internal connections" in new TestSetup {
      /**
       * This illustrates Graph Assembly layouts before and after fusing
       * when connecting m.out1 to ba.in.
       *
       *                   Broadcast (b) + Merge (m)                           Balance (ba) + concat (c)
       *
       *                             ┌————————————————————————————————————————————┐
       *                             |                                            |
       *            +——————+——————+⋯ |  ⋯+——————+——————+———————+       +———————+——————+———————+———————+———————+
       *  ins:      | b.in | m.i2 |  |   | m.i1 | null | null  |       | ba.in | c.i2 | c.i1  | null  | null  |
       *  inOw:     | 0    | 1    |  |   | 1    | -1   | -1    |———+———| 0     | 1    | 1     | -1    | -1    |
       *  outs:     | null | null |  |   | b.o1 | b.o2 | m.out |   |   | null  | null | ba.o1 | ba.o2 | c.out |
       *  outOw:    | -1   | -1   |  |   | 0    | 0    | 1     |   |   | -1    | -1   | 0     | 0     | 1     |
       *            +——————+——————+⋯ |  ⋯+——————+——————+———————+   |   +———————+——————+———————+———————+———————+
       *               |      |      |      |      |               |                      |       |       |
       *               |      |      |      |      |               |           ┌——————————┘       |       |
       * Fused:     +——V———+——V———+——V———+——V———+⋯ |  ⋯+———————+———V———+⋯      |     ⋯+———————+———V———+———V———+
       *  ins:      | b.in | m.i2 | c.i2 | m.i1 |  |   | c.i1  | ba.in |       |      | null  | null  | null  |
       *  inOw:     | 0    | 1    | 3    | 1    |  |   | 3     | 2     |       |      | -1    | -1    | -1    |
       *  outs:     | null | null | null | b.o1 |  |   | ba.o1 | m.out |       |      | b.o2  | ba.o2 | c.out |
       *  outOw:    | -1   | -1   | -1   | 0    |  |   | 2     | 1     |       |      | 0     | 2     | 3     |
       *            +——————+——————+——————+——————+⋯ |  ⋯+———^———+———————+⋯      |     ⋯+———^———+———————+———————+
       *                                           |       |                   |          |
       *                                           |       └———————————————————┘          |
       *                                           |                                      |
       *                                           └——————————————————————————————————————┘
       */
      val bcast = Broadcast[Int](2)
      val merge = Merge[Int](2)

      val assembly1 = builder(bcast, merge)
        .connect(Upstream, bcast.in)
        .connect(Upstream, merge.in(1))
        .connect(bcast.out(0), merge.in(0))
        .connect(bcast.out(1), Downstream)
        .connect(merge.out, Downstream)
        .buildAssembly()
      val graph1 = new Graph[BidiShape[Int, Int, Int, Int], Unit] {
        def shape = BidiShape(bcast.in, merge.out, merge.in(1), bcast.out(1))
        def module = GraphModule(assembly1, shape, Attributes.none)
        def withAttributes(attr: Attributes) = ???
      }

      val balance = Balance[Int](2)
      val concat = Concat[Int](2)

      val assembly2 = builder(balance, concat)
        .connect(Upstream, balance.in)
        .connect(Upstream, concat.in(1))
        .connect(balance.out(0), concat.in(0))
        .connect(balance.out(1), Downstream)
        .connect(concat.out, Downstream)
        .buildAssembly()
      val graph2 = new Graph[BidiShape[Int, Int, Int, Int], Unit] {
        def shape = BidiShape(balance.in, concat.out, concat.in(1), balance.out(1))
        def module = GraphModule(assembly2, shape, Attributes.none)
        def withAttributes(attr: Attributes) = ???
      }

      val fusedGraph = FlowGraph.create() { implicit builder ⇒
        import FlowGraph.Implicits._

        val g1 = builder.add(graph1)
        val g2 = builder.add(graph2)

        g1.out1 ~> g2.in1

        AmorphousShape(immutable.Seq(g1.in1, g1.in2, g2.in2), immutable.Seq(g1.out2, g2.out1, g2.out2))
      }

      val fused = fusedGraph.module.asInstanceOf[CompositeModule].subModules.head.subModules.head.asInstanceOf[GraphModule]

      fused.assembly.stages shouldBe assembly1.stages ++ assembly2.stages

      fused.assembly.ins shouldBe Array(bcast.in, merge.in(1), concat.in(1), merge.in(0), concat.in(0), balance.in, null, null, null)
      fused.assembly.inOwners shouldBe Array(0, 1, 3, 1, 3, 2, -1, -1, -1)

      fused.assembly.outs shouldBe Array(null, null, null, bcast.out(0), balance.out(0), merge.out, bcast.out(1), balance.out(1), concat.out)
      fused.assembly.outOwners shouldBe Array(-1, -1, -1, 0, 2, 1, 0, 2, 3)

      fused.shape.inlets.map(_.toString) shouldBe Seq("Broadcast.in", "Merge.in1", "Concat.in1")
      fused.shape.outlets.map(_.toString) shouldBe Seq("Broadcast.out1", "Concat.out", "Balance.out1")
    }
  }

}
