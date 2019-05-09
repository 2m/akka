/*
 * Copyright (C) 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.javadsl;

import akka.NotUsed;
import akka.stream.StreamTest;
import akka.testkit.AkkaJUnitActorSystemResource;
import akka.testkit.AkkaSpec;
import org.junit.ClassRule;
import org.junit.Test;

class FlowWithContextTest extends StreamTest {

  public FlowWithContextTest() {
    super(actorSystemResource);
  }

  @ClassRule
  public static AkkaJUnitActorSystemResource actorSystemResource =
      new AkkaJUnitActorSystemResource("SinkTest", AkkaSpec.testConf());

  @Test
  public void mustCompileComposingFlows() throws Exception {
    final FlowWithContext<Integer, NotUsed, Integer, NotUsed, NotUsed> flow1 =
        FlowWithContext.create();
    final FlowWithContext<Integer, NotUsed, String, NotUsed, NotUsed> flow2 =
        FlowWithContext.<Integer, NotUsed>create().map(Object::toString);

    final FlowWithContext<Integer, NotUsed, String, NotUsed, NotUsed> flow3 = flow1.via(flow2);
  }
}
