package com.google.cloud.functions.invoker;

import static com.google.cloud.functions.invoker.BackgroundFunctionExecutor.backgroundFunctionTypeArgument;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BackgroundFunctionExecutorTest {
  private static class PubSubMessage {
    String data;
    Map<String, String> attributes;
    String messageId;
    String publishTime;
  }

  private static class PubSubFunction implements BackgroundFunction<PubSubMessage> {
    @Override
    public void accept(PubSubMessage payload, Context context) {}
  }

  @Test
  public void backgroundFunctionTypeArgument_simple() {
    assertThat(backgroundFunctionTypeArgument(PubSubFunction.class)).hasValue(PubSubMessage.class);
  }

  private abstract static class Parent implements BackgroundFunction<PubSubMessage> {}

  private static class Child extends Parent {
    @Override
    public void accept(PubSubMessage payload, Context context) {}
  }

  @Test
  public void backgroundFunctionTypeArgument_superclass() {
    assertThat(backgroundFunctionTypeArgument(Child.class)).hasValue(PubSubMessage.class);
  }

  private interface GenericParent<T> extends BackgroundFunction<T> {}

  private static class GenericChild implements GenericParent<PubSubMessage> {
    @Override
    public void accept(PubSubMessage payload, Context context) {}
  }

  @Test
  public void backgroundFunctionTypeArgument_genericInterface() {
    assertThat(backgroundFunctionTypeArgument(GenericChild.class)).hasValue(PubSubMessage.class);
  }

  @SuppressWarnings("rawtypes")
  private static class ForgotTypeParameter implements BackgroundFunction {
    @Override
    public void accept(Object payload, Context context) {}
  }

  @Test
  public void backgroundFunctionTypeArgument_raw() {
    @SuppressWarnings("unchecked")
    Class<? extends BackgroundFunction<?>> c =
        (Class<? extends BackgroundFunction<?>>) (Class<?>) ForgotTypeParameter.class;
    assertThat(backgroundFunctionTypeArgument(c)).isEmpty();
  }

  @Test
  public void parseLegacyEventPubSub() throws IOException {
    try (Reader reader =
        new InputStreamReader(getClass().getResourceAsStream("/pubsub_background.json"))) {
      Event event = BackgroundFunctionExecutor.parseLegacyEvent(reader);

      Context context = event.getContext();
      assertThat(context.eventType()).isEqualTo("google.pubsub.topic.publish");
      assertThat(context.eventId()).isEqualTo("1");
      assertThat(context.timestamp()).isEqualTo("2021-06-28T05:46:32.390Z");

      JsonObject data = event.getData().getAsJsonObject();
      assertThat(data.get("data").getAsString()).isEqualTo("eyJmb28iOiJiYXIifQ==");
      String attr = data.get("attributes").getAsJsonObject().get("test").getAsString();
      assertThat(attr).isEqualTo("123");
    }
  }

  @Test
  public void parseLegacyEventPubSubEmulator() throws IOException {
    try (Reader reader =
        new InputStreamReader(getClass().getResourceAsStream("/pubsub_emulator.json"))) {
      Event event = BackgroundFunctionExecutor.parseLegacyEvent(reader);

      Context context = event.getContext();
      assertThat(context.eventType()).isEqualTo("google.pubsub.topic.publish");
      assertThat(context.eventId()).isEqualTo("1");
      assertThat(context.timestamp()).isNotNull();
      ;

      JsonObject data = event.getData().getAsJsonObject();
      assertThat(data.get("data").getAsString()).isEqualTo("eyJmb28iOiJiYXIifQ==");
      String attr = data.get("attributes").getAsJsonObject().get("test").getAsString();
      assertThat(attr).isEqualTo("123");
    }
  }
}
