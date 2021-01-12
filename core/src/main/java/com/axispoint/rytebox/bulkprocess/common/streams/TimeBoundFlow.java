package com.axispoint.rytebox.bulkprocess.common.streams;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import akka.NotUsed;
import akka.stream.Attributes;
import akka.stream.FlowShape;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.stage.AbstractInHandler;
import akka.stream.stage.AbstractOutHandler;
import akka.stream.stage.GraphStageLogic;
import akka.stream.stage.GraphStageLogicWithLogging;
import akka.stream.stage.GraphStageWithMaterializedValue;
import scala.Tuple2;

public class TimeBoundFlow<A> extends GraphStageWithMaterializedValue<FlowShape<A, A>, CompletionStage<Boolean>> {

    private final Inlet<A> in = Inlet.create("TimeBoundFlow.in");
    private final Outlet<A> out = Outlet.create("TimeBoundFlow.out");
    private final FlowShape<A, A> shape = FlowShape.of(in, out);

    private final Supplier<Optional<NotUsed>> canContinue;

    public TimeBoundFlow(Supplier<Integer> getTimeRemainingMillis, Duration finalizationWindow) {
        this.canContinue = () -> {
            Duration timeRemaining = Duration.ofMillis(getTimeRemainingMillis.get());
            boolean complete = timeRemaining.minus(finalizationWindow).toMillis() > 0L;
            if (!complete) {
                return Optional.empty();
            }
            return Optional.of(NotUsed.notUsed());
        };
    }

    @Override
    public Tuple2<GraphStageLogic, CompletionStage<Boolean>> createLogicAndMaterializedValue(
            Attributes inheritedAttributes) {
        CompletableFuture<Boolean> mat = new CompletableFuture<>();

        GraphStageLogic logic = new GraphStageLogicWithLogging(shape()) {

          {
            setHandler(
                in,
                new AbstractInHandler() {
                  @Override
                  public void onPush() {
                    push(out, grab(in));
                  }

                    @Override
                    public void onUpstreamFinish() throws Exception {
                      mat.complete(false);
                      super.onUpstreamFinish();
                    }
                });

            setHandler(
                out,
                new AbstractOutHandler() {
                  @Override
                  public void onPull() {
                      pullOrTimeout();
                  }
                });
          }

          private void pullOrTimeout() {
              Optional<NotUsed> cont = canContinue.get();
              cont
                 .ifPresentOrElse(
                         c -> pull(in),
                         () -> {
                             mat.complete(true);
                             log().info("Stream timed out");
                             pull(in);
                             completeStage();
                         }
                 );
          }
        };

        return Tuple2.apply(logic, mat);
    }

    @Override
    public FlowShape<A, A> shape() {
        return shape;
    }
}
